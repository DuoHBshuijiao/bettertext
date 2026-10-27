package me.rerere.bettertext.latex.functions

import me.rerere.bettertext.latex.layout.makeSpan
import me.rerere.bettertext.latex.parse.CrNode
import me.rerere.bettertext.latex.parse.SizeNode

/** cr.ts: row breaks within tabular environments, and line breaks at top level. */
internal fun registerCr() {
    // \DeclareRobustCommand\\{...\@xnewline}
    defineFunction(
        names = listOf("\\\\"),
        numArgs = 0,
        numOptionalArgs = 0,
        allowedInText = true,
    ) { ctx, _, _ ->
        val parser = ctx.parser
        val size = if (parser.gullet.future().text == "[") {
            // Equivalent of KaTeX's parser.parseSizeGroup(true).
            parser.parseArguments("\\\\", 0, 1, listOf(ArgType.SIZE)).second[0]
        } else {
            null
        }
        // KaTeX: newLine = !displayMode || !handleStrict(newLineInDisplayMode, report: false).
        // There is no strict setting here; the default "warn" behavior makes
        // handleStrict return false, so a newline is always produced.
        val newLine = true
        CrNode(parser.mode, newLine, (size as? SizeNode)?.value)
    }

    // The following builder is called only at the top level,
    // not within tabular/array environments.
    defineHtmlBuilder<CrNode> { group, options ->
        val span = makeSpan(listOf("mspace"), emptyList(), options)
        if (group.newLine) {
            span.classes.add("katex-newline")
            // KaTeX sets style.marginTop = calculateSize(group.size). There is no
            // vertical margin in the box model and buildHTML drops the newline
            // box, so the extra line gap of `\\[size]` is not represented.
        }
        span
    }
}
