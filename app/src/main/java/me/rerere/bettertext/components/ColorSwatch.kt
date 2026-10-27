package me.rerere.bettertext.components

import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import me.rerere.bettertext.text.DrawnInline
import me.rerere.bettertext.text.InlineMetrics

/**
 * An example of a custom [DrawnInline]: a rounded square of [color], centered
 * on the surrounding text, with a faint outline in the text color so it stays
 * visible on a background of the same color. Sizes are in em of the
 * surrounding font size.
 */
internal class ColorSwatch(
    val color: Color,
    val size: Float = 0.8f,
    val cornerRadius: Float = 0.2f,
    val margin: Float = 0.15f,
) : DrawnInline() {
    private val argb = color.toArgb()

    override fun measure(segment: Int, paint: Paint, metrics: InlineMetrics) {
        val s = size * paint.textSize
        val center = (paint.ascent() + paint.descent()) / 2f
        metrics.width = s + 2 * margin * paint.textSize
        metrics.ascent = s / 2 - center
        metrics.descent = s / 2 + center
    }

    override fun draw(segment: Int, canvas: Canvas, x: Float, baseline: Float, paint: Paint) {
        val em = paint.textSize
        val s = size * em
        val left = x + margin * em
        val top = baseline + (paint.ascent() + paint.descent()) / 2f - s / 2
        val r = cornerRadius * em
        val textColor = paint.color
        paint.style = Paint.Style.FILL
        paint.color = argb
        canvas.drawRoundRect(left, top, left + s, top + s, r, r, paint)

        val stroke = maxOf(1f, em / 16f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke
        paint.color = textColor and 0x00FFFFFF or (((textColor ushr 24) * 0.3f).toInt() shl 24)
        val inset = stroke / 2
        canvas.drawRoundRect(left + inset, top + inset, left + s - inset, top + s - inset, r, r, paint)
    }
}
