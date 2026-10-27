package me.rerere.bettertext.latex.functions

import me.rerere.bettertext.latex.ParseError
import me.rerere.bettertext.latex.layout.Box
import me.rerere.bettertext.latex.layout.buildExpression
import me.rerere.bettertext.latex.layout.buildGroup
import me.rerere.bettertext.latex.layout.makeAnchor
import me.rerere.bettertext.latex.layout.makeFragment
import me.rerere.bettertext.latex.layout.makeSpan
import me.rerere.bettertext.latex.layout.makeSymbol
import me.rerere.bettertext.latex.parse.HBoxNode
import me.rerere.bettertext.latex.parse.HrefNode
import me.rerere.bettertext.latex.parse.HtmlMathmlNode
import me.rerere.bettertext.latex.parse.HtmlNode
import me.rerere.bettertext.latex.parse.InternalNode
import me.rerere.bettertext.latex.parse.MacroDefinition
import me.rerere.bettertext.latex.parse.Mode
import me.rerere.bettertext.latex.parse.OrdGroupNode
import me.rerere.bettertext.latex.parse.ParseNode
import me.rerere.bettertext.latex.parse.Parser
import me.rerere.bettertext.latex.parse.PmbNode
import me.rerere.bettertext.latex.parse.RawNode
import me.rerere.bettertext.latex.parse.ReflectBoxNode
import me.rerere.bettertext.latex.parse.SpacingNode
import me.rerere.bettertext.latex.parse.StylingNode
import me.rerere.bettertext.latex.parse.TextNode
import me.rerere.bettertext.latex.parse.TextOrdNode
import me.rerere.bettertext.latex.parse.Token
import me.rerere.bettertext.latex.parse.UrlNode
import me.rerere.bettertext.latex.parse.VerbNode
import me.rerere.bettertext.latex.parse.ordArgument

/*
 * Ported from KaTeX's verb.ts, hbox.ts, pmb.ts, href.ts, html.ts,
 * htmlmathml.ts, char.ts, relax.ts, def.ts, math.ts and reflectbox.ts.
 * tag.ts only has a MathML builder; the HTML side lives in buildHTML.
 */

internal fun registerMisc() {
    registerVerb()
    registerHBox()
    registerPmb()
    registerHref()
    registerHtml()
    registerHtmlMathml()
    registerChar()
    registerRelax()
    registerDef()
    registerMath()
    registerReflectBox()
}

// ---------------------------------------------------------------- verb.ts

/**
 * Converts verb group into body string.
 *
 * \verb* replaces each space with an open box ␣
 * \verb replaces each space with a no-break space \xA0
 */
private fun makeVerb(group: VerbNode): String =
    group.body.replace(" ", if (group.star) "␣" else " ")

private fun registerVerb() {
    defineFunction(
        names = listOf("\\verb"),
        numArgs = 0,
        allowedInText = true,
    ) { _, _, _ ->
        // \verb and \verb* are dealt with directly in Parser.
        // If we end up here, it's because of a failure to match the two delimiters
        // in the regex in Lexer.  LaTeX raises the following error when \verb is
        // terminated by end of line (or file).
        throw ParseError("\\verb ended by end of line instead of matching delimiter")
    }
    defineHtmlBuilder<VerbNode> { group, options ->
        val text = makeVerb(group)
        val body = ArrayList<Box>()
        // \verb enters text mode and therefore is sized like \textstyle
        val newOptions = options.havingStyle(options.style.text())
        for (ch in text) {
            var c = ch.toString()
            if (c == "~") c = "\\textasciitilde"
            body.add(makeSymbol(c, "Typewriter-Regular", group.mode, newOptions, listOf("mord", "texttt")))
        }
        makeSpan(listOf("mord", "text"), body, newOptions)
    }
}

// ---------------------------------------------------------------- hbox.ts

/**
 * \hbox is provided for compatibility with LaTeX \vcenter.
 * This function by itself doesn't do anything but prevent a soft line break.
 */
private fun registerHBox() {
    defineFunction(
        names = listOf("\\hbox"),
        numArgs = 1,
        argTypes = listOf(ArgType.TEXT),
        allowedInText = true,
        primitive = true,
    ) { ctx, args, _ ->
        HBoxNode(ctx.parser.mode, ordArgument(args[0]))
    }
    defineHtmlBuilder<HBoxNode> { group, options ->
        makeFragment(buildExpression(group.body, options.withFont(""), false))
    }
}

// ---------------------------------------------------------------- pmb.ts

/**
 * \pmb is a simulation of bold font. KaTeX uses CSS
 * `text-shadow: 0.02em 0.01em 0.04px`; we draw a second copy of the body
 * offset by (0.02em, 0.01em) behind it.
 */
private fun registerPmb() {
    defineFunction(
        names = listOf("\\pmb"),
        numArgs = 1,
        allowedInText = true,
    ) { ctx, args, _ ->
        PmbNode(ctx.parser.mode, mclass = binrelClass(args[0]), body = ordArgument(args[0]))
    }
    defineHtmlBuilder<PmbNode> { group, options ->
        val elements = buildExpression(group.body, options, true)
        val main = makeSpan(emptyList(), elements)
        val shadow = makeSpan(emptyList(), buildExpression(group.body, options, true))
        val dx = options.em(0.02f)
        shadow.marginLeft = dx
        shadow.marginRight = -(dx + shadow.width)
        shadow.shiftY = options.em(0.01f)
        makeSpan(listOf(group.mclass), listOf(shadow, main), options)
    }
}

// ---------------------------------------------------------------- href.ts

private fun registerHref() {
    // Trust checks are not ported: links are always rendered.
    defineFunction(
        names = listOf("\\href"),
        numArgs = 2,
        argTypes = listOf(ArgType.URL, ArgType.ORIGINAL),
        allowedInText = true,
    ) { ctx, args, _ ->
        val href = args[0].assertType<UrlNode>().url
        HrefNode(ctx.parser.mode, href, ordArgument(args[1]))
    }

    defineFunction(
        names = listOf("\\url"),
        numArgs = 1,
        argTypes = listOf(ArgType.URL),
        allowedInText = true,
    ) { ctx, args, _ ->
        val href = args[0].assertType<UrlNode>().url
        val chars = href.map { ch ->
            val c = if (ch == '~') "\\textasciitilde" else ch.toString()
            TextOrdNode(Mode.TEXT, c)
        }
        val body = TextNode(ctx.parser.mode, chars, font = "\\texttt")
        HrefNode(ctx.parser.mode, href, ordArgument(body))
    }

    defineHtmlBuilder<HrefNode> { group, options ->
        val elements = buildExpression(group.body, options, false)
        makeAnchor(group.href, emptyList(), elements, options)
    }
}

// ---------------------------------------------------------------- html.ts

private const val ESCAPED_COMMA = "{,}"
private val whitespace = Regex("\\s+")

private fun registerHtml() {
    // Trust checks and strict-mode warnings are not ported. Attributes other
    // than the class are parsed but have no effect on the layout.
    defineFunction(
        names = listOf("\\htmlClass", "\\htmlId", "\\htmlStyle", "\\htmlData"),
        numArgs = 2,
        argTypes = listOf(ArgType.RAW, ArgType.ORIGINAL),
        allowedInText = true,
    ) { ctx, args, _ ->
        val value = args[0].assertType<RawNode>().string
        val body = args[1]
        val attributes = LinkedHashMap<String, String>()
        when (ctx.funcName) {
            "\\htmlClass" -> attributes["class"] = value
            "\\htmlId" -> attributes["id"] = value
            "\\htmlStyle" -> attributes["style"] = value
            "\\htmlData" -> {
                // `{,}` escapes a literal comma.
                val data = ArrayList<String>()
                val current = StringBuilder()
                var i = 0
                while (i < value.length) {
                    if (value.startsWith(ESCAPED_COMMA, i)) {
                        current.append(',')
                        i += ESCAPED_COMMA.length - 1
                    } else if (value[i] == ',') {
                        data.add(current.toString())
                        current.clear()
                    } else {
                        current.append(value[i])
                    }
                    i++
                }
                data.add(current.toString())
                for (item in data) {
                    val firstEquals = item.indexOf('=')
                    if (firstEquals < 0) {
                        throw ParseError("\\htmlData key/value '$item' missing equals sign")
                    }
                    val key = item.substring(0, firstEquals)
                    attributes["data-" + key.trim()] = item.substring(firstEquals + 1)
                }
            }

            else -> throw IllegalStateException("Unrecognized html command")
        }
        HtmlNode(ctx.parser.mode, attributes, ordArgument(body))
    }

    defineHtmlBuilder<HtmlNode> { group, options ->
        val elements = buildExpression(group.body, options, false)
        val classes = arrayListOf("enclosing")
        group.attributes["class"]?.let { cls ->
            classes.addAll(cls.trim().split(whitespace).filter { it.isNotEmpty() })
        }
        makeSpan(classes, elements, options)
    }
}

// ---------------------------------------------------------------- htmlmathml.ts

private fun registerHtmlMathml() {
    defineFunction(
        names = listOf("\\html@mathml"),
        numArgs = 2,
        allowedInArgument = true,
        allowedInText = true,
    ) { ctx, args, _ ->
        HtmlMathmlNode(ctx.parser.mode, html = ordArgument(args[0]), mathml = ordArgument(args[1]))
    }
    defineHtmlBuilder<HtmlMathmlNode> { group, options ->
        makeFragment(buildExpression(group.html, options, false))
    }
}

// ---------------------------------------------------------------- char.ts

private val leadingInt = Regex("^\\s*[+-]?\\d+")

/** Concatenates the textords of [group], like KaTeX's `assertCharacterGroup`. */
private fun assertCharacterGroup(group: OrdGroupNode, errorMessage: String, allowSpaces: Boolean = false): String {
    val text = StringBuilder()
    for (node in group.body) {
        if (node is TextOrdNode) {
            text.append(node.text)
        } else if (allowSpaces && node is SpacingNode && node.text == " ") {
            text.append(' ')
        } else {
            throw ParseError(errorMessage)
        }
    }
    return text.toString()
}

/**
 * \@char is an internal function that takes a grouped decimal argument like
 * {123} and converts into symbol with code 123.  It is used by the *macro*
 * \char defined in macros.
 */
private fun registerChar() {
    defineFunction(
        names = listOf("\\@char"),
        numArgs = 1,
        allowedInText = true,
    ) { ctx, args, _ ->
        val arg = args[0].assertType<OrdGroupNode>()
        val number = assertCharacterGroup(arg, "\\@char has non-numeric argument")
        // parseInt semantics: leading integer, NaN otherwise.
        val code = leadingInt.find(number)?.value?.trim()?.toLongOrNull()
            ?: throw ParseError("\\@char has non-numeric argument $number")
        if (code < 0 || code > 0x10ffff) {
            throw ParseError("\\@char with invalid code point $number")
        }
        TextOrdNode(ctx.parser.mode, String(Character.toChars(code.toInt())))
    }
}

// ---------------------------------------------------------------- relax.ts

private fun registerRelax() {
    defineFunction(
        names = listOf("\\relax"),
        numArgs = 0,
        allowedInText = true,
        allowedInArgument = true,
    ) { ctx, _, _ ->
        InternalNode(ctx.parser.mode)
    }
}

// ---------------------------------------------------------------- def.ts

private val globalMap = mapOf(
    "\\global" to "\\global",
    "\\long" to "\\\\globallong",
    "\\\\globallong" to "\\\\globallong",
    "\\def" to "\\gdef",
    "\\gdef" to "\\gdef",
    "\\edef" to "\\xdef",
    "\\xdef" to "\\xdef",
    "\\let" to "\\\\globallet",
    "\\futurelet" to "\\\\globalfuture",
)

private val notControlSequence = Regex("^(?:[\\\\{}$&#^_]|EOF)$")
private val argNumber = Regex("^[1-9]$")

private fun checkControlSequence(tok: Token): String {
    val name = tok.text
    if (notControlSequence.matches(name)) throw ParseError("Expected a control sequence", tok)
    return name
}

private fun getRHS(parser: Parser): Token {
    var tok = parser.gullet.popToken()
    if (tok.text == "=") { // consume optional equals
        tok = parser.gullet.popToken()
        if (tok.text == " ") { // consume one optional space
            tok = parser.gullet.popToken()
        }
    }
    return tok
}

private fun letCommand(parser: Parser, name: String, tok: Token, global: Boolean) {
    var macro = parser.gullet.macros.get(tok.text)
    if (macro == null) {
        // don't expand it later even if a macro with the same name is defined
        // e.g., \let\foo=\frac \def\frac{\relax} \frac12
        tok.noexpand = true
        macro = MacroDefinition.Expansion(
            tokens = listOf(tok),
            numArgs = 0,
            // reproduce the same behavior in expansion
            unexpandable = !parser.gullet.isExpandable(tok.text),
        )
    }
    parser.gullet.macros.set(name, macro, global)
}

/**
 * KaTeX's `parser.parseFunction()` (private in [Parser]): parses the
 * function at the current token with its arguments.
 */
private fun parseFunctionAt(parser: Parser): ParseNode {
    val token = parser.fetch()
    val func = token.text
    val funcData = Registry.functions[func] ?: throw ParseError("Invalid token after macro prefix", token)
    parser.consume()
    if (parser.mode == Mode.TEXT && !funcData.allowedInText) {
        throw ParseError("Can't use function '$func' in text mode", token)
    } else if (parser.mode == Mode.MATH && !funcData.allowedInMath) {
        throw ParseError("Can't use function '$func' in math mode", token)
    }
    val (args, optArgs) = parser.parseArguments(
        func, funcData.numArgs, funcData.numOptionalArgs, funcData.argTypes, funcData.primitive,
    )
    return parser.callFunction(func, args, optArgs, token)
}

private fun registerDef() {
    // <assignment> -> <non-macro assignment>|<macro assignment>
    // <non-macro assignment> -> <simple assignment>|\global<non-macro assignment>
    // <macro assignment> -> <definition>|<prefix><macro assignment>
    // <prefix> -> \global|\long|\outer
    defineFunction(
        names = listOf(
            "\\global", "\\long",
            "\\\\globallong", // can’t be entered directly
        ),
        numArgs = 0,
        allowedInText = true,
    ) { ctx, _, _ ->
        val parser = ctx.parser
        parser.consumeSpaces()
        val token = parser.fetch()
        val mapped = globalMap[token.text] ?: throw ParseError("Invalid token after macro prefix", token)
        // KaTeX doesn't have \par, so ignore \long
        if (ctx.funcName == "\\global" || ctx.funcName == "\\\\globallong") {
            token.text = mapped
        }
        parseFunctionAt(parser).assertType<InternalNode>()
    }

    // Basic support for macro definitions: \def, \gdef, \edef, \xdef
    // <definition> -> <def><control sequence><definition text>
    // <def> -> \def|\gdef|\edef|\xdef
    // <definition text> -> <parameter text><left brace><balanced text><right brace>
    defineFunction(
        names = listOf("\\def", "\\gdef", "\\edef", "\\xdef"),
        numArgs = 0,
        allowedInText = true,
        primitive = true,
    ) { ctx, _, _ ->
        val parser = ctx.parser
        val funcName = ctx.funcName
        var tok = parser.gullet.popToken()
        val name = tok.text
        if (notControlSequence.matches(name)) throw ParseError("Expected a control sequence", tok)

        var numArgs = 0
        var insert: Token? = null
        val delimiters = arrayListOf<MutableList<String>>(ArrayList())
        // <parameter text> contains no braces
        while (parser.gullet.future().text != "{") {
            tok = parser.gullet.popToken()
            if (tok.text == "#") {
                // If the very last character of the <parameter text> is #, so that
                // this # is immediately followed by {, TeX will behave as if the {
                // had been inserted at the right end of both the parameter text
                // and the replacement text.
                if (parser.gullet.future().text == "{") {
                    insert = parser.gullet.future()
                    delimiters[numArgs].add("{")
                    break
                }

                // A parameter, the first appearance of # must be followed by 1,
                // the next by 2, and so on; up to nine #’s are allowed
                tok = parser.gullet.popToken()
                if (!argNumber.matches(tok.text)) {
                    throw ParseError("Invalid argument number \"${tok.text}\"")
                }
                if (tok.text.toInt() != numArgs + 1) {
                    throw ParseError("Argument number \"${tok.text}\" out of order")
                }
                numArgs++
                delimiters.add(ArrayList())
            } else if (tok.text == "EOF") {
                throw ParseError("Expected a macro definition")
            } else {
                delimiters[numArgs].add(tok.text)
            }
        }
        // replacement text, enclosed in '{' and '}' and properly nested
        var tokens: MutableList<Token> = parser.gullet.consumeArg().tokens.toMutableList()
        if (insert != null) tokens.add(0, insert)

        if (funcName == "\\edef" || funcName == "\\xdef") {
            tokens = parser.gullet.expandTokens(tokens).toMutableList()
            tokens.reverse() // to fit in with stack order
        }
        // Final arg is the expansion of the macro
        parser.gullet.macros.set(
            name,
            MacroDefinition.Expansion(tokens, numArgs, delimiters),
            global = funcName == globalMap[funcName],
        )
        InternalNode(parser.mode)
    }

    // <simple assignment> -> <let assignment>
    // <let assignment> -> \futurelet<control sequence><token><token>
    //     | \let<control sequence><equals><one optional space><token>
    // <equals> -> <optional spaces>|<optional spaces>=
    defineFunction(
        names = listOf(
            "\\let",
            "\\\\globallet", // can’t be entered directly
        ),
        numArgs = 0,
        allowedInText = true,
        primitive = true,
    ) { ctx, _, _ ->
        val parser = ctx.parser
        val name = checkControlSequence(parser.gullet.popToken())
        parser.gullet.consumeSpaces()
        val tok = getRHS(parser)
        letCommand(parser, name, tok, ctx.funcName == "\\\\globallet")
        InternalNode(parser.mode)
    }

    // ref: https://www.tug.org/TUGboat/tb09-3/tb22bechtolsheim.pdf
    defineFunction(
        names = listOf(
            "\\futurelet",
            "\\\\globalfuture", // can’t be entered directly
        ),
        numArgs = 0,
        allowedInText = true,
        primitive = true,
    ) { ctx, _, _ ->
        val parser = ctx.parser
        val name = checkControlSequence(parser.gullet.popToken())
        val middle = parser.gullet.popToken()
        val tok = parser.gullet.popToken()
        letCommand(parser, name, tok, ctx.funcName == "\\\\globalfuture")
        parser.gullet.pushToken(tok)
        parser.gullet.pushToken(middle)
        InternalNode(parser.mode)
    }
}

// ---------------------------------------------------------------- math.ts

private fun registerMath() {
    // Switching from text mode back to math mode
    defineFunction(
        names = listOf("\\(", "$"),
        numArgs = 0,
        allowedInText = true,
        allowedInMath = false,
    ) { ctx, _, _ ->
        val parser = ctx.parser
        val outerMode = parser.mode
        parser.switchMode(Mode.MATH)
        val close = if (ctx.funcName == "\\(") "\\)" else "$"
        val body = parser.parseExpression(false, close)
        parser.expect(close)
        parser.switchMode(outerMode)
        StylingNode(parser.mode, style = "text", body = body, resetFont = true)
    }

    // Check for extra closing math delimiters
    defineFunction(
        names = listOf("\\)", "\\]"),
        numArgs = 0,
        allowedInText = true,
        allowedInMath = false,
    ) { ctx, _, _ ->
        throw ParseError("Mismatched ${ctx.funcName}")
    }
}

// ---------------------------------------------------------------- reflectbox.ts

private fun registerReflectBox() {
    val handler: FunctionHandler = { ctx, args, _ -> ReflectBoxNode(ctx.parser.mode, args[0]) }
    // The box model has no horizontal mirroring, so the body is drawn unmirrored.
    defineFunction(
        names = listOf("\\reflectbox"),
        numArgs = 1,
        argTypes = listOf(ArgType.HBOX),
        allowedInText = true,
        handler = handler,
    )
    defineHtmlBuilder<ReflectBoxNode> { group, options ->
        makeSpan(listOf("mord", "reflectbox"), listOf(buildGroup(group.body, options)), options)
    }
    // Parse math directly so the shared builders inherit the surrounding style.
    // \reflectbox instead uses an hbox argument for LaTeX's text-box behavior.
    defineFunction(
        names = listOf("\\mathreflectbox"),
        numArgs = 1,
        argTypes = listOf(ArgType.MATH),
        handler = handler,
    )
}
