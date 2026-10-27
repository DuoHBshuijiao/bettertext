package me.rerere.bettertext.latex.functions

import me.rerere.bettertext.latex.layout.Box
import me.rerere.bettertext.latex.layout.HAlign
import me.rerere.bettertext.latex.layout.Options
import me.rerere.bettertext.latex.layout.SpanBox
import me.rerere.bettertext.latex.layout.SymbolBox
import me.rerere.bettertext.latex.layout.VListChild
import me.rerere.bettertext.latex.layout.VListPosition
import me.rerere.bettertext.latex.layout.applyStretchyMinWidth
import me.rerere.bettertext.latex.layout.buildGroup
import me.rerere.bettertext.latex.layout.makeOrd
import me.rerere.bettertext.latex.layout.makeSpan
import me.rerere.bettertext.latex.layout.makeVList
import me.rerere.bettertext.latex.layout.staticSvg
import me.rerere.bettertext.latex.layout.stretchySvg
import me.rerere.bettertext.latex.parse.AccentNode
import me.rerere.bettertext.latex.parse.AccentUnderNode
import me.rerere.bettertext.latex.parse.Mode
import me.rerere.bettertext.latex.parse.ParseNode
import me.rerere.bettertext.latex.parse.SupSubNode
import me.rerere.bettertext.latex.parse.TextOrdNode

/* Ported from KaTeX's functions/accent.ts and functions/accentunder.ts. */

private fun getBaseSymbol(group: Box): SymbolBox? {
    if (group is SymbolBox) return group
    if (group is SpanBox && group.children.size == 1) return getBaseSymbol(group.children[0])
    return null
}

/**
 * NOTE: Unlike most builders, this one handles not only "accent", but also
 * "supsub" since an accent can affect super/subscripting.
 */
private fun accentHtmlBuilder(grp: ParseNode, options: Options): Box {
    // Accents are handled in the TeXbook pg. 443, rule 12.
    val base: ParseNode
    val group: AccentNode
    var supSubGroup: SpanBox? = null
    if (grp is SupSubNode) {
        // If our base is a character box, and we have superscripts and
        // subscripts, the supsub will defer to us. In particular, we want
        // to attach the superscripts and subscripts to the inner body (so
        // that the position of the superscripts and subscripts won't be
        // affected by the height of the accent). We accomplish this by
        // sticking the base of the accent into the base of the supsub, and
        // rendering that, while keeping track of where the accent is.

        // The real accent group is the base of the supsub group
        group = grp.base.assertType<AccentNode>()
        // The character box is the base of the accent group
        base = group.base
        // Stick the character box into the base of the supsub group, and
        // rerender the supsub group with its new base.
        val rebased = SupSubNode(grp.mode, base, grp.sup, grp.sub).also { it.loc = grp.loc }
        supSubGroup = buildGroup(rebased, options) as? SpanBox
            ?: error("Expected span<HtmlDomNode> but got another node.")
    } else {
        group = grp.assertType<AccentNode>()
        base = group.base
    }

    // Build the base group
    val body = buildGroup(base, options.havingCrampedStyle())

    // Does the accent need to shift for the skew of a character?
    val mustShift = group.isShifty && isCharacterBox(base)

    // Calculate the skew of the accent. This is based on the line "If the
    // nucleus is not a single character, let s = 0; otherwise set s to the
    // kern amount for the nucleus followed by the \skewchar of its font."
    // Note that our skew metrics are just the kern between each character
    // and the skewchar.
    var skew = 0f
    if (mustShift) {
        // Read the skew from the rendered base symbol.
        // This preserves font metrics from font wrappers like \mathbb.
        skew = getBaseSymbol(body)?.skew ?: 0f
    }

    val accentBelow = group.label == "\\c"

    // calculate the amount of space between the body and the accent
    var clearance = if (accentBelow) body.height + body.depth else minOf(body.height, options.fm.xHeight)

    // Build the accent
    val accentBody: Box
    if (!group.isStretchy) {
        val accent: Box
        val width: Float
        if (group.label == "\\vec") {
            // Before version 0.9, \vec used the combining font glyph U+20D7.
            // But browsers, especially Safari, are not consistent in how they
            // render combining characters when not preceded by a character.
            // So now we use an SVG.
            accent = staticSvg("vec", options)
            width = accent.width
        } else {
            accent = makeOrd(TextOrdNode(group.mode, group.label), options) as? SymbolBox
                ?: error("Expected symbolNode but got another node.")
            // Remove the italic correction of the accent, because it only serves to
            // shift the accent over to a place we don't want.
            accent.italic = 0f
            width = accent.width
            if (accentBelow) clearance += accent.depth
        }

        val accentSpan = makeSpan(listOf("accent-body"), listOf(accent))

        // "Full" accents expand the width of the resulting symbol to be
        // at least the width of the accent, and overlap directly onto the
        // character without any vertical offset.
        val accentFull = group.label == "\\textcircled"
        if (accentFull) {
            accentSpan.classes.add("accent-full")
            clearance = body.height
        }

        // Shift the accent over by the skew.
        var left = skew

        // CSS defines `.katex .katex-accent .accent-body:not(.accent-full) { width: 0 }`
        // so that the accent doesn't contribute to the bounding box.
        // We need to shift the character by its width (effectively half
        // its width) to compensate.
        if (!accentFull) {
            left -= width / 2
            accentSpan.width = 0f
        }

        // `position: relative; left`: move the drawing without changing the advance.
        accentSpan.marginLeft = left
        accentSpan.marginRight = -left

        // \textcircled uses the \bigcirc glyph, so it needs some
        // vertical adjustment to match LaTeX.
        if (group.label == "\\textcircled") accentSpan.shiftY = options.em(0.2f)

        accentBody = makeVList(
            VListPosition.FirstBaseline,
            listOf(
                VListChild.Elem(body),
                VListChild.Kern(-clearance),
                VListChild.Elem(accentSpan),
            ),
            HAlign.CENTER,
        )
    } else {
        val svg = stretchySvg(group, options)
        accentBody = makeVList(
            VListPosition.FirstBaseline,
            listOf(
                VListChild.Elem(body),
                // wrapperStyle: width: calc(100% - 2 skew); margin-left: 2 skew
                VListChild.Elem(svg, marginLeft = if (skew > 0) 2 * skew else 0f, align = HAlign.LEFT),
            ),
            HAlign.CENTER,
        )
        applyStretchyMinWidth(accentBody, svg, if (skew > 0) 2 * skew else 0f)
    }

    val accentWrap = makeSpan(listOf("mord", "katex-accent"), listOf(accentBody), options)

    return if (supSubGroup != null) {
        // Here, we replace the "base" child of the supsub with our newly
        // generated accent.
        supSubGroup.children[0] = accentWrap

        // Since we don't rerun the height calculation after replacing the
        // accent, we manually recalculate height.
        supSubGroup.height = maxOf(accentWrap.height, supSubGroup.height)

        // Accents should always be ords, even when their innards are not.
        if (supSubGroup.classes.isEmpty()) supSubGroup.classes.add("mord") else supSubGroup.classes[0] = "mord"
        supSubGroup
    } else {
        accentWrap
    }
}

private val nonStretchyAccents = setOf(
    "\\acute", "\\grave", "\\ddot", "\\tilde", "\\bar", "\\breve",
    "\\check", "\\hat", "\\vec", "\\dot", "\\mathring",
)

internal fun registerAccent() {
    // Accents
    defineFunction(
        names = listOf(
            "\\acute", "\\grave", "\\ddot", "\\tilde", "\\bar", "\\breve",
            "\\check", "\\hat", "\\vec", "\\dot", "\\mathring", "\\widecheck",
            "\\widehat", "\\widetilde", "\\overrightarrow", "\\overleftarrow",
            "\\Overrightarrow", "\\overleftrightarrow", "\\overgroup",
            "\\overlinesegment", "\\overleftharpoon", "\\overrightharpoon",
        ),
        numArgs = 1,
    ) { ctx, args, _ ->
        val base = normalizeArgument(args[0])
        val isStretchy = ctx.funcName !in nonStretchyAccents
        val isShifty = !isStretchy ||
            ctx.funcName == "\\widehat" ||
            ctx.funcName == "\\widetilde" ||
            ctx.funcName == "\\widecheck"
        AccentNode(ctx.parser.mode, ctx.funcName, isStretchy, isShifty, base)
    }

    // Text-mode accents
    defineFunction(
        names = listOf(
            "\\'", "\\`", "\\^", "\\~", "\\=", "\\u", "\\.", "\\\"",
            "\\c", "\\r", "\\H", "\\v", "\\textcircled",
        ),
        numArgs = 1,
        allowedInText = true,
        allowedInMath = true, // unless in strict mode
        argTypes = listOf(ArgType.PRIMITIVE),
    ) { ctx, args, _ ->
        // KaTeX reports "LaTeX's accent ... works only in text mode" in strict
        // mode; strict mode is not supported, so math mode is accepted.
        AccentNode(Mode.TEXT, ctx.funcName, isStretchy = false, isShifty = true, base = args[0])
    }

    defineHtmlBuilder<AccentNode> { group, options -> accentHtmlBuilder(group, options) }
    SupSubDelegates.accent = { group, options -> accentHtmlBuilder(group, options) }

    // Horizontal overlap functions
    defineFunction(
        names = listOf(
            "\\underleftarrow", "\\underrightarrow", "\\underleftrightarrow",
            "\\undergroup", "\\underlinesegment", "\\utilde",
        ),
        numArgs = 1,
    ) { ctx, args, _ ->
        AccentUnderNode(ctx.parser.mode, ctx.funcName, base = args[0])
    }

    defineHtmlBuilder<AccentUnderNode> { group, options ->
        // Treat under accents much like underlines.
        val innerGroup = buildGroup(group.base, options)
        val accentBody = stretchySvg(group, options)
        val kern = if (group.label == "\\utilde") options.em(0.12f) else 0f

        // Generate the vlist, with the appropriate kerns
        val vlist = makeVList(
            VListPosition.Top(innerGroup.height),
            listOf(
                VListChild.Elem(accentBody, align = HAlign.LEFT),
                VListChild.Kern(kern),
                VListChild.Elem(innerGroup),
            ),
        )
        applyStretchyMinWidth(vlist, accentBody)
        makeSpan(listOf("mord", "accentunder"), listOf(vlist), options)
    }
}
