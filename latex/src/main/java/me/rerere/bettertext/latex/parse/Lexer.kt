package me.rerere.bettertext.latex.parse

import me.rerere.bettertext.latex.ParseError

private const val SPACE = "[ \\r\\n\\t]"
private const val CONTROL_WORD = "\\\\[a-zA-Z@]+"
private const val CONTROL_SYMBOL = "\\\\[^\\x{10000}-\\x{10FFFF}\\uD800-\\uDFFF]"
private const val CONTROL_WORD_WHITESPACE = "($CONTROL_WORD)$SPACE*"
private const val CONTROL_SPACE = "\\\\(\\n|[ \\r\\t]+\\n?)[ \\r\\t]*"
private const val COMBINING_MARK = "[\\u0300-\\u036f]"

internal val combiningDiacriticalMarksEndRegex = Regex("$COMBINING_MARK+$")

/*
 * Capturing groups:
 *   [1] regular whitespace
 *   [2] backslash followed by whitespace
 *   [3] anything else, which may include:
 *     [4] left character of \verb*
 *     [5] left character of \verb
 *     [6] backslash followed by word, excluding any trailing whitespace
 */
private val tokenPattern = java.util.regex.Pattern.compile(
    "($SPACE+)|" +
        "$CONTROL_SPACE|" +
        "([!-\\[\\]-\\u2027\\u202A-\\uD7FF\\uF900-\\uFFFF]$COMBINING_MARK*" +
        "|[\\x{10000}-\\x{10FFFF}]$COMBINING_MARK*" +
        "|\\\\verb\\*([\\s\\S]).*?\\4" +
        "|\\\\verb([^*a-zA-Z]).*?\\5" +
        "|$CONTROL_WORD_WHITESPACE" +
        "|$CONTROL_SYMBOL)"
)

/** Splits the input into tokens, one at a time. Mirrors KaTeX's Lexer. */
internal class Lexer(val input: String) {
    private val matcher = tokenPattern.matcher(input)
    private var pos = 0

    /** Category codes. The lexer only supports comment characters (14); 13 is "active". */
    val catcodes: MutableMap<String, Int> = hashMapOf("%" to 14, "~" to 13)

    fun setCatcode(char: String, code: Int) {
        catcodes[char] = code
    }

    fun lex(): Token {
        val start = pos
        if (start == input.length) {
            return Token("EOF", SourceLocation(input, start, start))
        }
        matcher.region(start, input.length)
        if (!matcher.lookingAt()) {
            throw ParseError(
                "Unexpected character: '${input[start]}'",
                Token(input[start].toString(), SourceLocation(input, start, start + 1)),
            )
        }
        pos = matcher.end()
        val text = matcher.group(6) ?: matcher.group(3) ?: if (matcher.group(2) != null) "\\ " else " "
        if (catcodes[text] == 14) {
            val nl = input.indexOf('\n', pos)
            pos = if (nl == -1) input.length else nl + 1
            return lex()
        }
        return Token(text, SourceLocation(input, start, pos))
    }
}
