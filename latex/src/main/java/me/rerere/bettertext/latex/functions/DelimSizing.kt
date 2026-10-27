package me.rerere.bettertext.latex.functions

import me.rerere.bettertext.latex.ParseError
import me.rerere.bettertext.latex.layout.Box
import me.rerere.bettertext.latex.layout.Options
import me.rerere.bettertext.latex.layout.SpanBox
import me.rerere.bettertext.latex.layout.buildExpression
import me.rerere.bettertext.latex.layout.makeLeftRightDelim
import me.rerere.bettertext.latex.layout.makeNullDelimiter
import me.rerere.bettertext.latex.layout.makeSizedDelim
import me.rerere.bettertext.latex.layout.makeSpan
import me.rerere.bettertext.latex.parse.DelimSizingNode
import me.rerere.bettertext.latex.parse.LeftRightNode
import me.rerere.bettertext.latex.parse.LeftRightRightNode
import me.rerere.bettertext.latex.parse.MacroDefinition
import me.rerere.bettertext.latex.parse.MiddleNode
import me.rerere.bettertext.latex.parse.ParseNode
import me.rerere.bettertext.latex.parse.SymbolParseNode

/* Ported from KaTeX's functions/delimsizing.ts. */

private class DelimSize(val mclass: String, val size: Int)

// Extra data needed for the delimiter handler down below
private val delimiterSizes = mapOf(
    "\\bigl" to DelimSize("mopen", 1),
    "\\Bigl" to DelimSize("mopen", 2),
    "\\biggl" to DelimSize("mopen", 3),
    "\\Biggl" to DelimSize("mopen", 4),
    "\\bigr" to DelimSize("mclose", 1),
    "\\Bigr" to DelimSize("mclose", 2),
    "\\biggr" to DelimSize("mclose", 3),
    "\\Biggr" to DelimSize("mclose", 4),
    "\\bigm" to DelimSize("mrel", 1),
    "\\Bigm" to DelimSize("mrel", 2),
    "\\biggm" to DelimSize("mrel", 3),
    "\\Biggm" to DelimSize("mrel", 4),
    "\\big" to DelimSize("mord", 1),
    "\\Big" to DelimSize("mord", 2),
    "\\bigg" to DelimSize("mord", 3),
    "\\Bigg" to DelimSize("mord", 4),
)

private val delimiters = setOf(
    "(", "\\lparen", ")", "\\rparen",
    "[", "\\lbrack", "]", "\\rbrack",
    "\\{", "\\lbrace", "\\}", "\\rbrace",
    "\\lfloor", "\\rfloor", "⌊", "⌋",
    "\\lceil", "\\rceil", "⌈", "⌉",
    "<", ">", "\\langle", "⟨", "\\rangle", "⟩", "\\lt", "\\gt",
    "\\lvert", "\\rvert", "\\lVert", "\\rVert",
    "\\lgroup", "\\rgroup", "⟮", "⟯",
    "\\lmoustache", "\\rmoustache", "⎰", "⎱",
    "/", "\\backslash",
    "|", "\\vert", "\\|", "\\Vert",
    "\\uparrow", "\\Uparrow",
    "\\downarrow", "\\Downarrow",
    "\\updownarrow", "\\Updownarrow",
    ".",
)

/**
 * A built `\middle` delimiter. It carries the delimiter and the options that
 * were active when `\middle` was called, so the `\left`/`\right` builder can
 * rebuild it at the right size (KaTeX's ad-hoc `isMiddle` property).
 */
private class MiddleDelimBox(val delim: String, val delimOptions: Options, inner: Box) :
    SpanBox(inner.classes, mutableListOf(inner)) {
    init {
        sizeFromChildren()
    }
}

private fun parseErrorAt(message: String, node: ParseNode): ParseError =
    ParseError(message, node.loc?.start ?: -1)

// Delimiter functions
private fun checkDelimiter(delim: ParseNode, context: FunctionContext): SymbolParseNode {
    val symDelim = delim as? SymbolParseNode
    if (symDelim != null && symDelim.text in delimiters) {
        return symDelim
    } else if (symDelim != null) {
        throw parseErrorAt("Invalid delimiter '${symDelim.text}' after '${context.funcName}'", delim)
    } else {
        throw parseErrorAt("Invalid delimiter type '${delim::class.simpleName}'", delim)
    }
}

internal fun registerDelimSizing() {
    defineFunction(
        names = listOf(
            "\\bigl", "\\Bigl", "\\biggl", "\\Biggl",
            "\\bigr", "\\Bigr", "\\biggr", "\\Biggr",
            "\\bigm", "\\Bigm", "\\biggm", "\\Biggm",
            "\\big", "\\Big", "\\bigg", "\\Bigg",
        ),
        numArgs = 1,
        argTypes = listOf(ArgType.PRIMITIVE),
    ) { ctx, args, _ ->
        val delim = checkDelimiter(normalizeArgument(args[0]), ctx)
        val data = delimiterSizes.getValue(ctx.funcName)
        DelimSizingNode(ctx.parser.mode, data.size, data.mclass, delim.text)
    }
    defineHtmlBuilder<DelimSizingNode> { group, options ->
        if (group.delim == ".") {
            // Empty delimiters still count as elements, even though they don't
            // show anything.
            makeSpan(listOf(group.mclass))
        } else {
            makeSizedDelim(group.delim, group.size, options, group.mode, listOf(group.mclass))
        }
    }

    defineFunction(
        names = listOf("\\right"),
        numArgs = 1,
        primitive = true,
    ) { ctx, args, _ ->
        // \left case below triggers parsing of \right in
        //   `val right = parseRight(parser)`
        // uses this return value.
        val color = when (val c = ctx.parser.gullet.macros.get("\\current@color")) {
            null -> null
            is MacroDefinition.Text -> c.body
            else -> throw ParseError("\\current@color set to non-string in \\right")
        }
        LeftRightRightNode(ctx.parser.mode, checkDelimiter(args[0], ctx).text, color)
    }

    defineFunction(
        names = listOf("\\left"),
        numArgs = 1,
        primitive = true,
    ) { ctx, args, _ ->
        val delim = checkDelimiter(args[0], ctx)

        val parser = ctx.parser
        // Parse out the implicit body
        ++parser.leftrightDepth
        // parseExpression stops before '\\right'
        val body = parser.parseExpression(false)
        --parser.leftrightDepth
        // Check the next token
        parser.expect("\\right", false)
        // KaTeX calls `parser.parseFunction()` here; that method is private in
        // the Kotlin parser, so do the same steps through its public API.
        val token = parser.fetch()
        val funcData = Registry.functions.getValue("\\right")
        parser.consume()
        val (rightArgs, rightOptArgs) = parser.parseArguments(
            "\\right", funcData.numArgs, funcData.numOptionalArgs, funcData.argTypes, funcData.primitive,
        )
        val right = parser.callFunction("\\right", rightArgs, rightOptArgs, token).assertType<LeftRightRightNode>()
        LeftRightNode(parser.mode, body, delim.text, right.delim, right.color)
    }

    defineHtmlBuilder<LeftRightNode> { group, options ->
        // Build the inner expression
        val inner = buildExpression(group.body, options, true, "mopen" to "mclose")

        var innerHeight = 0f
        var innerDepth = 0f
        var hadMiddle = false

        // Calculate its height and depth
        for (node in inner) {
            if (node is MiddleDelimBox) {
                hadMiddle = true
            } else {
                innerHeight = maxOf(node.height, innerHeight)
                innerDepth = maxOf(node.depth, innerDepth)
            }
        }

        // The size of delimiters is the same, regardless of what style we are
        // in. KaTeX scales the inner size by options.sizeMultiplier here; our
        // dimensions are already absolute.

        val leftDelim = if (group.left == ".") {
            // Empty delimiters in \left and \right make null delimiter spaces.
            makeNullDelimiter(options, listOf("mopen"))
        } else {
            // Otherwise, use leftRightDelim to generate the correct sized
            // delimiter.
            makeLeftRightDelim(group.left, innerHeight, innerDepth, options, group.mode, listOf("mopen"))
        }
        // Add it to the beginning of the expression
        inner.add(0, leftDelim)

        // Handle middle delimiters
        if (hadMiddle) {
            for (i in 1 until inner.size) {
                val middleDelim = inner[i]
                if (middleDelim is MiddleDelimBox) {
                    // Apply the options that were active when \middle was called
                    inner[i] = makeLeftRightDelim(
                        middleDelim.delim, innerHeight, innerDepth, middleDelim.delimOptions, group.mode, emptyList(),
                    )
                }
            }
        }

        // Same for the right delimiter, but using color specified by \color
        val rightDelim = if (group.right == ".") {
            makeNullDelimiter(options, listOf("mclose"))
        } else {
            val colorOptions = if (group.rightColor != null) options.withColor(group.rightColor) else options
            makeLeftRightDelim(group.right, innerHeight, innerDepth, colorOptions, group.mode, listOf("mclose"))
        }
        // Add it to the end of the expression.
        inner.add(rightDelim)

        makeSpan(listOf("minner"), inner, options)
    }

    defineFunction(
        names = listOf("\\middle"),
        numArgs = 1,
        primitive = true,
    ) { ctx, args, _ ->
        val delim = checkDelimiter(args[0], ctx)
        if (ctx.parser.leftrightDepth == 0) {
            throw parseErrorAt("\\middle without preceding \\left", delim)
        }
        MiddleNode(ctx.parser.mode, delim.text)
    }
    defineHtmlBuilder<MiddleNode> { group, options ->
        if (group.delim == ".") {
            makeNullDelimiter(options, emptyList())
        } else {
            val sized = makeSizedDelim(group.delim, 1, options, group.mode, emptyList())
            // Mark the node so the \left/\right builder can reconstruct
            // appropriately sized middle delimiters.
            MiddleDelimBox(group.delim, options, sized)
        }
    }
}
