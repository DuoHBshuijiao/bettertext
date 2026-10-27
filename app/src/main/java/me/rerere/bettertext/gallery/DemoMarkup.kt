package me.rerere.bettertext.gallery

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import me.rerere.bettertext.latex.LatexLayout
import me.rerere.bettertext.latex.LatexPainter
import me.rerere.bettertext.latex.LatexSettings
import me.rerere.bettertext.text.BetterInline
import me.rerere.bettertext.text.ComposableInline
import me.rerere.bettertext.text.LatexInline
import me.rerere.bettertext.text.appendInline

/**
 * Converts the demo markup of [RichSample] into text and inline content. A
 * stand-in for the markdown layer, which doesn't exist yet.
 */
internal class DemoMarkup(
    private val context: Context,
    private val painter: LatexPainter,
    private val codeBackground: Color,
    private val citation: (Int) -> ComposableInline,
) {
    val inlineContent = HashMap<String, BetterInline>()

    fun AnnotatedString.Builder.appendMarkup(source: String) {
        var bold = false
        var i = 0
        while (i < source.length) {
            val c = source[i]
            val close = when {
                c == '$' || c == '`' -> source.indexOf(c, i + 1)
                source.startsWith("[^", i) -> source.indexOf(']', i)
                else -> -1
            }
            when {
                c == '$' && close > 0 -> {
                    val id = "math${inlineContent.size}"
                    val layout = LatexLayout.create(source.substring(i + 1, close), LatexSettings(), context)
                    inlineContent[id] = LatexInline(layout, painter)
                    appendInline(id, source.substring(i, close + 1))
                    i = close + 1
                }

                c == '`' && close > 0 -> {
                    withStyle(code()) { append(source, i + 1, close) }
                    i = close + 1
                }

                c == '[' && close > 0 && source.substring(i + 2, close).toIntOrNull() != null -> {
                    val n = source.substring(i + 2, close).toInt()
                    val id = "cite${inlineContent.size}"
                    inlineContent[id] = citation(n)
                    appendInline(id, "[$n]")
                    i = close + 1
                }

                source.startsWith("**", i) -> {
                    if (bold) pop() else pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    bold = !bold
                    i += 2
                }

                c == '#' && isHexColor(source, i + 1) -> {
                    val hex = source.substring(i, i + 7)
                    val id = "color${inlineContent.size}"
                    inlineContent[id] = ColorSwatch(Color(0xFF000000 or hex.substring(1).toLong(16)))
                    appendInline(id, "■")
                    withStyle(code()) { append(hex) }
                    i += 7
                }

                else -> {
                    append(c)
                    i++
                }
            }
        }
        if (bold) pop()
    }

    private fun code() = SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)

    private fun isHexColor(s: String, start: Int) =
        start + 6 <= s.length && (start until start + 6).all { s[it].isDigit() || s[it].lowercaseChar() in 'a'..'f' }
}

