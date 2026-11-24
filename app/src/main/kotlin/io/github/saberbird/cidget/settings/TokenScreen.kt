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
 * Settings → Token: paste, show/hide, validate and save, replace, delete. The pasted text lives only in
 * this screen's `remember` state and is cleared after a successful save.
 *
 * @param showContentsHint true when the branch list of some repo was forbidden (token lacks Contents: read).
 */
@Composable
fun TokenScreen(graph: AppGraph, showContentsHint: Boolean, modifier: Modifier = Modifier) {
    val controller = remember { TokenController(graph) }
    val summary by graph.tokenStore.summary.collectAsState()
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var input by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<ErrorText?>(null) }
    var emptyError by remember { mutableStateOf(false) }
    var savedNow by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val s = summary
        if (s == null) {
            Text(stringResource(R.string.token_none), style = MaterialTheme.typography.titleMedium)
        } else {
            Text(stringResource(R.string.token_saved, s.last4), style = MaterialTheme.typography.titleMedium)
            val exp = s.expiresAt
            Text(
                if (exp == null) {
                    stringResource(R.string.token_no_expiry)
                } else {
                    val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                        .withZone(ZoneId.systemDefault()).format(exp.toJavaInstant())
                    stringResource(R.string.token_expires, date)
                },
            )
            if (s.state == TokenState.INVALID) {
                Text(stringResource(R.string.token_invalid), color = MaterialTheme.colorScheme.error)
            }
        }
        if (showContentsHint) Text(stringResource(R.string.token_contents_hint))

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
                        when (val r = controller.validateAndSave(input)) {
                            SaveResult.Saved -> { input = ""; savedNow = true }
                            SaveResult.Empty -> emptyError = true
                            is SaveResult.Failed -> error = r.error.toErrorText()
                        }
                        busy = false
                    }
                },
            ) { Text(stringResource(R.string.token_save)) }
            if (summary != null) {
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch { controller.delete(); savedNow = false; busy = false }
                    },
                ) { Text(stringResource(R.string.token_delete)) }
            }
        }
        TextButton(onClick = { uriHandler.openUri(TOKEN_HELP_URL) }) {
            Text(stringResource(R.string.token_howto))
        }
    }
}
