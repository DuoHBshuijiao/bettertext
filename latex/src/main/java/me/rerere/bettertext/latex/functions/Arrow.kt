package me.rerere.bettertext.latex.functions

import me.rerere.bettertext.latex.layout.Box
import me.rerere.bettertext.latex.layout.HAlign
import me.rerere.bettertext.latex.layout.VListChild
import me.rerere.bettertext.latex.layout.VListPosition
import me.rerere.bettertext.latex.layout.applyStretchyMinWidth
import me.rerere.bettertext.latex.layout.buildGroup
import me.rerere.bettertext.latex.layout.makeSpan
import me.rerere.bettertext.latex.layout.makeVList
import me.rerere.bettertext.latex.layout.stretchySvg
import me.rerere.bettertext.latex.layout.wrapFragment
import me.rerere.bettertext.latex.parse.XArrowNode

/* Ported from KaTeX's functions/arrow.ts. The `\\cd*` arrows of the {CD} environment are skipped. */

internal fun registerArrow() {
    // Stretchy arrows with an optional argument
    defineFunction(
        names = listOf(
            "\\xleftarrow", "\\xrightarrow", "\\xLeftarrow", "\\xRightarrow",
            "\\xleftrightarrow", "\\xLeftrightarrow", "\\xhookleftarrow",
            "\\xhookrightarrow", "\\xmapsto", "\\xrightharpoondown",
            "\\xrightharpoonup", "\\xleftharpoondown", "\\xleftharpoonup",
            "\\xrightleftharpoons", "\\xleftrightharpoons", "\\xlongequal",
            "\\xtwoheadrightarrow", "\\xtwoheadleftarrow", "\\xtofrom",
            // The next 3 functions are here to support the mhchem extension.
            // Direct use of these functions is discouraged and may break someday.
            "\\xrightleftarrows", "\\xrightequilibrium", "\\xleftequilibrium",
        ),
        numArgs = 1,
        numOptionalArgs = 1,
    ) { ctx, args, optArgs ->
        XArrowNode(ctx.parser.mode, ctx.funcName, args[0], optArgs[0])
    }

    defineHtmlBuilder<XArrowNode> { group, options ->
        val style = options.style

        // Build the argument groups in the appropriate style.
        // Ref: amsmath.dtx:   \hbox{$\scriptstyle\mkern#3mu{#6}\mkern#4mu$}%

        // Some groups can return document fragments.  Handle those by wrapping
        // them in a span.
        var newOptions = options.havingStyle(style.sup())
        val upperGroup = wrapFragment(buildGroup(group.body, newOptions, options), options)
        val arrowPrefix = if (group.label.startsWith("\\x")) "x" else "cd"
        upperGroup.classes.add("$arrowPrefix-arrow-pad")
        // .x-arrow-pad { padding: 0 0.5em }, in the font size of the group.
        upperGroup.marginLeft += newOptions.em(0.5f)
        upperGroup.marginRight += newOptions.em(0.5f)

        var lowerGroup: Box? = null
        if (group.below != null) {
            // Build the lower group
            newOptions = options.havingStyle(style.sub())
            lowerGroup = wrapFragment(buildGroup(group.below, newOptions, options), options)
            lowerGroup.classes.add("$arrowPrefix-arrow-pad")
            lowerGroup.marginLeft += newOptions.em(0.5f)
            lowerGroup.marginRight += newOptions.em(0.5f)
        }

        val arrowBody = stretchySvg(group, options)
        val axisHeight = options.fm.axisHeight
        val twoMu = options.em(0.111f) // 0.111 em = 2 mu

        // Re shift: Note that stretchySvg returned arrowBody.depth = 0.
        // The point we want on the math axis is at 0.5 * arrowBody.height.
        val arrowShift = -axisHeight + 0.5f * arrowBody.height
        // 2 mu kern. Ref: amsmath.dtx: #7\if0#2\else\mkern#2mu\fi
        var upperShift = -axisHeight - 0.5f * arrowBody.height - twoMu
        if (upperGroup.depth > options.em(0.25f) || group.label == "\\xleftequilibrium") {
            upperShift -= upperGroup.depth // shift up if depth encroaches
        }

        // Generate the vlist
        val children = arrayListOf<VListChild>(
            VListChild.Elem(upperGroup, shift = upperShift),
            VListChild.Elem(arrowBody, shift = arrowShift, align = HAlign.LEFT),
        )
        if (lowerGroup != null) {
            val lowerShift = -axisHeight + lowerGroup.height + 0.5f * arrowBody.height + twoMu
            children.add(VListChild.Elem(lowerGroup, shift = lowerShift))
        }
        // .x-arrow { text-align: center }
        val vlist = makeVList(VListPosition.IndividualShift, children, HAlign.CENTER)
        applyStretchyMinWidth(vlist, arrowBody)

        makeSpan(listOf("mrel", "x-arrow"), listOf(vlist), options)
    }
}
