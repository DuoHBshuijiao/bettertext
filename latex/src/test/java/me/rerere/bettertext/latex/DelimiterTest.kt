package me.rerere.bettertext.latex

import me.rerere.bettertext.latex.layout.Box
import me.rerere.bettertext.latex.layout.Options
import me.rerere.bettertext.latex.layout.SpanBox
import me.rerere.bettertext.latex.layout.Style
import me.rerere.bettertext.latex.layout.SvgBox
import me.rerere.bettertext.latex.layout.SymbolBox
import me.rerere.bettertext.latex.layout.VListBox
import me.rerere.bettertext.latex.layout.buildHTML
import me.rerere.bettertext.latex.parse.parseTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DelimiterTest {
    private val eps = 1e-3f

    private fun layout(latex: String, display: Boolean = false): LatexLayout {
        val l = LatexLayout.create(latex, LatexSettings(displayMode = display))
        assertNull("$latex: ${l.error?.message}", l.error)
        return l
    }

    private fun build(latex: String, display: Boolean = false): SpanBox {
        val settings = LatexSettings(displayMode = display)
        val options = Options(style = if (display) Style.DISPLAY else Style.TEXT)
        val result = buildHTML(parseTree(latex, settings), options)
        return result.chunks.single()!!
    }

    private fun walk(box: Box, visit: (Box) -> Unit) {
        visit(box)
        when (box) {
            is SpanBox -> box.children.forEach { walk(it, visit) }
            is VListBox -> box.items.forEach { walk(it.box, visit) }
            else -> Unit
        }
    }

    private fun symbols(box: Box): List<SymbolBox> {
        val out = ArrayList<SymbolBox>()
        walk(box) { if (it is SymbolBox) out.add(it) }
        return out
    }

    private fun svgs(box: Box): List<SvgBox> {
        val out = ArrayList<SvgBox>()
        walk(box) { if (it is SvgBox) out.add(it) }
        return out
    }

    @Test
    fun fracMatchesKatex() {
        // KaTeX: \frac{1}{2} has height 0.8451em, depth 0.345em.
        val l = layout("\\frac{1}{2}")
        assertEquals(0.8451f, l.height, eps)
        assertEquals(0.345f, l.depth, eps)

        val ab = layout("\\frac{a}{b}")
        assertEquals(0.394f + 0.43056f * 0.7f, ab.height, eps)
        assertEquals(0.345f, ab.depth, eps)
        assertTrue(ab.width > 0.5f)
    }

    @Test
    fun displayFracMatchesKatex() {
        // KaTeX: \dfrac{1}{2} / display \frac{1}{2} has height 1.3214em, depth 0.686em.
        val l = layout("\\frac{1}{2}", display = true)
        assertEquals(1.3214f, l.height, eps)
        assertEquals(0.686f, l.depth, eps)
    }

    @Test
    fun dfracTallerThanTfrac() {
        val d = layout("\\dfrac{1}{2}")
        val t = layout("\\tfrac{1}{2}")
        assertTrue(d.height + d.depth > t.height + t.depth)
        assertEquals(1.3214f, d.height, eps)
    }

    @Test
    fun infixFractions() {
        val over = layout("a \\over b")
        val frac = layout("\\frac{a}{b}")
        assertEquals(frac.height, over.height, eps)
        assertEquals(frac.depth, over.depth, eps)
        layout("a \\atop b")
        layout("n \\choose k")
        layout("a \\brace b")
        layout("a \\brack b")
        layout("a \\above 2pt b")
        layout("\\genfrac(]{0pt}{2}{a+b}{c+d}")
        layout("\\cfrac{1}{2+\\cfrac{1}{x}}")
    }

    @Test
    fun binomUsesSize1Parens() {
        val l = layout("\\binom{n}{k}")
        // Delimiters of size delim2 = 1.01em pick the Size1 parentheses (0.85 + 0.35).
        assertEquals(0.85f, l.height, eps)
        assertEquals(0.35f, l.depth, eps)
        val fonts = symbols(build("\\binom{n}{k}")).map { it.font }
        assertTrue(fonts.toString(), fonts.count { it == "Size1-Regular" } == 2)
        // No fraction bar.
        layout("\\dbinom{n}{k}")
        layout("\\tbinom{n}{k}")
    }

    @Test
    fun sqrtMatchesKatex() {
        // KaTeX: \sqrt{x} has height 0.8003em and depth 0.2397em.
        val l = layout("\\sqrt{x}")
        assertEquals(0.80028f, l.height, eps)
        assertEquals(0.23972f, l.depth, eps)
        val box = build("\\sqrt{x}")
        val boxWidth = box.width // resolves the `width: 100%` surd
        val surd = svgs(box).single()
        assertEquals("xMinYMin slice", surd.preserveAspectRatio)
        assertTrue(surd.fillWidth)
        // The surd spans the advance width (0.833em) plus the body.
        assertEquals(boxWidth, surd.width, eps)
        assertEquals(1.08f, surd.svgHeight, eps)
        assertTrue(l.width > 0.833f + 0.5f)
    }

    @Test
    fun sqrtWithIndex() {
        val plain = layout("\\sqrt{x}")
        val root = layout("\\sqrt[3]{x}")
        // The index adds 5mu - 10mu of kerning plus its own width (0.5 * 0.5em for "3"),
        // so it overlaps the surd and barely changes the width.
        assertEquals(plain.width + 0.27778f - 0.55556f + 0.25f, root.width, eps)
        assertEquals(plain.depth, root.depth, eps)
    }

    @Test
    fun tallSqrtUsesBiggerSurd() {
        val small = layout("\\sqrt{x}")
        val tall = layout("\\sqrt{\\frac{a}{b}}", display = true)
        assertTrue(tall.height + tall.depth > small.height + small.depth)
        val huge = layout("\\sqrt{\\frac{\\frac{a}{b}}{\\frac{c}{d}}}", display = true)
        assertTrue(huge.height + huge.depth > tall.height + tall.depth)
        layout("\\sqrt{\\frac{\\frac{\\frac{a}{b}}{\\frac{c}{d}}}{\\frac{\\frac{a}{b}}{\\frac{c}{d}}}}", display = true)
    }

    @Test
    fun leftRightAroundFrac() {
        // KaTeX: \left(\frac{a}{b}\right) has a 1.2em strut with depth 0.35em (Size1 parens).
        val l = layout("\\left( \\frac{a}{b} \\right)")
        assertEquals(0.85f, l.height, eps)
        assertEquals(0.35f, l.depth, eps)
        val box = build("\\left( \\frac{a}{b} \\right)")
        val inner = box.children.single()
        assertEquals("minner", inner.classes.first())
    }

    @Test
    fun leftRightStacks() {
        val l = layout("\\left( \\frac{\\frac{\\frac{a}{b}}{c}}{\\frac{d}{\\frac{e}{f}}} \\right]", display = true)
        val svg = svgs(build("\\left( \\frac{\\frac{\\frac{a}{b}}{c}}{\\frac{d}{\\frac{e}{f}}} \\right]", display = true))
        // Both delimiters became single tall SVGs.
        assertEquals(2, svg.size)
        assertTrue(svg.all { it.height >= l.height + l.depth - 0.5f })
        layout("\\left\\{ \\frac{\\frac{a}{b}}{\\frac{c}{d}} \\right\\}", display = true)
        layout("\\left\\| \\frac{\\frac{a}{b}}{\\frac{c}{d}} \\right\\Vert", display = true)
        layout("\\left\\uparrow \\frac{\\frac{a}{b}}{\\frac{c}{d}} \\right\\Downarrow", display = true)
        layout("\\left\\lgroup \\frac{\\frac{a}{b}}{\\frac{c}{d}} \\right\\rmoustache", display = true)
        layout("\\left. \\frac{a}{b} \\right|")
        val cases = layout("\\left\\{ \\begin{matrix} a \\\\ b \\\\ c \\end{matrix} \\right.")
        assertTrue(cases.height + cases.depth > 3f)
        layout("\\left\\langle x \\middle| y \\right\\rangle")
    }

    @Test
    fun middleIsResized() {
        val box = build("\\left( \\frac{a}{b} \\middle/ c \\right)")
        val fonts = symbols(box).map { it.font }
        // All three delimiters are Size1 glyphs.
        assertEquals(fonts.toString(), 3, fonts.count { it == "Size1-Regular" })
    }

    @Test
    fun bigDelimiters() {
        val bigl = layout("\\bigl(")
        assertEquals(0.85f, bigl.height, eps)
        assertEquals(0.35f, bigl.depth, eps)
        val bigg = layout("\\Bigg[")
        val fonts = symbols(build("\\Bigg[")).map { it.font }
        assertEquals(listOf("Size4-Regular"), fonts)
        assertTrue(bigg.height + bigg.depth > 2.9f)
        // \big| stacks to the size of the Size1 delimiters.
        val vert = layout("\\big|")
        assertEquals(1.2f, vert.height + vert.depth, 0.05f)
        layout("\\Big\\langle x \\Bigr\\rangle \\biggm| \\Biggl\\{ \\bigr.")
    }

    @Test
    fun invalidDelimiterIsError() {
        val l = LatexLayout.create("\\left a \\right)")
        assertTrue(l.error != null)
        val m = LatexLayout.create("\\middle|")
        assertTrue(m.error != null)
    }

    @Test
    fun scriptStyleFraction() {
        val l = layout("x^{\\frac{a}{b}} + \\binom{x^{\\binom{n}{k}}}{2}")
        assertTrue(l.height > 0.8f)
    }
}
