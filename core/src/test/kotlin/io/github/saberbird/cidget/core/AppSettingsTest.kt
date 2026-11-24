package io.github.saberbird.cidget.core

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class AppSettingsTest {
    private val now = Instant.parse("2026-11-24T12:00:00Z")

    @Test fun defaultsFollowD13() {
        val s = AppSettings()
        assertEquals(30, s.refreshMinutes)
        assertTrue(s.batteryNotLow)
        assertFalse(s.unmeteredOnly)
        assertEquals(7, s.expiryWarnDays)
        assertEquals(60.minutes, s.staleAfter())
    }

    @Test fun refreshStepsEndInOff() {
        assertEquals(60, AppSettings().withRefreshStep(1).refreshMinutes)
        assertEquals(15, AppSettings().withRefreshStep(-1).refreshMinutes)
        assertEquals(15, AppSettings(refreshMinutes = 15).withRefreshStep(-1).refreshMinutes)
        assertNull(AppSettings(refreshMinutes = 360).withRefreshStep(1).refreshMinutes)
        assertEquals(360, AppSettings(refreshMinutes = null).withRefreshStep(-1).refreshMinutes)
        assertNull(AppSettings(refreshMinutes = null).staleAfter())
    }

    @Test fun normalizedSnapsToAnAllowedInterval() {
        assertEquals(15, AppSettings(refreshMinutes = 5).normalized().refreshMinutes)
        assertEquals(60, AppSettings(refreshMinutes = 70).normalized().refreshMinutes)
        assertEquals(30, AppSettings(expiryWarnDays = 99).normalized().expiryWarnDays)
        assertEquals(1, AppSettings().withExpiryWarnStep(-10).expiryWarnDays)
    }

    @Test fun staleAfterTwiceTheInterval() {
        val s = AppSettings(refreshMinutes = 30)
        assertFalse(isStale(now - 59.minutes, now, s))
        assertTrue(isStale(now - 61.minutes, now, s))
        assertFalse(isStale(null, now, s))
        assertFalse(isStale(now - 100.days, now, AppSettings(refreshMinutes = null)))
    }

    @Test fun expiryWarningWithinTheWindowOnly() {
        val s = AppSettings(expiryWarnDays = 7)
        assertEquals(3, expiryWarningDays(now + 3.days + 2.hours, now, s))
        assertEquals(0, expiryWarningDays(now + 5.hours, now, s))
        assertNull(expiryWarningDays(now + 8.days, now, s))
        assertNull(expiryWarningDays(now - 1.hours, now, s))
        assertNull(expiryWarningDays(null, now, s))
    }
}
