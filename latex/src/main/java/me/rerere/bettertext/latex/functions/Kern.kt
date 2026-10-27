package me.rerere.bettertext.latex.functions

import me.rerere.bettertext.latex.layout.CssColor
import me.rerere.bettertext.latex.layout.RuleBox
import me.rerere.bettertext.latex.layout.calculateSizeAbs
import me.rerere.bettertext.latex.layout.makeGlue
import me.rerere.bettertext.latex.parse.KernNode
import me.rerere.bettertext.latex.parse.RuleNode
import me.rerere.bettertext.latex.parse.SizeNode

/* Ported from KaTeX's kern.ts (horizontal spacing commands) and rule.ts. */

internal fun registerKern() {
    // TODO: \hskip and \mskip should support plus and minus in lengths
    // KaTeX's strict-mode unit warnings are not ported (they only report).
    defineFunction(
        names = listOf("\\kern", "\\mkern", "\\hskip", "\\mskip"),
        numArgs = 1,
        argTypes = listOf(ArgType.SIZE),
        primitive = true,
        allowedInText = true,
    ) { ctx, args, _ ->
        val size = args[0].assertType<SizeNode>()
        KernNode(ctx.parser.mode, size.value)
    }
    defineHtmlBuilder<KernNode> { group, options -> makeGlue(group.dimension, options) }

    defineFunction(
        names = listOf("\\rule"),
        numArgs = 2,
        numOptionalArgs = 1,
        allowedInText = true,
        allowedInMath = true,
        argTypes = listOf(ArgType.SIZE, ArgType.SIZE, ArgType.SIZE),
    ) { ctx, args, optArgs ->
        val shift = optArgs[0]
        val width = args[0].assertType<SizeNode>()
        val height = args[1].assertType<SizeNode>()
        RuleNode(
            ctx.parser.mode,
            shift = shift?.assertType<SizeNode>()?.value,
            width = width.value,
            height = height.value,
        )
    }

    defineHtmlBuilder<RuleNode> { group, options ->
        // Make an empty box for the rule
        val rule = RuleBox(listOf("mord", "katex-rule"))
        if (options.style.isTight()) rule.classes.add("mtight")
        options.getColor()?.let { rule.color = CssColor.parse(it) }

        // Calculate the shift, width, and height of the rule, and account for units
        val width = calculateSizeAbs(group.width, options)
        val height = calculateSizeAbs(group.height, options)
        val shift = group.shift?.let { calculateSizeAbs(it, options) } ?: 0f

        // The rule covers [-height - shift, -shift] around the baseline, i.e. it is
        // raised by `shift` (KaTeX's CSS `bottom`).
        rule.width = width
        rule.height = height + shift
        rule.depth = -shift
        // Font size is the number large enough that the browser will
        // reserve at least `absHeight` space above the baseline.
        // The 1.125 factor was empirically determined
        rule.maxFontSize = height * 1.125f
        rule
    }
}
