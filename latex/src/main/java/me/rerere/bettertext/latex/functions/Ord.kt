package me.rerere.bettertext.latex.functions

import me.rerere.bettertext.latex.ParseError
import me.rerere.bettertext.latex.layout.buildExpression
import me.rerere.bettertext.latex.layout.makeFragment
import me.rerere.bettertext.latex.layout.makeOrd
import me.rerere.bettertext.latex.layout.makeSpan
import me.rerere.bettertext.latex.layout.mathsym
import me.rerere.bettertext.latex.parse.AtomNode
import me.rerere.bettertext.latex.parse.MathOrdNode
import me.rerere.bettertext.latex.parse.Mode
import me.rerere.bettertext.latex.parse.OrdGroupNode
import me.rerere.bettertext.latex.parse.SpacingNode
import me.rerere.bettertext.latex.parse.TextOrdNode

/** symbolsOrd.ts, symbolsOp.ts, symbolsSpacing.ts and ordgroup.ts. */
internal fun registerOrd() {
    defineHtmlBuilder<MathOrdNode> { group, options -> makeOrd(group, options) }
    defineHtmlBuilder<TextOrdNode> { group, options -> makeOrd(group, options) }
    defineHtmlBuilder<AtomNode> { group, options ->
        mathsym(group.text, group.mode, options, listOf("m" + group.family))
    }

    val cssSpace = mapOf("\\nobreak" to "nobreak", "\\allowbreak" to "allowbreak")
    val regularSpace = mapOf(" " to "", "\\ " to "", "~" to "nobreak", "\\space" to "", "\\nobreakspace" to "nobreak")
    defineHtmlBuilder<SpacingNode> { group, options ->
        val regularClass = regularSpace[group.text]
        val cssSpaceClass = cssSpace[group.text]
        if (regularClass != null) {
            if (group.mode == Mode.TEXT) {
                makeOrd(group, options).also { it.classes.add(regularClass) }
            } else {
                makeSpan(listOf("mspace", regularClass), listOf(mathsym(group.text, group.mode, options)), options)
            }
        } else if (cssSpaceClass != null) {
            makeSpan(listOf("mspace", cssSpaceClass), emptyList(), options)
        } else {
            throw ParseError("Unknown type of space \"${group.text}\"")
        }
    }

    defineHtmlBuilder<OrdGroupNode> { group, options ->
        if (group.semisimple) {
            makeFragment(buildExpression(group.body, options, false))
        } else {
            makeSpan(listOf("mord"), buildExpression(group.body, options, true), options)
        }
    }
}
