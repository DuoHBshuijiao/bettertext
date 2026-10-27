package me.rerere.bettertext.latex.functions

import me.rerere.bettertext.latex.layout.Box
import me.rerere.bettertext.latex.layout.HAlign
import me.rerere.bettertext.latex.layout.Options
import me.rerere.bettertext.latex.layout.Style
import me.rerere.bettertext.latex.layout.VListBox
import me.rerere.bettertext.latex.layout.VListChild
import me.rerere.bettertext.latex.layout.VListPosition
import me.rerere.bettertext.latex.layout.applyStretchyMinWidth
import me.rerere.bettertext.latex.layout.buildGroup
import me.rerere.bettertext.latex.layout.makeSpan
import me.rerere.bettertext.latex.layout.makeVList
import me.rerere.bettertext.latex.layout.stretchySvg
import me.rerere.bettertext.latex.parse.HorizBraceNode
import me.rerere.bettertext.latex.parse.ParseNode
import me.rerere.bettertext.latex.parse.SupSubNode

/* Ported from KaTeX's functions/horizBrace.ts. */

/**
 * NOTE: Unlike most builders, this one handles not only "horizBrace", but
 * also "supsub" since an over/underbrace can affect super/subscripting.
 */
private fun horizBraceHtmlBuilder(grp: ParseNode, options: Options): Box {
    val style = options.style

    // Pull out the HorizBraceNode if grp is a "supsub" node.
    var supSubGroup: Box? = null
    val group: HorizBraceNode
    if (grp is SupSubNode) {
        // Ref: LaTeX source2e: }}}}\limits}
        // i.e. LaTeX treats the brace similar to an op and passes it
        // with \limits, so we need to assign supsub style.
        supSubGroup = if (grp.sup != null) {
            buildGroup(grp.sup, options.havingStyle(style.sup()), options)
        } else {
            buildGroup(grp.sub, options.havingStyle(style.sub()), options)
        }
        group = grp.base.assertType<HorizBraceNode>()
    } else {
        group = grp.assertType<HorizBraceNode>()
    }

    // Build the base group
    val body = buildGroup(group.base, options.havingBaseStyle(Style.DISPLAY))

    // Create the stretchy element
    val braceBody = stretchySvg(group, options)
    val kern1 = options.em(0.1f)

    // Generate the vlist, with the appropriate kerns        ┏━━━━━━━━┓
    // This first vlist contains the content and the brace:   equation
    var vlist: VListBox = if (group.isOver) {
        makeVList(
            VListPosition.FirstBaseline,
            listOf(
                VListChild.Elem(body),
                VListChild.Kern(kern1),
                VListChild.Elem(braceBody, align = HAlign.LEFT),
            ),
            HAlign.CENTER,
        )
    } else {
        makeVList(
            VListPosition.Bottom(body.depth + kern1 + braceBody.height),
            listOf(
                VListChild.Elem(braceBody, align = HAlign.LEFT),
                VListChild.Kern(kern1),
                VListChild.Elem(body),
            ),
            HAlign.CENTER,
        )
    }
    applyStretchyMinWidth(vlist, braceBody)

    val mclass = if (group.isOver) "mover" else "munder"
    if (supSubGroup != null) {
        // To write the supsub, wrap the first vlist in another vlist:
        // They can't all go in the same vlist, because the note might be
        // wider than the equation. We want the equation to control the
        // brace width.

        //      note          long note           long note
        //   ┏━━━━━━━━┓   or    ┏━━━┓     not    ┏━━━━━━━━━┓
        //    equation           eqn                 eqn

        val vSpan = makeSpan(listOf("minner", mclass), listOf(vlist), options)
        val kern2 = options.em(0.2f)
        vlist = if (group.isOver) {
            makeVList(
                VListPosition.FirstBaseline,
                listOf(
                    VListChild.Elem(vSpan),
                    VListChild.Kern(kern2),
                    VListChild.Elem(supSubGroup),
                ),
                HAlign.CENTER,
            )
        } else {
            makeVList(
                VListPosition.Bottom(vSpan.depth + kern2 + supSubGroup.height + supSubGroup.depth),
                listOf(
                    VListChild.Elem(supSubGroup),
                    VListChild.Kern(kern2),
                    VListChild.Elem(vSpan),
                ),
                HAlign.CENTER,
            )
        }
    }

    return makeSpan(listOf("minner", mclass), listOf(vlist), options)
}

internal fun registerHorizBrace() {
    // Horizontal stretchy braces
    defineFunction(
        names = listOf("\\overbrace", "\\underbrace", "\\overbracket", "\\underbracket"),
        numArgs = 1,
    ) { ctx, args, _ ->
        HorizBraceNode(ctx.parser.mode, ctx.funcName, ctx.funcName.contains("\\over"), args[0])
    }
    defineHtmlBuilder<HorizBraceNode> { group, options -> horizBraceHtmlBuilder(group, options) }
    SupSubDelegates.horizBrace = { group, options -> horizBraceHtmlBuilder(group, options) }
}
