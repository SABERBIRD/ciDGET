package io.github.saberbird.cidget.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.saberbird.cidget.R
import io.github.saberbird.cidget.graph

/** Launcher activity: the settings home with the token screen. More screens arrive in later tasks. */
class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = applicationContext.graph
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var onToken by remember { mutableStateOf(false) }
                    BackHandler(enabled = onToken) { onToken = false }
                    Column(modifier = Modifier.safeDrawingPadding()) {
                        if (onToken) {
                            Text(
                                stringResource(R.string.token_title),
                                style = MaterialTheme.typography.headlineMedium,
                                modifier = Modifier.padding(start = 16.dp, top = 16.dp),
                            )
                            TokenScreen(graph, showContentsHint = false)
                        } else {
                            Text(
                                stringResource(R.string.settings_title),
                                style = MaterialTheme.typography.headlineMedium,
                                modifier = Modifier.padding(16.dp),
                            )
                            Text(
                                stringResource(R.string.token_title),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.fillMaxWidth().clickable { onToken = true }.padding(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
