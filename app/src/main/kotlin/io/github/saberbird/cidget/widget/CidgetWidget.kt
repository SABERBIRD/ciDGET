package io.github.saberbird.cidget.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontFamily
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.unit.dp
import io.github.saberbird.cidget.core.Terminal

/** The skeleton widget: renders only the terminal prompt. It reads no network and no stores. */
class CidgetWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(Color(0xFF101214))
                    .padding(8.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = Terminal.WIDGET_TEXT,
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF58A6FF)),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                    ),
                )
            }
        }
    }
}
