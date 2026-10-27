package me.rerere.bettertext.latex.render

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.graphics.PathParser
import me.rerere.bettertext.latex.layout.Box
import me.rerere.bettertext.latex.layout.RuleBox
import me.rerere.bettertext.latex.layout.SpanBox
import me.rerere.bettertext.latex.layout.SvgBox
import me.rerere.bettertext.latex.layout.SvgElement
import me.rerere.bettertext.latex.layout.SymbolBox
import me.rerere.bettertext.latex.layout.VListBox
import java.util.concurrent.ConcurrentHashMap

/** Draws a [Box] tree onto an Android canvas. */
internal class MathRenderer(private val typefaces: MathTypefaces) {
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val matrix = Matrix()
    private val tmpPath = Path()
    private val rect = RectF()

    /**
     * Draw [box] with its left edge at [x] and baseline at [y], in pixels.
     * [em] is the size of one em in pixels; [color] is the inherited ARGB color.
     */
    fun draw(canvas: Canvas, box: Box, x: Float, y: Float, em: Float, color: Int) {
        val c = box.color ?: color
        val top = y - box.height * em
        val bottom = y + box.depth * em
        box.backgroundColor?.let {
            fillPaint.color = it
            canvas.drawRect(x, top, x + box.width * em, bottom, fillPaint)
        }
        val clipped = box.clip
        if (clipped) {
            canvas.save()
            canvas.clipRect(x, top, x + box.width * em, bottom)
        }
        when (box) {
            is SymbolBox -> if (alpha(c) != 0 && box.text.isNotEmpty()) {
                textPaint.color = c
                textPaint.typeface = typefaces.get(box.font)
                textPaint.textSize = box.scale * em
                canvas.drawText(box.text, x, y, textPaint)
            }

            is SpanBox -> {
                var cx = x
                for (child in box.children) {
                    cx += child.marginLeft * em
                    draw(canvas, child, cx, y + child.shiftY * em, em, c)
                    cx += (child.advance - child.marginLeft) * em
                }
            }

            is VListBox -> for (item in box.items) {
                val child = item.box
                val cx = x + (box.itemX(item) + child.marginLeft) * em
                draw(canvas, child, cx, y + (item.y + child.shiftY) * em, em, c)
            }

            is RuleBox -> if (alpha(c) != 0) {
                fillPaint.color = c
                if (box.dashed) {
                    drawDashed(canvas, x, top, x + box.width * em, bottom)
                } else {
                    canvas.drawRect(x, top, x + box.width * em, bottom, fillPaint)
                }
            }

            is SvgBox -> if (alpha(c) != 0) drawSvg(canvas, box, x, y, em, c)
        }
        if (clipped) canvas.restore()
        if (box.borderWidth > 0f && alpha(box.borderColor ?: c) != 0) {
            strokePaint.color = box.borderColor ?: c
            val bw = box.borderWidth * em
            strokePaint.strokeWidth = bw
            canvas.drawRect(x + bw / 2, top + bw / 2, x + box.width * em - bw / 2, bottom - bw / 2, strokePaint)
        }
    }

    private fun drawDashed(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float) {
        val w = right - left
        val h = bottom - top
        if (h > w) {
            // Vertical rule, e.g. a `:` column separator.
            val dash = maxOf(w * 3f, 2f)
            var y = top
            while (y < bottom) {
                canvas.drawRect(left, y, right, minOf(y + dash, bottom), fillPaint)
                y += dash * 2
            }
            return
        }
        val dash = maxOf(h * 3f, 2f)
        var x = left
        while (x < right) {
            canvas.drawRect(x, top, minOf(x + dash, right), bottom, fillPaint)
            x += dash * 2
        }
    }

    private fun drawSvg(canvas: Canvas, box: SvgBox, x: Float, y: Float, em: Float, color: Int) {
        val w = box.width * em
        val h = box.svgHeight * em
        val top = y - h
        val viewBox = box.viewBox
        canvas.save()
        if (viewBox == null) {
            for (el in box.elements) if (el is SvgElement.Line) {
                strokePaint.color = color
                strokePaint.strokeWidth = el.strokeWidth * em
                canvas.drawLine(x + el.x1 * w, top + el.y1 * h, x + el.x2 * w, top + el.y2 * h, strokePaint)
            }
            canvas.restore()
            return
        }
        val parts = box.preserveAspectRatio.trim().split(' ')
        val align = parts[0]
        val slice = parts.getOrNull(1) == "slice"
        val sx = w / viewBox[2]
        val sy = h / viewBox[3]
        matrix.reset()
        if (align == "none") {
            matrix.setScale(sx, sy)
            matrix.postTranslate(x - viewBox[0] * sx, top - viewBox[1] * sy)
        } else {
            val s = if (slice) maxOf(sx, sy) else minOf(sx, sy)
            val dx = when {
                align.startsWith("xMin") -> 0f
                align.startsWith("xMax") -> w - viewBox[2] * s
                else -> (w - viewBox[2] * s) / 2
            }
            val dy = when {
                align.endsWith("YMin") -> 0f
                align.endsWith("YMax") -> h - viewBox[3] * s
                else -> (h - viewBox[3] * s) / 2
            }
            matrix.setScale(s, s)
            matrix.postTranslate(x + dx - viewBox[0] * s, top + dy - viewBox[1] * s)
        }
        // KaTeX svgs are absolutely positioned inside an overflow-hidden span.
        rect.set(x, top, x + w, top + h)
        canvas.clipRect(rect)
        fillPaint.color = color
        for (el in box.elements) {
            when (el) {
                is SvgElement.Path -> {
                    tmpPath.set(parse(el.d))
                    tmpPath.transform(matrix)
                    canvas.drawPath(tmpPath, fillPaint)
                }

                is SvgElement.Line -> {
                    strokePaint.color = color
                    strokePaint.strokeWidth = el.strokeWidth * em
                    canvas.drawLine(x + el.x1 * w, top + el.y1 * h, x + el.x2 * w, top + el.y2 * h, strokePaint)
                }
            }
        }
        canvas.restore()
    }

    private fun alpha(color: Int) = color ushr 24

    companion object {
        private val pathCache = ConcurrentHashMap<String, Path>()

        fun parse(d: String): Path = pathCache.getOrPut(d) { PathParser.createPathFromPathData(d) }
    }
}
