package me.rerere.bettertext.gallery.highlight

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import me.rerere.bettertext.highlight.CODE_FONT_FEATURES
import me.rerere.bettertext.highlight.HighlightTextColorPalette
import me.rerere.bettertext.highlight.rememberHighlightedCode
import me.rerere.bettertext.text.BetterText

/** The colors code is highlighted with. */
internal enum class CodeTheme(val label: String) {
    System("跟随主题"),
    Dark("One Dark"),
    Light("One Light"),
}

/**
 * Highlighted code on a card. It's drawn with BetterText, not Text, so inside a
 * BetterSelectionContainer it can be selected along with the text around it.
 */
@Composable
internal fun CodeCard(code: String, language: String?, modifier: Modifier = Modifier, theme: CodeTheme = CodeTheme.System) {
    val background = when (theme) {
        CodeTheme.System -> MaterialTheme.colorScheme.surfaceContainerHigh
        CodeTheme.Dark -> Color(0xFF282C34)
        CodeTheme.Light -> Color(0xFFFAFAFA)
    }
    val colors = if (background.luminance() < 0.5f) HighlightTextColorPalette.AtomOneDark else HighlightTextColorPalette.AtomOneLight
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = background) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            language?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = colors.comment) }
            BetterText(
                text = rememberHighlightedCode(code, language.orEmpty(), colors),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodySmall.copy(
                    color = colors.fallback,
                    fontFamily = FontFamily.Monospace,
                    fontFeatureSettings = CODE_FONT_FEATURES,
                ),
            )
        }
    }
}
