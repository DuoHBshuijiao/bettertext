package me.rerere.bettertext.latex.functions

import me.rerere.bettertext.latex.layout.Box
import me.rerere.bettertext.latex.layout.Options
import me.rerere.bettertext.latex.layout.Style
import me.rerere.bettertext.latex.layout.SymbolBox
import me.rerere.bettertext.latex.layout.VListChild
import me.rerere.bettertext.latex.layout.VListPosition
import me.rerere.bettertext.latex.layout.buildExpression
import me.rerere.bettertext.latex.layout.makeSpan
import me.rerere.bettertext.latex.layout.makeSymbol
import me.rerere.bettertext.latex.layout.makeVList
import me.rerere.bettertext.latex.layout.mathsym
import me.rerere.bettertext.latex.layout.staticSvg
import me.rerere.bettertext.latex.parse.Mode
import me.rerere.bettertext.latex.parse.OpNode
import me.rerere.bettertext.latex.parse.ParseNode
import me.rerere.bettertext.latex.parse.SupSubNode
import me.rerere.bettertext.latex.parse.ordArgument

/* Ported from KaTeX's op.ts: limits, symbols. */

// Most operators have a large successor symbol, but these don't.
private val noSuccessor = setOf("\\smallint")

/**
 * Unlike most builders, this one handles not only "op", but also "supsub"
 * since some of them (like \int) can affect super/subscripting.
 */
private fun opHtmlBuilder(grp: ParseNode, options: Options): Box {
    // Operators are handled in the TeXbook pg. 443-444, rule 13(a).
    var supGroup: ParseNode? = null
    var subGroup: ParseNode? = null
    var hasLimits = false
    val group: OpNode
    if (grp is SupSubNode) {
        // If we have limits, supsub will pass us its group to handle. Pull
        // out the superscript and subscript and set the group to the op in
        // its base.
        supGroup = grp.sup
        subGroup = grp.sub
        group = grp.base.assertType()
        hasLimits = true
    } else {
        group = grp.assertType()
    }

    val style = options.style
    // Most symbol operators get larger in displaystyle (rule 13)
    val large = style.size == Style.DISPLAY.size && group.symbol && group.name !in noSuccessor

    var base: Box
    val isOiint = group.name == "\\oiint" || group.name == "\\oiiint"
    if (group.symbol) {
        // If this is a symbol, create the symbol.
        val fontName = if (large) "Size2-Regular" else "Size1-Regular"

        var stash = ""
        var name = group.name ?: ""
        if (isOiint) {
            // No font glyphs yet, so use a glyph w/o the oval.
            stash = name.substring(1)
            name = if (stash == "oiint") "\\iint" else "\\iiint"
        }

        base = makeSymbol(
            name, fontName, Mode.MATH, options,
            listOf("mop", "op-symbol", if (large) "large-op" else "small-op"),
        )
        // Italic correction from the symbol glyph, captured before the symbol
        // may be wrapped in a vlist (for \oiint/\oiiint).
        val symbolItalic = base.italic

        if (stash.isNotEmpty()) {
            // We're in \oiint or \oiiint. Overlay the oval.
            val oval = staticSvg(stash + "Size" + (if (large) "2" else "1"), options)
            base = makeVList(
                VListPosition.IndividualShift,
                listOf(
                    VListChild.Elem(base, shift = 0f),
                    VListChild.Elem(oval, shift = if (large) options.em(0.08f) else 0f),
                ),
            )
            base.classes.add(0, "mop")
            // Carry the italic correction from the original symbol to the
            // vlist wrapper so supsub can use it for subscript positioning.
            base.italic = symbolItalic
        }
    } else if (group.body != null) {
        // If this is a list, compose that list.
        val inner = buildExpression(group.body, options, true)
        if (inner.size == 1 && inner[0] is SymbolBox) {
            base = inner[0]
            if (base.classes.isEmpty()) base.classes.add("mop") else base.classes[0] = "mop" // replace old mclass
        } else {
            base = makeSpan(listOf("mop"), inner, options)
        }
    } else {
        // Otherwise, this is a text operator. Build the text from the
        // operator's name.
        val name = group.name ?: ""
        val output = ArrayList<Box>()
        for (i in 1 until name.length) {
            output.add(mathsym(name[i].toString(), group.mode, options))
        }
        base = makeSpan(listOf("mop"), output, options)
    }

    // If content of op is a single symbol, shift it vertically.
    var baseShift = 0f
    var slant = 0f
    if ((base is SymbolBox || isOiint) && !group.suppressBaseShift) {
        // We suppress the shift of the base of \overset and \underset. Otherwise,
        // shift the symbol so its center lies on the axis (rule 13). It
        // appears that our fonts have the centers of the symbols already
        // almost on the axis, so these numbers are very small. Note we
        // don't actually apply this here, but instead it is used either in
        // the vlist creation or separately when there are no limits.
        baseShift = (base.height - base.depth) / 2 - options.fm.axisHeight

        // The slant of the symbol is just its italic correction.
        slant = base.italic
    }

    return if (hasLimits) {
        assembleSupSub(base, supGroup, subGroup, options, style, slant, baseShift)
    } else {
        if (baseShift != 0f) base.shiftY = baseShift
        base
    }
}

private val singleCharBigOps = mapOf(
    "∏" to "\\prod",
    "∐" to "\\coprod",
    "∑" to "\\sum",
    "⋀" to "\\bigwedge",
    "⋁" to "\\bigvee",
    "⋂" to "\\bigcap",
    "⋃" to "\\bigcup",
    "⨀" to "\\bigodot",
    "⨁" to "\\bigoplus",
    "⨂" to "\\bigotimes",
    "⨄" to "\\biguplus",
    "⨆" to "\\bigsqcup",
)

private val singleCharIntegrals = mapOf(
    "∫" to "\\int",
    "∬" to "\\iint",
    "∭" to "\\iiint",
    "∮" to "\\oint",
    "∯" to "\\oiint",
    "∰" to "\\oiiint",
)

internal fun registerOp() {
    defineHtmlBuilder<OpNode> { group, options -> opHtmlBuilder(group, options) }
    SupSubDelegates.op = { group, options -> opHtmlBuilder(group, options) }

    defineFunction(
        names = listOf(
            "\\coprod", "\\bigvee", "\\bigwedge", "\\biguplus", "\\bigcap",
            "\\bigcup", "\\intop", "\\prod", "\\sum", "\\bigotimes",
            "\\bigoplus", "\\bigodot", "\\bigsqcup", "\\smallint", "∏",
            "∐", "∑", "⋀", "⋁", "⋂", "⋃", "⨀",
            "⨁", "⨂", "⨄", "⨆",
        ),
        numArgs = 0,
    ) { ctx, _, _ ->
        val fName = if (ctx.funcName.length == 1) singleCharBigOps.getValue(ctx.funcName) else ctx.funcName
        OpNode(ctx.parser.mode, limits = true, parentIsSupSub = false, symbol = true, name = fName)
    }

    defineFunction(
        names = listOf("\\mathop"),
        numArgs = 1,
        primitive = true,
    ) { ctx, args, _ ->
        OpNode(ctx.parser.mode, limits = false, parentIsSupSub = false, symbol = false, body = ordArgument(args[0]))
    }

    // There are 2 flags for operators; whether they produce limits in
    // displaystyle, and whether they are symbols and should grow in
    // displaystyle. These four groups cover the four possible choices.

    // No limits, not symbols
    defineFunction(
        names = listOf(
            "\\arcsin", "\\arccos", "\\arctan", "\\arctg", "\\arcctg",
            "\\arg", "\\ch", "\\cos", "\\cosec", "\\cosh", "\\cot", "\\cotg",
            "\\coth", "\\csc", "\\ctg", "\\cth", "\\deg", "\\dim", "\\exp",
            "\\hom", "\\ker", "\\lg", "\\ln", "\\log", "\\sec", "\\sin",
            "\\sinh", "\\sh", "\\tan", "\\tanh", "\\tg", "\\th",
        ),
        numArgs = 0,
    ) { ctx, _, _ ->
        OpNode(ctx.parser.mode, limits = false, parentIsSupSub = false, symbol = false, name = ctx.funcName)
    }

    // Limits, not symbols
    defineFunction(
        names = listOf("\\det", "\\gcd", "\\inf", "\\lim", "\\max", "\\min", "\\Pr", "\\sup"),
        numArgs = 0,
    ) { ctx, _, _ ->
        OpNode(ctx.parser.mode, limits = true, parentIsSupSub = false, symbol = false, name = ctx.funcName)
    }

    // No limits, symbols
    defineFunction(
        names = listOf(
            "\\int", "\\iint", "\\iiint", "\\oint", "\\oiint", "\\oiiint",
            "∫", "∬", "∭", "∮", "∯", "∰",
        ),
        numArgs = 0,
        allowedInArgument = true,
    ) { ctx, _, _ ->
        val fName = if (ctx.funcName.length == 1) singleCharIntegrals.getValue(ctx.funcName) else ctx.funcName
        OpNode(ctx.parser.mode, limits = false, parentIsSupSub = false, symbol = true, name = fName)
    }
}
