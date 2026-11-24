package io.github.saberbird.cidget.core

import kotlinx.serialization.Serializable

/** The head commit of a branch, from `GET /branches`. */
@Serializable
public data class BranchHead(val name: String, val sha: String)
