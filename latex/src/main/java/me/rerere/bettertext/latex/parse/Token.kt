package me.rerere.bettertext.latex.parse

/** A range of the source string, end-exclusive. */
internal class SourceLocation(val input: String, val start: Int, val end: Int) {
    companion object {
        fun range(first: Token?, second: Token? = null): SourceLocation? {
            if (second == null) return first?.loc
            val a = first?.loc ?: return null
            val b = second.loc ?: return null
            if (a.input !== b.input) return null
            return SourceLocation(a.input, a.start, b.end)
        }

        fun range(first: SourceLocation?, second: SourceLocation?): SourceLocation? {
            if (first == null || second == null || first.input !== second.input) return null
            return SourceLocation(first.input, first.start, second.end)
        }
    }
}

internal class Token(
    var text: String,
    val loc: SourceLocation? = null,
) {
    /** Don't expand the token. */
    var noexpand: Boolean = false

    /** Used by `\noexpand`: the token is interpreted as `\relax`. */
    var treatAsRelax: Boolean = false

    fun range(endToken: Token, text: String): Token = Token(text, SourceLocation.range(this, endToken))

    override fun toString(): String = text
}
