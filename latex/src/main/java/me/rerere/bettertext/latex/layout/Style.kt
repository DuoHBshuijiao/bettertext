package me.rerere.bettertext.latex.layout

/**
 * One of TeX's eight styles: display, text, script, scriptscript, each normal or cramped.
 * [size] is shared by the cramped and uncramped variants.
 */
internal class Style private constructor(val id: Int, val size: Int, val cramped: Boolean) {
    fun sup(): Style = styles[SUP[id]]
    fun sub(): Style = styles[SUB[id]]
    fun fracNum(): Style = styles[FRAC_NUM[id]]
    fun fracDen(): Style = styles[FRAC_DEN[id]]
    fun cramp(): Style = styles[CRAMP[id]]
    fun text(): Style = styles[TEXT_[id]]

    /** True for scriptstyle and scriptscriptstyle. */
    fun isTight(): Boolean = size >= 2

    companion object {
        private const val D = 0
        private const val DC = 1
        private const val T = 2
        private const val TC = 3
        private const val S = 4
        private const val SC = 5
        private const val SS = 6
        private const val SSC = 7

        private val styles = listOf(
            Style(D, 0, false), Style(DC, 0, true),
            Style(T, 1, false), Style(TC, 1, true),
            Style(S, 2, false), Style(SC, 2, true),
            Style(SS, 3, false), Style(SSC, 3, true),
        )

        private val SUP = intArrayOf(S, SC, S, SC, SS, SSC, SS, SSC)
        private val SUB = intArrayOf(SC, SC, SC, SC, SSC, SSC, SSC, SSC)
        private val FRAC_NUM = intArrayOf(T, TC, S, SC, SS, SSC, SS, SSC)
        private val FRAC_DEN = intArrayOf(TC, TC, SC, SC, SSC, SSC, SSC, SSC)
        private val CRAMP = intArrayOf(DC, DC, TC, TC, SC, SC, SSC, SSC)
        private val TEXT_ = intArrayOf(D, DC, T, TC, T, TC, T, TC)

        val DISPLAY: Style = styles[D]
        val TEXT: Style = styles[T]
        val SCRIPT: Style = styles[S]
        val SCRIPTSCRIPT: Style = styles[SS]

        fun fromName(name: String): Style = when (name) {
            "display" -> DISPLAY
            "text" -> TEXT
            "script" -> SCRIPT
            "scriptscript" -> SCRIPTSCRIPT
            else -> TEXT
        }
    }
}
