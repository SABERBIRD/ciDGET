package io.github.saberbird.cidget.core

import kotlinx.serialization.Serializable

/** Colour state of a branch. */
@Serializable
public enum class Status {
    RED,
    YELLOW,
    GREEN,
    GREY,
    ;

    /** Badness for "worst of repo": RED > YELLOW > GREY > GREEN. */
    internal val worstRank: Int
        get() = when (this) {
            RED -> 3
            YELLOW -> 2
            GREY -> 1
            GREEN -> 0
        }
}
