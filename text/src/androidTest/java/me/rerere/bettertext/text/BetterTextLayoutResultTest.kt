package me.rerere.bettertext.text

import android.graphics.Canvas
import android.graphics.Paint
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.text.buildAnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BetterTextLayoutResultTest {
    @Test
    fun inputRangeAfterSegmentedFormulaUsesExpandedLayoutOffsets() {
        val input = buildAnnotatedString {
            append("prefix ")
            appendInline("formula", "\$x+y\$")
            append(" target")
        }
        val expanded = expandInlines(input, mapOf("formula" to item(2)))
        val layout = layout(expanded.text)
        val result = BetterTextLayoutResult(input, layout, expanded.offsets, expanded.endOffsets)
        val sourceStart = input.text.indexOf("target")
        val layoutStart = expanded.text.indexOf("target")
        val expected = android.graphics.Path().also { layout.getSelectionPath(layoutStart, layoutStart + 6, it) }
        assertEquals(expected.asComposePath().getBounds(), result.getPathForRange(sourceStart, sourceStart + 6).getBounds())
        assertEquals(layout.getLineForOffset(layoutStart), result.getLineForOffset(sourceStart))
    }

    @Test
    fun rangeEndingAtDecorationDoesNotSelectItsLayoutCharacters() {
        val input = buildAnnotatedString {
            append("before")
            appendInline("decoration", "")
            append("after")
        }
        val expanded = expandInlines(input, mapOf("decoration" to item(1)))
        val layout = layout(expanded.text)
        val result = BetterTextLayoutResult(input, layout, expanded.offsets, expanded.endOffsets)
        val expected = android.graphics.Path().also { layout.getSelectionPath(0, 6, it) }
        assertEquals(expected.asComposePath().getBounds(), result.getPathForRange(0, 6).getBounds())
        assertEquals(layout.getLineTop(0).toFloat(), result.getLineTop(0))
        assertEquals(layout.getLineBottom(0).toFloat(), result.getLineBottom(0))
    }

    private fun layout(text: String): StaticLayout = StaticLayout.Builder.obtain(
        text, 0, text.length, TextPaint().apply { textSize = 20f }, 80,
    ).build()

    private fun item(segments: Int) = object : DrawnInline() {
        override val segmentCount = segments
        override fun measure(segment: Int, paint: Paint, metrics: InlineMetrics) = Unit
        override fun draw(segment: Int, canvas: Canvas, x: Float, baseline: Float, paint: Paint) = Unit
    }
}
