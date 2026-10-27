package me.rerere.bettertext.text

import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import me.rerere.bettertext.latex.DefaultMathScale
import me.rerere.bettertext.latex.LatexLayout
import me.rerere.bettertext.latex.LatexPainter
import me.rerere.bettertext.latex.LatexSettings
import me.rerere.bettertext.latex.rememberLatexPainter

/**
 * An inline formula. Each unbreakable chunk of [layout] is a segment, so long
 * formulas wrap after relations and binary operators like KaTeX. The formula
 * is drawn [scale] times the surrounding font size, in the surrounding color.
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
