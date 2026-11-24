package io.github.saberbird.cidget.settings

import android.content.ClipboardManager
import android.content.Context
import io.github.saberbird.cidget.R
import io.github.saberbird.cidget.di.AppGraph

/** The text on the clipboard, or null. Read only when the user taps the paste button (D25). */
internal fun clipboardText(context: Context): String? =
    context.getSystemService(ClipboardManager::class.java)?.primaryClip
        ?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()

/** Outcome of pasting a token. */
internal sealed interface PasteResult {
    data object Saved : PasteResult
    data object Empty : PasteResult
    data class Failed(val error: ErrorText) : PasteResult
}

/**
 * Validates [raw] (the clipboard text) and saves it as a new token. On success the clipboard is cleared. The
 * text lives only in this call; it is never logged or kept (SECURITY.md).
 */
internal suspend fun saveFromClipboard(context: Context, graph: AppGraph, raw: String?): PasteResult {
    if (raw.isNullOrBlank()) return PasteResult.Empty
    return when (val r = TokenController(graph).validateAndSave(raw, "", null)) {
        SaveResult.Saved -> {
            runCatching { context.getSystemService(ClipboardManager::class.java)?.clearPrimaryClip() }
            PasteResult.Saved
        }
        SaveResult.Empty -> PasteResult.Empty
        is SaveResult.Failed -> PasteResult.Failed(r.error.toErrorText())
    }
}

/** Text for a toast about [this] result. */
internal fun PasteResult.message(context: Context): String = when (this) {
    PasteResult.Saved -> context.getString(R.string.token_saved_ok)
    PasteResult.Empty -> context.getString(R.string.token_err_empty)
    is PasteResult.Failed -> error.arg?.let { context.getString(error.res, it) } ?: context.getString(error.res)
}
