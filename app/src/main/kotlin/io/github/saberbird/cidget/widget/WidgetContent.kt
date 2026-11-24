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
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
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
import io.github.saberbird.cidget.action.ActionActivity
import io.github.saberbird.cidget.core.BranchStatus
import io.github.saberbird.cidget.core.DisplaySettings
import io.github.saberbird.cidget.core.SegmentRole
import io.github.saberbird.cidget.core.Status
import io.github.saberbird.cidget.core.TinySource
import io.github.saberbird.cidget.core.cellsFor
import io.github.saberbird.cidget.core.charsPerLine
import io.github.saberbird.cidget.core.fitColumns
import io.github.saberbird.cidget.core.renderRow
import io.github.saberbird.cidget.core.rowSegments
import io.github.saberbird.cidget.core.rowUrl
import io.github.saberbird.cidget.core.selectBranches
import io.github.saberbird.cidget.core.shareText
import io.github.saberbird.cidget.data.RepoSnapshot
import io.github.saberbird.cidget.widget.config.WidgetConfigActivity
import kotlinx.datetime.Instant

private const val PADDING_DP = 8
private const val LINE_HEIGHT_FACTOR = 1.5f

private fun style(color: Color, sizeSp: Int) = TextStyle(
    color = ColorProvider(color),
    fontFamily = FontFamily.Monospace,
    fontSize = sizeSp.sp,
)

private fun statusColor(s: Status): Color = when (s) {
    Status.RED -> Palette.red
    Status.YELLOW -> Palette.yellow
    Status.GREEN -> Palette.green
    Status.GREY -> Palette.grey
}

private fun viewIntent(appWidgetId: Int, url: String) =
    Intent(Intent.ACTION_VIEW, Uri.parse(url)).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

/** Picks TINY or LIST for the exact size the launcher reports (WIDGET.md §2). */
@Composable
internal fun WidgetContent(
    appWidgetId: Int,
    repo: String?,
    snapshot: RepoSnapshot?,
    tokenPresent: Boolean,
    tokenInvalid: Boolean,
    now: Instant,
    glyphs: Glyphs,
    display: DisplaySettings,
) {
    val context = LocalContext.current
    val size = LocalSize.current
    val fontScale = context.resources.configuration.fontScale
    val lineHeightDp = display.fontSizeSp * fontScale * LINE_HEIGHT_FACTOR
    val picker: Action = actionStartActivity(
        Intent(context, WidgetConfigActivity::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            .setData(Uri.parse("cidget://settings/$appWidgetId")),
    )
    val tiny = size.width.value < 100f || size.height.value < 2 * lineHeightDp + 2 * PADDING_DP
    if (tiny) {
        TinyContent(appWidgetId, repo, snapshot, tokenPresent, tokenInvalid, now, glyphs, display, picker, size.width.value, size.height.value)
    } else {
        ListContent(appWidgetId, repo, snapshot, tokenPresent, tokenInvalid, now, glyphs, display, picker, size.width.value, fontScale)
    }
}

/** TINY: one big glyph in the status colour, `!` in a corner on error. Tap opens the shown run. */
@Composable
private fun TinyContent(
    appWidgetId: Int,
    repo: String?,
    snapshot: RepoSnapshot?,
    tokenPresent: Boolean,
    tokenInvalid: Boolean,
    now: Instant,
    glyphs: Glyphs,
    display: DisplaySettings,
    settings: Action,
    widthDp: Float,
    heightDp: Float,
) {
    val view = selectBranches(snapshot?.branches.orEmpty(), display.viewOptions().copy(maxRows = null), now)
    val pinned = if (display.tinySource == TinySource.PINNED) display.pinned.firstOrNull() else null
    val target: BranchStatus? = pinned?.let { p -> view.rows.firstOrNull { it.branch == p } }
        ?: view.rows.firstOrNull { it.status == view.worst }
    val status = target?.status ?: view.worst
    val hasError = !tokenPresent || tokenInvalid || snapshot?.error != null
    val click: Action = if (repo != null && target != null) {
        actionStartActivity(viewIntent(appWidgetId, rowUrl(repo, target)))
    } else {
        settings
    }
    val glyphSp = minOf(0.6f * minOf(widthDp, heightDp), 48f).toInt().coerceAtLeast(10)
    Box(
        modifier = GlanceModifier.fillMaxSize().background(Palette.bg).clickable(click),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyphs.forStatus(status), style = style(statusColor(status), glyphSp))
        if (hasError) {
            Box(modifier = GlanceModifier.fillMaxSize().padding(2.dp), contentAlignment = Alignment.TopEnd) {
                Text("!", style = style(Palette.red, 12))
            }
        }
    }
}

/** LIST: header, meta line, then fitted rows (WIDGET.md §2–§3). */
@Composable
private fun ListContent(
    appWidgetId: Int,
    repo: String?,
    snapshot: RepoSnapshot?,
    tokenPresent: Boolean,
    tokenInvalid: Boolean,
    now: Instant,
    glyphs: Glyphs,
    display: DisplaySettings,
    picker: Action,
    widthDp: Float,
    fontScale: Float,
) {
    val context = LocalContext.current
    val sp = display.fontSizeSp
    Column(GlanceModifier.fillMaxSize().background(Palette.bg).padding(PADDING_DP.dp)) {
        Row(GlanceModifier.fillMaxWidth()) {
            val name = repo?.substringAfter('/') ?: ""
            Text(
                text = "~/$name $ ci",
                style = style(Palette.prompt, sp),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight().clickable(picker),
            )
            if (repo != null && tokenPresent) {
                val label = if (snapshot?.refreshing == true) "[...]" else "[${glyphs.refresh}]"
                Text(label, style = style(Palette.dim, sp), modifier = GlanceModifier.clickable(actionRunCallback<RefreshCallback>()))
            }
        }
        val meta = metaLine(context, snapshot, tokenPresent, tokenInvalid, now)
        if (meta.isNotEmpty()) Text(meta, style = style(Palette.dim, sp), maxLines = 1)

        when {
            repo == null -> Text(
                context.getString(R.string.w_pick_repo),
                style = style(Palette.dim, sp),
                modifier = GlanceModifier.fillMaxWidth().clickable(picker),
            )
            !tokenPresent -> Text(
                context.getString(R.string.w_set_token),
                style = style(Palette.dim, sp),
                modifier = GlanceModifier.fillMaxWidth().clickable(picker),
            )
            snapshot == null || (snapshot.fetchedAt == null && snapshot.branches.isEmpty()) -> Text(
                context.getString(if (snapshot?.error == null) R.string.w_loading else R.string.w_no_data),
                style = style(Palette.dim, sp),
            )
            else -> Rows(context, appWidgetId, repo, snapshot, now, glyphs, display, widthDp, fontScale)
        }
    }
}

@Composable
private fun Rows(
    context: Context,
    appWidgetId: Int,
    repo: String,
    snapshot: RepoSnapshot,
    now: Instant,
    glyphs: Glyphs,
    display: DisplaySettings,
    widthDp: Float,
    fontScale: Float,
) {
    val sp = display.fontSizeSp
    val rows = selectBranches(snapshot.branches, display.viewOptions(), now).rows
    if (rows.isEmpty()) {
        Text(context.getString(R.string.w_no_runs), style = style(Palette.dim, sp))
        return
    }
    val cellGlyphs = glyphs.cellGlyphs(display.glyphStyle)
    val cells = rows.map { cellsFor(it, now, cellGlyphs) }
    // One character of safety margin: some launchers report sizes a few dp off (RESEARCH.md R2).
    val cpl = (charsPerLine(widthDp, 2f * PADDING_DP, sp.toFloat(), fontScale, Glyphs.advanceRatio) - 1).coerceAtLeast(0)
    val fitted = fitColumns(display.columns, cells, cpl)
    if (fitted.isEmpty()) return
    LazyColumn(GlanceModifier.fillMaxSize()) {
        items(rows.indices.toList()) { i ->
            val b = rows[i]
            val url = rowUrl(repo, b)
            val segments = rowSegments(fitted, renderRow(fitted, cells[i]))
            Row(GlanceModifier.fillMaxWidth().clickable(actionStartActivity(viewIntent(appWidgetId, url)))) {
                segments.forEach { seg ->
                    when (seg.role) {
                        SegmentRole.STATUS -> Text(seg.text, style = style(statusColor(b.status), sp), maxLines = 1)
                        SegmentRole.FG -> Text(seg.text, style = style(Palette.fg, sp), maxLines = 1)
                        SegmentRole.DIM -> Text(seg.text, style = style(Palette.dim, sp), maxLines = 1)
                        SegmentRole.COPY -> Text(
                            seg.text,
                            style = style(Palette.dim, sp),
                            maxLines = 1,
                            modifier = GlanceModifier.clickable(actionStartActivity(ActionActivity.copy(context, appWidgetId, b.branch, url))),
                        )
                        SegmentRole.SHARE -> Text(
                            seg.text,
                            style = style(Palette.dim, sp),
                            maxLines = 1,
                            modifier = GlanceModifier.clickable(
                                actionStartActivity(ActionActivity.share(context, appWidgetId, b.branch, shareText(repo, b))),
                            ),
                        )
                    }
                }
            }
        }
    }
}
