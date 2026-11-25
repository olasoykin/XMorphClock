package com.drdisagree.xmorphclock.xposed.hook

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.drdisagree.xmorphclock.xposed.report.HookReporter
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

object HookSafety {

    private const val STATE_FILE = "xmorph_hook_safety.state"
    private const val MAX_CRASH_STRIKES = 2
    private const val CRASH_WINDOW_MS = 180_000L
    private const val HEALTHY_UPTIME_MS = 10_000L
    private const val WATCHDOG_INTERVAL_MS = 2_000L
    private const val WATCHDOG_STALL_MS = 4_500L

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val breakers = ConcurrentHashMap<String, CircuitBreaker>()
    private val trippedHooks = ConcurrentHashMap.newKeySet<String>()
    private val activeHook = AtomicReference<String?>(null)
    private val activeHookSinceMs = AtomicLong(0L)
    private val watchdogStarted = AtomicBoolean(false)

    @Volatile
    var isSafeMode: Boolean = false
        private set

    /**
     * Checks whether previous startups crashed or froze before reaching healthy uptime.
     * Returns true if Safe Mode was activated and hooks should be skipped.
     */
    fun beginSession(context: Context?, nowMs: Long = System.currentTimeMillis()): Boolean {
        val file = context?.let(::stateFile) ?: return false
        val previous = readState(file)
        if (previous != null && previous.strikes >= MAX_CRASH_STRIKES && (nowMs - previous.lastStartMs) in 0..CRASH_WINDOW_MS) {
            isSafeMode = true
            runCatching {
                HookEnv.log("HookSafety: Safe Mode active (${previous.strikes} incomplete starts); hooks disabled")
                HookReporter.probe("safety: safe_mode_active strikes=${previous.strikes}")
            }
            return true
        }
        val nextStrikes = if (previous != null && (nowMs - previous.lastStartMs) in 0..CRASH_WINDOW_MS) {
            previous.strikes + 1
        } else {
            1
        }
        writeState(file, SessionState(nextStrikes, nowMs))
        mainHandler.postDelayed({ markHealthy(file) }, HEALTHY_UPTIME_MS)
        startWatchdog()
        return false
    }

    fun markHealthy(file: File) {
        writeState(file, SessionState(0, System.currentTimeMillis()))
    }

    fun isTripped(hookId: String): Boolean = isSafeMode || hookId in trippedHooks

    inline fun <T> runGuarded(
        hookId: String,
        maxCallsPerSecond: Int = DEFAULT_MAX_CALLS_PER_SEC,
        maxConsecutiveErrors: Int = DEFAULT_MAX_ERRORS,
        block: () -> T
    ): T? {
        if (!enter(hookId, maxCallsPerSecond, maxConsecutiveErrors)) return null
        return try {
            val result = block()
            exitSuccess(hookId)
            result
        } catch (t: Throwable) {
            exitFailure(hookId, maxConsecutiveErrors, t)
            null
        }
    }

    @PublishedApi
    internal fun enter(
        hookId: String,
        maxCallsPerSecond: Int,
        maxConsecutiveErrors: Int
    ): Boolean {
        if (isTripped(hookId)) return false
        val breaker = breakers.getOrPut(hookId) { CircuitBreaker(maxCallsPerSecond, maxConsecutiveErrors) }
        val allowed = breaker.beforeCall(SystemClock.elapsedRealtime())
        if (!allowed) {
            trip(hookId, "rate_limit_exceeded (>${maxCallsPerSecond}/s)")
            return false
        }
        activeHook.set(hookId)
        activeHookSinceMs.set(SystemClock.elapsedRealtime())
        return true
    }

    @PublishedApi
    internal fun exitSuccess(hookId: String) {
        if (activeHook.get() == hookId) {
            activeHook.set(null)
        }
        breakers[hookId]?.recordSuccess()
    }

    @PublishedApi
    internal fun exitFailure(hookId: String, maxConsecutiveErrors: Int, error: Throwable) {
        if (activeHook.get() == hookId) {
            activeHook.set(null)
        }
        val breaker = breakers.getOrPut(hookId) { CircuitBreaker(DEFAULT_MAX_CALLS_PER_SEC, maxConsecutiveErrors) }
        if (breaker.recordFailure()) {
            trip(hookId, "consecutive_errors (${error.javaClass.simpleName}: ${error.message})")
        }
    }

    fun trip(hookId: String, reason: String) {
        if (trippedHooks.add(hookId)) {
            runCatching {
                HookEnv.log("HookSafety: Tripped circuit breaker for '$hookId' -> $reason")
                HookReporter.probe("safety: tripped=$hookId reason=$reason")
            }
        }
    }

    private fun startWatchdog() {
        if (!watchdogStarted.compareAndSet(false, true)) return
        val thread = Thread({
            while (!isSafeMode) {
                try {
                    Thread.sleep(WATCHDOG_INTERVAL_MS)
                } catch (_: InterruptedException) {
                    return@Thread
                }
                val hook = activeHook.get()
                if (hook != null) {
                    val elapsed = SystemClock.elapsedRealtime() - activeHookSinceMs.get()
                    if (elapsed > WATCHDOG_STALL_MS) {
                        trip(hook, "stall_${elapsed}ms")
                        activeHook.set(null)
                    }
                }
                val pingAck = AtomicBoolean(false)
                val pingAt = SystemClock.elapsedRealtime()
                mainHandler.post { pingAck.set(true) }
                try {
                    Thread.sleep(WATCHDOG_STALL_MS)
                } catch (_: InterruptedException) {
                    return@Thread
                }
                if (!pingAck.get()) {
                    val stallMs = SystemClock.elapsedRealtime() - pingAt
                    val stalledHook = activeHook.get() ?: "main_thread"
                    trip(stalledHook, "main_thread_unresponsive_${stallMs}ms")
                }
            }
        }, "XMorph-SafetyWatchdog").apply {
            isDaemon = true
        }
        thread.start()
    }

    private fun stateFile(context: Context): File = File(context.codeCacheDir, STATE_FILE)

    private fun readState(file: File): SessionState? = runCatching {
        if (!file.exists()) return null
        val parts = file.readText().trim().split(',')
        if (parts.size != 2) return null
        SessionState(parts[0].toInt(), parts[1].toLong())
    }.getOrNull()

    private fun writeState(file: File, state: SessionState) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText("${state.strikes},${state.lastStartMs}")
        }
    }

    internal data class SessionState(val strikes: Int, val lastStartMs: Long)

    internal class CircuitBreaker(
        private val maxCallsPerWindow: Int,
        private val maxConsecutiveErrors: Int,
        private val windowMs: Long = 1_000L
    ) {
        private val windowStartMs = AtomicLong(0L)
        private val windowCount = AtomicInteger(0)
        private val errorCount = AtomicInteger(0)

        @Synchronized
        fun beforeCall(nowMs: Long): Boolean {
            val start = windowStartMs.get()
            if (start == 0L || nowMs - start >= windowMs) {
                windowStartMs.set(nowMs)
                windowCount.set(1)
                return true
            }
            return windowCount.incrementAndGet() <= maxCallsPerWindow
        }

        fun recordSuccess() {
            errorCount.set(0)
        }

        fun recordFailure(): Boolean = errorCount.incrementAndGet() >= maxConsecutiveErrors
    }

    const val DEFAULT_MAX_CALLS_PER_SEC = 25
    const val DEFAULT_MAX_ERRORS = 3
}
