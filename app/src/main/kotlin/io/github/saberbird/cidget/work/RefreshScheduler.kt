package io.github.saberbird.cidget.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.github.saberbird.cidget.core.AppSettings
import io.github.saberbird.cidget.data.RefreshOutcome
import io.github.saberbird.cidget.di.AppGraph
import io.github.saberbird.cidget.graph
import java.util.concurrent.TimeUnit

/** Refreshes one repo (`owner/repo` from the input data). Handled errors are `success`; transient ones `retry`. */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = inputData.getString(KEY_REPO) ?: return Result.success()
        return when (applicationContext.graph.refresh.refresh(repo)) {
            RefreshOutcome.SUCCESS -> Result.success()
            RefreshOutcome.RETRY -> Result.retry()
        }
    }

    companion object {
        const val KEY_REPO = "repo"
    }
}

/** Reloads the list of pickable repos for all tokens. */
class RepoListWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = when (applicationContext.graph.repoLists.refresh()) {
        RefreshOutcome.SUCCESS -> Result.success()
        RefreshOutcome.RETRY -> Result.retry()
    }
}

/**
 * The periodic `refresh-all` work (ARCHITECTURE.md §3): refreshes each distinct repo some widget shows, one after
 * another. Repos that are rate limited or whose token is invalid are skipped by [io.github.saberbird.cidget.data.RefreshRepository].
 * Transient errors wait for the next period. Widgets are redrawn at the end so ages and the stale marker move on.
 */
class RefreshAllWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val graph = applicationContext.graph
        graph.settings.reposInUse().sorted().forEach { graph.refresh.refresh(it) }
        graph.redrawAll()
        return Result.success()
    }
}

/** What the periodic work should look like; null = no periodic work (off, or no widget shows a repo). */
internal data class PeriodicSpec(val minutes: Long, val unmeteredOnly: Boolean, val batteryNotLow: Boolean)

/** Pure: the periodic work for [settings] when [reposInUse] repos are shown. */
internal fun periodicSpec(settings: AppSettings, reposInUse: Int): PeriodicSpec? {
    val minutes = settings.refreshMinutes ?: return null
    if (reposInUse == 0) return null
    return PeriodicSpec(minutes.toLong(), settings.unmeteredOnly, settings.batteryNotLow)
}

/** Enqueues refresh work. */
object RefreshScheduler {
    private const val PERIODIC = "refresh-all"

    /** Expedited unique work `refresh:{repo}`; a second request while one is queued is ignored (KEEP). No network constraint: offline attempts fail fast, record `! offline` and retry with backoff. */
    fun refreshNow(context: Context, repo: String) {
        val request = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setInputData(workDataOf(RefreshWorker.KEY_REPO to repo))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("refresh:$repo", ExistingWorkPolicy.KEEP, request)
    }

    /** Cancels the queued or running refresh of [repo] (it is no longer shown by any widget). */
    fun cancel(context: Context, repo: String) {
        WorkManager.getInstance(context).cancelUniqueWork("refresh:$repo")
    }

    /** Expedited unique work `repolist` (KEEP): loads the repos the tokens can see. */
    fun refreshRepoList(context: Context) {
        val request = OneTimeWorkRequestBuilder<RepoListWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("repolist", ExistingWorkPolicy.KEEP, request)
    }

    /**
     * Enqueues, updates or cancels the periodic `refresh-all` work to match the app settings and the widgets. Safe
     * to call often: an unchanged request keeps its schedule (`UPDATE`).
     */
    suspend fun schedulePeriodic(context: Context, graph: AppGraph) {
        val wm = WorkManager.getInstance(context)
        val spec = periodicSpec(graph.settings.getAppSettings(), graph.settings.reposInUse().size)
        if (spec == null) {
            wm.cancelUniqueWork(PERIODIC)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (spec.unmeteredOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(spec.batteryNotLow)
            .build()
        val request = PeriodicWorkRequestBuilder<RefreshAllWorker>(spec.minutes, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
