package io.github.saberbird.cidget.core

/** State of a single run (AGGREGATION.md §2). [rank]: higher is worse. */
public enum class RunState(public val rank: Int) {
    FAILED(4),
    RUNNING(3),
    INCOMPLETE(2),
    SUCCESS(1),
    NEUTRAL(0),
    UNKNOWN(2),
    ;

    public companion object {
        private val failed = setOf("failure", "timed_out", "startup_failure", "action_required")
        private val running = setOf("queued", "in_progress", "waiting", "pending", "requested")
        private val incomplete = setOf("cancelled", "stale")
        private val neutral = setOf("skipped", "neutral")

        /** Maps raw API strings to a state. Unknown values never throw; they become [UNKNOWN]. */
        public fun of(status: String, conclusion: String?): RunState = when {
            status in running -> RUNNING
            status != "completed" -> UNKNOWN
            conclusion in failed -> FAILED
            conclusion in incomplete -> INCOMPLETE
            conclusion == "success" -> SUCCESS
            conclusion in neutral -> NEUTRAL
            else -> UNKNOWN
        }
    }
}

/** The [RunState] of this run. */
public val Run.state: RunState
    get() = RunState.of(status, conclusion)
