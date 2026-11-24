package io.github.saberbird.cidget.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontFamily
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.saberbird.cidget.R
import io.github.saberbird.cidget.core.BranchStatus
import io.github.saberbird.cidget.core.GreyReason
import io.github.saberbird.cidget.core.Status
import io.github.saberbird.cidget.core.ViewOptions
import io.github.saberbird.cidget.core.formatAge
import io.github.saberbird.cidget.core.selectBranches
import io.github.saberbird.cidget.data.RepoSnapshot
import io.github.saberbird.cidget.settings.SettingsActivity
import io.github.saberbird.cidget.widget.config.RepoPickerActivity
import kotlinx.datetime.Instant

private fun style(color: Color) = TextStyle(
    color = ColorProvider(color),
    fontFamily = FontFamily.Monospace,
    fontSize = 13.sp,
)

private fun statusColor(b: BranchStatus): Color = when (b.status) {
    Status.RED -> Palette.red
    Status.YELLOW -> Palette.yellow
    Status.GREEN -> Palette.green
    Status.GREY -> Palette.grey
}

private fun branchUrl(repo: String, branch: String) = "https://github.com/$repo/tree/" + Uri.encode(branch, "/")

private fun viewIntent(appWidgetId: Int, url: String) =
    Intent(Intent.ACTION_VIEW, Uri.parse(url)).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

/** The LIST layout (MVP: fixed columns, no fitting yet). */
@Composable
internal fun WidgetContent(
    appWidgetId: Int,
    repo: String?,
    snapshot: RepoSnapshot?,
    tokenPresent: Boolean,
    tokenInvalid: Boolean,
    now: Instant,
    glyphs: Glyphs,
) {
    val context = LocalContext.current
    val picker: Action = actionStartActivity(
        Intent(context, RepoPickerActivity::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            .setData(Uri.parse("cidget://picker/$appWidgetId")),
    )
    val settings: Action = actionStartActivity(
        Intent(context, SettingsActivity::class.java).setData(Uri.parse("cidget://settings/$appWidgetId")),
    )
    Column(GlanceModifier.fillMaxSize().background(Palette.bg).padding(8.dp)) {
        Row(GlanceModifier.fillMaxWidth()) {
            val name = repo?.substringAfter('/') ?: ""
            Text(
                text = "~/$name $ ci",
                style = style(Palette.prompt),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight().clickable(picker),
            )
            if (repo != null && tokenPresent) {
                val label = if (snapshot?.refreshing == true) "[...]" else "[${glyphs.refresh}]"
                Text(label, style = style(Palette.dim), modifier = GlanceModifier.clickable(actionRunCallback<RefreshCallback>()))
            }
        }
        val meta = metaLine(context, snapshot, tokenPresent, tokenInvalid, now)
        if (meta.isNotEmpty()) Text(meta, style = style(Palette.dim), maxLines = 1)

        when {
            repo == null -> Text(
                context.getString(R.string.w_pick_repo),
                style = style(Palette.dim),
                modifier = GlanceModifier.fillMaxWidth().clickable(picker),
            )
            !tokenPresent -> Text(
                context.getString(R.string.w_set_token),
                style = style(Palette.dim),
                modifier = GlanceModifier.fillMaxWidth().clickable(settings),
            )
            snapshot == null || (snapshot.fetchedAt == null && snapshot.branches.isEmpty()) -> Text(
                context.getString(if (snapshot?.error == null) R.string.w_loading else R.string.w_no_data),
                style = style(Palette.dim),
            )
            else -> Rows(context, appWidgetId, repo, snapshot, now, glyphs)
        }
    }
}

@Composable
private fun Rows(context: Context, appWidgetId: Int, repo: String, snapshot: RepoSnapshot, now: Instant, glyphs: Glyphs) {
    val rows = selectBranches(snapshot.branches, ViewOptions(), now).rows
    if (rows.isEmpty()) {
        Text(context.getString(R.string.w_no_runs), style = style(Palette.dim))
        return
    }
    LazyColumn(GlanceModifier.fillMaxSize()) {
        items(rows) { b ->
            val rep = b.representative
            val url = rep?.htmlUrl ?: branchUrl(repo, b.branch)
            val status = glyphs.forStatus(b.status) + if (b.headApproximate) "~" else " "
            val run = rep?.let { "#${it.runNumber}" }.orEmpty().padStart(5)
            val age = rep?.let { formatAge(it.updatedAt, now) }.orEmpty().padStart(3)
            val noRuns = b.greyReason == GreyReason.NO_RUNS
            Row(GlanceModifier.fillMaxWidth().clickable(actionStartActivity(viewIntent(appWidgetId, url)))) {
                Text(status, style = style(statusColor(b)))
                Text(b.branch, style = style(Palette.fg), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                Text(" $run $age ${if (noRuns) " " else glyphs.copy}", style = style(Palette.dim), maxLines = 1)
            }
        }
    }
}
