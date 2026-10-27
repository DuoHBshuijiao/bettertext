package me.rerere.bettertext.latex

import me.rerere.bettertext.latex.functions.Builtins
import me.rerere.bettertext.latex.layout.Box
import me.rerere.bettertext.latex.layout.Options
import me.rerere.bettertext.latex.layout.RuleBox
import me.rerere.bettertext.latex.layout.SpanBox
import me.rerere.bettertext.latex.layout.Style
import me.rerere.bettertext.latex.layout.VListBox
import me.rerere.bettertext.latex.layout.buildGroup
import me.rerere.bettertext.latex.layout.buildHTML
import me.rerere.bettertext.latex.parse.AlignSpec
import me.rerere.bettertext.latex.parse.ArrayNode
import me.rerere.bettertext.latex.parse.CrNode
import me.rerere.bettertext.latex.parse.LeftRightNode
import me.rerere.bettertext.latex.parse.MathOrdNode
import me.rerere.bettertext.latex.parse.Mode
import me.rerere.bettertext.latex.parse.OrdGroupNode
import me.rerere.bettertext.latex.parse.StylingNode
import me.rerere.bettertext.latex.parse.parseTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrayTest {
    private val eps = 1e-3f

    private fun settings(display: Boolean = false) = LatexSettings(displayMode = display)

    private fun layout(latex: String, display: Boolean = false) = LatexLayout.create(latex, settings(display))

    private fun parseArray(latex: String, display: Boolean = false): ArrayNode =
        parseTree(latex, settings(display)).single() as ArrayNode

    private fun ord(c: String) = OrdGroupNode(Mode.MATH, listOf(MathOrdNode(Mode.MATH, c)))

    private fun width(c: String): Float {
        Builtins.ensureRegistered()
        return buildGroup(MathOrdNode(Mode.MATH, c), Options(Style.TEXT)).advance
    }

    private fun collect(box: Box, out: MutableList<Box> = ArrayList()): List<Box> {
        out.add(box)
        when (box) {
            is SpanBox -> box.children.forEach { collect(it, out) }
            is VListBox -> box.items.forEach { collect(it.box, out) }
            else -> Unit
        }
        return out
    }

    // ---- parsing ----

    @Test
    fun parsesMatrix() {
        val node = parseArray("\\begin{matrix}a&b\\\\c&d\\end{matrix}")
        assertEquals(2, node.body.size)
        assertEquals(listOf(2, 2), node.body.map { it.size })
        assertEquals(false, node.hskipBeforeAndAfter)
        assertEquals(2, node.cols!!.size)
        assertTrue(node.body[0][0] is StylingNode)
        assertEquals(1f, node.arraystretch)
    }

    @Test
    fun trailingNewlineDropsEmptyRow() {
        val node = parseArray("\\begin{matrix}a\\\\b\\\\\\end{matrix}")
        assertEquals(2, node.body.size)
    }

    @Test
    fun parsesArrayColumnSpecAndHlines() {
        val node = parseArray("\\begin{array}{c|l}\\hline a&b\\\\\\hdashline c&d\\\\\\hline\\end{array}")
        val cols = node.cols!!
        assertEquals(3, cols.size)
        assertEquals("c", (cols[0] as AlignSpec.Align).align)
        assertEquals("|", (cols[1] as AlignSpec.Separator).separator)
        assertEquals("l", (cols[2] as AlignSpec.Align).align)
        assertEquals(2, node.body.size)
        assertEquals(listOf(listOf(false), listOf(true), listOf(false)), node.hLinesBeforeRow)
    }

    @Test
    fun crMacroAndRowGap() {
        val node = parseArray("\\begin{matrix}a\\cr b\\\\[2pt] c\\end{matrix}")
        assertEquals(3, node.body.size)
        assertNull(node.rowGaps[0])
        assertEquals(2f, node.rowGaps[1]!!.number)
    }

    @Test
    fun arraystretchMacro() {
        val node = parseArray("\\begin{matrix}a\\end{matrix}")
        assertEquals(1f, node.arraystretch)
        val stretched = parseTree(
            "\\begin{matrix}a\\end{matrix}",
            LatexSettings(macros = mapOf("\\arraystretch" to "1.5")),
        ).single() as ArrayNode
        assertEquals(1.5f, stretched.arraystretch)
    }

    @Test
    fun parsesAligned() {
        val node = parseArray("\\begin{aligned}a&=b\\\\c&=d\\end{aligned}")
        val cols = node.cols!!.map { it as AlignSpec.Align }
        assertEquals(listOf("r", "l"), cols.map { it.align })
        assertEquals("align", node.colSeparationType)
        assertEquals(true, node.addJot)
        assertNull(node.tags)
        // An empty group is prepended to the second cell so `=` stays a relation.
        val second = (node.body[0][1] as StylingNode).body[0] as OrdGroupNode
        assertTrue(second.body[0] is OrdGroupNode)
    }

    @Test
    fun parsesAlignat() {
        val node = parseArray("\\begin{alignedat}{2}a&=b&c&=d\\end{alignedat}")
        assertEquals(4, node.cols!!.size)
        assertEquals("alignat", node.colSeparationType)
    }

    @Test
    fun alignRequiresDisplayMode() {
        assertNotNull(layout("\\begin{align}a&=b\\end{align}").error)
        val node = parseArray("\\begin{align}a&=b\\\\c&=d\\end{align}", display = true)
        assertEquals(listOf<Any>(true, true), node.tags)
    }

    @Test
    fun nonumberSuppressesTag() {
        val node = parseArray("\\begin{gather}a\\nonumber\\\\b\\end{gather}", display = true)
        assertEquals(listOf<Any>(false, true), node.tags)
    }

    @Test
    fun casesAndPmatrixProduceLeftRight() {
        val cases = parseTree("\\begin{cases}a&b\\\\c&d\\end{cases}", settings()).single() as LeftRightNode
        assertEquals("\\{", cases.left)
        assertEquals(".", cases.right)
        assertEquals(1.2f, (cases.body.single() as ArrayNode).arraystretch)
        val rcases = parseTree("\\begin{rcases}a\\end{rcases}", settings()).single() as LeftRightNode
        assertEquals("\\}", rcases.right)
        val pm = parseTree("\\begin{pmatrix*}[r]a&b\\end{pmatrix*}", settings()).single() as LeftRightNode
        assertEquals("(", pm.left)
        val inner = pm.body.single() as ArrayNode
        assertEquals("r", (inner.cols!![1] as AlignSpec.Align).align)
    }

    @Test
    fun environmentErrors() {
        assertNotNull(layout("\\begin{foo}a\\end{foo}").error)
        assertNotNull(layout("\\begin{matrix}a\\end{pmatrix}").error)
        assertNotNull(layout("\\hline").error)
        assertNotNull(layout("\\begin{array}{x}a\\end{array}").error)
        assertNotNull(layout("\\begin{subarray}{c}a&b\\end{subarray}").error)
        assertNotNull(layout("\\begin{equation}a&b\\end{equation}", display = true).error)
    }

    @Test
    fun topLevelNewlineParsesToCr() {
        val tree = parseTree("a\\\\b", settings())
        assertEquals(3, tree.size)
        assertTrue(tree[1] is CrNode)
        assertTrue((tree[1] as CrNode).newLine)
        val sized = parseTree("a\\\\[3pt]b", settings())
        assertEquals(3f, (sized[1] as CrNode).size!!.number)
    }

    // ---- layout, independent of other builders (cells are plain ordgroups) ----

    @Test
    fun matrixLayoutDimensions() {
        Builtins.ensureRegistered()
        val node = ArrayNode(
            Mode.MATH,
            hskipBeforeAndAfter = false,
            cols = listOf(AlignSpec.Align("c"), AlignSpec.Align("c")),
            arraystretch = 1f,
            body = listOf(listOf(ord("a"), ord("b")), listOf(ord("c"), ord("d"))),
            rowGaps = listOf(null),
            hLinesBeforeRow = listOf(emptyList(), emptyList(), emptyList()),
        )
        val box = buildGroup(node, Options(Style.TEXT))
        // Columns are separated by 2 * \\arraycolsep (postgap + pregap) = 1em.
        val expectedWidth = maxOf(width("a"), width("c")) + 1f + maxOf(width("b"), width("d"))
        assertEquals(expectedWidth, box.width, eps)
        // KaTeX: two rows of \arstrut (0.84 + 0.36 em), centered on the axis.
        assertEquals(1.45f, box.height, eps)
        assertEquals(0.95f, box.depth, eps)
    }

    @Test
    fun arrayWithSeparatorAndHline() {
        Builtins.ensureRegistered()
        val node = ArrayNode(
            Mode.MATH,
            hskipBeforeAndAfter = true,
            cols = listOf(AlignSpec.Align("c"), AlignSpec.Separator("|"), AlignSpec.Align("l")),
            arraystretch = 1f,
            body = listOf(listOf(ord("a"), ord("b")), listOf(ord("c"), ord("d"))),
            rowGaps = listOf(null),
            hLinesBeforeRow = listOf(listOf(false), emptyList(), listOf(true)),
        )
        val box = buildGroup(node, Options(Style.TEXT))
        val expectedWidth = 0.5f + maxOf(width("a"), width("c")) + 0.5f + 0.5f + maxOf(width("b"), width("d")) + 0.5f
        assertEquals(expectedWidth, box.width, eps)
        val all = collect(box)
        val sep = all.single { it.hasClass("vertical-separator") } as RuleBox
        assertEquals(0f, sep.advance, eps)
        assertEquals(2.4f, sep.height + sep.depth, eps)
        val hline = all.single { it.hasClass("katex-hline") }
        val hdash = all.single { it.hasClass("katex-hdashline") } as RuleBox
        assertTrue(hdash.dashed)
        assertEquals(expectedWidth, hline.width, eps)
        assertEquals(expectedWidth, hdash.width, eps)
    }

    @Test
    fun columnAlignment() {
        Builtins.ensureRegistered()
        val node = ArrayNode(
            Mode.MATH,
            hskipBeforeAndAfter = false,
            cols = listOf(AlignSpec.Align("r")),
            arraystretch = 1f,
            body = listOf(listOf(ord("a")), listOf(OrdGroupNode(Mode.MATH, listOf(ord("a"), ord("a"), ord("a"))))),
            rowGaps = listOf(null),
            hLinesBeforeRow = listOf(emptyList(), emptyList(), emptyList()),
        )
        val box = buildGroup(node, Options(Style.TEXT))
        val vlist = collect(box).filterIsInstance<VListBox>().single()
        val first = vlist.items.first()
        // Right aligned: the narrow cell ends at the right edge.
        assertEquals(vlist.width, vlist.itemX(first) + first.box.advance, eps)
        assertTrue(vlist.itemX(first) > 0f)
    }

    // ---- end-to-end; these need the styling builder (cells are \textstyle groups) ----

    @Test
    fun matrixEndToEnd() {
        val l = layout("\\begin{matrix}a&b\\\\c&d\\end{matrix}")
        assertNull(l.error?.message, l.error)
        assertEquals(1, l.chunkCount)
        // Columns are separated by 2 * \\arraycolsep (postgap + pregap) = 1em.
        val expectedWidth = maxOf(width("a"), width("c")) + 1f + maxOf(width("b"), width("d"))
        assertEquals(expectedWidth, l.width, eps)
        assertEquals(1.45f, l.height, eps)
        assertEquals(0.95f, l.depth, eps)
    }

    @Test
    fun arrayEndToEnd() {
        val l = layout("\\begin{array}{c|l}a&b\\\\c&d\\end{array}")
        assertNull(l.error?.message, l.error)
        assertEquals(1.45f, l.height, eps)
    }

    @Test
    fun alignedEndToEnd() {
        val l = layout("\\begin{aligned}a&=b\\\\c&=d\\end{aligned}")
        assertNull(l.error?.message, l.error)
        assertTrue(l.width > 0f)
        // Rows get \jot (3pt) extra depth except the last: (0.7+0.3)*1.2 * 2 + 0.3.
        assertEquals(2.7f / 2 + 0.25f, l.height, eps)
    }

    @Test
    fun casesEndToEnd() {
        val l = layout("f(x)=\\begin{cases}a&x>0\\\\b&x\\le 0\\end{cases}")
        assertNull(l.error?.message, l.error)
        assertTrue(l.width > 0f)
    }

    @Test
    fun topLevelNewlineBreaksLine() {
        val l = layout("a\\\\b")
        assertNull(l.error?.message, l.error)
        assertEquals(2, l.chunkCount)
        assertTrue(l.isLineBreakBefore(1))
        val n = layout("a\\newline b")
        assertNull(n.error?.message, n.error)
        assertEquals(2, n.chunkCount)
        assertTrue(n.isLineBreakBefore(1))
    }

    @Test
    fun equationTag() {
        val tree = parseTree("\\begin{equation}x\\end{equation}", settings(true))
        val node = tree.single() as ArrayNode
        assertEquals(listOf<Any>(true), node.tags)
        val result = buildHTML(tree, Options(Style.DISPLAY))
        assertNotNull(result.tag)
        assertTrue(result.tag!!.hasClass("katex-tag"))
        assertEquals(1, result.chunks.size)
        // "(1)"
        assertTrue(result.tag!!.width > 0f)

        val starred = LatexLayout.create("\\begin{equation*}x\\end{equation*}", settings(true))
        assertNull(starred.error?.message, starred.error)
        assertNull(starred.tag)

        val tagged = LatexLayout.create("\\begin{equation}x\\tag{1}\\end{equation}", settings(true))
        assertNull(tagged.error?.message, tagged.error)
        assertNotNull(tagged.tag)
        assertTrue(tagged.tagWidth > 0f)
    }
}
