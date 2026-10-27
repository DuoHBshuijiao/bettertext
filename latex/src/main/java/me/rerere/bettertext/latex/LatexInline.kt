package me.rerere.bettertext.latex

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import me.rerere.bettertext.text.DrawnInline
import me.rerere.bettertext.text.InlineMetrics

/**
 * An inline formula for `BetterText`. Each unbreakable chunk of [layout] is a
 * segment, so long formulas wrap after relations and binary operators like
 * KaTeX. The formula is drawn [scale] times the surrounding font size, in the
 * surrounding color.
 */
class LatexInline(
    val layout: LatexLayout,
    private val painter: LatexPainter,
    val scale: Float = DefaultMathScale,
) : DrawnInline() {
    override val segmentCount: Int get() = layout.chunkCount

    override fun measure(segment: Int, paint: Paint, metrics: InlineMetrics) {
        val em = paint.textSize * scale
        metrics.width = layout.chunkWidth(segment) * em
        metrics.ascent = layout.chunkHeight(segment) * em
        metrics.descent = layout.chunkDepth(segment) * em
    }

    override fun draw(segment: Int, canvas: Canvas, x: Float, baseline: Float, paint: Paint) {
        painter.drawChunk(canvas, layout, segment, x, baseline, paint.textSize * scale, paint.color)
    }

    companion object {
        /** Laid-out formulas by source, so text that streams in doesn't lay out its formulas again. */
        private val layouts = LruCache<Pair<String, LatexSettings>, LatexLayout>(256)

        @Volatile
        private var painter: LatexPainter? = null

        /**
         * [latex] as an inline item, for text built outside composition, such as
         * the `inlineMath` component of Markdown. Layouts are cached by source and
         * settings, and all formulas share one painter, so draw them on the main thread.
         */
        fun cached(latex: String, context: Context, settings: LatexSettings = LatexSettings()): LatexInline {
            val key = latex to settings
            val layout = layouts.get(key) ?: LatexLayout.create(latex, settings, context).also { layouts.put(key, it) }
            val painter = painter ?: LatexPainter(context).also { painter = it }
            return LatexInline(layout, painter)
        }
    }
}

/** Parses [latex] with the bundled fonts and wraps it as an inline item. */
@Composable
fun rememberLatexInline(latex: String, settings: LatexSettings = LatexSettings()): LatexInline {
    val context = LocalContext.current
    val painter = rememberLatexPainter()
    return remember(latex, settings, painter) {
        LatexInline(LatexLayout.create(latex, settings, context), painter)
    }
}
