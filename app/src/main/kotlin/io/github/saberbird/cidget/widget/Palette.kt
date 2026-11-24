package io.github.saberbird.cidget.widget

import androidx.compose.ui.graphics.Color
import io.github.saberbird.cidget.core.Theme
import io.github.saberbird.cidget.widget.ui.UiRole

/** One terminal palette (WIDGET.md §1). Every text colour reaches 4.5:1 on [bg]. */
internal data class Palette(
    val bg: Color,
    val fg: Color,
    val dim: Color,
    val prompt: Color,
    val red: Color,
    val yellow: Color,
    val green: Color,
    val grey: Color,
    val link: Color,
) {
    /** Colour of a text [role]. */
    fun of(role: UiRole): Color = when (role) {
        UiRole.FG -> fg
        UiRole.DIM -> dim
        UiRole.PROMPT -> prompt
        UiRole.GREEN -> green
        UiRole.RED -> red
        UiRole.YELLOW -> yellow
        UiRole.GREY -> grey
        UiRole.LINK -> link
    }

    companion object {
        val DARK = Palette(
            bg = Color(0xFF101214),
            fg = Color(0xFFD8DEE4),
            dim = Color(0xFF8B949E),
            prompt = Color(0xFF58A6FF),
            red = Color(0xFFFF6B6B),
            yellow = Color(0xFFE3B341),
            green = Color(0xFF3FB950),
            grey = Color(0xFF8B929A),
            link = Color(0xFF79C0FF),
        )

        val LIGHT = Palette(
            bg = Color(0xFFFAFAFA),
            fg = Color(0xFF1F2328),
            dim = Color(0xFF59636E),
            prompt = Color(0xFF0969DA),
            red = Color(0xFFCF222E),
            yellow = Color(0xFF9A6700),
            green = Color(0xFF1A7F37),
            grey = Color(0xFF656D76),
            link = Color(0xFF0550AE),
        )

        /** The palette for [theme]; SYSTEM needs the night flag of the current configuration. */
        fun of(theme: Theme, night: Boolean): Palette = when (theme) {
            Theme.DARK -> DARK
            Theme.LIGHT -> LIGHT
            Theme.SYSTEM -> if (night) DARK else LIGHT
        }

        /** Fill of the 1×1 tile per status (D28); the text on it is white in every theme. */
        val tileGreen = Color(0xFF1F6F33)
        val tileRed = Color(0xFF8F2A2A)
        val tileYellow = Color(0xFF7A5F12)
        val tileGrey = Color(0xFF3B4048)
    }
}
