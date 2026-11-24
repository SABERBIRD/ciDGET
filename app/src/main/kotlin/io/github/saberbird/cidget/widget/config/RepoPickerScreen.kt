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
import io.github.saberbird.cidget.github.ApiError
import io.github.saberbird.cidget.github.ApiResult
import io.github.saberbird.cidget.data.RepoList
import io.github.saberbird.cidget.data.TokenState
import io.github.saberbird.cidget.settings.ErrorText
import io.github.saberbird.cidget.settings.toErrorText
import kotlinx.coroutines.launch

private val repoPattern = Regex("^[A-Za-z0-9._-]+/[A-Za-z0-9._-]+$")

/**
 * Pick a repo from the cached list of every token (private ones get a lock; the token label is shown when
 * there are several tokens) or type `owner/repo`, which is checked with each usable token in turn. Needs a
 * token; the caller shows the token screen otherwise. The list is refreshed in the worker (D24).
 */
@Composable
fun RepoPickerScreen(graph: AppGraph, onPicked: suspend (repo: String, tokenId: String) -> Unit) {
    val tokens by graph.tokenStore.summaries.collectAsState()
    val list by graph.repoListStore.observe().collectAsState(RepoList())
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<ErrorText?>(null) }
    var manual by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var badFormat by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { graph.requestRepoList() }

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
                    var firstError: ErrorText? = null
                    var found = false
                    for (t in tokens.filter { it.state == TokenState.OK }) {
                        when (val r = graph.githubFor(t.id).getRepo(text.substringBefore('/'), text.substringAfter('/'))) {
                            is ApiResult.Ok -> { onPicked(r.value.fullName, t.id); found = true; break }
                            is ApiResult.Err -> if (firstError == null || r.error != ApiError.NotFound) firstError = r.error.toErrorText()
                        }
                    }
                    if (!found) error = firstError ?: ApiError.NoToken.toErrorText()
                    checking = false
                }
            },
        ) { Text(stringResource(R.string.picker_use)) }

        list.error?.let {
            val e = it.toErrorText()
            val msg = if (e.arg != null) stringResource(e.res, e.arg) else stringResource(e.res)
            Text(msg, color = MaterialTheme.colorScheme.error)
        }
        val multi = tokens.size > 1
        when {
            list.repos.isEmpty() && (list.refreshing || list.error == null) -> Text(stringResource(R.string.picker_loading))
            else -> LazyColumn(Modifier.weight(1f, fill = false)) {
                items(list.repos, key = { it.fullName }) { r ->
                    val label = if (multi) tokens.firstOrNull { it.id == r.tokenId }?.label?.takeIf { it.isNotEmpty() } else null
                    Text(
                        text = (if (r.isPrivate) "🔒 " else "    ") + r.fullName +
                            (if (r.archived) " " + stringResource(R.string.picker_archived) else "") +
                            (if (label != null) "  [$label]" else ""),
                        modifier = Modifier.fillMaxWidth().clickable { scope.launch { onPicked(r.fullName, r.tokenId) } }.padding(vertical = 12.dp),
                    )
                }
            }
        }
    }
}
