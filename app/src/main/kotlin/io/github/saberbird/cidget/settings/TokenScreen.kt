package io.github.saberbird.cidget.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.saberbird.cidget.R
import io.github.saberbird.cidget.data.TokenState
import io.github.saberbird.cidget.di.AppGraph
import kotlinx.coroutines.launch
import kotlinx.datetime.toJavaInstant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val TOKEN_HELP_URL = "https://github.com/SABERBIRD/ciDGET/blob/main/docs/user/TOKEN.md"

/**
 * Settings → Tokens (D23): the saved tokens (label, last 4, expiry, state) each with Replace and Delete, and a
 * form to add one. The pasted text lives only in this screen's `remember` state and is cleared after a
 * successful save.
 *
 * @param showContentsHint true when the branch list of some repo was forbidden (token lacks Contents: read).
 */
@Composable
fun TokenScreen(graph: AppGraph, showContentsHint: Boolean, modifier: Modifier = Modifier) {
    val controller = remember { TokenController(graph) }
    val tokens by graph.tokenStore.summaries.collectAsState()
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var input by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var replaceId by remember { mutableStateOf<String?>(null) }
    var visible by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<ErrorText?>(null) }
    var emptyError by remember { mutableStateOf(false) }
    var savedNow by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (tokens.isEmpty()) Text(stringResource(R.string.token_none), style = MaterialTheme.typography.titleMedium)
        tokens.forEach { t ->
            val name = t.label.ifEmpty { stringResource(R.string.token_unnamed) }
            Text(stringResource(R.string.token_item, name, t.last4), style = MaterialTheme.typography.titleMedium)
            val exp = t.expiresAt
            Text(
                if (exp == null) {
                    stringResource(R.string.token_no_expiry)
                } else {
                    val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                        .withZone(ZoneId.systemDefault()).format(exp.toJavaInstant())
                    stringResource(R.string.token_expires, date)
                },
            )
            if (t.state == TokenState.INVALID) {
                Text(stringResource(R.string.token_invalid), color = MaterialTheme.colorScheme.error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = !busy,
                    onClick = { replaceId = t.id; label = t.label; input = ""; error = null; savedNow = false },
                ) { Text(stringResource(R.string.token_replace)) }
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            controller.delete(t.id)
                            if (replaceId == t.id) { replaceId = null; label = ""; input = "" }
                            savedNow = false
                            busy = false
                        }
                    },
                ) { Text(stringResource(R.string.token_delete)) }
            }
            HorizontalDivider()
        }
        if (showContentsHint) Text(stringResource(R.string.token_contents_hint))

        Text(
            stringResource(if (replaceId == null) R.string.token_add_title else R.string.token_replace_title),
            style = MaterialTheme.typography.titleMedium,
        )
        OutlinedTextField(
            value = label,
            onValueChange = { label = it },
            label = { Text(stringResource(R.string.token_label_field)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
        )
        OutlinedTextField(
            value = input,
            onValueChange = { input = it; error = null; emptyError = false; savedNow = false },
            label = { Text(stringResource(R.string.token_field_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
            enabled = !busy,
        )
        TextButton(onClick = { visible = !visible }) {
            Text(stringResource(if (visible) R.string.token_hide else R.string.token_show))
        }
        if (emptyError) Text(stringResource(R.string.token_err_empty), color = MaterialTheme.colorScheme.error)
        error?.let { e ->
            val msg = if (e.arg != null) stringResource(e.res, e.arg) else stringResource(e.res)
            Text(msg, color = MaterialTheme.colorScheme.error)
        }
        if (savedNow) Text(stringResource(R.string.token_saved_ok))
        if (busy) Text(stringResource(R.string.token_checking))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        when (val r = controller.validateAndSave(input, label, replaceId)) {
                            SaveResult.Saved -> { input = ""; label = ""; replaceId = null; savedNow = true }
                            SaveResult.Empty -> emptyError = true
                            is SaveResult.Failed -> error = r.error.toErrorText()
                        }
                        busy = false
                    }
                },
            ) { Text(stringResource(R.string.token_save)) }
            if (replaceId != null) {
                OutlinedButton(
                    enabled = !busy,
                    onClick = { replaceId = null; label = ""; input = ""; error = null },
                ) { Text(stringResource(R.string.token_cancel)) }
            }
        }
        TextButton(onClick = { uriHandler.openUri(TOKEN_HELP_URL) }) {
            Text(stringResource(R.string.token_howto))
        }
    }
}
