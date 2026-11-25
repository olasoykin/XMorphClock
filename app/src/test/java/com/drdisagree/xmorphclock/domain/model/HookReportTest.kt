package com.drdisagree.xmorphclock.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HookReportTest {

    private fun sampleReport(
        timestamp: Long = 20_000L,
        moduleUpdatedAt: Long = 10_000L,
        moduleLoadedAt: Long = 12_000L,
        clockFace: String? = "Stacked",
        adapter: String? = "LockscreenElementFactory"
    ) = HookReport(
        timestamp = timestamp,
        framework = "LSPosed",
        systemUiVersion = "16 (36)",
        buildId = "AP4A",
        adapter = adapter,
        signals = listOf(HookSignal("CLOCK_SIZE", "KeyguardClockViewModel")),
        clockFace = clockFace,
        errors = emptyList(),
        moduleUpdatedAt = moduleUpdatedAt,
        moduleLoadedAt = moduleLoadedAt
    )

    @Test
    fun staleWhenModuleUpdatedBeforeCurrentInstall() {
        val report = sampleReport(moduleUpdatedAt = 5_000L, moduleLoadedAt = 6_000L)
        assertTrue(report.isStale(installedAt = 10_000L))
        assertEquals(ClockAttachState.STALE, report.attachState(installedAt = 10_000L))
    }

    @Test
    fun staleFallbackUsesModuleLoadedAtWhenModuleUpdatedAtIsZero() {
        val report = sampleReport(moduleUpdatedAt = 0L, moduleLoadedAt = 6_000L)
        assertTrue(report.isStale(installedAt = 10_000L))
        assertEquals(ClockAttachState.STALE, report.attachState(installedAt = 10_000L))
    }

    @Test
    fun notStaleWhenModuleUpdatedMatchesInstallWithinSlack() {
        val report = sampleReport(moduleUpdatedAt = 9_000L, moduleLoadedAt = 10_500L)
        assertFalse(report.isStale(installedAt = 10_000L))
        assertEquals(ClockAttachState.ATTACHED, report.attachState(installedAt = 10_000L))
    }

    @Test
    fun freshSinceRejectsReportFromBeforeSystemUiRestartEvenWithNewTimestamp() {
        val dyingProcessReport = sampleReport(
            timestamp = 50_500L,
            moduleUpdatedAt = 10_000L,
            moduleLoadedAt = 12_000L
        )
        assertFalse(dyingProcessReport.isFreshSince(sinceMs = 50_000L, installedAt = 10_000L))
    }

    @Test
    fun freshSinceAcceptsReportLoadedAfterRestart() {
        val restartedReport = sampleReport(
            timestamp = 53_000L,
            moduleUpdatedAt = 10_000L,
            moduleLoadedAt = 52_000L
        )
        assertTrue(restartedReport.isFreshSince(sinceMs = 50_000L, installedAt = 10_000L))
    }
}
