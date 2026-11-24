package io.github.saberbird.cidget.widget

import android.graphics.Paint
import android.graphics.Typeface
import io.github.saberbird.cidget.core.GlyphFallback
import io.github.saberbird.cidget.core.Status

/** Glyphs after the `hasGlyph` check against the device's monospace font (D10, WIDGET.md §1). */
internal class Glyphs(private val hasGlyph: (String) -> Boolean) {
    private fun g(s: String) = GlyphFallback.resolve(s, hasGlyph)

    val red = g("✗")
    val yellow = g("●")
    val green = g("✓")
    val grey = g("○")
    val copy = g("⧉")
    val refresh = g("⟳")

    fun forStatus(status: Status): String = when (status) {
        Status.RED -> red
        Status.YELLOW -> yellow
        Status.GREEN -> green
        Status.GREY -> grey
    }

    companion object {
        /** Checked once per process; the font does not change while the app runs. */
        val system: Glyphs by lazy {
            val paint = Paint().apply { typeface = Typeface.MONOSPACE }
            Glyphs { paint.hasGlyph(it) }
        }
    }
}
