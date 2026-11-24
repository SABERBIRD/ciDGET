package io.github.saberbird.cidget.widget.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.saberbird.cidget.R
import io.github.saberbird.cidget.core.WidgetConfig
import io.github.saberbird.cidget.core.WidgetView
import io.github.saberbird.cidget.core.charsPerLine
import io.github.saberbird.cidget.data.RepoList
import io.github.saberbird.cidget.di.AppGraph
import io.github.saberbird.cidget.settings.clipboardText
import io.github.saberbird.cidget.settings.message
import io.github.saberbird.cidget.settings.saveFromClipboard
import io.github.saberbird.cidget.widget.Glyphs
import io.github.saberbird.cidget.widget.Palette
import io.github.saberbird.cidget.widget.withLanguage
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

private const val WIDE_FONT_SP = 14

/**
 * The wide full-screen terminal (D28): the same screens as inside the widget, drawn from the same model, for
 * widgets too small to hold them. [onClose] runs when the user leaves the branch list view (the `[x]` button,
 * back, or picking a repo).
 */
@Composable
internal fun WideTerminal(graph: AppGraph, appWidgetId: Int, onClose: () -> Unit) {
    val base = LocalContext.current
    val controller = remember(graph) { UiController(graph) }
    val scope = rememberCoroutineScope()
    val config by graph.settings.widgetConfig(appWidgetId).collectAsState(WidgetConfig())
    val tokens by graph.tokenStore.summaries.collectAsState()
    val tokenState = remember(tokens, config.tokenId) { graph.tokenStore.stateOf(config.tokenId) }
    val repoList by graph.repoListStore.observe().collectAsState(RepoList())
    val repo = config.repo
    val snapshots = remember(repo) { repo?.let { graph.snapshots.observe(it) } ?: flowOf(null) }
    val snapshot by snapshots.collectAsState(null)
    val ctx = remember(base, config.display.language) { base.withLanguage(config.display.language) }

    if (config.view == WidgetView.BRANCHES) LaunchedEffect(Unit) { onClose() }
    BackHandler { scope.launch { controller.handle(appWidgetId, UiAction.Do("view", WidgetView.BRANCHES.name)) } }

    BoxWithConstraints(Modifier.fillMaxSize().background(Palette.bg).windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp)) {
        val cpl = (charsPerLine(maxWidth.value, 0f, WIDE_FONT_SP.toFloat(), base.resources.configuration.fontScale, Glyphs.advanceRatio) - 1).coerceAtLeast(0)
        val env = UiEnv(ctx, appWidgetId, config, tokens, tokenState, repoList, snapshot, Clock.System.now(), Glyphs.system, cpl)
        val screen = buildScreen(env)
        val perform: (UiAction) -> Unit = { action ->
            when (action) {
                is UiAction.Do -> scope.launch { controller.handle(appWidgetId, action) }
                is UiAction.Open -> base.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(action.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                is UiAction.Copy -> copyToClipboard(base, action.text)
                is UiAction.Share -> base.startActivity(
                    Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, action.text), null)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                UiAction.Paste -> {
                    val raw = clipboardText(base)
                    scope.launch { Toast.makeText(base, saveFromClipboard(base, graph, raw).message(ctx), Toast.LENGTH_LONG).show() }
                }
                UiAction.Uninstall -> base.startActivity(uninstallIntent(base))
                UiAction.OpenWide -> Unit
            }
        }
        Column(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                items(screen.body) { WideLine(it, perform, env) }
            }
            screen.foot.forEach { WideLine(it, perform, env) }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("ciDGET", text))
    // Android 13+ shows its own confirmation.
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) Toast.makeText(context, R.string.action_copied, Toast.LENGTH_SHORT).show()
}

@Composable
private fun WideLine(line: UiLine, perform: (UiAction) -> Unit, env: UiEnv) {
    var modifier = Modifier.fillMaxWidth()
    line.action?.let { a -> modifier = modifier.clickable { perform(a) } }
    Row(modifier.padding(vertical = 5.dp)) {
        line.left.forEach { WideSpan(it, perform, env) }
        if (line.right.isNotEmpty()) {
            Spacer(Modifier.weight(1f))
            line.right.forEach { WideSpan(it, perform, env) }
        }
    }
}

@Composable
private fun WideSpan(span: UiSpan, perform: (UiAction) -> Unit, env: UiEnv) {
    var modifier = Modifier
    span.cells?.let { n -> modifier = modifier.width((n * WIDE_FONT_SP * env.ctx.resources.configuration.fontScale * Glyphs.advanceRatio + 1f).dp) }
    span.action?.let { a -> modifier = modifier.clickable { perform(a) } }
    Text(
        text = span.text,
        color = span.role.color(),
        fontFamily = FontFamily.Monospace,
        fontSize = WIDE_FONT_SP.sp,
        softWrap = span.cells == null,
        maxLines = if (span.cells == null) Int.MAX_VALUE else 1,
        modifier = modifier,
    )
}
