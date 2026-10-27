package me.rerere.bettertext.latex.parse

import me.rerere.bettertext.latex.data.UnicodeData

internal class UnicodeAccent(val text: String, val math: String?)

internal object UnicodeTables {
    /** Mapping of Unicode combining accents to their LaTeX equivalent in text and math mode. */
    val accents: Map<Char, UnicodeAccent> = mapOf(
        '́' to UnicodeAccent("\\'", "\\acute"),
        '̀' to UnicodeAccent("\\`", "\\grave"),
        '̈' to UnicodeAccent("\\\"", "\\ddot"),
        '̃' to UnicodeAccent("\\~", "\\tilde"),
        '̄' to UnicodeAccent("\\=", "\\bar"),
        '̆' to UnicodeAccent("\\u", "\\breve"),
        '̌' to UnicodeAccent("\\v", "\\check"),
        '̂' to UnicodeAccent("\\^", "\\hat"),
        '̇' to UnicodeAccent("\\.", "\\dot"),
        '̊' to UnicodeAccent("\\r", "\\mathring"),
        '̋' to UnicodeAccent("\\H", null),
        '̧' to UnicodeAccent("\\c", null),
    )

    /** Precomposed characters (e.g. `é`) mapped to base character plus combining marks. */
    val symbols: Map<Char, String> = parsePairs(UnicodeData.symbols).mapKeys { it.key[0] }

    /** Unicode sub/superscript characters mapped to their plain equivalents. */
    val subsAndSups: Map<String, String> = parsePairs(UnicodeData.subsAndSups)

    fun isSubscript(text: String) = text.isNotEmpty() && UnicodeData.SUB_CHARS.indexOf(text[0]) >= 0

    /** Whether a codepoint belongs to a script that may appear in `\text{}`. */
    fun supportedCodepoint(codepoint: Int): Boolean {
        val ranges = UnicodeData.supportedRanges
        var i = 0
        while (i < ranges.size) {
            if (codepoint >= ranges[i] && codepoint <= ranges[i + 1]) return true
            i += 2
        }
        return false
    }

    private fun parsePairs(raw: String): Map<String, String> =
        raw.split('\u0002').associate { record ->
            val idx = record.indexOf('\u0001')
            record.substring(0, idx) to record.substring(idx + 1)
        }
}
