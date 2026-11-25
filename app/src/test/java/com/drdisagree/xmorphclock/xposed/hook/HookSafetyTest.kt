package com.drdisagree.xmorphclock.xposed.hook

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HookSafetyTest {

    @Test
    fun circuitBreakerTripsWhenRateLimitExceededInWindow() {
        val breaker = HookSafety.CircuitBreaker(maxCallsPerWindow = 3, maxConsecutiveErrors = 3, windowMs = 1_000L)

        assertTrue(breaker.beforeCall(100L))
        assertTrue(breaker.beforeCall(200L))
        assertTrue(breaker.beforeCall(300L))
        assertFalse(breaker.beforeCall(400L))

        // New window resets counter
        assertTrue(breaker.beforeCall(1_200L))
    }

    @Test
    fun circuitBreakerTripsOnConsecutiveErrorsAndResetsOnSuccess() {
        val breaker = HookSafety.CircuitBreaker(maxCallsPerWindow = 10, maxConsecutiveErrors = 3)

        assertFalse(breaker.recordFailure())
        assertFalse(breaker.recordFailure())
        breaker.recordSuccess()
        assertFalse(breaker.recordFailure())
        assertFalse(breaker.recordFailure())
        assertTrue(breaker.recordFailure())
    }

    @Test
    fun tripMarksHookAsTripped() {
        val id = "test_hook_trip_${System.nanoTime()}"
        assertFalse(HookSafety.isTripped(id))
        HookSafety.trip(id, "unit_test")
        assertTrue(HookSafety.isTripped(id))
    }
}
