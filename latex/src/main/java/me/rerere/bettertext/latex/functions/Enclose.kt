package me.rerere.bettertext.latex.functions

import me.rerere.bettertext.latex.layout.AnglBox
import me.rerere.bettertext.latex.layout.Box
import me.rerere.bettertext.latex.layout.CssColor
import me.rerere.bettertext.latex.layout.HAlign
import me.rerere.bettertext.latex.layout.Options
import me.rerere.bettertext.latex.layout.RuleBox
import me.rerere.bettertext.latex.layout.StretchyBox
import me.rerere.bettertext.latex.layout.SvgBox
import me.rerere.bettertext.latex.layout.SvgElement
import me.rerere.bettertext.latex.layout.VListChild
import me.rerere.bettertext.latex.layout.VListPosition
import me.rerere.bettertext.latex.layout.buildGroup
import me.rerere.bettertext.latex.layout.calculateSizeAbs
import me.rerere.bettertext.latex.layout.makeSpan
import me.rerere.bettertext.latex.layout.makeVList
import me.rerere.bettertext.latex.layout.stretchyEnclose
import me.rerere.bettertext.latex.layout.wrapFragment
import me.rerere.bettertext.latex.parse.ColorTokenNode
import me.rerere.bettertext.latex.parse.EncloseNode
import me.rerere.bettertext.latex.parse.Measurement

/* Ported from KaTeX's functions/enclose.ts. */

/** The angle of `\phase`, from svgGeometry.ts. */
private fun phasePath(y: Int): String {
    val x = y / 2 // x coordinate at top of angle
    return "M400000 $y H0 L$x 0 l65 45 L145 ${y - 80} H400000z"
}

private fun Box.addPadding(left: Float, right: Float) {
    marginLeft += left
    marginRight += right
}

private fun encloseHtmlBuilder(group: EncloseNode, options: Options): Box {
    // \cancel, \bcancel, \xcancel, \sout, \fbox, \colorbox, \fcolorbox, \phase
    // Some groups can return document fragments.  Handle those by wrapping
    // them in a span.
    val inner = wrapFragment(buildGroup(group.body, options), options)

    val label = group.label.substring(1)
    val img: Box
    val imgShift: Float
    var imgAlign: HAlign? = null

    // In the LaTeX cancel package, line geometry is slightly different
    // depending on whether the subject is wider than it is tall, or vice versa.
    // We don't know the width of a group, so as a proxy, we test if
    // the subject is a single character. This captures most of the
    // subjects that should get the "tall" treatment.
    val isSingleChar = isCharacterBox(group.body)

    if (label == "sout") {
        // .katex-sout { border-bottom: 0.08em solid }: a 0.08em line whose
        // bottom sits half an x-height above the baseline.
        val line = RuleBox(listOf("katex-stretchy", "katex-sout"))
        line.fillWidth = true
        line.height = options.em(0.08f)
        img = line
        imgShift = -0.5f * options.fm.xHeight
    } else if (label == "phase") {
        // Set a couple of dimensions from the steinmetz package.
        val lineWeight = calculateSizeAbs(Measurement(0.6f, "pt"), options)
        val clearance = calculateSizeAbs(Measurement(0.35f, "ex"), options)

        // Prevent size changes like \Huge from affecting line thickness
        val newOptions = options.havingBaseSizing()

        val angleHeight = inner.height + inner.depth + lineWeight + clearance
        // Reserve a left pad for the angle.
        inner.addPadding(angleHeight / 2 + lineWeight, 0f)

        // Create an SVG
        val viewBoxHeight = kotlin.math.floor(1000 * angleHeight / newOptions.sizeMultiplier).toInt()
        val svg = SvgBox(
            listOf(SvgElement.Path(phasePath(viewBoxHeight))),
            floatArrayOf(0f, 0f, 400000f, viewBoxHeight.toFloat()),
            svgHeight = angleHeight,
            preserveAspectRatio = "xMinYMin slice",
        )
        // Wrap it in a span with overflow: hidden (KaTeX leaves its height 0).
        img = StretchyBox(listOf("hide-tail"), listOf(svg), listOf(0f to 1f), 0f)
        // The svg clips itself to its own rect; the zero-height span must not clip it.
        img.clip = false
        options.getColor()?.let { img.color = CssColor.parse(it) }
        imgShift = inner.depth + lineWeight + clearance
        imgAlign = HAlign.LEFT
    } else {
        // Add horizontal padding
        if (Regex("cancel").containsMatchIn(label)) {
            if (!isSingleChar) {
                inner.classes.add("cancel-pad")
                inner.addPadding(options.em(0.2f), options.em(0.2f))
            }
        } else if (label == "angl") {
            inner.classes.add("anglpad")
            inner.addPadding(options.em(0.03889f), options.em(0.03889f))
        } else {
            inner.classes.add("boxpad")
            inner.addPadding(options.em(0.3f), options.em(0.3f))
        }

        // Add vertical padding
        val topPad: Float
        val bottomPad: Float
        var ruleThickness = 0f
        // ref: cancel package: \advance\totalheight2\p@ % "+2"
        if (Regex("box").containsMatchIn(label)) {
            ruleThickness = maxOf(
                options.fm.fboxrule, // default
                options.em(options.minRuleThickness), // User override.
            )
            topPad = options.fm.fboxsep + (if (label == "colorbox") 0f else ruleThickness)
            bottomPad = topPad
        } else if (label == "angl") {
            ruleThickness = maxOf(options.fm.defaultRuleThickness, options.em(options.minRuleThickness))
            topPad = 4 * ruleThickness // gap = 3 × line, plus the line itself.
            bottomPad = maxOf(0f, options.em(0.25f) - inner.depth)
        } else {
            topPad = if (isSingleChar) options.em(0.2f) else 0f
            bottomPad = topPad
        }

        img = stretchyEnclose(inner, label, topPad, bottomPad, options)
        if (Regex("fbox|boxed|fcolorbox").containsMatchIn(label)) {
            img.borderWidth = ruleThickness
        } else if (label == "angl" && img is AnglBox) {
            img.ruleThickness = ruleThickness
        }
        imgShift = inner.depth + bottomPad

        if (group.backgroundColor != null) {
            img.backgroundColor = CssColor.parse(group.backgroundColor)
            if (group.borderColor != null) img.borderColor = CssColor.parse(group.borderColor)
        }
        if (Regex("cancel").containsMatchIn(label)) imgAlign = HAlign.LEFT
    }

    val vlist = if (group.backgroundColor != null) {
        makeVList(
            VListPosition.IndividualShift,
            listOf(
                // Put the color background behind inner;
                VListChild.Elem(img, shift = imgShift),
                VListChild.Elem(inner, shift = 0f),
            ),
        )
    } else {
        makeVList(
            VListPosition.IndividualShift,
            listOf(
                // Write the \cancel stroke on top of inner.
                VListChild.Elem(inner, shift = 0f),
                VListChild.Elem(img, shift = imgShift, align = imgAlign),
            ),
        )
    }

    if (Regex("cancel").containsMatchIn(label)) {
        // The cancel package documentation says that cancel lines add their height
        // to the expression, but tests show that isn't how it actually works.
        vlist.height = inner.height
        vlist.depth = inner.depth
    }

    return if (Regex("cancel").containsMatchIn(label) && !isSingleChar) {
        // cancel does not create horiz space for its line extension.
        makeSpan(listOf("mord", "cancel-lap"), listOf(vlist), options).also {
            it.marginLeft = -options.em(0.2f)
            it.marginRight = -options.em(0.2f)
        }
    } else {
        makeSpan(listOf("mord"), listOf(vlist), options)
    }
}

internal fun registerEnclose() {
    defineFunction(
        names = listOf("\\colorbox"),
        numArgs = 2,
        allowedInText = true,
        argTypes = listOf(ArgType.COLOR, ArgType.HBOX),
    ) { ctx, args, _ ->
        val color = args[0].assertType<ColorTokenNode>().color
        EncloseNode(ctx.parser.mode, ctx.funcName, args[1], backgroundColor = color)
    }

    defineFunction(
        names = listOf("\\fcolorbox"),
        numArgs = 3,
        allowedInText = true,
        argTypes = listOf(ArgType.COLOR, ArgType.COLOR, ArgType.HBOX),
    ) { ctx, args, _ ->
        val borderColor = args[0].assertType<ColorTokenNode>().color
        val backgroundColor = args[1].assertType<ColorTokenNode>().color
        EncloseNode(ctx.parser.mode, ctx.funcName, args[2], backgroundColor, borderColor)
    }

    defineFunction(
        names = listOf("\\fbox"),
        numArgs = 1,
        argTypes = listOf(ArgType.HBOX),
        allowedInText = true,
    ) { ctx, args, _ ->
        EncloseNode(ctx.parser.mode, "\\fbox", args[0])
    }

    defineFunction(
        names = listOf("\\cancel", "\\bcancel", "\\xcancel", "\\phase"),
        numArgs = 1,
    ) { ctx, args, _ ->
        EncloseNode(ctx.parser.mode, ctx.funcName, args[0])
    }

    defineFunction(
        names = listOf("\\sout"),
        numArgs = 1,
        allowedInText = true,
    ) { ctx, args, _ ->
        // KaTeX reports "LaTeX's \sout works only in text mode" in strict mode only.
        EncloseNode(ctx.parser.mode, ctx.funcName, args[0])
    }

    defineFunction(
        names = listOf("\\angl"),
        numArgs = 1,
        argTypes = listOf(ArgType.HBOX),
        allowedInText = false,
    ) { ctx, args, _ ->
        EncloseNode(ctx.parser.mode, "\\angl", args[0])
    }

    defineHtmlBuilder<EncloseNode> { group, options -> encloseHtmlBuilder(group, options) }
}
