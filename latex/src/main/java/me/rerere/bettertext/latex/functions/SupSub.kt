package me.rerere.bettertext.latex.functions

import me.rerere.bettertext.latex.layout.Box
import me.rerere.bettertext.latex.layout.Options
import me.rerere.bettertext.latex.layout.Style
import me.rerere.bettertext.latex.layout.SymbolBox
import me.rerere.bettertext.latex.layout.VListChild
import me.rerere.bettertext.latex.layout.VListPosition
import me.rerere.bettertext.latex.layout.buildGroup
import me.rerere.bettertext.latex.layout.getTypeOfDomTree
import me.rerere.bettertext.latex.layout.makeSpan
import me.rerere.bettertext.latex.layout.makeVList
import me.rerere.bettertext.latex.parse.AccentNode
import me.rerere.bettertext.latex.parse.HorizBraceNode
import me.rerere.bettertext.latex.parse.OpNode
import me.rerere.bettertext.latex.parse.OperatorNameNode
import me.rerere.bettertext.latex.parse.SupSubNode

/**
 * Builders of groups that handle their own super/subscripts (e.g. `\sum` with
 * limits). They are called with the whole [SupSubNode]. Filled in by the
 * respective function files.
 */
internal object SupSubDelegates {
    var op: ((SupSubNode, Options) -> Box)? = null
    var operatorname: ((SupSubNode, Options) -> Box)? = null
    var accent: ((SupSubNode, Options) -> Box)? = null
    var horizBrace: ((SupSubNode, Options) -> Box)? = null
}

private fun htmlBuilderDelegate(group: SupSubNode, options: Options): ((SupSubNode, Options) -> Box)? {
    return when (val base = group.base) {
        null -> null
        is OpNode -> {
            val delegate = base.limits && (options.style.size == Style.DISPLAY.size || base.alwaysHandleSupSub)
            if (delegate) SupSubDelegates.op else null
        }

        is OperatorNameNode -> {
            val delegate = base.alwaysHandleSupSub && (options.style.size == Style.DISPLAY.size || base.limits)
            if (delegate) SupSubDelegates.operatorname else null
        }

        is AccentNode -> if (isCharacterBox(base.base)) SupSubDelegates.accent else null
        is HorizBraceNode -> {
            val isSup = group.sub == null
            if (isSup == base.isOver) SupSubDelegates.horizBrace else null
        }

        else -> null
    }
}

/** supsub.ts: TeXbook rules 18(a-f). */
internal fun registerSupSub() {
    defineHtmlBuilder<SupSubNode> { group, options ->
        htmlBuilderDelegate(group, options)?.let { return@defineHtmlBuilder it(group, options) }

        val valueBase = group.base
        val valueSup = group.sup
        val valueSub = group.sub
        val base = buildGroup(valueBase, options)
        var supm: Box? = null
        var subm: Box? = null
        val metrics = options.fm

        // Rule 18a
        var supShift = 0f
        var subShift = 0f
        val isCharBox = valueBase != null && isCharacterBox(valueBase)
        if (valueSup != null) {
            val newOptions = options.havingStyle(options.style.sup())
            supm = buildGroup(valueSup, newOptions, options)
            if (!isCharBox) supShift = base.height - newOptions.fm.supDrop
        }
        if (valueSub != null) {
            val newOptions = options.havingStyle(options.style.sub())
            subm = buildGroup(valueSub, newOptions, options)
            if (!isCharBox) subShift = base.depth + newOptions.fm.subDrop
        }

        // Rule 18c
        val minSupShift = when {
            options.style === Style.DISPLAY -> metrics.sup1
            options.style.cramped -> metrics.sup3
            else -> metrics.sup2
        }

        // \scriptspace is a font-size-independent 0.5pt.
        val marginRight = 0.5f / options.fontMetrics().ptPerEm
        var marginLeft = 0f
        if (subm != null) {
            // Subscripts shouldn't be shifted by the base's italic correction.
            val baseOp = valueBase as? OpNode
            val isOiint = baseOp?.name == "\\oiint" || baseOp?.name == "\\oiiint"
            if (base is SymbolBox || isOiint) marginLeft = -base.italic
        }

        val supsub = if (supm != null && subm != null) {
            supShift = maxOf(supShift, minSupShift, supm.depth + 0.25f * metrics.xHeight)
            subShift = maxOf(subShift, metrics.sub2)
            val ruleWidth = metrics.defaultRuleThickness
            // Rule 18e
            val maxWidth = 4 * ruleWidth
            if ((supShift - supm.depth) - (subm.height - subShift) < maxWidth) {
                subShift = maxWidth - (supShift - supm.depth) + subm.height
                val psi = 0.8f * metrics.xHeight - (supShift - supm.depth)
                if (psi > 0) {
                    supShift += psi
                    subShift -= psi
                }
            }
            makeVList(
                VListPosition.IndividualShift,
                listOf(
                    VListChild.Elem(subm, marginLeft = marginLeft, marginRight = marginRight, shift = subShift),
                    VListChild.Elem(supm, marginRight = marginRight, shift = -supShift),
                ),
            )
        } else if (subm != null) {
            // Rule 18b
            subShift = maxOf(subShift, metrics.sub1, subm.height - 0.8f * metrics.xHeight)
            makeVList(
                VListPosition.Shift(subShift),
                listOf(VListChild.Elem(subm, marginLeft = marginLeft, marginRight = marginRight)),
            )
        } else if (supm != null) {
            // Rule 18c, d
            supShift = maxOf(supShift, minSupShift, supm.depth + 0.25f * metrics.xHeight)
            makeVList(
                VListPosition.Shift(-supShift),
                listOf(VListChild.Elem(supm, marginRight = marginRight)),
            )
        } else {
            error("supsub must have either sup or sub.")
        }

        val mclass = getTypeOfDomTree(base, "right") ?: "mord"
        makeSpan(listOf(mclass), listOf(base, makeSpan(listOf("msupsub"), listOf(supsub))), options)
    }
}
