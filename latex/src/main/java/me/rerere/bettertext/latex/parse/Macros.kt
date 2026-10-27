package me.rerere.bettertext.latex.parse

import me.rerere.bettertext.latex.ParseError
import me.rerere.bettertext.latex.contrib.MhchemException
import me.rerere.bettertext.latex.contrib.chemParse
import me.rerere.bettertext.latex.data.MacrosData

/** KaTeX's built-in macros: the generated string macros plus the ones implemented as functions. */
internal object BuiltinMacros {
    val all: Map<String, MacroDefinition> by lazy { build() }

    private fun build(): Map<String, MacroDefinition> {
        val map = HashMap<String, MacroDefinition>(512)
        for (record in MacrosData.raw.split('\u0002')) {
            val idx = record.indexOf('\u0001')
            map[record.substring(0, idx)] = MacroDefinition.Text(record.substring(idx + 1))
        }
        fun def(name: String, fn: (MacroExpander) -> MacroDefinition) {
            map[name] = MacroDefinition.Function(fn)
        }

        def("\\noexpand") { ctx ->
            val t = ctx.popToken()
            if (ctx.isExpandable(t.text)) {
                t.noexpand = true
                t.treatAsRelax = true
            }
            MacroDefinition.Expansion(listOf(t), 0)
        }
        def("\\expandafter") { ctx ->
            val t = ctx.popToken()
            ctx.expandOnce(true)
            MacroDefinition.Expansion(listOf(t), 0)
        }
        def("\\@firstoftwo") { ctx -> MacroDefinition.Expansion(ctx.consumeArgs(2)[0], 0) }
        def("\\@secondoftwo") { ctx -> MacroDefinition.Expansion(ctx.consumeArgs(2)[1], 0) }
        def("\\@ifnextchar") { ctx ->
            val args = ctx.consumeArgs(3)
            ctx.consumeSpaces()
            val next = ctx.future()
            if (args[0].size == 1 && args[0][0].text == next.text) {
                MacroDefinition.Expansion(args[1], 0)
            } else {
                MacroDefinition.Expansion(args[2], 0)
            }
        }
        def("\\TextOrMath") { ctx ->
            val args = ctx.consumeArgs(2)
            MacroDefinition.Expansion(if (ctx.mode == Mode.TEXT) args[0] else args[1], 0)
        }
        def("\\char") { ctx -> MacroDefinition.Text("\\@char{${parseCharNumber(ctx)}}") }
        def("\\newcommand") { ctx -> newcommand(ctx, existsOK = false, nonexistsOK = true, skipIfExists = false) }
        def("\\renewcommand") { ctx -> newcommand(ctx, existsOK = true, nonexistsOK = false, skipIfExists = false) }
        def("\\providecommand") { ctx -> newcommand(ctx, existsOK = true, nonexistsOK = true, skipIfExists = true) }
        def("\\message") { ctx ->
            ctx.consumeArgs(1)
            MacroDefinition.Text("")
        }
        def("\\errmessage") { ctx ->
            ctx.consumeArgs(1)
            MacroDefinition.Text("")
        }
        def("\\show") { ctx ->
            ctx.popToken()
            MacroDefinition.Text("")
        }
        def("\\dots") { ctx ->
            var thedots = "\\dotso"
            val next = ctx.expandAfterFuture().text
            val byToken = dotsByToken[next]
            if (byToken != null) {
                thedots = byToken
            } else if (next.startsWith("\\not")) {
                thedots = "\\dotsb"
            } else {
                val sym = Symbols.math[next]
                if (sym != null && (sym.group == "bin" || sym.group == "rel")) thedots = "\\dotsb"
            }
            MacroDefinition.Text(thedots)
        }
        def("\\dotso") { ctx ->
            MacroDefinition.Text(if (ctx.future().text in spaceAfterDots) "\\ldots\\," else "\\ldots")
        }
        def("\\dotsc") { ctx ->
            val next = ctx.future().text
            MacroDefinition.Text(if (next in spaceAfterDots && next != ",") "\\ldots\\," else "\\ldots")
        }
        def("\\cdots") { ctx ->
            MacroDefinition.Text(if (ctx.future().text in spaceAfterDots) "\\@cdots\\," else "\\@cdots")
        }
        def("\\tag@literal") { ctx ->
            if (ctx.macros.get("\\df@tag") != null) throw ParseError("Multiple \\tag")
            MacroDefinition.Text("\\gdef\\df@tag{\\text{#1}}")
        }
        def("\\bra@ket", braketHelper(false))
        def("\\bra@set", braketHelper(true))

        // contrib/mhchem
        def("\\ce") { ctx -> mhchem(ctx, "ce") }
        def("\\pu") { ctx -> mhchem(ctx, "pu") }
        // Needed for \bond for the ~ forms. Raise by 2.56mu, not 2mu: we're raising a
        // hyphen-minus, U+002D, not a mathematical minus, U+2212, so we need that extra 0.56.
        map["\\tripledash"] = MacroDefinition.Text(
            "{\\vphantom{-}\\raisebox{2.56mu}{\$\\mkern2mu" +
                "\\tiny\\text{-}\\mkern1mu\\text{-}\\mkern1mu\\text{-}\\mkern2mu\$}}",
        )
        return map
    }

    /** `\ce` and `\pu`: converts the argument to TeX; mhchem errors become parse errors. */
    private fun mhchem(ctx: MacroExpander, stateMachine: String): MacroDefinition {
        val tokens = ctx.consumeArgs(1)[0]
        val tex = try {
            chemParse(tokens, stateMachine)
        } catch (e: MhchemException) {
            throw ParseError(e.message ?: e.name, tokens.lastOrNull()?.loc?.start ?: -1)
        }
        return MacroDefinition.Text(tex)
    }

    private val digitToNumber: Map<String, Int> = buildMap {
        for (i in 0..9) put(i.toString(), i)
        for ((i, c) in "abcdef".withIndex()) {
            put(c.toString(), 10 + i)
            put(c.uppercaseChar().toString(), 10 + i)
        }
    }

    private fun parseCharNumber(ctx: MacroExpander): Int {
        var token = ctx.popToken()
        var base = 0
        var number = 0
        when (token.text) {
            "'" -> {
                base = 8
                token = ctx.popToken()
            }

            "\"" -> {
                base = 16
                token = ctx.popToken()
            }

            "`" -> {
                token = ctx.popToken()
                number = when {
                    token.text.startsWith("\\") -> token.text[1].code
                    token.text == "EOF" -> throw ParseError("\\char` missing argument")
                    else -> token.text[0].code
                }
            }

            else -> base = 10
        }
        if (base != 0) {
            number = digitToNumber[token.text]?.takeIf { it < base }
                ?: throw ParseError("Invalid base-$base digit ${token.text}")
            while (true) {
                val digit = digitToNumber[ctx.future().text] ?: break
                if (digit >= base) break
                number = number * base + digit
                ctx.popToken()
            }
        }
        return number
    }

    private fun newcommand(
        ctx: MacroExpander,
        existsOK: Boolean,
        nonexistsOK: Boolean,
        skipIfExists: Boolean,
    ): MacroDefinition {
        var arg = ctx.consumeArg().tokens
        if (arg.size != 1) throw ParseError("\\newcommand's first argument must be a macro name")
        val name = arg[0].text
        val exists = ctx.isDefined(name)
        if (exists && !existsOK) {
            throw ParseError("\\newcommand{$name} attempting to redefine $name; use \\renewcommand")
        }
        if (!exists && !nonexistsOK) {
            throw ParseError("\\renewcommand{$name} when command $name does not yet exist; use \\newcommand")
        }
        var numArgs = 0
        arg = ctx.consumeArg().tokens
        if (arg.size == 1 && arg[0].text == "[") {
            val argText = StringBuilder()
            var token = ctx.expandNextToken()
            while (token.text != "]" && token.text != "EOF") {
                argText.append(token.text)
                token = ctx.expandNextToken()
            }
            numArgs = argText.trim().toString().toIntOrNull()
                ?: throw ParseError("Invalid number of arguments: $argText")
            arg = ctx.consumeArg().tokens
        }
        if (!(exists && skipIfExists)) {
            ctx.macros.set(name, MacroDefinition.Expansion(arg, numArgs))
        }
        return MacroDefinition.Text("")
    }

    private fun braketHelper(one: Boolean): (MacroExpander) -> MacroDefinition = { ctx ->
        val left = ctx.consumeArg().tokens
        val middle = ctx.consumeArg().tokens
        val middleDouble = ctx.consumeArg().tokens
        val right = ctx.consumeArg().tokens
        val oldMiddle = ctx.macros.get("|")
        val oldMiddleDouble = ctx.macros.get("\\|")
        ctx.macros.beginGroup()
        fun midMacro(double: Boolean) = MacroDefinition.Function { c ->
            if (one) {
                c.macros.set("|", oldMiddle)
                if (middleDouble.isNotEmpty()) c.macros.set("\\|", oldMiddleDouble)
            }
            var doubled = double
            if (!double && middleDouble.isNotEmpty()) {
                if (c.future().text == "|") {
                    c.popToken()
                    doubled = true
                }
            }
            MacroDefinition.Expansion(if (doubled) middleDouble else middle, 0)
        }
        ctx.macros.set("|", midMacro(false))
        if (middleDouble.isNotEmpty()) ctx.macros.set("\\|", midMacro(true))
        val arg = ctx.consumeArg().tokens
        val expanded = ctx.expandTokens(right + arg + left)
        ctx.macros.endGroup()
        MacroDefinition.Expansion(expanded.reversed(), 0)
    }

    private val dotsByToken = mapOf(
        "," to "\\dotsc", "\\not" to "\\dotsb",
        "+" to "\\dotsb", "=" to "\\dotsb", "<" to "\\dotsb", ">" to "\\dotsb",
        "-" to "\\dotsb", "*" to "\\dotsb", ":" to "\\dotsb",
        "\\DOTSB" to "\\dotsb", "\\coprod" to "\\dotsb", "\\bigvee" to "\\dotsb",
        "\\bigwedge" to "\\dotsb", "\\biguplus" to "\\dotsb", "\\bigcap" to "\\dotsb",
        "\\bigcup" to "\\dotsb", "\\prod" to "\\dotsb", "\\sum" to "\\dotsb",
        "\\bigotimes" to "\\dotsb", "\\bigoplus" to "\\dotsb", "\\bigodot" to "\\dotsb",
        "\\bigsqcup" to "\\dotsb", "\\And" to "\\dotsb", "\\longrightarrow" to "\\dotsb",
        "\\Longrightarrow" to "\\dotsb", "\\longleftarrow" to "\\dotsb",
        "\\Longleftarrow" to "\\dotsb", "\\longleftrightarrow" to "\\dotsb",
        "\\Longleftrightarrow" to "\\dotsb", "\\mapsto" to "\\dotsb",
        "\\longmapsto" to "\\dotsb", "\\hookrightarrow" to "\\dotsb", "\\doteq" to "\\dotsb",
        "\\mathbin" to "\\dotsb", "\\mathrel" to "\\dotsb", "\\relbar" to "\\dotsb",
        "\\Relbar" to "\\dotsb", "\\xrightarrow" to "\\dotsb", "\\xleftarrow" to "\\dotsb",
        "\\DOTSI" to "\\dotsi", "\\int" to "\\dotsi", "\\oint" to "\\dotsi",
        "\\iint" to "\\dotsi", "\\iiint" to "\\dotsi", "\\iiiint" to "\\dotsi",
        "\\idotsint" to "\\dotsi", "\\DOTSX" to "\\dotsx",
    )

    private val spaceAfterDots = setOf(
        ")", "]", "\\rbrack", "\\}", "\\rbrace", "\\rangle", "\\rceil", "\\rfloor",
        "\\rgroup", "\\rmoustache", "\\right", "\\bigr", "\\biggr", "\\Bigr", "\\Biggr",
        "$", ";", ".", ",",
    )
}
