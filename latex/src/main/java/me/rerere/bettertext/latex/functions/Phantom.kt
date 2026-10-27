package me.rerere.bettertext.latex.functions

import me.rerere.bettertext.latex.layout.Box
import me.rerere.bettertext.latex.layout.Options
import me.rerere.bettertext.latex.layout.SpanBox
import me.rerere.bettertext.latex.layout.Style
import me.rerere.bettertext.latex.layout.VListChild
import me.rerere.bettertext.latex.layout.VListPosition
import me.rerere.bettertext.latex.layout.buildExpression
import me.rerere.bettertext.latex.layout.buildGroup
import me.rerere.bettertext.latex.layout.calculateSizeAbs
import me.rerere.bettertext.latex.layout.makeFragment
import me.rerere.bettertext.latex.layout.makeSpan
import me.rerere.bettertext.latex.layout.makeVList
import me.rerere.bettertext.latex.parse.LapNode
import me.rerere.bettertext.latex.parse.MathChoiceNode
import me.rerere.bettertext.latex.parse.OrdGroupNode
import me.rerere.bettertext.latex.parse.ParseNode
import me.rerere.bettertext.latex.parse.PhantomNode
import me.rerere.bettertext.latex.parse.RaiseBoxNode
import me.rerere.bettertext.latex.parse.SizeNode
import me.rerere.bettertext.latex.parse.SmashNode
import me.rerere.bettertext.latex.parse.SymbolParseNode
import me.rerere.bettertext.latex.parse.VCenterNode
import me.rerere.bettertext.latex.parse.VPhantomNode
import me.rerere.bettertext.latex.parse.ordArgument

/* Ported from KaTeX's phantom.ts, lap.ts, smash.ts, raisebox.ts, vcenter.ts and mathchoice.ts. */

/**
 * CSS `.llap/.rlap/.clap { width: 0 }` with an absolutely positioned
 * `.katex-inner`: give the inner box negative margins so it takes no
 * horizontal space but is drawn left of (llap), right of (rlap) or centered
 * on (clap) the current position.
 */
private fun makeLapInner(inner: Box, alignment: String) {
    val w = inner.width
    when (alignment) {
        "llap" -> inner.marginLeft = -w
        "clap" -> {
            inner.marginLeft = -w / 2
            inner.marginRight = -w / 2
        }

        else -> inner.marginRight = -w
    }
}

private fun chooseMathStyle(group: MathChoiceNode, options: Options): List<ParseNode> =
    when (options.style.size) {
        Style.DISPLAY.size -> group.display
        Style.TEXT.size -> group.text
        Style.SCRIPT.size -> group.script
        Style.SCRIPTSCRIPT.size -> group.scriptscript
        else -> group.text
    }

internal fun registerPhantom() {
    // phantom.ts
    defineFunction(
        names = listOf("\\phantom"),
        numArgs = 1,
        allowedInText = true,
    ) { ctx, args, _ ->
        PhantomNode(ctx.parser.mode, ordArgument(args[0]))
    }
    defineHtmlBuilder<PhantomNode> { group, options ->
        val elements = buildExpression(group.body, options.withPhantom(), false)
        // \phantom isn't supposed to affect the elements it contains.
        // See "color" for more details.
        makeFragment(elements)
    }
    // \hphantom is a macro: \smash{\phantom{#1}}

    defineFunction(
        names = listOf("\\vphantom"),
        numArgs = 1,
        allowedInText = true,
    ) { ctx, args, _ ->
        VPhantomNode(ctx.parser.mode, args[0])
    }
    defineHtmlBuilder<VPhantomNode> { group, options ->
        val inner = makeSpan(listOf("katex-inner"), listOf(buildGroup(group.body, options.withPhantom())))
        makeLapInner(inner, "rlap")
        val fix = makeSpan(listOf("katex-fix"), emptyList())
        makeSpan(listOf("mord", "rlap"), listOf(inner, fix), options)
    }

    // lap.ts: horizontal overlap functions
    defineFunction(
        names = listOf("\\mathllap", "\\mathrlap", "\\mathclap"),
        numArgs = 1,
        allowedInText = true,
    ) { ctx, args, _ ->
        LapNode(ctx.parser.mode, alignment = ctx.funcName.substring(5), body = args[0])
    }
    defineHtmlBuilder<LapNode> { group, options ->
        // mathllap, mathrlap, mathclap
        val inner: SpanBox = if (group.alignment == "clap") {
            // ref: https://www.math.lsu.edu/~aperlis/publications/mathclap/
            val span = makeSpan(emptyList(), listOf(buildGroup(group.body, options)))
            // wrap, since CSS will center a .clap > .katex-inner > span
            makeSpan(listOf("katex-inner"), listOf(span), options)
        } else {
            makeSpan(listOf("katex-inner"), listOf(buildGroup(group.body, options)))
        }
        makeLapInner(inner, group.alignment)
        val fix = makeSpan(listOf("katex-fix"), emptyList())
        var node = makeSpan(listOf(group.alignment), listOf(inner, fix), options)

        // At this point, we have correctly set horizontal alignment of the
        // two items involved in the lap.
        // Next, use a strut to set the height of the HTML bounding box.
        // Otherwise, a tall argument may be misplaced.
        val strut = makeSpan(listOf("katex-strut"))
        strut.height = node.height
        strut.depth = node.depth
        node.children.add(0, strut)

        // Next, prevent vertical misplacement when next to something tall.
        node = makeSpan(listOf("katex-thinbox"), listOf(node), options)
        makeSpan(listOf("mord", "katex-vbox"), listOf(node), options)
    }

    // smash.ts: smash, with optional [tb], as in AMS
    defineFunction(
        names = listOf("\\smash"),
        numArgs = 1,
        numOptionalArgs = 1,
        allowedInText = true,
    ) { ctx, args, optArgs ->
        var smashHeight = false
        var smashDepth = false
        val tbArg = optArgs[0]?.assertType<OrdGroupNode>()
        if (tbArg != null) {
            // Optional [tb] argument is engaged.
            // ref: amsmath: \renewcommand{\smash}[1][tb]{%
            //               def\mb@t{\ht}\def\mb@b{\dp}\def\mb@tb{\ht\z@\z@\dp}%
            for (node in tbArg.body) {
                val letter = (node as? SymbolParseNode)?.text
                    ?: throw IllegalStateException(
                        "Expected node of symbol group type, but got node of type ${node::class.simpleName}",
                    )
                if (letter == "t") {
                    smashHeight = true
                } else if (letter == "b") {
                    smashDepth = true
                } else {
                    smashHeight = false
                    smashDepth = false
                    break
                }
            }
        } else {
            smashHeight = true
            smashDepth = true
        }
        SmashNode(ctx.parser.mode, args[0], smashHeight, smashDepth)
    }
    defineHtmlBuilder<SmashNode> { group, options ->
        val node = makeSpan(emptyList(), listOf(buildGroup(group.body, options)))
        if (!group.smashHeight && !group.smashDepth) return@defineHtmlBuilder node
        if (group.smashHeight) node.height = 0f
        if (group.smashDepth) node.depth = 0f
        if (group.smashHeight && group.smashDepth) {
            // Symmetric \smash can stay in inline layout.
            return@defineHtmlBuilder makeSpan(listOf("mord", "katex-smash"), listOf(node), options)
        }

        // In order to influence makeVList for asymmetric smashing, we have to
        // reset the children.
        for (child in node.children) {
            if (group.smashHeight) child.height = 0f
            if (group.smashDepth) child.depth = 0f
        }
        val smashedNode = makeVList(VListPosition.FirstBaseline, listOf(VListChild.Elem(node)))
        // For spacing, TeX treats \smash as a math group (same spacing as ord).
        makeSpan(listOf("mord"), listOf(smashedNode), options)
    }

    // raisebox.ts: box manipulation
    defineFunction(
        names = listOf("\\raisebox"),
        numArgs = 2,
        argTypes = listOf(ArgType.SIZE, ArgType.HBOX),
        allowedInText = true,
    ) { ctx, args, _ ->
        val amount = args[0].assertType<SizeNode>().value
        RaiseBoxNode(ctx.parser.mode, dy = amount, body = args[1])
    }
    defineHtmlBuilder<RaiseBoxNode> { group, options ->
        val body = buildGroup(group.body, options)
        val dy = calculateSizeAbs(group.dy, options)
        makeVList(VListPosition.Shift(-dy), listOf(VListChild.Elem(body)))
    }

    // vcenter.ts: vertically center the argument group on the math axis.
    defineFunction(
        names = listOf("\\vcenter"),
        numArgs = 1,
        argTypes = listOf(ArgType.ORIGINAL), // In LaTeX, \vcenter can act only on a box.
        allowedInText = false,
    ) { ctx, args, _ ->
        VCenterNode(ctx.parser.mode, args[0])
    }
    defineHtmlBuilder<VCenterNode> { group, options ->
        val body = buildGroup(group.body, options)
        val axisHeight = options.fm.axisHeight
        val dy = 0.5f * ((body.height - axisHeight) - (body.depth + axisHeight))
        makeVList(VListPosition.Shift(dy), listOf(VListChild.Elem(body)))
    }

    // mathchoice.ts
    defineFunction(
        names = listOf("\\mathchoice"),
        numArgs = 4,
        primitive = true,
    ) { ctx, args, _ ->
        MathChoiceNode(
            ctx.parser.mode,
            display = ordArgument(args[0]),
            text = ordArgument(args[1]),
            script = ordArgument(args[2]),
            scriptscript = ordArgument(args[3]),
        )
    }
    defineHtmlBuilder<MathChoiceNode> { group, options ->
        val body = chooseMathStyle(group, options)
        makeFragment(buildExpression(body, options, false))
    }
}
