package me.rerere.bettertext.latex.functions

import me.rerere.bettertext.latex.ParseError
import me.rerere.bettertext.latex.layout.Box
import me.rerere.bettertext.latex.layout.HAlign
import me.rerere.bettertext.latex.layout.Options
import me.rerere.bettertext.latex.layout.Style
import me.rerere.bettertext.latex.layout.VListChild
import me.rerere.bettertext.latex.layout.VListPosition
import me.rerere.bettertext.latex.layout.buildGroup
import me.rerere.bettertext.latex.layout.calculateSizeAbs
import me.rerere.bettertext.latex.layout.makeCustomSizedDelim
import me.rerere.bettertext.latex.layout.makeLineSpan
import me.rerere.bettertext.latex.layout.makeNullDelimiter
import me.rerere.bettertext.latex.layout.makeSpan
import me.rerere.bettertext.latex.layout.makeVList
import me.rerere.bettertext.latex.parse.AtomNode
import me.rerere.bettertext.latex.parse.GenFracNode
import me.rerere.bettertext.latex.parse.InfixNode
import me.rerere.bettertext.latex.parse.Measurement
import me.rerere.bettertext.latex.parse.OrdGroupNode
import me.rerere.bettertext.latex.parse.ParseNode
import me.rerere.bettertext.latex.parse.SizeNode
import me.rerere.bettertext.latex.parse.StylingNode
import me.rerere.bettertext.latex.parse.TextOrdNode

/* Ported from KaTeX's functions/genfrac.ts. */

private fun genFracHtml(group: GenFracNode, options: Options): Box {
    // Fractions are handled in the TeXbook on pages 444-445, rules 15(a-e).
    val style = options.style
    val fm = options.fm

    val nstyle = style.fracNum()
    val dstyle = style.fracDen()

    var newOptions = options.havingStyle(nstyle)
    val numerm = buildGroup(group.numer, newOptions, options)

    if (group.continued) {
        // \cfrac inserts a \strut into the numerator.
        // Get \strut dimensions from TeXbook page 353.
        val hStrut = options.em(8.5f / options.fontMetrics().ptPerEm)
        val dStrut = options.em(3.5f / options.fontMetrics().ptPerEm)
        numerm.height = if (numerm.height < hStrut) hStrut else numerm.height
        numerm.depth = if (numerm.depth < dStrut) dStrut else numerm.depth
    }

    newOptions = options.havingStyle(dstyle)
    val denomm = buildGroup(group.denom, newOptions, options)

    val rule: Box?
    val ruleWidth: Float
    val ruleSpacing: Float
    if (group.hasBarLine) {
        val barSize = group.barSize
        rule = if (barSize != null) {
            makeLineSpan("frac-line", options, calculateSizeAbs(barSize, options))
        } else {
            makeLineSpan("frac-line", options)
        }
        ruleWidth = rule.height
        ruleSpacing = rule.height
    } else {
        rule = null
        ruleWidth = 0f
        ruleSpacing = fm.defaultRuleThickness
    }

    // Rule 15b
    var numShift: Float
    val clearance: Float
    var denomShift: Float
    if (style.size == Style.DISPLAY.size) {
        numShift = fm.num1
        clearance = if (ruleWidth > 0) 3 * ruleSpacing else 7 * ruleSpacing
        denomShift = fm.denom1
    } else {
        if (ruleWidth > 0) {
            numShift = fm.num2
            clearance = ruleSpacing
        } else {
            numShift = fm.num3
            clearance = 3 * ruleSpacing
        }
        denomShift = fm.denom2
    }

    // `.mfrac > span > span { text-align: center }`
    val frac = if (rule == null) {
        // Rule 15c
        val candidateClearance = (numShift - numerm.depth) - (denomm.height - denomShift)
        if (candidateClearance < clearance) {
            numShift += 0.5f * (clearance - candidateClearance)
            denomShift += 0.5f * (clearance - candidateClearance)
        }

        makeVList(
            VListPosition.IndividualShift,
            listOf(
                VListChild.Elem(denomm, shift = denomShift),
                VListChild.Elem(numerm, shift = -numShift),
            ),
            HAlign.CENTER,
        )
    } else {
        // Rule 15d
        val axisHeight = fm.axisHeight

        if ((numShift - numerm.depth) - (axisHeight + 0.5f * ruleWidth) < clearance) {
            numShift += clearance - ((numShift - numerm.depth) - (axisHeight + 0.5f * ruleWidth))
        }

        if ((axisHeight - 0.5f * ruleWidth) - (denomm.height - denomShift) < clearance) {
            denomShift += clearance - ((axisHeight - 0.5f * ruleWidth) - (denomm.height - denomShift))
        }

        val midShift = -(axisHeight - 0.5f * ruleWidth)

        makeVList(
            VListPosition.IndividualShift,
            listOf(
                VListChild.Elem(denomm, shift = denomShift),
                VListChild.Elem(rule, shift = midShift),
                VListChild.Elem(numerm, shift = -numShift),
            ),
            HAlign.CENTER,
        )
    }

    // KaTeX rescales frac.height/depth by havingStyle(style) here, which is a
    // no-op in absolute units.

    // Rule 15e. KaTeX passes these font-relative values as the (absolute)
    // delimiter height; kept as is.
    val delimSize = when (style.size) {
        Style.DISPLAY.size -> options.fontMetrics().delim1
        Style.SCRIPTSCRIPT.size -> options.havingStyle(Style.SCRIPT).fontMetrics().delim2
        else -> options.fontMetrics().delim2
    }

    val leftDelim = if (group.leftDelim == null) {
        makeNullDelimiter(options, listOf("mopen"))
    } else {
        makeCustomSizedDelim(group.leftDelim, delimSize, true, options.havingStyle(style), group.mode, listOf("mopen"))
    }

    val rightDelim = if (group.continued) {
        makeSpan() // zero width for \cfrac
    } else if (group.rightDelim == null) {
        makeNullDelimiter(options, listOf("mclose"))
    } else {
        makeCustomSizedDelim(group.rightDelim, delimSize, true, options.havingStyle(style), group.mode, listOf("mclose"))
    }

    return makeSpan(listOf("mord"), listOf(leftDelim, makeSpan(listOf("mfrac"), listOf(frac)), rightDelim), options)
}

private fun wrapWithStyle(frac: GenFracNode, style: String?): ParseNode =
    if (style == null) frac else StylingNode(frac.mode, style, listOf(frac))

private val stylArray = listOf("display", "text", "script", "scriptscript")

private fun delimFromValue(delimString: String): String? =
    if (delimString.isNotEmpty() && delimString != ".") delimString else null

internal fun registerGenFrac() {
    defineFunction(
        names = listOf(
            "\\cfrac", "\\dfrac", "\\frac", "\\tfrac",
            "\\dbinom", "\\binom", "\\tbinom",
            "\\\\atopfrac", // can’t be entered directly
            "\\\\bracefrac", "\\\\brackfrac", // ditto
        ),
        numArgs = 2,
        allowedInArgument = true,
    ) { ctx, args, _ ->
        val funcName = ctx.funcName
        val numer = args[0]
        val denom = args[1]
        var leftDelim: String? = null
        var rightDelim: String? = null
        val hasBarLine: Boolean = when (funcName) {
            "\\cfrac", "\\dfrac", "\\frac", "\\tfrac" -> true
            "\\\\atopfrac" -> false
            "\\dbinom", "\\binom", "\\tbinom" -> {
                leftDelim = "("
                rightDelim = ")"
                false
            }

            "\\\\bracefrac" -> {
                leftDelim = "\\{"
                rightDelim = "\\}"
                false
            }

            "\\\\brackfrac" -> {
                leftDelim = "["
                rightDelim = "]"
                false
            }

            else -> throw ParseError("Unrecognized genfrac command")
        }

        val continued = funcName == "\\cfrac"
        val style = when {
            continued || funcName.startsWith("\\d") -> "display"
            funcName.startsWith("\\t") -> "text"
            else -> null
        }

        wrapWithStyle(
            GenFracNode(
                ctx.parser.mode,
                continued = continued,
                numer = numer,
                denom = denom,
                hasBarLine = hasBarLine,
                leftDelim = leftDelim,
                rightDelim = rightDelim,
                barSize = null,
            ),
            style,
        )
    }
    defineHtmlBuilder<GenFracNode> { group, options -> genFracHtml(group, options) }

    // Infix generalized fractions -- these are not rendered directly, but replaced
    // immediately by one of the variants above.
    defineFunction(
        names = listOf("\\over", "\\choose", "\\atop", "\\brace", "\\brack"),
        numArgs = 0,
        infix = true,
    ) { ctx, _, _ ->
        val replaceWith = when (ctx.funcName) {
            "\\over" -> "\\frac"
            "\\choose" -> "\\binom"
            "\\atop" -> "\\\\atopfrac"
            "\\brace" -> "\\\\bracefrac"
            "\\brack" -> "\\\\brackfrac"
            else -> throw ParseError("Unrecognized infix genfrac command")
        }
        InfixNode(ctx.parser.mode, replaceWith, token = ctx.token)
    }

    defineFunction(
        names = listOf("\\genfrac"),
        numArgs = 6,
        allowedInArgument = true,
        argTypes = listOf(ArgType.MATH, ArgType.MATH, ArgType.SIZE, ArgType.TEXT, ArgType.MATH, ArgType.MATH),
    ) { ctx, args, _ ->
        val numer = args[4]
        val denom = args[5]

        // Look into the parse nodes to get the desired delimiters.
        val leftNode = normalizeArgument(args[0])
        val leftDelim = if (leftNode is AtomNode && leftNode.family == "open") delimFromValue(leftNode.text) else null
        val rightNode = normalizeArgument(args[1])
        val rightDelim =
            if (rightNode is AtomNode && rightNode.family == "close") delimFromValue(rightNode.text) else null

        val barNode = args[2].assertType<SizeNode>()
        val hasBarLine: Boolean
        var barSize: Measurement? = null
        if (barNode.isBlank) {
            // \genfrac acts differently than \above.
            // \genfrac treats an empty size group as a signal to use a
            // standard bar size. \above would see size = 0 and omit the bar.
            hasBarLine = true
        } else {
            barSize = barNode.value
            hasBarLine = barSize.number > 0
        }

        // Find out if we want displaystyle, textstyle, etc.
        var size: String? = null
        val styl = args[3]
        if (styl is OrdGroupNode) {
            if (styl.body.isNotEmpty()) {
                val textOrd = styl.body[0].assertType<TextOrdNode>()
                size = textOrd.text.toIntOrNull()?.let { stylArray.getOrNull(it) }
            }
        } else {
            val textOrd = styl.assertType<TextOrdNode>()
            size = textOrd.text.toIntOrNull()?.let { stylArray.getOrNull(it) }
        }

        wrapWithStyle(
            GenFracNode(
                ctx.parser.mode,
                continued = false,
                numer = numer,
                denom = denom,
                hasBarLine = hasBarLine,
                leftDelim = leftDelim,
                rightDelim = rightDelim,
                barSize = barSize,
            ),
            size,
        )
    }

    // \above is an infix fraction that also defines a fraction bar size.
    defineFunction(
        names = listOf("\\above"),
        numArgs = 1,
        argTypes = listOf(ArgType.SIZE),
        infix = true,
    ) { ctx, args, _ ->
        InfixNode(
            ctx.parser.mode,
            replaceWith = "\\\\abovefrac",
            size = args[0].assertType<SizeNode>().value,
            token = ctx.token,
        )
    }

    defineFunction(
        names = listOf("\\\\abovefrac"),
        numArgs = 3,
        argTypes = listOf(ArgType.MATH, ArgType.SIZE, ArgType.MATH),
    ) { ctx, args, _ ->
        val numer = args[0]
        val barSize = args[1].assertType<InfixNode>().size
            ?: throw ParseError("\\\\abovefrac expected size, but got null")
        val denom = args[2]
        val hasBarLine = barSize.number > 0
        GenFracNode(
            ctx.parser.mode,
            continued = false,
            numer = numer,
            denom = denom,
            hasBarLine = hasBarLine,
            leftDelim = null,
            rightDelim = null,
            barSize = barSize,
        )
    }
}
