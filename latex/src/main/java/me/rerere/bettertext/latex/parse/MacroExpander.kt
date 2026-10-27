package me.rerere.bettertext.latex.parse

import me.rerere.bettertext.latex.LatexSettings
import me.rerere.bettertext.latex.ParseError
import me.rerere.bettertext.latex.functions.Registry

/** A macro definition: a string body, pre-lexed tokens, or a function computing the expansion. */
internal sealed class MacroDefinition {
    class Text(val body: String) : MacroDefinition()

    /** [tokens] are stored in reverse order, as on the expander stack. */
    class Expansion(
        val tokens: List<Token>,
        val numArgs: Int,
        val delimiters: List<List<String>>? = null,
        val unexpandable: Boolean = false,
    ) : MacroDefinition()

    /** Returns either [Text] or [Expansion]. */
    class Function(val fn: (MacroExpander) -> MacroDefinition) : MacroDefinition()
}

internal class MacroArg(val tokens: List<Token>, val start: Token, val end: Token)

/** Commands handled directly by the parser. */
internal val implicitCommands = setOf("^", "_", "\\limits", "\\nolimits")

/** The "gullet" where macros are expanded until only non-macro tokens remain. */
internal class MacroExpander(input: String, private val settings: LatexSettings, var mode: Mode) {
    private var expansionCount = 0
    lateinit var lexer: Lexer
        private set
    val macros: Namespace<MacroDefinition> = Namespace(
        BuiltinMacros.all,
        settings.macros.mapValuesTo(HashMap()) { MacroDefinition.Text(it.value) },
    )

    /** Contains tokens in REVERSE order. */
    private val stack = ArrayList<Token>()

    init {
        feed(input)
    }

    fun feed(input: String) {
        lexer = Lexer(input)
    }

    fun switchMode(newMode: Mode) {
        mode = newMode
    }

    fun beginGroup() = macros.beginGroup()
    fun endGroup() = macros.endGroup()
    fun endGroups() = macros.endGroups()

    /** Returns the topmost token on the stack, without expanding it. */
    fun future(): Token {
        if (stack.isEmpty()) pushToken(lexer.lex())
        return stack.last()
    }

    /** Remove and return the next unexpanded token. */
    fun popToken(): Token {
        future()
        return stack.removeAt(stack.size - 1)
    }

    fun pushToken(token: Token) {
        stack.add(token)
    }

    fun pushTokens(tokens: List<Token>) {
        stack.addAll(tokens)
    }

    /**
     * Find a macro argument without expanding tokens and append the array of
     * tokens to the token stack. Uses Token as a container for the result.
     */
    fun scanArgument(isOptional: Boolean): Token? {
        val start: Token
        val end: Token
        val tokens: List<Token>
        if (isOptional) {
            consumeSpaces()
            if (future().text != "[") return null
            start = popToken()
            val arg = consumeArg(listOf("]"))
            tokens = arg.tokens
            end = arg.end
        } else {
            val arg = consumeArg()
            tokens = arg.tokens
            start = arg.start
            end = arg.end
        }
        pushToken(Token("EOF", end.loc))
        pushTokens(tokens)
        return Token("", SourceLocation.range(start, end))
    }

    fun consumeSpaces() {
        while (future().text == " ") stack.removeAt(stack.size - 1)
    }

    /** Consume an argument from the token stream. */
    fun consumeArg(delims: List<String>? = null): MacroArg {
        val tokens = ArrayList<Token>()
        val isDelimited = !delims.isNullOrEmpty()
        if (!isDelimited) consumeSpaces()
        val start = future()
        var tok: Token
        var depth = 0
        var match = 0
        do {
            tok = popToken()
            tokens.add(tok)
            when (tok.text) {
                "{" -> ++depth
                "}" -> {
                    --depth
                    if (depth == -1) throw ParseError("Extra }", tok)
                }

                "EOF" -> throw ParseError(
                    "Unexpected end of input in a macro argument, expected '" +
                        (if (delims != null && isDelimited) delims[match] else "}") + "'",
                    tok,
                )
            }
            if (delims != null && isDelimited) {
                if ((depth == 0 || (depth == 1 && delims[match] == "{")) && tok.text == delims[match]) {
                    ++match
                    if (match == delims.size) {
                        repeat(match) { tokens.removeAt(tokens.size - 1) }
                        break
                    }
                } else {
                    match = 0
                }
            }
        } while (depth != 0 || isDelimited)
        if (start.text == "{" && tokens.isNotEmpty() && tokens.last().text == "}") {
            tokens.removeAt(tokens.size - 1)
            tokens.removeAt(0)
        }
        tokens.reverse()
        return MacroArg(tokens, start, tok)
    }

    fun consumeArgs(numArgs: Int, delimiters: List<List<String>>? = null): List<List<Token>> {
        if (delimiters != null) {
            if (delimiters.size != numArgs + 1) {
                throw ParseError("The length of delimiters doesn't match the number of args!")
            }
            for (d in delimiters[0]) {
                val tok = popToken()
                if (d != tok.text) throw ParseError("Use of the macro doesn't match its definition", tok)
            }
        }
        return List(numArgs) { i -> consumeArg(delimiters?.get(i + 1)).tokens }
    }

    private fun countExpansion(amount: Int) {
        expansionCount += amount
        if (expansionCount > settings.maxExpand) {
            throw ParseError("Too many expansions: infinite loop or need to increase maxExpand setting")
        }
    }

    /**
     * Expand the next token only once if possible. Returns the number of tokens
     * pushed, or -1 if the token was not expanded.
     */
    fun expandOnce(expandableOnly: Boolean = false): Int {
        val topToken = popToken()
        val name = topToken.text
        val expansion = if (!topToken.noexpand) getExpansion(name) else null
        if (expansion == null || (expandableOnly && expansion.unexpandable)) {
            if (expandableOnly && expansion == null && name.startsWith("\\") && !isDefined(name)) {
                throw ParseError("Undefined control sequence: $name")
            }
            pushToken(topToken)
            return -1
        }
        countExpansion(1)
        var tokens = expansion.tokens
        val args = consumeArgs(expansion.numArgs, expansion.delimiters)
        if (expansion.numArgs > 0) {
            val out = ArrayList(tokens)
            var i = out.size - 1
            while (i >= 0) {
                var tok = out[i]
                if (tok.text == "#") {
                    if (i == 0) throw ParseError("Incomplete placeholder at end of macro body", tok)
                    tok = out[--i]
                    if (tok.text == "#") {
                        out.removeAt(i + 1)
                    } else if (tok.text.length == 1 && tok.text[0] in '1'..'9') {
                        out.removeAt(i)
                        out.removeAt(i)
                        out.addAll(i, args[tok.text.toInt() - 1])
                    } else {
                        throw ParseError("Not a valid argument number", tok)
                    }
                }
                i--
            }
            tokens = out
        }
        pushTokens(tokens)
        return tokens.size
    }

    /** Expand the next token once and return the resulting top token without removing it. */
    fun expandAfterFuture(): Token {
        expandOnce()
        return future()
    }

    /** Recursively expand first token, then return first non-expandable token. */
    fun expandNextToken(): Token {
        while (true) {
            if (expandOnce() == -1) {
                val token = stack.removeAt(stack.size - 1)
                if (token.treatAsRelax) token.text = "\\relax"
                return token
            }
        }
    }

    fun expandMacro(name: String): List<Token>? =
        if (macros.has(name)) expandTokens(listOf(Token(name))) else null

    /** Fully expand the given token stream (in reverse order); output is in forward order. */
    fun expandTokens(tokens: List<Token>): List<Token> {
        val output = ArrayList<Token>()
        val oldStackLength = stack.size
        pushTokens(tokens)
        while (stack.size > oldStackLength) {
            if (expandOnce(true) == -1) {
                val token = stack.removeAt(stack.size - 1)
                if (token.treatAsRelax) {
                    token.noexpand = false
                    token.treatAsRelax = false
                }
                output.add(token)
            }
        }
        countExpansion(output.size)
        return output
    }

    fun expandMacroAsText(name: String): String? = expandMacro(name)?.joinToString("") { it.text }

    private fun getExpansion(name: String): MacroDefinition.Expansion? {
        val definition = macros.get(name) ?: return null
        if (name.length == 1) {
            val catcode = lexer.catcodes[name]
            if (catcode != null && catcode != 13) return null
        }
        val expansion = if (definition is MacroDefinition.Function) definition.fn(this) else definition
        return when (expansion) {
            is MacroDefinition.Text -> lexExpansion(expansion.body)
            is MacroDefinition.Expansion -> expansion
            is MacroDefinition.Function -> throw ParseError("Macro function returned a function")
        }
    }

    private fun lexExpansion(body: String): MacroDefinition.Expansion {
        var numArgs = 0
        if (body.contains('#')) {
            val stripped = body.replace("##", "")
            while (stripped.contains("#" + (numArgs + 1))) ++numArgs
        }
        val bodyLexer = Lexer(body)
        val tokens = ArrayList<Token>()
        var tok = bodyLexer.lex()
        while (tok.text != "EOF") {
            tokens.add(tok)
            tok = bodyLexer.lex()
        }
        tokens.reverse()
        return MacroDefinition.Expansion(tokens, numArgs)
    }

    /** Whether a command is a macro, a function, a symbol, or an implicit command. */
    fun isDefined(name: String): Boolean =
        macros.has(name) ||
            Registry.functions.containsKey(name) ||
            Symbols.math.containsKey(name) ||
            Symbols.text.containsKey(name) ||
            name in implicitCommands

    fun isExpandable(name: String): Boolean {
        val macro = macros.get(name)
        if (macro != null) {
            return macro !is MacroDefinition.Expansion || !macro.unexpandable
        }
        val fn = Registry.functions[name]
        return fn != null && !fn.primitive
    }
}
