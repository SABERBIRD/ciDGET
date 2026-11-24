package io.github.saberbird.cidget.widget

import androidx.compose.ui.graphics.Color

/** Dark terminal palette (WIDGET.md §1). The light palette arrives with the theme task. */
internal object Palette {
    val bg = Color(0xFF101214)
    val fg = Color(0xFFD8DEE4)
    val dim = Color(0xFF8B949E)
    val prompt = Color(0xFF58A6FF)
    val red = Color(0xFFFF6B6B)
    val yellow = Color(0xFFE3B341)
    val green = Color(0xFF3FB950)
    val grey = Color(0xFF6E7681)
    val link = Color(0xFF79C0FF)

    /** Fill of the 1×1 tile per status (D28). */
    val tileGreen = Color(0xFF1F6F33)
    val tileRed = Color(0xFF8F2A2A)
    val tileYellow = Color(0xFF7A5F12)
    val tileGrey = Color(0xFF3B4048)
}
