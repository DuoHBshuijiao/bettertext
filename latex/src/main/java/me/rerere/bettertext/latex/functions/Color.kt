package me.rerere.bettertext.latex.functions

import me.rerere.bettertext.latex.layout.buildExpression
import me.rerere.bettertext.latex.layout.makeFragment
import me.rerere.bettertext.latex.parse.ColorNode
import me.rerere.bettertext.latex.parse.ColorTokenNode
import me.rerere.bettertext.latex.parse.MacroDefinition
import me.rerere.bettertext.latex.parse.ordArgument

/* Ported from KaTeX's color.ts. */

internal fun registerColor() {
    defineHtmlBuilder<ColorNode> { group, options ->
        val elements = buildExpression(group.body, options.withColor(group.color), false)
        // \color isn't supposed to affect the type of the elements it contains.
        // To accomplish this, we wrap the results in a fragment, so the inner
        // elements will be able to directly interact with their neighbors. For
        // example, `\color{red}{2 +} 3` has the same spacing as `2 + 3`
        makeFragment(elements)
    }

    defineFunction(
        names = listOf("\\textcolor"),
        numArgs = 2,
        allowedInText = true,
        argTypes = listOf(ArgType.COLOR, ArgType.ORIGINAL),
    ) { ctx, args, _ ->
        val color = args[0].assertType<ColorTokenNode>().color
        ColorNode(ctx.parser.mode, color, ordArgument(args[1]))
    }

    defineFunction(
        names = listOf("\\color"),
        numArgs = 1,
        allowedInText = true,
        argTypes = listOf(ArgType.COLOR),
    ) { ctx, args, _ ->
        val parser = ctx.parser
        val color = args[0].assertType<ColorTokenNode>().color

        // Set macro \current@color in current namespace to store the current
        // color, mimicking the behavior of color.sty.
        // This is currently used just to correctly color a \right
        // that follows a \color command.
        parser.gullet.macros.set("\\current@color", MacroDefinition.Text(color))

        // Parse out the implicit body that should be colored.
        val body = parser.parseExpression(true, ctx.breakOnTokenText)
        ColorNode(parser.mode, color, body)
    }
}
