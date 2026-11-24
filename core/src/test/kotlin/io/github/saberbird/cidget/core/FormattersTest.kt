package io.github.saberbird.cidget.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class FormattersTest {
    private val now = t(10_000_000)

    @Test fun ageBuckets() {
        assertEquals("now", formatAge(now - 59.seconds, now))
        assertEquals("now", formatAge(now + 5.minutes, now))
        assertEquals("1m", formatAge(now - 60.seconds, now))
        assertEquals("59m", formatAge(now - 59.minutes - 59.seconds, now))
        assertEquals("1h", formatAge(now - 60.minutes, now))
        assertEquals("23h", formatAge(now - 23.hours - 59.minutes, now))
        assertEquals("1d", formatAge(now - 24.hours, now))
        assertEquals("29d", formatAge(now - 29.days - 23.hours, now))
        assertEquals("1mo", formatAge(now - 30.days, now))
        assertEquals("11mo", formatAge(now - 364.days, now))
        assertEquals("1y", formatAge(now - 365.days, now))
        assertEquals("3y", formatAge(now - 1200.days, now))
    }

    @Test fun glyphFallbackMapping() {
        assertEquals("✓", GlyphFallback.resolve("✓") { true })
        val none = { _: String -> false }
        assertEquals(
            listOf("x", "*", "v", "o", "cp", "sh", "r", "~"),
            listOf("✗", "●", "✓", "○", "⧉", "↗", "⟳", "…").map { GlyphFallback.resolve(it, none) },
        )
        assertEquals("?", GlyphFallback.resolve("?", none))
        assertEquals("✗", GlyphFallback.resolve("✗") { it == "✗" })
    }
}
