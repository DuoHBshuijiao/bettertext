package me.rerere.bettertext.latex.parse

import me.rerere.bettertext.latex.LatexSettings
import me.rerere.bettertext.latex.ParseError
import me.rerere.bettertext.latex.functions.ArgType
import me.rerere.bettertext.latex.functions.Builtins
import me.rerere.bettertext.latex.functions.FunctionContext
import me.rerere.bettertext.latex.functions.FunctionSpec
import me.rerere.bettertext.latex.functions.Registry

private val endOfExpression = setOf("}", "\\endgroup", "\\end", "\\right", "&")
private val colorRegex = Regex("^(#[a-f0-9]{3,4}|#[a-f0-9]{6}|#[a-f0-9]{8}|[a-f0-9]{6}|[a-z]+)$", RegexOption.IGNORE_CASE)
private val sixHexRegex = Regex("^[0-9a-f]{6}$", RegexOption.IGNORE_CASE)
private val sizeGroupRegex = Regex("^[-+]? *(?:$|\\d+|\\d+\\.\\d*|\\.\\d*) *[a-z]{0,2} *$")
private val sizeRegex = Regex("([-+]?) *(\\d+(?:\\.\\d*)?|\\.\\d+) *([a-z]{2})")
private val urlEscapeRegex = Regex("\\\\([#$%&~_^{}])")
private val verbRegex = Regex("^\\\\verb[^a-zA-Z]")

/**
 * Recursive-descent parser for TeX math, ported from KaTeX's Parser.
 * Since TeX isn't context-free, the parser pulls tokens from the macro expander
 * one at a time and keeps a single token of lookahead.
 */
internal class Parser(input: String, val settings: LatexSettings) {
    var mode: Mode = Mode.MATH
        private set
    val gullet: MacroExpander
    var leftrightDepth = 0
    private var nextToken: Token? = null

    init {
        Builtins.ensureRegistered()
        gullet = MacroExpander(input, settings, mode)
    }

    fun expect(text: String, consume: Boolean = true) {
        if (fetch().text != text) {
            throw ParseError("Expected '$text', got '${fetch().text}'", fetch())
        }
        if (consume) consume()
    }

    fun consume() {
        nextToken = null
    }

    fun fetch(): Token {
        return nextToken ?: gullet.expandNextToken().also { nextToken = it }
    }

    fun switchMode(newMode: Mode) {
        mode = newMode
        gullet.switchMode(newMode)
    }

    fun parse(): List<ParseNode> {
        if (!settings.globalGroup) gullet.beginGroup()
        if (settings.colorIsTextColor) {
            gullet.macros.set("\\color", MacroDefinition.Text("\\textcolor"))
        }
        try {
            val parse = parseExpression(false)
            expect("EOF")
            if (!settings.globalGroup) gullet.endGroup()
            return parse
        } finally {
            gullet.endGroups()
        }
    }

    /** Fully parse a separate sequence of tokens (in reverse order) as a separate job. */
    fun subparse(tokens: List<Token>): List<ParseNode> {
        val oldToken = nextToken
        consume()
        gullet.pushToken(Token("}"))
        gullet.pushTokens(tokens)
        val parse = parseExpression(false)
        expect("}")
        nextToken = oldToken
        return parse
    }

    /**
     * Parses an "expression", which is a list of atoms.
     *
     * @param breakOnInfix stop when hitting infix nodes.
     * @param breakOnTokenText the token text that ends the expression.
     */
    fun parseExpression(breakOnInfix: Boolean, breakOnTokenText: String? = null): List<ParseNode> {
        val body = ArrayList<ParseNode>()
        while (true) {
            if (mode == Mode.MATH) consumeSpaces()
            val lex = fetch()
            if (lex.text in endOfExpression) break
            if (breakOnTokenText != null && lex.text == breakOnTokenText) break
            if (breakOnInfix && Registry.functions[lex.text]?.infix == true) break
            val atom = parseAtom(breakOnTokenText) ?: break
            if (atom is InternalNode) continue
            body.add(atom)
        }
        if (mode == Mode.TEXT) formLigatures(body)
        return handleInfixNodes(body)
    }

    /** Rewrites infix operators such as `\over` with corresponding commands such as `\frac`. */
    private fun handleInfixNodes(body: List<ParseNode>): List<ParseNode> {
        var overIndex = -1
        var funcName: String? = null
        for ((i, node) in body.withIndex()) {
            if (node is InfixNode) {
                if (overIndex != -1) throw ParseError("only one infix operator per group", node.token)
                overIndex = i
                funcName = node.replaceWith
            }
        }
        if (overIndex == -1 || funcName == null) return body
        val numerBody = body.subList(0, overIndex)
        val denomBody = body.subList(overIndex + 1, body.size)
        val numerNode = numerBody.singleOrNull() as? OrdGroupNode ?: OrdGroupNode(mode, numerBody.toList())
        val denomNode = denomBody.singleOrNull() as? OrdGroupNode ?: OrdGroupNode(mode, denomBody.toList())
        val node = if (funcName == "\\\\abovefrac") {
            callFunction(funcName, listOf(numerNode, body[overIndex], denomNode), emptyList())
        } else {
            callFunction(funcName, listOf(numerNode, denomNode), emptyList())
        }
        return listOf(node)
    }

    private fun handleSupSubscript(name: String): ParseNode {
        val symbolToken = fetch()
        val symbol = symbolToken.text
        consume()
        consumeSpaces()
        var group: ParseNode?
        do {
            group = parseGroup(name)
        } while (group is InternalNode)
        return group ?: throw ParseError("Expected group after '$symbol'", symbolToken)
    }

    /** Renders an unsupported command as red text instead of failing. */
    private fun formatUnsupportedCmd(text: String): ParseNode {
        val textordArray = text.map { TextOrdNode(Mode.TEXT, it.toString()) }
        return ColorNode(mode, settings.errorColor, listOf(TextNode(mode, textordArray)))
    }

    /** Parses a group with optional super/subscripts. */
    fun parseAtom(breakOnTokenText: String? = null): ParseNode? {
        val base = parseGroup("atom", breakOnTokenText)
        if (base is InternalNode) return base
        if (mode == Mode.TEXT) return base

        var superscript: ParseNode? = null
        var subscript: ParseNode? = null
        while (true) {
            consumeSpaces()
            val lex = fetch()
            if (lex.text == "\\limits" || lex.text == "\\nolimits") {
                when (base) {
                    is OpNode -> {
                        base.limits = lex.text == "\\limits"
                        base.alwaysHandleSupSub = true
                    }

                    is OperatorNameNode -> {
                        if (base.alwaysHandleSupSub) base.limits = lex.text == "\\limits"
                    }

                    else -> throw ParseError("Limit controls must follow a math operator", lex)
                }
                consume()
            } else if (lex.text == "^") {
                if (superscript != null) throw ParseError("Double superscript", lex)
                superscript = handleSupSubscript("superscript")
            } else if (lex.text == "_") {
                if (subscript != null) throw ParseError("Double subscript", lex)
                subscript = handleSupSubscript("subscript")
            } else if (lex.text == "'") {
                if (superscript != null) throw ParseError("Double superscript", lex)
                val prime = TextOrdNode(mode, "\\prime")
                val primes = arrayListOf<ParseNode>(prime)
                consume()
                while (fetch().text == "'") {
                    primes.add(prime)
                    consume()
                }
                if (fetch().text == "^") {
                    primes.add(handleSupSubscript("superscript"))
                }
                superscript = OrdGroupNode(mode, primes)
            } else if (UnicodeTables.subsAndSups.containsKey(lex.text)) {
                val isSub = UnicodeTables.isSubscript(lex.text)
                val subsupTokens = arrayListOf(Token(UnicodeTables.subsAndSups.getValue(lex.text)))
                consume()
                while (true) {
                    val token = fetch().text
                    val mapped = UnicodeTables.subsAndSups[token] ?: break
                    if (UnicodeTables.isSubscript(token) != isSub) break
                    subsupTokens.add(0, Token(mapped))
                    consume()
                }
                val body = subparse(subsupTokens)
                if (isSub) {
                    subscript = OrdGroupNode(Mode.MATH, body)
                } else {
                    superscript = OrdGroupNode(Mode.MATH, body)
                }
            } else {
                break
            }
        }
        return if (superscript != null || subscript != null) {
            SupSubNode(mode, base, superscript, subscript)
        } else {
            base
        }
    }

    /** Parses an entire function, including its base and all of its arguments. */
    private fun parseFunction(breakOnTokenText: String?, name: String?): ParseNode? {
        val token = fetch()
        val func = token.text
        val funcData = Registry.functions[func] ?: return null
        consume()
        if (name != null && name != "atom" && !funcData.allowedInArgument) {
            throw ParseError("Got function '$func' with no arguments as $name", token)
        } else if (mode == Mode.TEXT && !funcData.allowedInText) {
            throw ParseError("Can't use function '$func' in text mode", token)
        } else if (mode == Mode.MATH && !funcData.allowedInMath) {
            throw ParseError("Can't use function '$func' in math mode", token)
        }
        val (args, optArgs) = parseArguments(
            func,
            funcData.numArgs,
            funcData.numOptionalArgs,
            funcData.argTypes,
            funcData.primitive,
            isSqrt = func == "\\sqrt",
        )
        return callFunction(func, args, optArgs, token, breakOnTokenText)
    }

    fun callFunction(
        name: String,
        args: List<ParseNode>,
        optArgs: List<ParseNode?>,
        token: Token? = null,
        breakOnTokenText: String? = null,
    ): ParseNode {
        val func: FunctionSpec = Registry.functions[name]
            ?: throw ParseError("No function handler for $name")
        val handler = func.handler ?: throw ParseError("No function handler for $name")
        return handler(FunctionContext(name, this, token, breakOnTokenText), args, optArgs)
    }

    /** Parses the arguments of a function or environment. */
    fun parseArguments(
        func: String,
        numArgs: Int,
        numOptionalArgs: Int,
        argTypes: List<ArgType?>?,
        primitive: Boolean = false,
        isSqrt: Boolean = false,
    ): Pair<List<ParseNode>, List<ParseNode?>> {
        val totalArgs = numArgs + numOptionalArgs
        if (totalArgs == 0) return emptyList<ParseNode>() to emptyList()
        val args = ArrayList<ParseNode>()
        val optArgs = ArrayList<ParseNode?>()
        for (i in 0 until totalArgs) {
            var argType = argTypes?.getOrNull(i)
            val isOptional = i < numOptionalArgs
            if ((primitive && argType == null) ||
                // \sqrt expands into primitive if optional argument doesn't exist
                (isSqrt && i == 1 && optArgs[0] == null)
            ) {
                argType = ArgType.PRIMITIVE
            }
            val arg = parseGroupOfType("argument to '$func'", argType, isOptional)
            if (isOptional) {
                optArgs.add(arg)
            } else if (arg != null) {
                args.add(arg)
            } else {
                throw ParseError("Null argument, please report this as a bug")
            }
        }
        return args to optArgs
    }

    private fun parseGroupOfType(name: String, type: ArgType?, optional: Boolean): ParseNode? {
        return when (type) {
            ArgType.COLOR -> parseColorGroup(optional)
            ArgType.SIZE -> parseSizeGroup(optional)
            ArgType.URL -> parseUrlGroup(optional)
            ArgType.MATH -> parseArgumentGroup(optional, Mode.MATH)
            ArgType.TEXT -> parseArgumentGroup(optional, Mode.TEXT)
            ArgType.HBOX -> {
                val group = parseArgumentGroup(optional, Mode.TEXT) ?: return null
                StylingNode(group.mode, "text", listOf(group), resetFont = true)
            }

            ArgType.RAW -> {
                gullet.consumeSpaces()
                val percentCatcode = gullet.lexer.catcodes["%"]
                gullet.lexer.setCatcode("%", 12)
                val token = parseStringGroup(optional)
                gullet.lexer.setCatcode("%", percentCatcode ?: 14)
                token?.let { RawNode(Mode.TEXT, it.text) }
            }

            ArgType.PRIMITIVE -> {
                if (optional) throw ParseError("A primitive argument cannot be optional")
                parseGroup(name) ?: throw ParseError("Expected group as $name", fetch())
            }

            ArgType.ORIGINAL, null -> parseArgumentGroup(optional, null)
        }
    }

    fun consumeSpaces() {
        while (fetch().text == " ") consume()
    }

    private fun parseStringGroup(optional: Boolean): Token? {
        val argToken = gullet.scanArgument(optional) ?: return null
        val str = StringBuilder()
        var next: Token
        while (fetch().also { next = it }.text != "EOF") {
            str.append(next.text)
            consume()
        }
        consume()
        argToken.text = str.toString()
        return argToken
    }

    private fun parseRegexGroup(regex: Regex, modeName: String): Token {
        val firstToken = fetch()
        var lastToken = firstToken
        var str = ""
        var next: Token
        while (fetch().also { next = it }.text != "EOF" && regex.containsMatchIn(str + next.text)) {
            lastToken = next
            str += lastToken.text
            consume()
        }
        if (str.isEmpty()) throw ParseError("Invalid $modeName: '${firstToken.text}'", firstToken)
        return firstToken.range(lastToken, str)
    }

    private fun parseColorGroup(optional: Boolean): ColorTokenNode? {
        val res = parseStringGroup(optional) ?: return null
        val match = colorRegex.find(res.text) ?: throw ParseError("Invalid color: '${res.text}'", res)
        var color = match.value
        if (sixHexRegex.matches(color)) color = "#$color"
        return ColorTokenNode(mode, color)
    }

    private fun parseSizeGroup(optional: Boolean): SizeNode? {
        var isBlank = false
        gullet.consumeSpaces()
        val res: Token = if (!optional && gullet.future().text != "{") {
            parseRegexGroup(sizeGroupRegex, "size")
        } else {
            parseStringGroup(optional)
        } ?: return null
        if (!optional && res.text.isEmpty()) {
            res.text = "0pt"
            isBlank = true
        }
        val match = sizeRegex.find(res.text) ?: throw ParseError("Invalid size: '${res.text}'", res)
        val data = Measurement(
            (match.groupValues[1] + match.groupValues[2]).toFloat(),
            match.groupValues[3],
        )
        if (!me.rerere.bettertext.latex.layout.validUnit(data.unit)) {
            throw ParseError("Invalid unit: '${data.unit}'", res)
        }
        return SizeNode(mode, data, isBlank)
    }

    private fun parseUrlGroup(optional: Boolean): UrlNode? {
        val percentCatcode = gullet.lexer.catcodes["%"]
        val tildeCatcode = gullet.lexer.catcodes["~"]
        gullet.lexer.setCatcode("%", 13)
        gullet.lexer.setCatcode("~", 12)
        val res = parseStringGroup(optional)
        gullet.lexer.setCatcode("%", percentCatcode ?: 14)
        gullet.lexer.setCatcode("~", tildeCatcode ?: 13)
        if (res == null) return null
        return UrlNode(mode, res.text.replace(urlEscapeRegex, "$1"))
    }

    private fun parseArgumentGroup(optional: Boolean, mode: Mode?): OrdGroupNode? {
        val argToken = gullet.scanArgument(optional) ?: return null
        val outerMode = this.mode
        if (mode != null) switchMode(mode)
        gullet.beginGroup()
        val expression = parseExpression(false, "EOF")
        expect("EOF")
        gullet.endGroup()
        val result = OrdGroupNode(this.mode, expression).also { it.loc = argToken.loc }
        if (mode != null) switchMode(outerMode)
        return result
    }

    /**
     * Parses an ordinary group: a single nucleus (like "x"), an expression in
     * braces (like "{x+y}"), or a function with its arguments.
     */
    fun parseGroup(name: String, breakOnTokenText: String? = null): ParseNode? {
        val firstToken = fetch()
        val text = firstToken.text
        if (text == "{" || text == "\\begingroup") {
            consume()
            val groupEnd = if (text == "{") "}" else "\\endgroup"
            gullet.beginGroup()
            val expression = parseExpression(false, groupEnd)
            val lastToken = fetch()
            expect(groupEnd)
            gullet.endGroup()
            return OrdGroupNode(mode, expression, semisimple = text == "\\begingroup").also {
                it.loc = SourceLocation.range(firstToken, lastToken)
            }
        }
        var result = parseFunction(breakOnTokenText, name) ?: parseSymbol()
        if (result == null && text.startsWith("\\") && text !in implicitCommands) {
            result = formatUnsupportedCmd(text)
            consume()
        }
        return result
    }

    /** Form ligature-like combinations of characters for text mode: `--`, `---`, ``` `` ```, `''`. */
    private fun formLigatures(group: MutableList<ParseNode>) {
        var n = group.size - 1
        var i = 0
        while (i < n) {
            val a = group[i]
            val next = group.getOrNull(i + 1)
            if (a !is TextOrdNode || next !is TextOrdNode) {
                i++
                continue
            }
            val v = a.text
            if (v == "-" && next.text == "-") {
                val afterNext = group.getOrNull(i + 2)
                if (i + 1 < n && afterNext is TextOrdNode && afterNext.text == "-") {
                    repeat(3) { group.removeAt(i) }
                    group.add(i, TextOrdNode(Mode.TEXT, "---").also { it.loc = SourceLocation.range(a.loc, afterNext.loc) })
                    n -= 2
                } else {
                    repeat(2) { group.removeAt(i) }
                    group.add(i, TextOrdNode(Mode.TEXT, "--").also { it.loc = SourceLocation.range(a.loc, next.loc) })
                    n -= 1
                }
            }
            if ((v == "'" || v == "`") && next.text == v) {
                repeat(2) { group.removeAt(i) }
                group.add(i, TextOrdNode(Mode.TEXT, v + v).also { it.loc = SourceLocation.range(a.loc, next.loc) })
                n -= 1
            }
            i++
        }
    }

    /** Parse a single symbol out of the string, including `\verb`. */
    private fun parseSymbol(): ParseNode? {
        val nucleus = fetch()
        var text = nucleus.text

        if (verbRegex.containsMatchIn(text)) {
            consume()
            var arg = text.substring(5)
            val star = arg.startsWith("*")
            if (star) arg = arg.substring(1)
            if (arg.length < 2 || arg.first() != arg.last()) {
                throw ParseError("\\verb assertion failed -- please report what input caused this bug")
            }
            return VerbNode(Mode.TEXT, arg.substring(1, arg.length - 1), star)
        }

        if (text.isEmpty()) return null
        val decomposed = UnicodeTables.symbols[text[0]]
        if (decomposed != null && !Symbols[mode].containsKey(text[0].toString())) {
            text = decomposed + text.substring(1)
        }

        val match = combiningDiacriticalMarksEndRegex.find(text)
        if (match != null) {
            text = text.substring(0, match.range.first)
            if (text == "i") text = "ı" else if (text == "j") text = "ȷ"
        }

        var symbol: ParseNode
        val info = Symbols[mode][text]
        val loc = SourceLocation.range(nucleus)
        if (info != null) {
            val group = info.group
            symbol = if (Symbols.isAtom(group)) {
                AtomNode(mode, group, text)
            } else {
                when (group) {
                    "mathord" -> MathOrdNode(mode, text)
                    "textord" -> TextOrdNode(mode, text)
                    "spacing" -> SpacingNode(mode, text)
                    "accent-token" -> AccentTokenNode(mode, text)
                    "op-token" -> OpTokenNode(mode, text)
                    else -> TextOrdNode(mode, text)
                }
            }
            symbol.loc = loc
        } else if (text[0].code >= 0x80) {
            // Nonmathematical Unicode characters are rendered as if they are in text mode.
            symbol = TextOrdNode(Mode.TEXT, text).also { it.loc = loc }
        } else {
            return null
        }
        consume()

        if (match != null) {
            for (accent in match.value) {
                val unicodeAccent = UnicodeTables.accents[accent]
                    ?: throw ParseError("Unknown accent ' $accent'", nucleus)
                val command = (if (mode == Mode.MATH) unicodeAccent.math else null) ?: unicodeAccent.text
                symbol = AccentNode(mode, command, isStretchy = false, isShifty = true, base = symbol)
                    .also { it.loc = loc }
            }
        }
        return symbol
    }
}
