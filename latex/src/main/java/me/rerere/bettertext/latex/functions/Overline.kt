package me.rerere.bettertext.latex.functions

import me.rerere.bettertext.latex.layout.VListChild
import me.rerere.bettertext.latex.layout.VListPosition
import me.rerere.bettertext.latex.layout.buildGroup
import me.rerere.bettertext.latex.layout.makeLineSpan
import me.rerere.bettertext.latex.layout.makeSpan
import me.rerere.bettertext.latex.layout.makeVList
import me.rerere.bettertext.latex.parse.OverlineNode
import me.rerere.bettertext.latex.parse.UnderlineNode

/* Ported from KaTeX's functions/overline.ts and functions/underline.ts. */

internal fun registerOverline() {
    defineFunction(
        names = listOf("\\overline"),
        numArgs = 1,
    ) { ctx, args, _ ->
        OverlineNode(ctx.parser.mode, args[0])
    }

    defineHtmlBuilder<OverlineNode> { group, options ->
        // Overlines are handled in the TeXbook pg 443, Rule 9.

        // Build the inner group in the cramped style.
        val innerGroup = buildGroup(group.body, options.havingCrampedStyle())

        // Create the line above the body
        val line = makeLineSpan("overline-line", options)

        // Generate the vlist, with the appropriate kerns
        val defaultRuleThickness = options.fm.defaultRuleThickness
        val vlist = makeVList(
            VListPosition.FirstBaseline,
            listOf(
                VListChild.Elem(innerGroup),
                VListChild.Kern(3 * defaultRuleThickness),
                VListChild.Elem(line),
                VListChild.Kern(defaultRuleThickness),
            ),
        )
        makeSpan(listOf("mord", "katex-overline"), listOf(vlist), options)
    }

    defineFunction(
        names = listOf("\\underline"),
        numArgs = 1,
        allowedInText = true,
    ) { ctx, args, _ ->
        UnderlineNode(ctx.parser.mode, args[0])
    }

    defineHtmlBuilder<UnderlineNode> { group, options ->
        // Underlines are handled in the TeXbook pg 443, Rule 10.
        // Build the inner group.
        val innerGroup = buildGroup(group.body, options)

        // Create the line to go below the body
        val line = makeLineSpan("underline-line", options)

        // Generate the vlist, with the appropriate kerns
        val defaultRuleThickness = options.fm.defaultRuleThickness
        val vlist = makeVList(
            VListPosition.Top(innerGroup.height),
            listOf(
                VListChild.Kern(defaultRuleThickness),
                VListChild.Elem(line),
                VListChild.Kern(3 * defaultRuleThickness),
                VListChild.Elem(innerGroup),
            ),
        )
        makeSpan(listOf("mord", "katex-underline"), listOf(vlist), options)
    }
}
