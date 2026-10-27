package me.rerere.bettertext.latex.functions

import me.rerere.bettertext.latex.ParseError
import me.rerere.bettertext.latex.layout.Box
import me.rerere.bettertext.latex.layout.Options
import me.rerere.bettertext.latex.layout.Style
import me.rerere.bettertext.latex.layout.buildExpression
import me.rerere.bettertext.latex.layout.makeFragment
import me.rerere.bettertext.latex.parse.ParseNode
import me.rerere.bettertext.latex.parse.SizingNode
import me.rerere.bettertext.latex.parse.StylingNode

/* Ported from KaTeX's styling.ts and sizing.ts. */

private val styleMap = mapOf(
    "display" to Style.DISPLAY,
    "text" to Style.TEXT,
    "script" to Style.SCRIPT,
    "scriptscript" to Style.SCRIPTSCRIPT,
)

/**
 * Builds [value] with [options] as a fragment. KaTeX also adds size-resetting
 * classes and rescales heights here; boxes are already in absolute em, so
 * neither is needed.
 */
@Suppress("UNUSED_PARAMETER")
internal fun sizingGroup(value: List<ParseNode>, options: Options, baseOptions: Options): Box {
    val inner = buildExpression(value, options, false)
    return makeFragment(inner)
}

private val sizeFuncs = listOf(
    "\\tiny", "\\sixptsize", "\\scriptsize", "\\footnotesize", "\\small",
    "\\normalsize", "\\large", "\\Large", "\\LARGE", "\\huge", "\\Huge",
)

internal fun registerStyling() {
    defineFunction(
        names = listOf("\\displaystyle", "\\textstyle", "\\scriptstyle", "\\scriptscriptstyle"),
        numArgs = 0,
        allowedInText = true,
        primitive = true,
    ) { ctx, _, _ ->
        val parser = ctx.parser
        // parse out the implicit body
        val body = parser.parseExpression(true, ctx.breakOnTokenText)
        // Figure out what style to use by pulling out the style from
        // the function name
        val style = ctx.funcName.substring(1, ctx.funcName.length - 5)
        if (style !in styleMap) throw ParseError("Unknown style: $style")
        StylingNode(parser.mode, style, body)
    }

    defineHtmlBuilder<StylingNode> { group, options ->
        // Style changes are handled in the TeXbook on pg. 442, Rule 3.
        val newStyle = styleMap[group.style] ?: Style.TEXT
        var newOptions = options.havingStyle(newStyle)
        if (group.resetFont) newOptions = newOptions.withFont("")
        sizingGroup(group.body, newOptions, options)
    }

    defineFunction(
        names = sizeFuncs,
        numArgs = 0,
        allowedInText = true,
    ) { ctx, _, _ ->
        val body = ctx.parser.parseExpression(false, ctx.breakOnTokenText)
        // Figure out what size to use based on the list of functions above
        SizingNode(ctx.parser.mode, sizeFuncs.indexOf(ctx.funcName) + 1, body)
    }

    defineHtmlBuilder<SizingNode> { group, options ->
        // Handle sizing operators like \Huge. Real TeX doesn't actually allow
        // these functions inside of math expressions, so we do some special
        // handling.
        val newOptions = options.havingSize(group.size)
        sizingGroup(group.body, newOptions, options)
    }
}
