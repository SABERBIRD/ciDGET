package io.github.saberbird.cidget.settings

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.saberbird.cidget.R
import io.github.saberbird.cidget.data.TokenState
import io.github.saberbird.cidget.di.AppGraph
import io.github.saberbird.cidget.widget.Palette
import io.github.saberbird.cidget.widget.config.TermButton
import io.github.saberbird.cidget.widget.config.TermRule
import io.github.saberbird.cidget.widget.config.TermTitle
import kotlinx.coroutines.launch
import kotlinx.datetime.toJavaInstant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val TOKEN_HELP_URL = "https://github.com/SABERBIRD/ciDGET/blob/main/docs/user/TOKEN.md"

/** The text on the clipboard, or null. Read only when the user taps the paste button. */
private fun clipboardText(context: Context): String? =
    context.getSystemService(ClipboardManager::class.java)?.primaryClip
        ?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()

/**
 * Settings → Tokens (D23, D25): the saved tokens (last 4, expiry, state) each with Replace and Delete, and one
 * paste button. There is no input field: the token is taken from the clipboard, validated, saved, and the
 * clipboard is cleared. The text lives only in local variables of the click handler.
 *
 * @param showContentsHint true when the branch list of some repo was forbidden (token lacks Contents: read).
 */
@Composable
fun TokenScreen(graph: AppGraph, showContentsHint: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
        TokenSection(graph, showContentsHint)
    }
}

/** The token list and the paste button without its own scrolling, so other screens can embed it. */
@Composable
fun TokenSection(graph: AppGraph, showContentsHint: Boolean) {
    val controller = remember { TokenController(graph) }
    val tokens by graph.tokenStore.summaries.collectAsState()
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<ErrorText?>(null) }
    var emptyError by remember { mutableStateOf(false) }
    var savedNow by remember { mutableStateOf(false) }

    /** Validates the clipboard text and saves it as a new token, or over [replaceId]. */
    fun paste(replaceId: String?) {
        error = null; emptyError = false; savedNow = false
        val raw = clipboardText(context)
        if (raw.isNullOrBlank()) { emptyError = true; return }
        busy = true
        scope.launch {
            when (val r = controller.validateAndSave(raw, "", replaceId)) {
                SaveResult.Saved -> {
                    savedNow = true
                    runCatching { context.getSystemService(ClipboardManager::class.java)?.clearPrimaryClip() }
                }
                SaveResult.Empty -> emptyError = true
                is SaveResult.Failed -> error = r.error.toErrorText()
            }
            busy = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TermTitle(stringResource(R.string.token_title))
        if (tokens.isEmpty()) Text(stringResource(R.string.token_none))
        tokens.forEach { t ->
            TermRule()
            val name = t.label.ifEmpty { stringResource(R.string.token_unnamed) }
            Text(stringResource(R.string.token_item, name, t.last4))
            val exp = t.expiresAt
            Text(
                if (exp == null) {
                    stringResource(R.string.token_no_expiry)
                } else {
                    val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                        .withZone(ZoneId.systemDefault()).format(exp.toJavaInstant())
                    stringResource(R.string.token_expires, date)
                },
                color = Palette.dim,
            )
            if (t.state == TokenState.INVALID) {
                Text(stringResource(R.string.token_invalid), color = MaterialTheme.colorScheme.error)
            }
            TermButton(stringResource(R.string.token_replace_paste), enabled = !busy) { paste(t.id) }
            TermButton(stringResource(R.string.token_delete), enabled = !busy) {
                busy = true
                scope.launch {
                    controller.delete(t.id)
                    savedNow = false
                    busy = false
                }
            }
        }
        TermRule()
        if (showContentsHint) Text(stringResource(R.string.token_contents_hint))
        Text(stringResource(R.string.token_paste_hint), color = Palette.dim)
        TermButton(stringResource(R.string.token_paste), enabled = !busy) { paste(null) }
        if (emptyError) Text(stringResource(R.string.token_err_empty), color = MaterialTheme.colorScheme.error)
        error?.let { e ->
            val msg = if (e.arg != null) stringResource(e.res, e.arg) else stringResource(e.res)
            Text(msg, color = MaterialTheme.colorScheme.error)
        }
        if (savedNow) Text(stringResource(R.string.token_saved_ok), color = Palette.green)
        if (busy) Text(stringResource(R.string.token_checking), color = Palette.dim)
        TermButton(stringResource(R.string.token_howto)) { uriHandler.openUri(TOKEN_HELP_URL) }
    }
}
