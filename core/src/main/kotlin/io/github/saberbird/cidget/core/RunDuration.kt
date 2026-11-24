package io.github.saberbird.cidget.core

import kotlinx.datetime.Instant
import kotlin.time.Duration

/**
 * Duration of [run]: `updatedAt - runStartedAt` when completed, `now - runStartedAt` while running,
 * null when `runStartedAt` is missing. Never negative.
 */
public fun runDuration(run: Run, now: Instant): Duration? {
    val start = run.runStartedAt ?: return null
    val end = if (run.status == "completed") run.updatedAt else now
    return (end - start).coerceAtLeast(Duration.ZERO)
}
