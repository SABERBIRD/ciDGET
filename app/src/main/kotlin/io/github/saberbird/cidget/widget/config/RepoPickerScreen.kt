package io.github.saberbird.cidget.widget.config

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.saberbird.cidget.R
import io.github.saberbird.cidget.di.AppGraph
import io.github.saberbird.cidget.github.ApiResult
import io.github.saberbird.cidget.github.RepoSummary
import io.github.saberbird.cidget.settings.ErrorText
import io.github.saberbird.cidget.settings.toErrorText
import kotlinx.coroutines.launch

private val repoPattern = Regex("^[A-Za-z0-9._-]+/[A-Za-z0-9._-]+$")

/**
 * Pick a repo from `/user/repos` (private ones get a lock) or type `owner/repo`, which is checked with
 * `GET /repos/{owner}/{repo}`. Needs a token; the caller shows the token screen otherwise.
 */
@Composable
fun RepoPickerScreen(graph: AppGraph, onPicked: suspend (String) -> Unit) {
    val token by graph.tokenStore.summary.collectAsState()
    val scope = rememberCoroutineScope()
    var repos by remember { mutableStateOf<List<RepoSummary>?>(null) }
    var error by remember { mutableStateOf<ErrorText?>(null) }
    var manual by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var badFormat by remember { mutableStateOf(false) }

    LaunchedEffect(token != null) {
        if (token == null) return@LaunchedEffect
        when (val r = graph.github.listUserRepos()) {
            is ApiResult.Ok -> repos = r.value
            is ApiResult.Err -> error = r.error.toErrorText()
        }
    }

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.picker_title), style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = manual,
            onValueChange = { manual = it; error = null; badFormat = false },
            label = { Text(stringResource(R.string.picker_manual_label)) },
            singleLine = true,
            enabled = !checking,
            modifier = Modifier.fillMaxWidth(),
        )
        if (badFormat) Text(stringResource(R.string.picker_bad_format), color = MaterialTheme.colorScheme.error)
        error?.let {
            val msg = if (it.arg != null) stringResource(it.res, it.arg) else stringResource(it.res)
            Text(msg, color = MaterialTheme.colorScheme.error)
        }
        Button(
            enabled = !checking && manual.isNotBlank(),
            onClick = {
                val text = manual.trim()
                if (!repoPattern.matches(text)) {
                    badFormat = true
                    return@Button
                }
                checking = true
                scope.launch {
                    when (val r = graph.github.getRepo(text.substringBefore('/'), text.substringAfter('/'))) {
                        is ApiResult.Ok -> onPicked(r.value.fullName)
                        is ApiResult.Err -> error = r.error.toErrorText()
                    }
                    checking = false
                }
            },
        ) { Text(stringResource(R.string.picker_use)) }

        val list = repos
        when {
            list == null && error == null -> Text(stringResource(R.string.picker_loading))
            list != null -> LazyColumn(Modifier.weight(1f, fill = false)) {
                items(list, key = { it.fullName }) { r ->
                    Text(
                        text = (if (r.isPrivate) "🔒 " else "    ") + r.fullName +
                            if (r.archived) " " + stringResource(R.string.picker_archived) else "",
                        modifier = Modifier.fillMaxWidth().clickable { scope.launch { onPicked(r.fullName) } }.padding(vertical = 12.dp),
                    )
                }
            }
        }
    }
}
