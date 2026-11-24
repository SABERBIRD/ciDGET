package io.github.saberbird.cidget.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** Receives the AppWidget broadcasts and hands them to [CidgetWidget]. */
class CidgetWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CidgetWidget()
}
