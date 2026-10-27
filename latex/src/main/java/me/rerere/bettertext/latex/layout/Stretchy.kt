package me.rerere.bettertext.latex.layout

import me.rerere.bettertext.latex.parse.AccentNode
import me.rerere.bettertext.latex.parse.AccentUnderNode
import me.rerere.bettertext.latex.parse.HorizBraceNode
import me.rerere.bettertext.latex.parse.OrdGroupNode
import me.rerere.bettertext.latex.parse.ParseNode
import me.rerere.bettertext.latex.parse.XArrowNode

/*
 * Ported from KaTeX's stretchy.ts: stretchy wide elements rendered from SVG
 * images and CSS trickery. MathML support is not ported.
 *
 * Many of the KaTeX SVG images have been adapted from glyphs in KaTeX fonts.
 * Copyright (c) 2009-2010, Design Science, Inc. (<www.mathjax.org>)
 * Copyright (c) 2014-2017 Khan Academy (<www.khanacademy.org>)
 * Licensed under the SIL Open Font License, Version 1.1.
 *
 * Very Long SVGs
 *    Many of the KaTeX stretchy wide elements use a long SVG image and an
 *    overflow: hidden tactic to achieve a stretchy image while avoiding
 *    distortion of arrowheads or brace corners.
 *
 *    The SVG typically contains a very long (400 em) arrow. The SVG is in a
 *    container span that has overflow: hidden, so the span acts like a window
 *    that exposes only part of the SVG. After the SVG fills 100% of the
 *    height of the container span, there is a long arrow shaft left over.
 *    That left-over shaft is sliced off because of overflow: hidden.
 *
 *    Some functions, such as \cancel, need to vary their aspect ratio. These
 *    functions do not get the overflow SVG treatment.
 */

/**
 * A `width: 100%` stretchy span (`.katex-stretchy`, `.hide-tail` or a wide
 * accent span) made of SVG pieces. Each piece covers a fraction of the span,
 * emulating KaTeX's absolutely positioned `.halfarrow-*` / `.brace-*` spans:
 * [pieces] holds `(left, width)` fractions of the span width for each child.
 *
 * KaTeX also sets `min-width` on the span; [minWidth] (absolute em) must be
 * applied to the enclosing vlist with [applyStretchyMinWidth].
 */
internal class StretchyBox(
    classes: List<String>,
    svgs: List<SvgBox>,
    private val pieces: List<Pair<Float, Float>>,
    val minWidth: Float,
) : SpanBox(classes, svgs.toMutableList<Box>()) {
    init {
        fillWidth = true
        clip = true
    }

    override fun assignFillWidth(w: Float) {
        explicitWidth = w
        // Lay the pieces out left to right; negative margins make them overlap
        // exactly where the CSS absolute positioning would put them.
        var x = 0f
        for (i in children.indices) {
            val child = children[i]
            val (left, frac) = pieces[i]
            child.marginLeft = left * w - x
            child.width = frac * w
            child.marginRight = 0f
            x = left * w + frac * w
        }
        if (children.isNotEmpty()) children.last().marginRight = w - x
    }
}

/**
 * Applies the stretchy element's CSS `min-width` to the vlist containing it.
 * [extra] is the horizontal space the vlist reserves around the element (e.g. a
 * wrapper margin).
 */
internal fun applyStretchyMinWidth(vlist: VListBox, stretchy: Box, extra: Float = 0f) {
    if (stretchy is StretchyBox && stretchy.minWidth > 0f) {
        val w = vlist.width
        if (w < stretchy.minWidth + extra) vlist.width = stretchy.minWidth + extra
    }
}

/** path(s), minWidth, viewBox height, align (single-path only). */
private class SvgData(val paths: List<String>, val minWidth: Float, val viewBoxHeight: Int, val align: String? = null)

/*
 * In the katexImagesData object just below, the dimensions all correspond to
 * path geometry inside the relevant SVG. For example, \overrightarrow uses the
 * same arrowhead as glyph U+2192 from the KaTeX Main font. The scaling factor
 * is 1000. That is, inside the font, that arrowhead is 522 units tall, which
 * corresponds to 0.522 em inside the document.
 */
private val katexImagesData: Map<String, SvgData> = mapOf(
    "overrightarrow" to SvgData(listOf("rightarrow"), 0.888f, 522, "xMaxYMin"),
    "overleftarrow" to SvgData(listOf("leftarrow"), 0.888f, 522, "xMinYMin"),
    "underrightarrow" to SvgData(listOf("rightarrow"), 0.888f, 522, "xMaxYMin"),
    "underleftarrow" to SvgData(listOf("leftarrow"), 0.888f, 522, "xMinYMin"),
    "xrightarrow" to SvgData(listOf("rightarrow"), 1.469f, 522, "xMaxYMin"),
    "\\cdrightarrow" to SvgData(listOf("rightarrow"), 3.0f, 522, "xMaxYMin"), // CD minwwidth2.5pc
    "xleftarrow" to SvgData(listOf("leftarrow"), 1.469f, 522, "xMinYMin"),
    "\\cdleftarrow" to SvgData(listOf("leftarrow"), 3.0f, 522, "xMinYMin"),
    "Overrightarrow" to SvgData(listOf("doublerightarrow"), 0.888f, 560, "xMaxYMin"),
    "xRightarrow" to SvgData(listOf("doublerightarrow"), 1.526f, 560, "xMaxYMin"),
    "xLeftarrow" to SvgData(listOf("doubleleftarrow"), 1.526f, 560, "xMinYMin"),
    "overleftharpoon" to SvgData(listOf("leftharpoon"), 0.888f, 522, "xMinYMin"),
    "xleftharpoonup" to SvgData(listOf("leftharpoon"), 0.888f, 522, "xMinYMin"),
    "xleftharpoondown" to SvgData(listOf("leftharpoondown"), 0.888f, 522, "xMinYMin"),
    "overrightharpoon" to SvgData(listOf("rightharpoon"), 0.888f, 522, "xMaxYMin"),
    "xrightharpoonup" to SvgData(listOf("rightharpoon"), 0.888f, 522, "xMaxYMin"),
    "xrightharpoondown" to SvgData(listOf("rightharpoondown"), 0.888f, 522, "xMaxYMin"),
    "xlongequal" to SvgData(listOf("longequal"), 0.888f, 334, "xMinYMin"),
    "\\cdlongequal" to SvgData(listOf("longequal"), 3.0f, 334, "xMinYMin"),
    "xtwoheadleftarrow" to SvgData(listOf("twoheadleftarrow"), 0.888f, 334, "xMinYMin"),
    "xtwoheadrightarrow" to SvgData(listOf("twoheadrightarrow"), 0.888f, 334, "xMaxYMin"),

    "overleftrightarrow" to SvgData(listOf("leftarrow", "rightarrow"), 0.888f, 522),
    "overbrace" to SvgData(listOf("leftbrace", "midbrace", "rightbrace"), 1.6f, 548),
    "underbrace" to SvgData(listOf("leftbraceunder", "midbraceunder", "rightbraceunder"), 1.6f, 548),
    "underleftrightarrow" to SvgData(listOf("leftarrow", "rightarrow"), 0.888f, 522),
    "xleftrightarrow" to SvgData(listOf("leftarrow", "rightarrow"), 1.75f, 522),
    "xLeftrightarrow" to SvgData(listOf("doubleleftarrow", "doublerightarrow"), 1.75f, 560),
    "xrightleftharpoons" to SvgData(listOf("leftharpoondownplus", "rightharpoonplus"), 1.75f, 716),
    "xleftrightharpoons" to SvgData(listOf("leftharpoonplus", "rightharpoondownplus"), 1.75f, 716),
    "xhookleftarrow" to SvgData(listOf("leftarrow", "righthook"), 1.08f, 522),
    "xhookrightarrow" to SvgData(listOf("lefthook", "rightarrow"), 1.08f, 522),
    "overlinesegment" to SvgData(listOf("leftlinesegment", "rightlinesegment"), 0.888f, 522),
    "underlinesegment" to SvgData(listOf("leftlinesegment", "rightlinesegment"), 0.888f, 522),
    "overbracket" to SvgData(listOf("leftbracketover", "rightbracketover"), 1.6f, 440),
    "underbracket" to SvgData(listOf("leftbracketunder", "rightbracketunder"), 1.6f, 410),
    "overgroup" to SvgData(listOf("leftgroup", "rightgroup"), 0.888f, 342),
    "undergroup" to SvgData(listOf("leftgroupunder", "rightgroupunder"), 0.888f, 342),
    "xmapsto" to SvgData(listOf("leftmapsto", "rightarrow"), 1.5f, 522),
    "xtofrom" to SvgData(listOf("leftToFrom", "rightToFrom"), 1.75f, 528),

    // The next three arrows are from the mhchem package.
    // In mhchem.sty, min-length is 2.0em. But these arrows might appear in the
    // document as \xrightarrow or \xrightleftharpoons. Those have
    // min-length = 1.75em, so we set min-length on these next three to match.
    "xrightleftarrows" to SvgData(listOf("baraboveleftarrow", "rightarrowabovebar"), 1.75f, 901),
    "xrightequilibrium" to SvgData(listOf("baraboveshortleftharpoon", "rightharpoonaboveshortbar"), 1.75f, 716),
    "xleftequilibrium" to SvgData(listOf("shortbaraboveleftharpoon", "shortrightharpoonabovebar"), 1.75f, 716),
)

private val wideAccentLabels = setOf("widehat", "widecheck", "widetilde", "utilde")

/** `(left, width)` fractions of `.halfarrow-left/right` (CSS: left 0 / right 0, width 50.2%). */
private val halfArrowPieces = listOf(0f to 0.502f, 0.498f to 0.502f)

/** `(left, width)` fractions of `.brace-left/center/right` (25.1% / left 25% 50% / right 25.1%). */
private val bracePieces = listOf(0f to 0.251f, 0.25f to 0.5f, 0.749f to 0.251f)

/**
 * Creates a span with inline SVG for a stretchy accent, under-accent, extensible
 * arrow or horizontal brace. Like KaTeX, the returned box has depth 0; any
 * adjustment relative to the baseline must be done by the caller.
 */
internal fun stretchySvg(group: ParseNode, options: Options): Box {
    val (labelFull, base) = when (group) {
        is AccentNode -> group.label to group.base
        is AccentUnderNode -> group.label to group.base
        is XArrowNode -> group.label to null
        is HorizBraceNode -> group.label to group.base
        else -> error("stretchySvg: unsupported group ${group::class.simpleName}")
    }
    val k = options.sizeMultiplier
    val label = labelFull.substring(1)
    val svgs: List<SvgBox>
    val pieces: List<Pair<Float, Float>>
    val classes: List<String>
    val minWidth: Float
    val height: Float

    if (label in wideAccentLabels && base != null) {
        // There are four SVG images available for each function.
        // Choose a taller image when there are more characters.
        val numChars = if (base is OrdGroupNode) base.body.size else 1
        val viewBoxWidth: Int
        val viewBoxHeight: Int
        val pathName: String
        if (numChars > 5) {
            if (label == "widehat" || label == "widecheck") {
                viewBoxHeight = 420
                viewBoxWidth = 2364
                height = 0.42f
                pathName = label + "4"
            } else {
                viewBoxHeight = 312
                viewBoxWidth = 2340
                height = 0.34f
                pathName = "tilde4"
            }
        } else {
            val imgIndex = intArrayOf(1, 1, 2, 2, 3, 3)[numChars]
            if (label == "widehat" || label == "widecheck") {
                viewBoxWidth = intArrayOf(0, 1062, 2364, 2364, 2364)[imgIndex]
                viewBoxHeight = intArrayOf(0, 239, 300, 360, 420)[imgIndex]
                height = floatArrayOf(0f, 0.24f, 0.3f, 0.3f, 0.36f, 0.42f)[imgIndex]
                pathName = label + imgIndex
            } else {
                viewBoxWidth = intArrayOf(0, 600, 1033, 2339, 2340)[imgIndex]
                viewBoxHeight = intArrayOf(0, 260, 286, 306, 312)[imgIndex]
                height = floatArrayOf(0f, 0.26f, 0.286f, 0.3f, 0.306f, 0.34f)[imgIndex]
                pathName = "tilde$imgIndex"
            }
        }
        svgs = listOf(
            SvgBox(
                listOf(SvgElement.Path(SvgPaths[pathName])),
                floatArrayOf(0f, 0f, viewBoxWidth.toFloat(), viewBoxHeight.toFloat()),
                svgHeight = height * k,
                preserveAspectRatio = "none",
            ),
        )
        pieces = listOf(0f to 1f)
        classes = emptyList()
        minWidth = 0f
    } else {
        val data = katexImagesData[label] ?: error("No SVG data for \"$label\".")
        val viewBoxWidth = 400000f // default
        val viewBoxHeight = data.viewBoxHeight
        height = viewBoxHeight / 1000f
        minWidth = data.minWidth
        val aligns: List<String>
        when (data.paths.size) {
            1 -> {
                // Returned directly as a `.hide-tail` span.
                classes = listOf("hide-tail")
                aligns = listOf(data.align ?: error("Expected 4-tuple for single-path SVG data \"$label\"."))
                pieces = listOf(0f to 1f)
            }

            2 -> {
                classes = listOf("katex-stretchy")
                aligns = listOf("xMinYMin", "xMaxYMin")
                pieces = halfArrowPieces
            }

            3 -> {
                classes = listOf("katex-stretchy")
                aligns = listOf("xMinYMin", "xMidYMin", "xMaxYMin")
                pieces = bracePieces
            }

            else -> error("Correct katexImagesData or update code here to support ${data.paths.size} children.")
        }
        svgs = data.paths.mapIndexed { i, path ->
            SvgBox(
                listOf(SvgElement.Path(SvgPaths[path])),
                floatArrayOf(0f, 0f, viewBoxWidth, viewBoxHeight.toFloat()),
                svgHeight = height * k,
                preserveAspectRatio = aligns[i] + " slice",
            ).also { it.height = height * k }
        }
    }

    val span = StretchyBox(classes, svgs, pieces, minWidth * k)
    options.getColor()?.let { span.color = CssColor.parse(it) }
    // Note that we are returning span.depth = 0.
    // Any adjustments relative to the baseline must be done in buildHTML.
    span.height = height * k
    span.depth = 0f
    return span
}

/**
 * A `width: 100%` span with only top and right borders, like KaTeX's `.angl`
 * (`border-top`/`border-right: 0.049em solid`). [ruleThickness] is absolute em.
 */
internal class AnglBox(classes: List<String>, var ruleThickness: Float) : SpanBox(classes) {
    init {
        fillWidth = true
    }

    override fun assignFillWidth(w: Float) {
        explicitWidth = w
        children.clear()
        val t = ruleThickness
        val top = RuleBox(listOf("angl-top"))
        top.width = w
        top.height = t
        top.shiftY = -(height - t)
        top.marginRight = -w
        val right = RuleBox(listOf("angl-right"))
        right.marginLeft = w - t
        right.width = t
        right.height = height
        right.depth = depth
        children.add(top)
        children.add(right)
    }
}

/**
 * Returns an image box for `\cancel`, `\bcancel`, `\xcancel`, `\fbox`,
 * `\colorbox` or `\angl`, `topPad + inner + bottomPad` tall with depth 0.
 * Borders and backgrounds are set by the caller.
 */
internal fun stretchyEnclose(inner: Box, label: String, topPad: Float, bottomPad: Float, options: Options): Box {
    val totalHeight = inner.height + inner.depth + topPad + bottomPad
    val img: Box
    if (Regex("fbox|color|angl").containsMatchIn(label)) {
        img = if (label == "angl") {
            AnglBox(listOf("katex-stretchy", label), options.em(0.049f))
        } else {
            SpanBox(listOf("katex-stretchy", label)).also { it.fillWidth = true }
        }
        options.getColor()?.let { img.color = CssColor.parse(it) }
        if (label == "fbox") {
            val color = if (options.color != null) options.getColor() else null
            if (color != null) img.borderColor = CssColor.parse(color)
        }
    } else {
        // \cancel, \bcancel, or \xcancel
        // Since \cancel's SVG is inline and it omits the viewBox attribute,
        // its stroke-width will not vary with span area.
        val strokeWidth = options.em(0.046f)
        val lines = ArrayList<SvgElement>()
        if (Regex("^[bx]cancel$").matches(label)) {
            lines.add(SvgElement.Line(0f, 0f, 1f, 1f, strokeWidth))
        }
        if (Regex("^x?cancel$").matches(label)) {
            lines.add(SvgElement.Line(0f, 1f, 1f, 0f, strokeWidth))
        }
        img = SvgBox(lines, null, svgHeight = totalHeight)
        img.fillWidth = true
        options.getColor()?.let { img.color = CssColor.parse(it) }
    }
    img.height = totalHeight
    return img
}
