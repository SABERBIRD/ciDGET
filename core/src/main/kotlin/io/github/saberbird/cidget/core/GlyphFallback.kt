package io.github.saberbird.cidget.core

/** Status and action glyphs and their ASCII fallbacks (WIDGET.md §1, D10, D11). */
public object GlyphFallback {
    /** Fallback for each glyph the widget uses. */
    public val fallbacks: Map<String, String> = mapOf(
        "✗" to "x",
        "●" to "*",
        "✓" to "v",
        "○" to "o",
        "⧉" to "cp",
        "↗" to "sh",
        "⟳" to "r",
        "…" to "~",
    )

    /** [glyph] if [hasGlyph] says the font has it, otherwise its ASCII fallback (or [glyph] itself if unknown). */
    public fun resolve(glyph: String, hasGlyph: (String) -> Boolean): String =
        if (hasGlyph(glyph)) glyph else fallbacks[glyph] ?: glyph
}
