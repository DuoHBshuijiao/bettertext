package me.rerere.bettertext.latex

import me.rerere.bettertext.latex.layout.Box
import me.rerere.bettertext.latex.layout.GlyphMeasurer
import me.rerere.bettertext.latex.layout.Options
import me.rerere.bettertext.latex.layout.SpanBox
import me.rerere.bettertext.latex.layout.Style
import me.rerere.bettertext.latex.layout.buildHTML
import me.rerere.bettertext.latex.layout.makeSpan
import me.rerere.bettertext.latex.layout.makeSymbol
import me.rerere.bettertext.latex.layout.CssColor
import me.rerere.bettertext.latex.parse.Mode
import me.rerere.bettertext.latex.parse.parseTree

/**
 * A laid-out formula. All dimensions are in em of the font size it is drawn at.
 *
 * The formula is split into [chunkCount] unbreakable chunks, the same way
 * KaTeX splits inline math: after each top-level relation or binary operator
 * (and at `\allowbreak`). A host text layout may place each chunk separately
 * so long formulas wrap across lines.
 */
class LatexLayout internal constructor(
    val source: String,
    val displayMode: Boolean,
    internal val chunks: List<SpanBox>,
    /** Indices of chunks that must start a new line (`\\` / `\newline`). */
    internal val lineBreaksBefore: Set<Int>,
    internal val tag: Box?,
    /** Non-null if the source failed to parse; the layout then shows the source text. */
    val error: LatexParseException?,
    /** Place the equation tag on the left ([LatexSettings.leqno]). */
    val leqno: Boolean = false,
    /** Left-align display math ([LatexSettings.fleqn]). */
    val fleqn: Boolean = false,
) {
    val chunkCount: Int get() = chunks.size

    fun chunkWidth(index: Int): Float = chunks[index].advance
    fun chunkHeight(index: Int): Float = chunks[index].height
    fun chunkDepth(index: Int): Float = chunks[index].depth

    /** Whether chunk [index] must start on a new line. */
    fun isLineBreakBefore(index: Int): Boolean = index in lineBreaksBefore

    /** Width when laid out on a single line (ignoring forced line breaks). */
    val width: Float get() = chunks.sumOf { it.advance.toDouble() }.toFloat()
    val height: Float get() = chunks.maxOfOrNull { it.height } ?: 0f
    val depth: Float get() = chunks.maxOfOrNull { it.depth } ?: 0f

    val tagWidth: Float get() = tag?.advance ?: 0f

    companion object {
        /**
         * Parses and lays out [latex]. Never throws: parse errors produce a
         * layout showing the source in [LatexSettings.errorColor], with [error] set.
         */
        fun create(latex: String, settings: LatexSettings = LatexSettings()): LatexLayout =
            create(latex, settings, GlyphMeasurer.Approximate)

        internal fun create(latex: String, settings: LatexSettings, measurer: GlyphMeasurer): LatexLayout {
            val options = Options(
                style = if (settings.displayMode) Style.DISPLAY else Style.TEXT,
                maxSize = settings.maxSize,
                minRuleThickness = settings.minRuleThickness,
                measurer = measurer,
            )
            return try {
                val tree = parseTree(latex, settings)
                val result = buildHTML(tree, options)
                val chunks = ArrayList<SpanBox>()
                val breaks = HashSet<Int>()
                for (chunk in result.chunks) {
                    if (chunk == null) breaks.add(chunks.size) else chunks.add(chunk)
                }
                LatexLayout(latex, settings.displayMode, chunks, breaks, result.tag, null, settings.leqno, settings.fleqn)
            } catch (e: LatexParseException) {
                errorLayout(latex, settings, options, e)
            } catch (e: RuntimeException) {
                errorLayout(latex, settings, options, LatexParseException(e.message ?: e.toString()))
            }
        }

        private fun errorLayout(
            latex: String,
            settings: LatexSettings,
            options: Options,
            error: LatexParseException,
        ): LatexLayout {
            val colored = options.withColor(settings.errorColor)
            val symbols = latex.codePoints().toArray().map { cp ->
                makeSymbol(String(Character.toChars(cp)), "Main-Regular", Mode.TEXT, colored)
            }
            val span = makeSpan(listOf("katex-error"), symbols, colored)
            span.color = CssColor.parse(settings.errorColor)
            return LatexLayout(latex, settings.displayMode, listOf(span), emptySet(), null, error, settings.leqno, settings.fleqn)
        }
    }
}
