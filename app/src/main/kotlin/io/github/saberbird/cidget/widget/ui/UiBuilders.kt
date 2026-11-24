package io.github.saberbird.cidget.widget.ui

import io.github.saberbird.cidget.core.displayWidth

internal const val RULE = "────────────────────"

internal fun span(text: String, role: UiRole = UiRole.FG, action: UiAction? = null) = UiSpan(text, role, action)

internal fun line(text: String, role: UiRole = UiRole.FG, action: UiAction? = null) = UiLine(listOf(UiSpan(text, role)), action = action)

/** A dim section header such as `─ Tokens ───────────`. */
internal fun section(title: String): UiLine = line("─ $title ${"─".repeat((18 - title.length).coerceAtLeast(2))}", UiRole.DIM)

internal fun rule(): UiLine = line(RULE, UiRole.DIM)

/**
 * Packs [items] (buttons or short texts) into lines of at most [cpl] characters, one blank between items.
 * An item is never split: when it does not fit it starts the next line (D28).
 */
internal fun wrapItems(items: List<UiSpan>, cpl: Int): List<UiLine> {
    val lines = mutableListOf<MutableList<UiSpan>>()
    var used = 0
    for (item in items) {
        val w = displayWidth(item.text)
        val current = lines.lastOrNull()
        if (current == null || used + 1 + w > cpl) {
            lines += mutableListOf(item)
            used = w
        } else {
            current += UiSpan(" ")
            current += item
            used += 1 + w
        }
    }
    return lines.map { UiLine(it) }
}

/** `(*) text` / `( ) text`: one choice of a radio group. */
internal fun radio(selected: Boolean, text: String, action: UiAction): UiLine =
    line((if (selected) "(*) " else "( ) ") + text, if (selected) UiRole.PROMPT else UiRole.FG, action)

/** `[x] text` / `[ ] text`: a switch. */
internal fun toggle(on: Boolean, text: String, action: UiAction): UiLine =
    line((if (on) "[x] " else "[ ] ") + text, if (on) UiRole.GREEN else UiRole.FG, action)

/**
 * The foot of a sub-screen: back and close with the title (`~ $ title`). When they do not fit one line the
 * title takes a line of its own and `[<]`, `[x]` go below it, in the corners (D28).
 */
internal fun subFoot(title: String, back: UiAction, close: UiAction, cpl: Int): List<UiLine> {
    val backSpan = span("[<]", UiRole.DIM, back)
    val closeSpan = span("[x]", UiRole.DIM, close)
    val full = "~ $ $title"
    return if (3 + 1 + displayWidth(full) + 1 + 3 <= cpl) {
        listOf(UiLine(listOf(backSpan, span(" "), span(full, UiRole.PROMPT)), listOf(closeSpan)))
    } else {
        listOf(line(full, UiRole.PROMPT), UiLine(listOf(backSpan), listOf(closeSpan)))
    }
}
