package io.github.saberbird.cidget.core

import kotlinx.serialization.Serializable

/** Why a branch is grey. */
@Serializable
public enum class GreyReason {
    /** No runs exist for the head commit. */
    NO_RUNS,

    /** Something was cancelled, stale or unknown, and nothing is failing or running. */
    INCOMPLETE,

    /** Every workflow was skipped or neutral. */
    NOTHING_RAN,

    /** The head commit's runs could not be loaded. */
    NOT_LOADED,
}
