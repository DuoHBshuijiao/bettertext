package me.rerere.bettertext.latex.functions

import me.rerere.bettertext.latex.layout.Box
import me.rerere.bettertext.latex.layout.FontMetrics
import me.rerere.bettertext.latex.layout.Options
import me.rerere.bettertext.latex.layout.SymbolBox
import me.rerere.bettertext.latex.layout.buildExpression
import me.rerere.bettertext.latex.layout.makeSpan
import me.rerere.bettertext.latex.parse.Mode
import me.rerere.bettertext.latex.parse.OperatorNameNode
import me.rerere.bettertext.latex.parse.ParseNode
import me.rerere.bettertext.latex.parse.SupSubNode
import me.rerere.bettertext.latex.parse.SymbolParseNode
import me.rerere.bettertext.latex.parse.TextOrdNode
import me.rerere.bettertext.latex.parse.ordArgument

/* Ported from KaTeX's operatorname.ts. */

/**
 * Unlike most builders, this one handles not only "operatorname", but also
 * "supsub" since \operatorname* can affect super/subscripting.
 */
private fun operatorNameHtmlBuilder(grp: ParseNode, options: Options): Box {
    // Operators are handled in the TeXbook pg. 443-444, rule 13(a).
    var supGroup: ParseNode? = null
    var subGroup: ParseNode? = null
    var hasLimits = false
    val group: OperatorNameNode
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

    val base = if (group.body.isNotEmpty()) {
        val body = group.body.map { child ->
            if (child is SymbolParseNode) TextOrdNode(child.mode, child.text) else child
        }

        // Consolidate function names into symbol characters.
        val expression = buildExpression(body, options.withFont("mathrm"), true)
        for (child in expression) {
            if (child is SymbolBox) {
                // Per amsopn package,
                // change minus to hyphen and \ast to asterisk
                val text = child.text.replaceFirst('−', '-').replaceFirst('∗', '*')
                if (text != child.text) {
                    // KaTeX lets the browser re-measure the new text; we must update the metrics.
                    child.text = text
                    FontMetrics.getCharacterMetrics(text, child.font, Mode.MATH)?.let { m ->
                        child.width = m.width * child.scale
                        child.height = m.height * child.scale
                        child.depth = m.depth * child.scale
                        child.italic = m.italic * child.scale
                    }
                }
            }
        }
        makeSpan(listOf("mop"), expression, options)
    } else {
        makeSpan(listOf("mop"), emptyList(), options)
    }

    return if (hasLimits) {
        assembleSupSub(base, supGroup, subGroup, options, options.style, 0f, 0f)
    } else {
        base
    }
}

internal fun registerOperatorName() {
    // \operatorname
    // amsopn.dtx: \mathop{#1\kern\z@\operator@font#3}\newmcodes@
    defineFunction(
        names = listOf("\\operatorname@", "\\operatornamewithlimits"),
        numArgs = 1,
    ) { ctx, args, _ ->
        OperatorNameNode(
            ctx.parser.mode,
            body = ordArgument(args[0]),
            alwaysHandleSupSub = ctx.funcName == "\\operatornamewithlimits",
            limits = false,
            parentIsSupSub = false,
        )
    }
    defineHtmlBuilder<OperatorNameNode> { group, options -> operatorNameHtmlBuilder(group, options) }
    SupSubDelegates.operatorname = { group, options -> operatorNameHtmlBuilder(group, options) }
    // \operatorname itself is a macro: \@ifstar\operatornamewithlimits\operatorname@
}
