package me.rerere.bettertext.latex

import me.rerere.bettertext.latex.layout.Box
import me.rerere.bettertext.latex.layout.Options
import me.rerere.bettertext.latex.layout.SpanBox
import me.rerere.bettertext.latex.layout.StretchyBox
import me.rerere.bettertext.latex.layout.Style
import me.rerere.bettertext.latex.layout.SvgBox
import me.rerere.bettertext.latex.layout.VListBox
import me.rerere.bettertext.latex.layout.buildHTML
import me.rerere.bettertext.latex.parse.parseTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccentTest {
    private fun layout(latex: String, display: Boolean = false): LatexLayout {
        val l = LatexLayout.create(latex, LatexSettings(displayMode = display))
        assertNull("$latex: ${l.error?.message}", l.error)
        return l
    }

    private fun tree(latex: String): Box {
        val settings = LatexSettings()
        val result = buildHTML(parseTree(latex, settings), Options(Style.TEXT))
        // Widths of `width: 100%` boxes are resolved lazily by their vlist.
        return result.chunks.single()!!.also { it.width }
    }

    private fun walk(box: Box, visit: (Box) -> Unit) {
        visit(box)
        when (box) {
            is SpanBox -> box.children.forEach { walk(it, visit) }
            is VListBox -> box.items.forEach { walk(it.box, visit) }
            else -> Unit
        }
    }

    private fun svgs(box: Box): List<SvgBox> {
        val out = ArrayList<SvgBox>()
        walk(box) { if (it is SvgBox) out.add(it) }
        return out
    }

    @Test
    fun hat() {
        val x = layout("x")
        val l = layout("\\hat x")
        // KaTeX: \hat x has height 0.6944 (the accent's top), same width as x.
        assertEquals(0.6944f, l.height, 0.01f)
        assertEquals(x.width, l.width, 0.001f)
    }

    @Test
    fun vec() {
        val l = layout("\\vec{v}")
        assertEquals(0.714f, l.height, 0.01f)
        assertTrue(svgs(tree("\\vec{v}")).size == 1)
    }

    @Test
    fun widehatIsWider() {
        val hat = layout("\\hat{abc}")
        val wide = layout("\\widehat{abc}")
        assertEquals(hat.width, wide.width, 0.001f)
        // The wide accent image spans the whole base; \hat is a single glyph.
        val svg = svgs(tree("\\widehat{abc}")).single()
        assertEquals(wide.width, svg.width, 0.001f)
        assertTrue(svg.width > 1.0f)
        assertEquals("none", svg.preserveAspectRatio)
        // abc (height 0.6944) + image 0.3
        assertEquals(0.6944f + 0.3f, wide.height, 0.01f)
    }

    @Test
    fun overrightarrow() {
        val l = layout("\\overrightarrow{AB}")
        val ab = layout("AB")
        assertTrue(l.height > ab.height + 0.4f)
        val svg = svgs(tree("\\overrightarrow{AB}")).single()
        assertEquals("xMaxYMin slice", svg.preserveAspectRatio)
        assertTrue(svg.width >= 0.888f)
    }

    @Test
    fun overlineAndUnderline() {
        val xy = layout("x+y")
        val over = layout("\\overline{x+y}")
        // inner height + 3 rule + rule + rule (0.04 each)
        assertEquals(xy.height + 5 * 0.04f, over.height, 0.01f)
        val under = layout("\\underline{x}")
        val x = layout("x")
        assertEquals(x.depth + 5 * 0.04f, under.depth, 0.01f)
    }

    @Test
    fun braces() {
        val over = layout("\\overbrace{a+b}^{n}")
        val plain = layout("a+b")
        assertTrue(over.height > plain.height + 0.548f)
        val under = layout("\\underbrace{a+b}_{n}")
        assertTrue(under.depth > plain.depth + 0.548f)
        val pieces = svgs(tree("\\overbrace{a+b}"))
        assertEquals(3, pieces.size)
        val total = pieces.sumOf { it.advance.toDouble() }.toFloat()
        assertEquals(plain.width, total, 0.01f)
    }

    @Test
    fun xrightarrow() {
        val l = layout("\\xrightarrow[below]{above}")
        assertTrue(l.width > 2f)
        assertTrue(l.height > 0.5f)
        assertTrue(l.depth > 0.3f)
        val short = layout("\\xrightarrow{}")
        // min-width 1.469em
        assertTrue(short.width >= 1.469f - 0.001f)
        var stretchy: StretchyBox? = null
        walk(tree("\\xrightarrow{}")) { if (it is StretchyBox) stretchy = it }
        assertEquals(1.469f, stretchy!!.width, 0.01f)
    }

    @Test
    fun cancel() {
        val x = layout("x")
        val l = layout("\\cancel{x}")
        assertEquals(x.height, l.height, 0.001f)
        assertEquals(x.width, l.width, 0.001f)
        // cancel-pad (0.2em each side) is cancelled by cancel-lap's negative margins.
        assertEquals(layout("ab").width, layout("\\xcancel{ab}").width, 0.001f)
        assertEquals(2, svgs(tree("\\xcancel{ab}")).single().elements.size)
    }

    @Test
    fun fbox() {
        val l = layout("\\fbox{x}")
        // padding 0.3em on each side plus fboxsep and fboxrule above/below.
        assertTrue(l.width > 0.6f + 0.5f)
        val b = layout("\\boxed{x}")
        assertTrue(b.height > 0.43f + 0.3f)
        assertTrue(b.depth > 0.3f)
    }

    @Test
    fun others() {
        for (s in listOf(
            "\\acute a", "\\bar x", "\\tilde{x}", "\\dot x", "\\ddot x", "\\check x", "\\breve x",
            "\\mathring x", "\\widetilde{abcdefg}", "\\widecheck{ab}", "\\overleftrightarrow{AB}",
            "\\Overrightarrow{AB}", "\\overgroup{AB}", "\\overlinesegment{AB}",
            "\\underleftarrow{AB}", "\\utilde{AB}", "\\undergroup{AB}",
            "\\overbracket{a+b}", "\\underbracket{a+b}",
            "\\xleftarrow{a}", "\\xLeftrightarrow{ab}", "\\xhookrightarrow{a}", "\\xmapsto{a}",
            "\\xrightleftharpoons{a}", "\\xlongequal{a}", "\\xtofrom{a}",
            "\\bcancel{x}", "\\xcancel{abc}", "\\phase{x}", "\\hat x^2", "\\hat{x}_1^2",
            "\\overbrace{a}^{b}_{c}",
        )) {
            layout(s)
        }
    }
}
