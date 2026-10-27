package me.rerere.bettertext.latex

import android.content.Context
import android.graphics.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import me.rerere.bettertext.latex.render.MathRenderer
import me.rerere.bettertext.latex.render.MathTypefaces

/**
 * Draws chunks of a [LatexLayout] onto an Android canvas, for hosts that lay
 * out text themselves. Not thread-safe: use one instance per drawing thread.
 */
class LatexPainter(context: Context) {
    private val renderer = MathRenderer(MathTypefaces.get(context))

    /**
     * Draws chunk [index] of [layout] with its left edge at [x] and baseline at [baseline].
     * [em] is the math em in pixels (font size × [DefaultMathScale]); [color] is ARGB.
     */
    fun drawChunk(canvas: Canvas, layout: LatexLayout, index: Int, x: Float, baseline: Float, em: Float, color: Int) {
        renderer.draw(canvas, layout.chunks[index], x, baseline, em, color)
    }
}

@Composable
fun rememberLatexPainter(): LatexPainter {
    val context = LocalContext.current
    return remember(context) { LatexPainter(context) }
}
