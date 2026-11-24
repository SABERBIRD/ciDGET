package io.github.saberbird.cidget.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.github.saberbird.cidget.data.RefreshOutcome
import io.github.saberbird.cidget.graph

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

/** Enqueues refresh work. Only the one-time path exists so far; periodic refresh arrives in T11. */
object RefreshScheduler {
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
}
