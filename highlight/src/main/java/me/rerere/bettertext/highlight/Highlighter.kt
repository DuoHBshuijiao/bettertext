package me.rerere.bettertext.highlight

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import me.rerere.bettertext.highlight.core.HighlightEngine
import me.rerere.bettertext.highlight.languages.builtinLanguages

/** Code longer than this is shown unhighlighted, since it's highlighted again on every change. */
const val MAX_HIGHLIGHTED_LENGTH = 4096

val LocalCodeHighlighter = staticCompositionLocalOf { CodeHighlighter() }

/**
 * A pure Kotlin syntax highlighter.
 *
 * Grammars are ported from highlight.js 11.11.1 and run on [HighlightEngine], a port of its mode
 * stack parser. An unsupported language is returned unhighlighted.
 *
 * Each language is compiled the first time it's used. Not thread-safe: compiled languages keep
 * matcher state, so call [highlight] from one thread at a time.
 */
class CodeHighlighter {
    private val engine = HighlightEngine(builtinLanguages())

    fun highlight(code: String, language: String): List<HighlightToken> {
        if (code.isEmpty()) return emptyList()

        return engine.highlight(code, language)
            ?: listOf(HighlightToken.Plain(code))
    }

    fun supports(language: String): Boolean = engine.supports(language)

    /** [code] highlighted with [colors], for `BetterText` or `Text`. */
    fun highlightToAnnotatedString(
        code: String,
        language: String,
        colors: HighlightTextColorPalette = HighlightTextColorPalette.Default,
    ): AnnotatedString = buildAnnotatedString {
        highlight(code, language).forEach { buildHighlightText(it, colors) }
    }
}

/**
 * [code] highlighted with [colors] by [LocalCodeHighlighter], or unhighlighted when longer than
 * [MAX_HIGHLIGHTED_LENGTH].
 *
 * Code fonts with ligatures, such as JetBrains Mono, should be drawn with [CODE_FONT_FEATURES].
 */
@Composable
fun rememberHighlightedCode(
    code: String,
    language: String,
    colors: HighlightTextColorPalette = HighlightTextColorPalette.Default,
): AnnotatedString {
    val highlighter = LocalCodeHighlighter.current
    return remember(code, language, colors, highlighter) {
        if (code.length > MAX_HIGHLIGHTED_LENGTH) {
            AnnotatedString(code)
        } else {
            highlighter.highlightToAnnotatedString(code, language, colors)
        }
    }
}

/** Font feature settings that turn off ligatures, so code shows the characters as typed. */
const val CODE_FONT_FEATURES = "'calt' 0, 'liga' 0, 'clig' 0"
