package io.github.saberbird.cidget.widget.ui

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.unit.dp
import io.github.saberbird.cidget.action.ActionActivity
import io.github.saberbird.cidget.graph
import io.github.saberbird.cidget.widget.CidgetWidget
import io.github.saberbird.cidget.widget.Glyphs
import io.github.saberbird.cidget.widget.Palette
import io.github.saberbird.cidget.widget.config.WidgetConfigActivity

internal fun UiRole.color(): Color = when (this) {
    UiRole.FG -> Palette.fg
    UiRole.DIM -> Palette.dim
    UiRole.PROMPT -> Palette.prompt
    UiRole.GREEN -> Palette.green
    UiRole.RED -> Palette.red
    UiRole.YELLOW -> Palette.yellow
    UiRole.GREY -> Palette.grey
    UiRole.LINK -> Palette.link
}

/** Runs a [UiAction.Do] from a widget tap and redraws that widget. */
class UiCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val kind = parameters[KIND] ?: return
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        UiController(context.graph).handle(appWidgetId, UiAction.Do(kind, parameters[ARG].orEmpty(), parameters[ARG2].orEmpty()))
        CidgetWidget().update(context, glanceId)
    }

    companion object {
        val KIND = ActionParameters.Key<String>("kind")
        val ARG = ActionParameters.Key<String>("arg")
        val ARG2 = ActionParameters.Key<String>("arg2")
    }
}

/** Maps a model action to a Glance action. */
internal fun UiAction.toGlance(context: Context, appWidgetId: Int): Action = when (this) {
    is UiAction.Do -> actionRunCallback<UiCallback>(
        actionParametersOf(UiCallback.KIND to kind, UiCallback.ARG to arg, UiCallback.ARG2 to arg2),
    )
    is UiAction.Open -> actionStartActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
    )
    is UiAction.Copy -> actionStartActivity(ActionActivity.copy(context, appWidgetId, branch, text))
    is UiAction.Share -> actionStartActivity(ActionActivity.share(context, appWidgetId, branch, text))
    UiAction.Paste -> actionStartActivity(ActionActivity.paste(context, appWidgetId))
    UiAction.Uninstall -> actionStartActivity(uninstallIntent(context))
    UiAction.OpenWide -> actionStartActivity(WidgetConfigActivity.wideIntent(context, appWidgetId))
}

/** The system's uninstall dialog for this app. */
internal fun uninstallIntent(context: Context): Intent =
    Intent(Intent.ACTION_DELETE, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

private fun textStyle(color: Color, sp: Int) = TextStyle(color = ColorProvider(color), fontFamily = FontFamily.Monospace, fontSize = sp.sp)

/** Draws a [UiScreen]: the body scrolls, the foot stays at the bottom. */
@Composable
internal fun GlanceScreen(screen: UiScreen, appWidgetId: Int, sp: Int) {
    val context = LocalContext.current
    Column(GlanceModifier.fillMaxSize().background(Palette.bg).padding(8.dp)) {
        LazyColumn(GlanceModifier.fillMaxWidth().defaultWeight()) {
            items(screen.body.indices.toList()) { i -> GlanceLine(screen.body[i], context, appWidgetId, sp) }
        }
        screen.foot.forEach { GlanceLine(it, context, appWidgetId, sp, footRow = true) }
    }
}

@Composable
private fun GlanceLine(line: UiLine, context: Context, appWidgetId: Int, sp: Int, footRow: Boolean = false) {
    val oneSpan = line.left.size == 1 && line.right.isEmpty() && line.action == null
    var modifier = GlanceModifier.fillMaxWidth()
    line.action?.let { modifier = modifier.clickable(it.toGlance(context, appWidgetId)) }
    Row(modifier) {
        line.left.forEach { GlanceSpan(it, context, appWidgetId, sp, wrap = oneSpan && !footRow) }
        if (line.right.isNotEmpty()) {
            Spacer(GlanceModifier.defaultWeight())
            line.right.forEach { GlanceSpan(it, context, appWidgetId, sp, wrap = false) }
        }
    }
}

@Composable
private fun GlanceSpan(span: UiSpan, context: Context, appWidgetId: Int, sp: Int, wrap: Boolean) {
    var modifier = GlanceModifier
    if (wrap) modifier = modifier.fillMaxWidth()
    span.cells?.let { n ->
        val widthDp = n * sp * context.resources.configuration.fontScale * Glyphs.advanceRatio
        modifier = modifier.width((widthDp + 1f).dp)
    }
    span.action?.let { modifier = modifier.clickable(it.toGlance(context, appWidgetId)) }
    Text(span.text, style = textStyle(span.role.color(), sp), maxLines = if (wrap) Int.MAX_VALUE else 1, modifier = modifier)
}
