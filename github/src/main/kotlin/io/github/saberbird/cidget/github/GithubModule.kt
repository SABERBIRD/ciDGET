package io.github.saberbird.cidget.github

/** Marker for the `:github` module. The GitHub client arrives in a later task. */
public object GithubModule {
    /** Module name, used only to keep the module non-empty until the client exists. */
    public const val NAME: String = "github"
}
