package me.rerere.bettertext.latex.functions

import me.rerere.bettertext.latex.layout.HAlign
import me.rerere.bettertext.latex.layout.Style
import me.rerere.bettertext.latex.layout.VListChild
import me.rerere.bettertext.latex.layout.VListPosition
import me.rerere.bettertext.latex.layout.buildGroup
import me.rerere.bettertext.latex.layout.makeSpan
import me.rerere.bettertext.latex.layout.makeSqrtImage
import me.rerere.bettertext.latex.layout.makeVList
import me.rerere.bettertext.latex.layout.wrapFragment
import me.rerere.bettertext.latex.parse.SqrtNode

/** Ported from KaTeX's functions/sqrt.ts. */
internal fun registerSqrt() {
    defineFunction(
        names = listOf("\\sqrt"),
        numArgs = 1,
        numOptionalArgs = 1,
    ) { ctx, args, optArgs ->
        SqrtNode(ctx.parser.mode, body = args[0], index = optArgs[0])
    }

    defineHtmlBuilder<SqrtNode> { group, options ->
        // Square roots are handled in the TeXbook pg. 443, Rule 11.

        // First, we do the same steps as in overline to build the inner group
        // and line
        var inner = buildGroup(group.body, options.havingCrampedStyle())
        if (inner.height == 0f) {
            // Render a small surd.
            inner.height = options.fm.xHeight
        }

        // Some groups can return document fragments.  Handle those by wrapping
        // them in a span.
        inner = wrapFragment(inner, options)

        // Calculate the minimum size for the \surd delimiter
        val metrics = options.fm
        val theta = metrics.defaultRuleThickness

        var phi = theta
        if (options.style.id < Style.TEXT.id) {
            phi = options.fm.xHeight
        }

        // Calculate the clearance between the body and line
        var lineClearance = theta + phi / 4

        val minDelimiterHeight = inner.height + inner.depth + lineClearance + theta

        // Create a sqrt SVG of the required minimum size
        val image = makeSqrtImage(minDelimiterHeight, options)
        val img = image.span
        val ruleWidth = image.ruleWidth
        val advanceWidth = image.advanceWidth

        val delimDepth = img.height - ruleWidth

        // Adjust the clearance based on the delimiter size
        if (delimDepth > inner.height + inner.depth + lineClearance) {
            lineClearance = (lineClearance + delimDepth - inner.height - inner.depth) / 2
        }

        // Shift the sqrt image
        val imgShift = img.height - inner.height - lineClearance - ruleWidth

        // `inner.style.paddingLeft = advanceWidth` becomes a vlist margin. The
        // surd's CSS min-width is honored by padding the inner element on the
        // right, since the `width: 100%` surd takes the vlist's width.
        val minWidthPad = maxOf(0f, image.minWidth - advanceWidth - inner.advance)

        // Overlay the image and the argument.
        val body = makeVList(
            VListPosition.FirstBaseline,
            listOf(
                VListChild.Elem(inner, marginLeft = advanceWidth, marginRight = minWidthPad, align = HAlign.LEFT),
                VListChild.Kern(-(inner.height + imgShift)),
                VListChild.Elem(img),
                VListChild.Kern(ruleWidth),
            ),
        )

        val index = group.index
        if (index == null) {
            makeSpan(listOf("mord", "sqrt"), listOf(body), options)
        } else {
            // Handle the optional root index

            // The index is always in scriptscript style
            val newOptions = options.havingStyle(Style.SCRIPTSCRIPT)
            val rootm = buildGroup(index, newOptions, options)

            // The amount the index is shifted by. This is taken from the TeX
            // source, in the definition of `\r@@t`.
            val toShift = 0.6f * (body.height - body.depth)

            // Build a VList with the superscript shifted up correctly
            val rootVList = makeVList(VListPosition.Shift(-toShift), listOf(VListChild.Elem(rootm)))
            // Add a class surrounding it so we can add on the appropriate
            // kerning: `.sqrt > .katex-root` has `\mkern 5mu` and `\mkern -10mu`.
            val rootVListWrap = makeSpan(listOf("katex-root"), listOf(rootVList))
            rootVListWrap.marginLeft = options.em(0.2777777778f)
            rootVListWrap.marginRight = options.em(-0.5555555556f)

            makeSpan(listOf("mord", "sqrt"), listOf(rootVListWrap, body), options)
        }
    }
}
