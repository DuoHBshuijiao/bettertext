package me.rerere.bettertext.latex.functions

import me.rerere.bettertext.latex.layout.Options
import me.rerere.bettertext.latex.layout.buildExpression
import me.rerere.bettertext.latex.layout.makeSpan
import me.rerere.bettertext.latex.parse.TextNode
import me.rerere.bettertext.latex.parse.ordArgument

/* Ported from KaTeX's text.ts: non-mathy text, possibly in a font. */

private val textFontFamilies = mapOf(
    "\\textrm" to "textrm",
    "\\textsf" to "textsf",
    "\\texttt" to "texttt",
    "\\textnormal" to "textrm",
)

private val textFontWeights = mapOf(
    "\\textbf" to "textbf",
    "\\textmd" to "textmd",
)

private val textFontShapes = mapOf(
    "\\textit" to "textit",
    "\\textup" to "textup",
)

private fun optionsWithFont(group: TextNode, options: Options): Options {
    val font = group.font
    // Checks if the argument is a font family or a font style.
    if (font.isNullOrEmpty()) return options
    textFontFamilies[font]?.let { return options.withTextFontFamily(it) }
    textFontWeights[font]?.let { return options.withTextFontWeight(it) }
    if (font == "\\emph") {
        return if (options.fontShape == "textit") {
            options.withTextFontShape("textup")
        } else {
            options.withTextFontShape("textit")
        }
    }
    // For \text the lookup yields undefined in KaTeX, which resets both the
    // math font and the font shape.
    return options.withTextFontShape(textFontShapes[font] ?: "")
}

internal fun registerText() {
    defineFunction(
        names = listOf(
            // Font families
            "\\text", "\\textrm", "\\textsf", "\\texttt", "\\textnormal",
            // Font weights
            "\\textbf", "\\textmd",
            // Font Shapes
            "\\textit", "\\textup", "\\emph",
        ),
        numArgs = 1,
        argTypes = listOf(ArgType.TEXT),
        allowedInArgument = true,
        allowedInText = true,
    ) { ctx, args, _ ->
        TextNode(ctx.parser.mode, ordArgument(args[0]), font = ctx.funcName)
    }

    defineHtmlBuilder<TextNode> { group, options ->
        val newOptions = optionsWithFont(group, options)
        val inner = buildExpression(group.body, newOptions, true)
        makeSpan(listOf("mord", "text"), inner, newOptions)
    }
}
