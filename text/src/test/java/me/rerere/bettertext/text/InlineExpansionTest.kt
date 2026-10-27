package me.rerere.bettertext.text

import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.text.buildAnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class InlineExpansionTest {
    private class Segments(override val segmentCount: Int) : DrawnInline() {
        override fun measure(segment: Int, paint: Paint, metrics: InlineMetrics) = Unit
        override fun draw(segment: Int, canvas: Canvas, x: Float, baseline: Float, paint: Paint) = Unit
    }

    @Test
    fun replacesAltTextWithOneCharPerSegment() {
        val math = Segments(3)
        val cite = ComposableInline {}
        val text = buildAnnotatedString {
            append("ab")
            appendInline("math", "\$x+y\$")
            append("c")
            appendInline("cite", "[1]")
        }
        val expanded = expandInlines(text, mapOf("math" to math, "cite" to cite))

        assertEquals("ab￼￼￼c￼", expanded.text)
        assertEquals(listOf(2, 6), expanded.items.map { it.start })
        assertEquals(listOf(3, 1), expanded.items.map { it.segments })
        assertSame(math, expanded.items[0].content)
        // "ab" maps 1:1, the alt text collapses to the item start, "c" follows the item.
        assertEquals(listOf(0, 1, 2, 2, 2, 2, 2, 5, 6, 6, 6, 7), expanded.offsets.toList())
    }

    @Test
    fun keepsAltTextOfUnknownIds() {
        val text = buildAnnotatedString {
            append("a")
            appendInline("missing", "[?]")
        }
        val expanded = expandInlines(text, emptyMap())
        assertEquals("a[?]", expanded.text)
        assertEquals(emptyList<InlineItem>(), expanded.items)
        assertEquals(listOf(0, 1, 2, 3, 4), expanded.offsets.toList())
    }

    @Test
    fun dropsItemsWithoutSegments() {
        val text = buildAnnotatedString {
            append("a")
            appendInline("empty", "\$\$")
            append("b")
        }
        val expanded = expandInlines(text, mapOf("empty" to Segments(0)))
        assertEquals("ab", expanded.text)
        assertEquals(emptyList<InlineItem>(), expanded.items)
    }
}
