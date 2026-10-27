package me.rerere.bettertext.latex.layout

import me.rerere.bettertext.latex.ParseError
import me.rerere.bettertext.latex.functions.Registry
import me.rerere.bettertext.latex.parse.Measurement
import me.rerere.bettertext.latex.parse.ParseNode
import me.rerere.bettertext.latex.parse.SizingNode
import me.rerere.bettertext.latex.parse.StylingNode
import me.rerere.bettertext.latex.parse.TagNode

/* Ported from KaTeX's buildHTML.ts and spacingData.ts. */

private val thinspace = Measurement(3f, "mu")
private val mediumspace = Measurement(4f, "mu")
private val thickspace = Measurement(5f, "mu")

/** Spacing between atom classes in display and text styles. */
private val spacings: Map<String, Map<String, Measurement>> = mapOf(
    "mord" to mapOf("mop" to thinspace, "mbin" to mediumspace, "mrel" to thickspace, "minner" to thinspace),
    "mop" to mapOf("mord" to thinspace, "mop" to thinspace, "mrel" to thickspace, "minner" to thinspace),
    "mbin" to mapOf("mord" to mediumspace, "mop" to mediumspace, "mopen" to mediumspace, "minner" to mediumspace),
    "mrel" to mapOf("mord" to thickspace, "mop" to thickspace, "mopen" to thickspace, "minner" to thickspace),
    "mopen" to emptyMap(),
    "mclose" to mapOf("mop" to thinspace, "mbin" to mediumspace, "mrel" to thickspace, "minner" to thinspace),
    "mpunct" to mapOf(
        "mord" to thinspace, "mop" to thinspace, "mrel" to thickspace, "mopen" to thinspace,
        "mclose" to thinspace, "mpunct" to thinspace, "minner" to thinspace,
    ),
    "minner" to mapOf(
        "mord" to thinspace, "mop" to thinspace, "mbin" to mediumspace, "mrel" to thickspace,
        "mopen" to thinspace, "mpunct" to thinspace, "minner" to thinspace,
    ),
)

/** Spacing between atom classes in script and scriptscript styles. */
private val tightSpacings: Map<String, Map<String, Measurement>> = mapOf(
    "mord" to mapOf("mop" to thinspace),
    "mop" to mapOf("mord" to thinspace, "mop" to thinspace),
    "mclose" to mapOf("mop" to thinspace),
    "minner" to mapOf("mop" to thinspace),
)

private val binLeftCanceller = setOf("leftmost", "mbin", "mopen", "mrel", "mop", "mpunct")
private val binRightCanceller = setOf("rightmost", "mrel", "mclose", "mpunct")
private val domTypes = setOf("mord", "mop", "mbin", "mrel", "mopen", "mclose", "mpunct", "minner")

/**
 * Take a list of nodes, build them in order, and return a list of the built
 * boxes. Fragments are flattened into their contents. [isRealGroup] is true if
 * [expression] is a real group (no atoms will be added on either side), false
 * for a partial group (e.g. one created by `\color`), and [ROOT] for the root.
 * [surrounding] gives the math classes of the atoms to the left and right.
 */
internal fun buildExpression(
    expression: List<ParseNode>,
    options: Options,
    isRealGroup: Any,
    surrounding: Pair<String?, String?> = null to null,
): MutableList<Box> {
    val groups = ArrayList<Box>()
    for (node in expression) {
        val output = buildGroup(node, options)
        if (output is FragmentBox) groups.addAll(output.children) else groups.add(output)
    }

    if (isRealGroup == false) return groups

    var glueOptions = options
    if (expression.size == 1) {
        val node = expression[0]
        if (node is SizingNode) {
            glueOptions = options.havingSize(node.size)
        } else if (node is StylingNode) {
            glueOptions = options.havingStyle(Style.fromName(node.style))
        }
    }

    val dummyPrev = makeSpan(listOf(surrounding.first ?: "leftmost"), emptyList(), options)
    val dummyNext = makeSpan(listOf(surrounding.second ?: "rightmost"), emptyList(), options)
    val isRoot = isRealGroup == ROOT

    // Before determining what spaces to insert, perform bin cancellation.
    traverseNonSpaceNodes(groups, { node, prev ->
        val prevType = prev.classes.firstOrNull()
        val type = node.classes.firstOrNull()
        if (prevType == "mbin" && type in binRightCanceller) {
            prev.classes[0] = "mord"
        } else if (type == "mbin" && prevType in binLeftCanceller) {
            node.classes[0] = "mord"
        }
        null
    }, TraversePrev(dummyPrev), dummyNext, isRoot)

    traverseNonSpaceNodes(groups, { node, prev ->
        val prevType = getTypeOfDomTree(prev)
        val type = getTypeOfDomTree(node)
        val space = if (prevType != null && type != null) {
            if (node.hasClass("mtight")) tightSpacings[prevType]?.get(type) else spacings[prevType]?.get(type)
        } else {
            null
        }
        space?.let { makeGlue(it, glueOptions) }
    }, TraversePrev(dummyPrev), dummyNext, isRoot)

    return groups
}

/** Marker for [buildExpression]'s `isRealGroup` at the root. */
internal const val ROOT = "root"

private class TraversePrev(var node: Box, var insertAfter: ((Box) -> Unit)? = null)

/**
 * Depth-first traverse non-space nodes, calling [callback] with the current and
 * previous node. A returned box is inserted after the previous node.
 */
private fun traverseNonSpaceNodes(
    nodes: MutableList<Box>,
    callback: (Box, Box) -> Box?,
    prev: TraversePrev,
    next: Box?,
    isRoot: Boolean,
) {
    if (next != null) nodes.add(next)
    var i = 0
    while (i < nodes.size) {
        val node = nodes[i]
        val partialGroup = checkPartialGroup(node)
        if (partialGroup != null) {
            traverseNonSpaceNodes(partialGroup.children, callback, prev, null, isRoot)
            i++
            continue
        }
        // Ignore explicit spaces (e.g., \;, \,) when determining implicit spacing.
        val nonspace = !node.hasClass("mspace")
        if (nonspace) {
            val result = callback(node, prev.node)
            if (result != null) {
                val insert = prev.insertAfter
                if (insert != null) insert(result) else nodes.add(0, result)
                // Inserting into this list before the current node shifts it right.
                if (nodes.getOrNull(i) !== node) i++
            }
        }
        if (nonspace) {
            prev.node = node
        } else if (isRoot && node.hasClass("katex-newline")) {
            prev.node = makeSpan(listOf("leftmost"))
        }
        val index = i
        val list = nodes
        prev.insertAfter = { n -> list.add(index + 1, n) }
        i++
    }
    if (next != null) nodes.removeAt(nodes.size - 1)
}

private fun checkPartialGroup(node: Box): SpanBox? =
    if (node is FragmentBox || node is AnchorBox || (node is SpanBox && node.hasClass("enclosing"))) {
        node as SpanBox
    } else {
        null
    }

private fun getOutermostNode(node: Box, side: String): Box {
    val partialGroup = checkPartialGroup(node)
    if (partialGroup != null && partialGroup.children.isNotEmpty()) {
        return if (side == "right") {
            getOutermostNode(partialGroup.children.last(), "right")
        } else {
            getOutermostNode(partialGroup.children.first(), "left")
        }
    }
    return node
}

/** Returns the math class (`mord`, `mbin`, ...) of a box, looking into partial groups on [side]. */
internal fun getTypeOfDomTree(node: Box?, side: String? = null): String? {
    if (node == null) return null
    val n = if (side != null) getOutermostNode(node, side) else node
    val cls = n.classes.firstOrNull() ?: return null
    return if (cls in domTypes) cls else null
}

/** An empty delimiter of width 0.12em, used by `\left.` and fractions. */
internal fun makeNullDelimiter(options: Options, classes: List<String>): Box {
    val span = makeSpan(classes + "nulldelimiter")
    // The width is in em of the base size, as KaTeX resets the size with baseSizingClasses.
    span.width = 0.12f
    return span
}

/** Builds a single parse node with its registered HTML builder. */
internal fun buildGroup(group: ParseNode?, options: Options, baseOptions: Options? = null): Box {
    if (group == null) return makeSpan()
    val builder = Registry.htmlBuilders[group::class]
        ?: throw ParseError("Got group of unknown type: '${group::class.simpleName}'")
    return builder(group, options)
}

/**
 * Combine boxes into an unbreakable unit (KaTeX's `.katex-base`).
 */
private fun buildHTMLUnbreakable(children: List<Box>, options: Options): SpanBox =
    makeSpan(listOf("katex-base"), children, options)

/** The result of [buildHTML]: unbreakable chunks, explicit newlines and an optional equation tag. */
internal class HtmlResult(
    /** Unbreakable chunks; `null` marks a forced line break (`\\` or `\newline` at the top level). */
    val chunks: List<SpanBox?>,
    val tag: Box?,
)

/**
 * Builds the whole tree and splits it into chunks between potential line breaks.
 *
 * The TeXBook [p.173] says "A formula will be broken only after a relation
 * symbol like $=$ or $<$ or $\rightarrow$, or after a binary operation symbol
 * like $+$ or $-$ or $\times$, where the relation or binary operation is on the
 * "outer level" of the formula."
 */
internal fun buildHTML(tree: List<ParseNode>, options: Options): HtmlResult {
    var nodes = tree
    var tag: List<ParseNode>? = null
    if (nodes.size == 1 && nodes[0] is TagNode) {
        val t = nodes[0] as TagNode
        tag = t.tag
        nodes = t.body
    }

    val expression = buildExpression(nodes, options, ROOT)

    var eqnNum: Box? = null
    if (expression.size == 2 && expression[1].hasClass("katex-tag")) {
        eqnNum = expression.removeAt(1)
    }

    val children = ArrayList<SpanBox?>()
    var parts = ArrayList<Box>()
    var i = 0
    while (i < expression.size) {
        val node = expression[i]
        parts.add(node)
        if (node.hasClass("mbin") || node.hasClass("mrel") || node.hasClass("allowbreak")) {
            // Put any post-operator glue on same line as operator.
            var nobreak = false
            while (i < expression.size - 1 &&
                expression[i + 1].hasClass("mspace") &&
                !expression[i + 1].hasClass("katex-newline")
            ) {
                i++
                parts.add(expression[i])
                if (expression[i].hasClass("nobreak")) nobreak = true
            }
            if (!nobreak) {
                children.add(buildHTMLUnbreakable(parts, options))
                parts = ArrayList()
            }
        } else if (node.hasClass("katex-newline")) {
            parts.removeAt(parts.size - 1)
            if (parts.isNotEmpty()) {
                children.add(buildHTMLUnbreakable(parts, options))
                parts = ArrayList()
            }
            children.add(null)
        }
        i++
    }
    if (parts.isNotEmpty()) children.add(buildHTMLUnbreakable(parts, options))

    val tagBox: Box? = if (tag != null) {
        buildHTMLUnbreakable(buildExpression(tag, options, true), options).also {
            it.classes.clear()
            it.classes.add("katex-tag")
        }
    } else {
        eqnNum
    }
    return HtmlResult(children, tagBox)
}
