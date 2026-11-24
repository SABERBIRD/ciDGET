package io.github.saberbird.cidget.core

import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.days

/** Sort key for branch rows. */
public enum class SortKey { RECENT, STATUS, NAME }

/** Filtering and sorting options (AGGREGATION.md §6). Defaults follow D13 and D21. */
public data class ViewOptions(
    /** Pinned branch names, in pin order. */
    val pinned: List<String> = emptyList(),
    val sortKey: SortKey = SortKey.RECENT,
    /** Drop branches idle for more than this many days; null = keep all. */
    val hideIdleDays: Int? = 30,
    /** Truncate to this many rows; null = no limit. */
    val maxRows: Int? = 50,
    val hideBranchesWithoutRuns: Boolean = false,
)

/** Result of [selectBranches]. [worst] is computed before truncation. */
public data class BranchView(val rows: List<BranchStatus>, val worst: Status)

private val naturalChunks = Regex("\\d+|\\D+")

private val naturalOrder: Comparator<String> = Comparator { a, b ->
    val ca = naturalChunks.findAll(a.lowercase()).map { it.value }.toList()
    val cb = naturalChunks.findAll(b.lowercase()).map { it.value }.toList()
    for (i in 0 until minOf(ca.size, cb.size)) {
        val x = ca[i]
        val y = cb[i]
        val c = if (x[0].isDigit() && y[0].isDigit()) {
            val nx = x.trimStart('0')
            val ny = y.trimStart('0')
            if (nx.length != ny.length) nx.length.compareTo(ny.length) else nx.compareTo(ny)
        } else {
            x.compareTo(y)
        }
        if (c != 0) return@Comparator c
    }
    ca.size.compareTo(cb.size)
}

private val recentOrder: Comparator<BranchStatus> = Comparator { a, b ->
    val noRuns = (a.greyReason == GreyReason.NO_RUNS).compareTo(b.greyReason == GreyReason.NO_RUNS)
    if (noRuns != 0) return@Comparator noRuns
    val x = a.lastActivity
    val y = b.lastActivity
    when {
        x == null && y == null -> 0
        x == null -> 1
        y == null -> -1
        else -> y.compareTo(x)
    }
}

/** Filters, sorts and truncates [branches] for display (AGGREGATION.md §6). Stable. */
public fun selectBranches(branches: List<BranchStatus>, options: ViewOptions, now: Instant): BranchView {
    val pinned = options.pinned.distinct()
    var list = branches
    if (options.hideBranchesWithoutRuns) {
        list = list.filterNot { it.greyReason == GreyReason.NO_RUNS }
    }
    options.hideIdleDays?.let { days ->
        val cutoff = now - days.days
        list = list.filter { it.branch in pinned || it.greyReason == GreyReason.NO_RUNS || it.lastActivity == null || it.lastActivity >= cutoff }
    }
    val byName = list.associateBy { it.branch }
    val pinnedRows = pinned.mapNotNull { byName[it] }
    val rest = list.filter { it.branch !in pinned }
    val sorted = when (options.sortKey) {
        SortKey.RECENT -> rest.sortedWith(recentOrder)
        SortKey.STATUS -> rest.sortedWith(compareBy<BranchStatus> { statusOrder(it.status) }.then(recentOrder))
        SortKey.NAME -> rest.sortedWith { a, b -> naturalOrder.compare(a.branch, b.branch) }
    }
    val all = pinnedRows + sorted
    val worst = all.maxByOrNull { it.status.worstRank }?.status ?: Status.GREY
    val rows = options.maxRows?.let { all.take(it) } ?: all
    return BranchView(rows, worst)
}

private fun statusOrder(s: Status): Int = when (s) {
    Status.RED -> 0
    Status.YELLOW -> 1
    Status.GREY -> 2
    Status.GREEN -> 3
}
