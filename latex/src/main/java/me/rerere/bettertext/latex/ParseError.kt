package me.rerere.bettertext.latex

import me.rerere.bettertext.latex.parse.Token

/**
 * Thrown when a LaTeX expression cannot be parsed or laid out.
 *
 * [position] is the offset in the source where the error was detected, or -1 if unknown.
 */
class LatexParseException internal constructor(
    val rawMessage: String,
    val position: Int = -1,
) : Exception(if (position >= 0) "$rawMessage at position ${position + 1}" else rawMessage) {
    internal constructor(message: String, token: Token?) : this(message, token?.loc?.start ?: -1)
}

internal typealias ParseError = LatexParseException
