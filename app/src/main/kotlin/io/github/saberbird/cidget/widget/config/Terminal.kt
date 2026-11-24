package io.github.saberbird.cidget.widget.config

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.saberbird.cidget.widget.Palette

private fun TextStyle.mono() = copy(fontFamily = FontFamily.Monospace)

private val terminalColors = darkColorScheme(
    primary = Palette.prompt,
    onPrimary = Palette.bg,
    background = Palette.bg,
    onBackground = Palette.fg,
    surface = Palette.bg,
    onSurface = Palette.fg,
    onSurfaceVariant = Palette.dim,
    outline = Palette.dim,
    error = Palette.red,
)

/** The settings look like the widget: dark background, monospace text, `[ button ]` and `[x]` controls (D25). */
@Composable
internal fun TerminalTheme(content: @Composable () -> Unit) {
    val base = Typography()
    val mono = Typography(
        displayLarge = base.displayLarge.mono(), displayMedium = base.displayMedium.mono(), displaySmall = base.displaySmall.mono(),
        headlineLarge = base.headlineLarge.mono(), headlineMedium = base.headlineMedium.mono(), headlineSmall = base.headlineSmall.mono(),
        titleLarge = base.titleLarge.mono(), titleMedium = base.titleMedium.mono(), titleSmall = base.titleSmall.mono(),
        bodyLarge = base.bodyLarge.mono(), bodyMedium = base.bodyMedium.mono(), bodySmall = base.bodySmall.mono(),
        labelLarge = base.labelLarge.mono(), labelMedium = base.labelMedium.mono(), labelSmall = base.labelSmall.mono(),
    )
    MaterialTheme(colorScheme = terminalColors, typography = mono) {
        Surface(color = Palette.bg, modifier = Modifier.background(Palette.bg)) { content() }
    }
}

/** `$ title` in the prompt colour. */
@Composable
internal fun TermTitle(text: String) {
    Text("$ $text", color = Palette.prompt, style = MaterialTheme.typography.titleMedium)
}

/** A command-like button: `[ text ]`, dim when disabled, in the prompt colour when [selected]. */
@Composable
internal fun TermButton(text: String, enabled: Boolean = true, selected: Boolean = false, onClick: () -> Unit) {
    Text(
        text = "[ $text ]",
        color = when {
            !enabled -> Palette.grey
            selected -> Palette.prompt
            else -> Palette.fg
        },
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick).padding(vertical = 8.dp),
    )
}

/** `[x] label` / `[ ] label`. */
@Composable
internal fun TermToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Text(
        text = (if (checked) "[x] " else "[ ] ") + label,
        color = if (checked) Palette.green else Palette.fg,
        modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 8.dp),
    )
}

/** `(*) label` / `( ) label`. */
@Composable
internal fun TermChoice(label: String, selected: Boolean, onSelect: () -> Unit) {
    Text(
        text = (if (selected) "(*) " else "( ) ") + label,
        color = if (selected) Palette.prompt else Palette.fg,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect).padding(vertical = 8.dp),
    )
}

/** A dim rule between sections. */
@Composable
internal fun TermRule() {
    Text("────────────────────────", color = Palette.dim, maxLines = 1, modifier = Modifier.padding(vertical = 4.dp))
}
