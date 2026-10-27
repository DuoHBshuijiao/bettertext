package me.rerere.bettertext.latex.parse

import me.rerere.bettertext.latex.data.SymbolsData

/** An entry of the symbol table: which font draws it, its group and the character to draw. */
internal class SymbolInfo(val font: String, val group: String, val replace: String?)

/** KaTeX's `symbols` table, keyed by mode then by command name or character. */
internal object Symbols {
    val math: Map<String, SymbolInfo>
    val text: Map<String, SymbolInfo>

    init {
        val math = HashMap<String, SymbolInfo>(2048)
        val text = HashMap<String, SymbolInfo>(1024)
        for (record in SymbolsData.raw.split('\u0002')) {
            val f = record.split('\u0001')
            val info = SymbolInfo(f[2], f[3], f[4].ifEmpty { null })
            if (f[0] == "math") math[f[1]] = info else text[f[1]] = info
        }
        this.math = math
        this.text = text
    }

    operator fun get(mode: Mode): Map<String, SymbolInfo> = if (mode == Mode.MATH) math else text

    val ligatures = setOf("--", "---", "``", "''")

    /** Latin-1 letters kept for backwards compatibility, not actually in the fonts. */
    const val EXTRA_LATIN = "ÐÞþ"

    private val atoms = setOf("bin", "close", "inner", "open", "punct", "rel")

    fun isAtom(group: String) = group in atoms
}
