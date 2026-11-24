package io.github.saberbird.cidget.core

/** Display width of a code point: 2 for East Asian wide characters and emoji, otherwise 1. */
internal fun codePointWidth(cp: Int): Int = when (cp) {
    in 0x1100..0x115F, in 0x2E80..0xA4CF, in 0xAC00..0xD7A3, in 0xF900..0xFAFF,
    in 0xFE30..0xFE6F, in 0xFF00..0xFF60, in 0xFFE0..0xFFE6,
    in 0x1F300..0x1FAFF, in 0x20000..0x3FFFD,
    -> 2
    else -> 1
}

/** Display width of [s] in monospace cells, counted by code points (wide characters count 2). */
public fun displayWidth(s: String): Int {
    var w = 0
    var i = 0
    while (i < s.length) {
        val cp = s.codePointAt(i)
        w += codePointWidth(cp)
        i += Character.charCount(cp)
    }
    return w
}

/** Cuts [s] to at most [width] cells, ending with `…` when something was removed. */
public fun truncate(s: String, width: Int): String {
    if (width <= 0) return ""
    if (displayWidth(s) <= width) return s
    if (width == 1) return "…"
    val sb = StringBuilder()
    var w = 0
    var i = 0
    while (i < s.length) {
        val cp = s.codePointAt(i)
        val cw = codePointWidth(cp)
        if (w + cw > width - 1) break
        sb.appendCodePoint(cp)
        w += cw
        i += Character.charCount(cp)
    }
    return sb.append('…').toString()
}

/** Pads or truncates [s] to exactly [width] cells, aligned left or right. */
public fun fitCell(s: String, width: Int, align: Align): String {
    val t = truncate(s, width)
    val pad = " ".repeat((width - displayWidth(t)).coerceAtLeast(0))
    return if (align == Align.RIGHT) pad + t else t + pad
}
