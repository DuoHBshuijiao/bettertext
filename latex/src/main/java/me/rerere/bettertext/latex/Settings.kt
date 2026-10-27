package me.rerere.bettertext.latex

/**
 * Options controlling how LaTeX is parsed and laid out.
 *
 * @param displayMode render in display style (`$$...$$`) instead of inline (`$...$`).
 * @param macros user macros, e.g. `"\\RR" to "\\mathbb{R}"`.
 * @param maxSize cap for user-specified sizes, in em.
 * @param maxExpand limit on macro expansions, guards against infinite loops.
 * @param minRuleThickness minimum thickness of fraction lines and rules, in em.
 * @param leqno place equation tags on the left.
 * @param fleqn left-align display math.
 * @param colorIsTextColor make `\color` behave like `\textcolor`.
 * @param errorColor color (CSS syntax) used to render unsupported commands.
 */
data class LatexSettings(
    val displayMode: Boolean = false,
    val macros: Map<String, String> = emptyMap(),
    val maxSize: Float = Float.POSITIVE_INFINITY,
    val maxExpand: Int = 1000,
    val minRuleThickness: Float = 0f,
    val leqno: Boolean = false,
    val fleqn: Boolean = false,
    val colorIsTextColor: Boolean = false,
    val errorColor: String = "#cc0000",
    internal val globalGroup: Boolean = false,
)
