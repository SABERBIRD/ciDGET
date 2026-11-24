package io.github.saberbird.cidget.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontFamily
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.saberbird.cidget.core.AppSettings
import io.github.saberbird.cidget.core.BranchStatus
import io.github.saberbird.cidget.core.Status
import io.github.saberbird.cidget.core.TinyTap
import io.github.saberbird.cidget.core.WidgetConfig
import io.github.saberbird.cidget.core.charsPerLine
import io.github.saberbird.cidget.core.rowUrl
import io.github.saberbird.cidget.core.selectBranches
import io.github.saberbird.cidget.core.tinyTarget
import io.github.saberbird.cidget.data.RepoList
import io.github.saberbird.cidget.data.RepoSnapshot
import io.github.saberbird.cidget.data.TokenState
import io.github.saberbird.cidget.data.TokenSummary
import io.github.saberbird.cidget.widget.ui.GlanceColors
import io.github.saberbird.cidget.widget.ui.GlanceScreen
import io.github.saberbird.cidget.widget.ui.Cmd
import io.github.saberbird.cidget.widget.ui.UiAction
import io.github.saberbird.cidget.widget.ui.UiEnv
import io.github.saberbird.cidget.widget.ui.buildScreen
import io.github.saberbird.cidget.widget.ui.toGlance
import io.github.saberbird.cidget.widget.ui.UiRole
import kotlinx.datetime.Instant

private const val PADDING_DP = 8
private const val LINE_HEIGHT_FACTOR = 1.5f

/** Narrower than this (dp) a widget is one cell wide: the 1×1 tile or the narrow column (D28). */
private const val ONE_CELL_DP = 110f

private fun style(color: Color, sizeSp: Int) = style(ColorProvider(color), sizeSp)

private fun style(color: ColorProvider, sizeSp: Int) = TextStyle(color = color, fontFamily = FontFamily.Monospace, fontSize = sizeSp.sp)

private fun statusRole(s: Status): UiRole = when (s) {
    Status.RED -> UiRole.RED
    Status.YELLOW -> UiRole.YELLOW
    Status.GREEN -> UiRole.GREEN
    Status.GREY -> UiRole.GREY
}

private fun tileColor(s: Status): Color = when (s) {
    Status.RED -> Palette.tileRed
    Status.YELLOW -> Palette.tileYellow
    Status.GREEN -> Palette.tileGreen
    Status.GREY -> Palette.tileGrey
}

/**
 * Picks the look for the exact size the launcher reports (WIDGET.md §2, D28): the 1×1 tile, the narrow
 * column (one cell wide and tall) or the terminal list with its settings screens.
 */
@Composable
internal fun WidgetContent(
    appWidgetId: Int,
    config: WidgetConfig,
    tokens: List<TokenSummary>,
    tokenState: TokenState?,
    repoList: RepoList,
    snapshot: RepoSnapshot?,
    now: Instant,
    glyphs: Glyphs,
    app: AppSettings,
) {
    val base = LocalContext.current
    val display = config.display
    val ctx = remember(base, display.language) { base.withLanguage(display.language) }
    val size = LocalSize.current
    val fontScale = base.resources.configuration.fontScale
    val sp = display.fontSizeSp
    val lineHeightDp = sp * fontScale * LINE_HEIGHT_FACTOR
    val w = size.width.value
    val h = size.height.value
    val colors = remember(display.theme) { GlanceColors(display.theme) }
    when {
        w < ONE_CELL_DP && h >= ONE_CELL_DP -> ColumnContent(appWidgetId, config, snapshot, now, glyphs, tokenState, colors)
        w < ONE_CELL_DP || h < 2 * lineHeightDp + 2 * PADDING_DP -> TinyContent(appWidgetId, config, snapshot, tokenState, now, glyphs, w, h)
        else -> {
            // One character of safety margin: some launchers report sizes a few dp off (RESEARCH.md R2).
            val cpl = (charsPerLine(w, 2f * PADDING_DP, sp.toFloat(), fontScale, Glyphs.advanceRatio) - 1).coerceAtLeast(0)
            val env = UiEnv(ctx, appWidgetId, config, tokens, tokenState, repoList, snapshot, now, glyphs, cpl, app)
            GlanceScreen(buildScreen(env), appWidgetId, sp, colors)
        }
    }
}

private fun targetOf(config: WidgetConfig, snapshot: RepoSnapshot?, now: Instant): BranchStatus? {
    val d = config.display
    val view = selectBranches(snapshot?.branches.orEmpty(), d.viewOptions().copy(maxRows = null), now)
    return tinyTarget(view, d.tinySource, d.pinned)
}

/** 1×1: a tile in the status colour with a small symbol and the run number. The tap is the user's choice. */
@Composable
private fun TinyContent(
    appWidgetId: Int,
    config: WidgetConfig,
    snapshot: RepoSnapshot?,
    tokenState: TokenState?,
    now: Instant,
    glyphs: Glyphs,
    widthDp: Float,
    heightDp: Float,
) {
    val context = LocalContext.current
    val repo = config.repo
    val target = if (repo != null) targetOf(config, snapshot, now) else null
    val status = target?.status ?: Status.GREY
    val hasError = tokenState == null || tokenState == TokenState.INVALID || snapshot?.error != null
    val action: UiAction = if (repo == null || target == null) {
        UiAction.OpenWide
    } else {
        when (config.display.tinyTap) {
            TinyTap.RUN -> UiAction.Open(rowUrl(repo, target))
            TinyTap.REFRESH -> UiAction.Do(Cmd.REFRESH)
            TinyTap.COPY -> UiAction.Copy(target.branch, rowUrl(repo, target))
        }
    }
    val symbolSp = minOf(0.34f * minOf(widthDp, heightDp), 26f).toInt().coerceAtLeast(12)
    Box(
        modifier = GlanceModifier.fillMaxSize().background(tileColor(status)).clickable(action.toGlance(context, appWidgetId)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(glyphs.forStatus(status), style = style(Color.White, symbolSp))
            target?.representative?.let { Text("#${it.runNumber}", style = style(Color.White, 11)) }
        }
        if (hasError) {
            Box(modifier = GlanceModifier.fillMaxSize().padding(2.dp), contentAlignment = Alignment.TopEnd) {
                Text("!", style = style(Color.White, 12))
            }
        }
    }
}

/**
 * One cell wide, taller: the blue `~$` on top opens the wide terminal (repo list, settings); below, per
 * branch a symbol, the run number and the copy button, one under another and flush left.
 */
@Composable
private fun ColumnContent(
    appWidgetId: Int,
    config: WidgetConfig,
    snapshot: RepoSnapshot?,
    now: Instant,
    glyphs: Glyphs,
    tokenState: TokenState?,
    colors: GlanceColors,
) {
    val context = LocalContext.current
    val display = config.display
    val sp = display.fontSizeSp
    val repo = config.repo
    val rows = if (repo != null && tokenState != null) selectBranches(snapshot?.branches.orEmpty(), display.viewOptions(), now).rows else emptyList()
    Column(GlanceModifier.fillMaxSize().background(colors.bg).padding(6.dp)) {
        Text("~$", style = style(colors.of(UiRole.PROMPT), sp + 2), modifier = GlanceModifier.clickable(UiAction.OpenWide.toGlance(context, appWidgetId)).padding(bottom = 2.dp))
        if (repo != null) {
            LazyColumn(GlanceModifier.fillMaxWidth()) {
                items(rows.indices.toList()) { i ->
                    val b = rows[i]
                    val url = rowUrl(repo, b)
                    val open = UiAction.Open(url).toGlance(context, appWidgetId)
                    Column(GlanceModifier.fillMaxWidth().padding(top = 6.dp)) {
                        Text(glyphs.statusLabel(b.status, display.glyphStyle), style = style(colors.of(statusRole(b.status)), sp), modifier = GlanceModifier.clickable(open))
                        Text(b.representative?.let { "#${it.runNumber}" } ?: "—", style = style(colors.of(UiRole.DIM), sp), modifier = GlanceModifier.clickable(open))
                        Text(
                            "[${glyphs.copy}]",
                            style = style(colors.of(UiRole.DIM), sp),
                            modifier = GlanceModifier.clickable(UiAction.Copy(b.branch, url).toGlance(context, appWidgetId)),
                        )
                    }
                }
            }
        }
    }
}
