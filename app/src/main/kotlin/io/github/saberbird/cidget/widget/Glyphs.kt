package io.github.saberbird.cidget.widget

import android.graphics.Paint
import android.graphics.Typeface
import io.github.saberbird.cidget.core.CellGlyphs
import io.github.saberbird.cidget.core.GlyphFallback
import io.github.saberbird.cidget.core.GlyphStyle
import io.github.saberbird.cidget.core.Status

/** Glyphs after the `hasGlyph` check against the device's monospace font (D10, WIDGET.md §1). */
internal class Glyphs(private val hasGlyph: (String) -> Boolean) {
    private fun g(s: String) = GlyphFallback.resolve(s, hasGlyph)

    val red = g("✗")
    val yellow = g("●")
    val green = g("✓")
    val grey = g("○")
    val copy = g("⧉")
    val share = g("↗")
    val refresh = g("⟳")

    fun forStatus(status: Status): String = when (status) {
        Status.RED -> red
        Status.YELLOW -> yellow
        Status.GREEN -> green
        Status.GREY -> grey
    }

    /** Status label for a style: a glyph, or `[OK] [FAIL] [RUN] [--]` in bracket style. */
    fun statusLabel(status: Status, style: GlyphStyle): String = when (style) {
        GlyphStyle.UNICODE -> forStatus(status)
        GlyphStyle.BRACKET -> when (status) {
            Status.RED -> "[FAIL]"
            Status.YELLOW -> "[RUN]"
            Status.GREEN -> "[OK]"
            Status.GREY -> "[--]"
        }
    }

    /** The glyphs the cell formatter needs. */
    fun cellGlyphs(style: GlyphStyle) = CellGlyphs({ statusLabel(it, style) }, copy, share)

    companion object {
        /** Width of one monospace cell relative to the font size, measured once (about 0.6). */
        val advanceRatio: Float by lazy {
            Paint().apply { typeface = Typeface.MONOSPACE; textSize = 100f }.measureText("M") / 100f
        }

        /** Checked once per process; the font does not change while the app runs. */
        val system: Glyphs by lazy {
            val paint = Paint().apply { typeface = Typeface.MONOSPACE }
            Glyphs { paint.hasGlyph(it) }
        }
    }
}
