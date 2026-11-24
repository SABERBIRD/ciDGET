package io.github.saberbird.cidget.widget.ui

/** Colour role of a span; each renderer maps roles to palette colours. */
internal enum class UiRole { FG, DIM, PROMPT, GREEN, RED, YELLOW, GREY, LINK }

/** The changes a tap can ask [UiController] for. Stored by name in the widget's tap intents. */
internal enum class Cmd {
    /** Show screen `arg` (a [io.github.saberbird.cidget.core.WidgetView] name). */
    VIEW,

    /** Refresh the repo list and every repo some widget shows. */
    REFRESH,

    /** Switch to repo `arg` seen by token `arg2`. */
    PICK,

    /** Ask for a second tap before a destructive action: `arg` is a [io.github.saberbird.cidget.core.ConfirmKind] name, `arg2` its target. */
    ASK,

    /** Run the pending confirmed action. */
    CONFIRM,

    /** Drop the pending confirmation. */
    CANCEL,
    COL_TOGGLE,
    COL_SELECT,
    COL_MOVE,
    PRIO_MOVE,
    COL_CAP,
    FONT,
    GLYPH,
    TINY_SOURCE,
    TINY_TAP,
    PIN,
    LANG,
    THEME,
    SORT,
    IDLE_DAYS,
    MAX_ROWS,
    HIDE_NO_RUNS,
    REFRESH_INTERVAL,
    REFRESH_UNMETERED,
    REFRESH_BATTERY,
    EXPIRY_WARN,
}

/** What a tap on a span or line does. */
internal sealed interface UiAction {
    /** A change handled in the app process by [UiController]; [arg] and [arg2] depend on [cmd]. */
    data class Do(val cmd: Cmd, val arg: String = "", val arg2: String = "") : UiAction

    /** Opens [url] in the browser. */
    data class Open(val url: String) : UiAction

    /** Copies [text] to the clipboard. [branch] only keeps the intents of different rows apart. */
    data class Copy(val branch: String, val text: String) : UiAction

    /** Opens the share sheet with [text]. */
    data class Share(val branch: String, val text: String) : UiAction

    /** Reads a token from the clipboard (needs an activity, see `ActionActivity`). */
    data object Paste : UiAction

    /** Opens the system's uninstall dialog for this app. */
    data object Uninstall : UiAction

    /** Opens the wide full-screen terminal for the widget. */
    data object OpenWide : UiAction
}

/**
 * A run of text with one colour and an optional own tap action. With [cells] the span is drawn in a box of
 * exactly that many character cells, so a symbol from another font cannot push the next column (D28).
 */
internal data class UiSpan(val text: String, val role: UiRole = UiRole.FG, val action: UiAction? = null, val cells: Int? = null)

/**
 * One terminal line: [left] spans from the left edge, [right] spans from the right edge, the gap between
 * them is empty. A tap anywhere on the line that is not on a span with its own action runs [action].
 */
internal data class UiLine(val left: List<UiSpan>, val right: List<UiSpan> = emptyList(), val action: UiAction? = null)

/** A screen: a scrolling [body] and a [foot] that stays at the bottom (D28). */
internal data class UiScreen(val body: List<UiLine>, val foot: List<UiLine>)
