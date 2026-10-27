package me.rerere.bettertext.latex.functions

import me.rerere.bettertext.latex.layout.Box
import me.rerere.bettertext.latex.layout.Options
import me.rerere.bettertext.latex.layout.SpanBox
import me.rerere.bettertext.latex.layout.Style
import me.rerere.bettertext.latex.layout.VListChild
import me.rerere.bettertext.latex.layout.VListPosition
import me.rerere.bettertext.latex.layout.HAlign
import me.rerere.bettertext.latex.layout.buildGroup
import me.rerere.bettertext.latex.layout.makeSpan
import me.rerere.bettertext.latex.layout.makeVList
import me.rerere.bettertext.latex.parse.AtomNode
import me.rerere.bettertext.latex.parse.ColorNode
import me.rerere.bettertext.latex.parse.FontNode
import me.rerere.bettertext.latex.parse.MathOrdNode
import me.rerere.bettertext.latex.parse.OrdGroupNode
import me.rerere.bettertext.latex.parse.ParseNode
import me.rerere.bettertext.latex.parse.TextOrdNode

/** Unwraps single-child ordgroups, colors and fonts to find the base element. */
internal fun getBaseElem(group: ParseNode): ParseNode = when (group) {
    is OrdGroupNode -> if (group.body.size == 1) getBaseElem(group.body[0]) else group
    is ColorNode -> if (group.body.size == 1) getBaseElem(group.body[0]) else group
    is FontNode -> getBaseElem(group.body)
    else -> group
}

/** Whether the group is a single character (mathord, textord or atom). */
internal fun isCharacterBox(group: ParseNode): Boolean {
    val base = getBaseElem(group)
    return base is MathOrdNode || base is TextOrdNode || base is AtomNode
}

/** Places limits above/below a big operator. Ported from assembleSupSub.ts. */
internal fun assembleSupSub(
    baseIn: Box,
    supGroup: ParseNode?,
    subGroup: ParseNode?,
    options: Options,
    style: Style,
    slant: Float,
    baseShift: Float,
): SpanBox {
    val base = makeSpan(emptyList(), listOf(baseIn))
    val subIsSingleCharacter = subGroup != null && isCharacterBox(subGroup)
    val fm = options.fm

    var sup: Pair<Box, Float>? = null
    var sub: Pair<Box, Float>? = null
    if (supGroup != null) {
        val elem = buildGroup(supGroup, options.havingStyle(style.sup()), options)
        sup = elem to maxOf(fm.bigOpSpacing1, fm.bigOpSpacing3 - elem.depth)
    }
    if (subGroup != null) {
        val elem = buildGroup(subGroup, options.havingStyle(style.sub()), options)
        sub = elem to maxOf(fm.bigOpSpacing2, fm.bigOpSpacing4 - elem.height)
    }

    val finalGroup = if (sup != null && sub != null) {
        val bottom = fm.bigOpSpacing5 + sub.first.height + sub.first.depth + sub.second + base.depth + baseShift
        makeVList(
            VListPosition.Bottom(bottom),
            listOf(
                VListChild.Kern(fm.bigOpSpacing5),
                VListChild.Elem(sub.first, marginLeft = -slant),
                VListChild.Kern(sub.second),
                VListChild.Elem(base),
                VListChild.Kern(sup.second),
                VListChild.Elem(sup.first, marginLeft = slant),
                VListChild.Kern(fm.bigOpSpacing5),
            ),
            HAlign.CENTER,
        )
    } else if (sub != null) {
        val top = base.height - baseShift
        makeVList(
            VListPosition.Top(top),
            listOf(
                VListChild.Kern(fm.bigOpSpacing5),
                VListChild.Elem(sub.first, marginLeft = -slant),
                VListChild.Kern(sub.second),
                VListChild.Elem(base),
            ),
            HAlign.CENTER,
        )
    } else if (sup != null) {
        val bottom = base.depth + baseShift
        makeVList(
            VListPosition.Bottom(bottom),
            listOf(
                VListChild.Elem(base),
                VListChild.Kern(sup.second),
                VListChild.Elem(sup.first, marginLeft = slant),
                VListChild.Kern(fm.bigOpSpacing5),
            ),
            HAlign.CENTER,
        )
    } else {
        return base
    }

    val parts = arrayListOf<Box>(finalGroup)
    if (sub != null && slant != 0f && !subIsSingleCharacter) {
        // A negative margin-left was applied to the lower limit.
        // Avoid an overlap by placing a spacer on the left of the group.
        val spacer = makeSpan(listOf("mspace"), emptyList(), options)
        spacer.marginRight = slant
        parts.add(0, spacer)
    }
    return makeSpan(listOf("mop", "op-limits"), parts, options)
}
