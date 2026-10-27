package me.rerere.bettertext.latex.functions

import me.rerere.bettertext.latex.layout.buildExpression
import me.rerere.bettertext.latex.layout.makeSpan
import me.rerere.bettertext.latex.parse.AtomNode
import me.rerere.bettertext.latex.parse.MClassNode
import me.rerere.bettertext.latex.parse.OpNode
import me.rerere.bettertext.latex.parse.OrdGroupNode
import me.rerere.bettertext.latex.parse.ParseNode
import me.rerere.bettertext.latex.parse.SupSubNode
import me.rerere.bettertext.latex.parse.ordArgument

/* Ported from KaTeX's mclass.ts. */

/**
 * \binrel@ spacing varies with (bin|rel|ord) of the atom in the argument.
 * (by rendering separately and with {}s before and after, and measuring
 * the change in spacing).  We'll do roughly the same by detecting the
 * atom type directly.
 */
internal fun binrelClass(arg: ParseNode): String {
    val atom = if (arg is OrdGroupNode && arg.body.isNotEmpty()) arg.body[0] else arg
    return if (atom is AtomNode && (atom.family == "bin" || atom.family == "rel")) {
        "m" + atom.family
    } else {
        "mord"
    }
}

internal fun registerMClass() {
    defineHtmlBuilder<MClassNode> { group, options ->
        val elements = buildExpression(group.body, options, true)
        makeSpan(listOf(group.mclass), elements, options)
    }

    // Math class commands except \mathop
    defineFunction(
        names = listOf(
            "\\mathord", "\\mathbin", "\\mathrel", "\\mathopen",
            "\\mathclose", "\\mathpunct", "\\mathinner",
        ),
        numArgs = 1,
        primitive = true,
    ) { ctx, args, _ ->
        val body = args[0]
        MClassNode(
            ctx.parser.mode,
            mclass = "m" + ctx.funcName.substring(5),
            body = ordArgument(body),
            isCharacterBox = isCharacterBox(body),
        )
    }

    // \@binrel{x}{y} renders like y but as mbin/mrel/mord if x is mbin/mrel/mord.
    // This is equivalent to \binrel@{x}\binrel@@{y} in AMSTeX.
    defineFunction(
        names = listOf("\\@binrel"),
        numArgs = 2,
    ) { ctx, args, _ ->
        MClassNode(
            ctx.parser.mode,
            mclass = binrelClass(args[0]),
            body = ordArgument(args[1]),
            isCharacterBox = isCharacterBox(args[1]),
        )
    }

    // Build a relation or stacked op by placing one symbol on top of another
    defineFunction(
        names = listOf("\\stackrel", "\\overset", "\\underset"),
        numArgs = 2,
    ) { ctx, args, _ ->
        val baseArg = args[1]
        val shiftedArg = args[0]

        val mclass = if (ctx.funcName != "\\stackrel") {
            // LaTeX applies \binrel spacing to \overset and \underset.
            binrelClass(baseArg)
        } else {
            "mrel" // for \stackrel
        }

        val baseOp = OpNode(
            baseArg.mode,
            limits = true,
            alwaysHandleSupSub = true,
            parentIsSupSub = false,
            symbol = false,
            suppressBaseShift = ctx.funcName != "\\stackrel",
            body = ordArgument(baseArg),
        )

        val supsub = if (ctx.funcName == "\\underset") {
            SupSubNode(shiftedArg.mode, base = baseOp, sup = null, sub = shiftedArg)
        } else {
            SupSubNode(shiftedArg.mode, base = baseOp, sup = shiftedArg, sub = null)
        }

        MClassNode(
            ctx.parser.mode,
            mclass = mclass,
            body = listOf(supsub),
            isCharacterBox = isCharacterBox(supsub),
        )
    }
}
