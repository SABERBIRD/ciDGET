package io.github.saberbird.cidget.core

/** Unicode status glyph used in share text, whatever style the widget shows (WIDGET.md §4). */
public fun Status.unicodeGlyph(): String = when (this) {
    Status.RED -> "✗"
    Status.YELLOW -> "●"
    Status.GREEN -> "✓"
    Status.GREY -> "○"
}

private fun percentEncode(s: String): String = buildString {
    for (b in s.encodeToByteArray()) {
        val c = b.toInt() and 0xFF
        val safe = c in 'a'.code..'z'.code || c in 'A'.code..'Z'.code || c in '0'.code..'9'.code ||
            c == '-'.code || c == '_'.code || c == '.'.code || c == '~'.code || c == '/'.code
        if (safe) append(c.toChar()) else append('%').append("0123456789ABCDEF"[c shr 4]).append("0123456789ABCDEF"[c and 15])
    }
}

/** `https://github.com/{owner}/{repo}/tree/{branch}`, the page a row without runs opens. */
public fun branchPageUrl(repo: String, branch: String): String =
    "https://github.com/$repo/tree/" + percentEncode(branch)

/** The URL a row opens and the COPY cell copies: the representative run, else the branch page. */
public fun rowUrl(repo: String, status: BranchStatus): String =
    status.representative?.htmlUrl ?: branchPageUrl(repo, status.branch)

/**
 * Text shared by the SHARE cell: `CI ✗ testING/main #412 CI: https://github.com/…`.
 * Uses the repo name without owner. A branch without runs has no run part: `CI ○ testING/dev: https://…`.
 */
public fun shareText(repo: String, status: BranchStatus): String {
    val run = status.representative
    val runPart = if (run == null) "" else buildString {
        append(" #").append(run.runNumber)
        if (run.workflowName.isNotBlank()) append(' ').append(run.workflowName)
    }
    return "CI ${status.status.unicodeGlyph()} ${repo.substringAfter('/')}/${status.branch}$runPart: ${rowUrl(repo, status)}"
}
