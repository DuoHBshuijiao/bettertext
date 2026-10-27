package me.rerere.bettertext.latex.functions

import me.rerere.bettertext.latex.layout.buildGroup
import me.rerere.bettertext.latex.parse.FontNode
import me.rerere.bettertext.latex.parse.MClassNode
import me.rerere.bettertext.latex.parse.OrdGroupNode

/* Ported from KaTeX's font.ts. TODO(kevinb): implement \sl and \sc */

private val fontAliases = mapOf(
    "\\Bbb" to "\\mathbb",
    "\\bold" to "\\mathbf",
    "\\frak" to "\\mathfrak",
)

internal fun registerFont() {
    defineHtmlBuilder<FontNode> { group, options ->
        buildGroup(group.body, options.withFont(group.font))
    }

    defineFunction(
        names = listOf(
            // styles, except \boldsymbol defined below
            "\\mathrm", "\\mathit", "\\mathbf", "\\mathnormal", "\\mathsfit",
            // families
            "\\mathbb", "\\mathcal", "\\mathfrak", "\\mathscr", "\\mathsf",
            "\\mathtt",
            // aliases, except \bm defined below
            "\\Bbb", "\\bold", "\\frak",
        ),
        numArgs = 1,
        allowedInArgument = true,
    ) { ctx, args, _ ->
        val body = normalizeArgument(args[0])
        val func = fontAliases[ctx.funcName] ?: ctx.funcName
        FontNode(ctx.parser.mode, func.substring(1), body)
    }

    defineFunction(
        names = listOf("\\boldsymbol", "\\bm"),
        numArgs = 1,
    ) { ctx, args, _ ->
        val body = args[0]
        // amsbsy.sty's \boldsymbol uses \binrel spacing to inherit the
        // argument's bin|rel|ord status
        MClassNode(
            ctx.parser.mode,
            mclass = binrelClass(body),
            body = listOf(FontNode(ctx.parser.mode, "boldsymbol", body)),
            isCharacterBox = isCharacterBox(body),
        )
    }

    // Old font changing functions
    defineFunction(
        names = listOf("\\rm", "\\sf", "\\tt", "\\bf", "\\it", "\\cal"),
        numArgs = 0,
        allowedInText = true,
    ) { ctx, _, _ ->
        val parser = ctx.parser
        val mode = parser.mode
        val body = parser.parseExpression(true, ctx.breakOnTokenText)
        FontNode(mode, "math" + ctx.funcName.substring(1), OrdGroupNode(parser.mode, body))
    }
}
