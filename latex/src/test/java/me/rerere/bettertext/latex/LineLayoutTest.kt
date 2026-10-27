package me.rerere.bettertext.latex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LineLayoutTest {
    private fun display(latex: String, leqno: Boolean = false, fleqn: Boolean = false) =
        LatexLayout.create(latex, LatexSettings(displayMode = true, leqno = leqno, fleqn = fleqn))

    private fun LineResult.bodyRight() = lines.maxOf { it.x + it.width }
    private fun LineResult.bodyLeft() = lines.minOf { it.x }

    @Test
    fun tagDoesNotOverlapBodyWhenUnbounded() {
        val layout = display("a=b\\tag{1}")
        val r = LineCache(layout).measure(Float.POSITIVE_INFINITY)
        assertTrue("body ${r.bodyRight()} overlaps tag ${r.tagX}", r.bodyRight() + LineCache.TAG_GAP <= r.tagX + 1e-4f)
        assertEquals(r.width, r.tagX + layout.tagWidth, 1e-4f)
    }

    @Test
    fun bodyIsCenteredAndTagAtEdgeInWideSpace() {
        val layout = display("a=b\\tag{1}")
        val r = LineCache(layout).measure(20f)
        val line = r.lines.single()
        assertEquals((20f - line.width) / 2f, line.x, 1e-4f)
        assertEquals(20f - layout.tagWidth, r.tagX, 1e-4f)
    }

    @Test
    fun autoNumberedEquationDoesNotOverlap() {
        val layout = display("\\begin{equation}a=b\\end{equation}")
        val r = LineCache(layout).measure(Float.POSITIVE_INFINITY)
        assertTrue(layout.tag != null)
        assertTrue(r.bodyRight() + LineCache.TAG_GAP <= r.tagX + 1e-4f)
    }

    @Test
    fun bodyShiftsAwayFromTagWhenTight() {
        val layout = display("a+b+c+d=e\\tag{1}")
        val needed = layout.width + LineCache.TAG_GAP + layout.tagWidth
        val r = LineCache(layout).measure(needed + 0.2f)
        assertEquals(1, r.lines.size)
        assertTrue(r.bodyRight() + LineCache.TAG_GAP <= r.tagX + 1e-4f)
    }

    @Test
    fun tagMovesToOwnLineWhenTooNarrow() {
        val layout = display("a+b+c+d=e\\tag{1}")
        val r = LineCache(layout).measure(layout.width + 0.1f)
        val line = r.lines.single()
        assertTrue("tag must be below the body", r.tagBaseline > line.baseline)
        assertEquals(r.width - layout.tagWidth, r.tagX, 1e-4f)
    }

    @Test
    fun tallTagIsIncludedInLineHeight() {
        val layout = display("x\\tag{\$\\dfrac{1}{2}\$}")
        val tag = layout.tag!!
        val r = LineCache(layout).measure(20f)
        // The tag's top is at or below y=0 and its bottom within the total height.
        assertTrue(r.tagBaseline - tag.height >= -1e-4f)
        assertTrue(r.tagBaseline + tag.depth <= r.totalHeight + 1e-4f)
        assertEquals(r.lines.single().baseline, r.tagBaseline, 1e-4f)
    }

    @Test
    fun tallTagInEquationEnvironmentKeepsItsSize() {
        val frac = display("\\dfrac{1}{2}")
        val layout = display("\\begin{equation}x\\tag{\$\\dfrac{1}{2}\$}\\end{equation}")
        val tag = layout.tag!!
        // The tag box spans the fraction's real extent instead of the row's
        // (it sits on its row's baseline, which the array centers on the axis).
        assertTrue(tag.height + tag.depth >= frac.height + frac.depth - 1e-4f)
        val r = LineCache(layout).measure(20f)
        assertTrue(r.tagBaseline - tag.height >= -1e-4f)
        assertTrue(r.tagBaseline + tag.depth <= r.totalHeight + 1e-4f)
        assertTrue(r.totalHeight >= frac.height + frac.depth - 1e-4f)
    }

    @Test
    fun alignRowsGrowForTallTags() {
        val plain = display("\\begin{align}a&=b\\\\c&=d\\end{align}")
        val tall = display("\\begin{align}a&=b\\tag{\$\\dfrac{1}{2}\$}\\\\c&=d\\end{align}")
        assertTrue(tall.height + tall.depth > plain.height + plain.depth)
        val r = LineCache(tall).measure(20f)
        assertTrue(r.tagBaseline - tall.tag!!.height >= -1e-4f)
        assertTrue(r.tagBaseline + tall.tag!!.depth <= r.totalHeight + 1e-4f)
    }

    @Test
    fun leqnoPutsTagOnTheLeft() {
        val layout = display("a=b\\tag{1}", leqno = true)
        val r = LineCache(layout).measure(Float.POSITIVE_INFINITY)
        assertEquals(0f, r.tagX, 1e-4f)
        assertTrue(r.bodyLeft() >= layout.tagWidth + LineCache.TAG_GAP - 1e-4f)
    }

    @Test
    fun fleqnIndentsBody() {
        val layout = display("a=b", fleqn = true)
        val r = LineCache(layout).measure(20f)
        assertEquals(LineCache.FLEQN_INDENT, r.lines.single().x, 1e-4f)
    }

    @Test
    fun operatornameHyphenUsesHyphenWidth() {
        val op = LatexLayout.create("\\operatorname{a-b}")
        val text = LatexLayout.create("\\text{a-b}")
        assertEquals(1.38889f, text.width, 1e-4f)
        assertEquals(text.width, op.width, 1e-4f)
    }
}
