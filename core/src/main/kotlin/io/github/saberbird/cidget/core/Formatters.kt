package io.github.saberbird.cidget.core

import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** `AGE` column (WIDGET.md §3): `now`, `59m`, `23h`, `29d`, `11mo`, `3y`. Future times count as `now`. */
public fun formatAge(from: Instant, now: Instant): String {
    val d: Duration = now - from
    return when {
        d < 1.minutes -> "now"
        d < 1.hours -> "${d.inWholeMinutes}m"
        d < 1.days -> "${d.inWholeHours}h"
        d < 30.days -> "${d.inWholeDays}d"
        d < 365.days -> "${(d.inWholeDays / 30).coerceIn(1, 11)}mo"
        else -> "${d.inWholeDays / 365}y"
    }
}
