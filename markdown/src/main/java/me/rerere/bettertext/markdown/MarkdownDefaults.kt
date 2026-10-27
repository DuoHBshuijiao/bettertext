package me.rerere.bettertext.markdown

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import me.rerere.bettertext.latex.LatexBlock
import me.rerere.bettertext.text.BetterInline

/**
 * Visual parameters of [Markdown]. The defaults are neutral; apps map their
 * design system onto them. Unspecified colors derive from the text color.
 */
@Immutable
data class MarkdownTheme(
    /** Text styles of h1 to h6, merged onto the body style; em font sizes are relative to the body. */
    val headings: List<TextStyle> = floatArrayOf(1.5f, 1.375f, 1.25f, 1.125f, 1f, 0.875f).map { scale ->
        TextStyle(fontWeight = FontWeight.Bold, fontSize = scale.em, lineHeight = 1.25.em)
    },
    val link: SpanStyle = SpanStyle(textDecoration = TextDecoration.Underline),
    val inlineCode: SpanStyle = SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x24808080)),
    /** Space between blocks. */
    val blockSpacing: Dp = 12.dp,
    /** Color of list markers, numbers and task checkboxes; unspecified uses the text color. */
    val markerColor: Color = Color.Unspecified,
    /** Color of the bar beside block quotes; unspecified uses the text color at 30%. */
    val quoteColor: Color = Color.Unspecified,
    /** Color of dividers; unspecified uses the text color at 20%. */
    val dividerColor: Color = Color.Unspecified,
    /** Style of text in quotes ("…", “…”, 「…」, 『…』); null leaves it unstyled. */
    val quotedText: SpanStyle? = null,
)

/** What's available when building custom inline content. */
@Immutable
class MarkdownInlineScope(
    /** The text style of the paragraph. */
    val style: TextStyle,
    val density: Density,
    val contentColor: Color,
)

/**
 * Components registered by the app. The module draws text, lists, quotes and
 * formulas itself; everything else is up to the app, with a plain fallback
 * when a slot is null: code and tables show their source, images their alt
 * text, and details blocks their whole content.
 *
 * Keep instances stable (a top-level value or remembered), or every
 * recomposition rebuilds all the text.
 */
@Immutable
class MarkdownComponents(
    val codeBlock: (@Composable (block: MarkdownBlock.CodeBlock) -> Unit)? = null,
    val table: (@Composable (table: MarkdownBlock.Table) -> Unit)? = null,
    /** [link] is set when the image is wrapped in a link. */
    val image: (@Composable (image: MarkdownInline.Image, link: String?) -> Unit)? = null,
    /** Display formulas; null draws them with [MarkdownDefaults.MathBlock]. */
    val mathBlock: (@Composable (formula: String) -> Unit)? = null,
    /**
     * `<details>` blocks. [summary] and [content] render the parts, so the
     * component only decides how to lay them out and when to show the content.
     */
    val details: (
        @Composable (block: MarkdownBlock.Details, summary: @Composable () -> Unit, content: @Composable () -> Unit) -> Unit
    )? = null,
    /** Draw inline formulas; when false they show as monospace source. */
    val inlineMath: Boolean = true,
    /** Renders some links differently, e.g. as citation tags; null renders a normal link. */
    val inlineLink: (MarkdownInlineScope.(link: MarkdownInline.Link) -> BetterInline?)? = null,
)

object MarkdownDefaults {
    val theme = MarkdownTheme()
    val components = MarkdownComponents()

    /** A display formula in the current text size and color, scrolling horizontally when too wide. */
    @Composable
    fun MathBlock(formula: String, modifier: Modifier = Modifier) {
        val style = LocalMarkdownTextStyle.current
        LatexBlock(
            latex = formula,
            modifier = modifier,
            fontSize = style.fontSize,
            color = style.color.takeOrElse { Color.Black },
        )
    }
}
