package me.rerere.bettertext.latex.layout

/**
 * Measures text that has no entry in the KaTeX metric tables (e.g. CJK inside `\text{}`).
 * Returns the advance width in em of the given font.
 */
internal fun interface GlyphMeasurer {
    fun measureWidth(text: String, font: String): Float

    companion object {
        /** Fallback used when no platform measurer is available. */
        val Approximate = GlyphMeasurer { text, _ ->
            text.codePoints().toArray().sumOf { cp -> if (cp >= 0x1100) 1.0 else 0.5 }.toFloat()
        }
    }
}

/**
 * The state threaded through the builders: style, size, color and font.
 *
 * Unlike KaTeX, every [Box] dimension is absolute, measured in em of the base
 * (normalsize, textstyle) font size. Values that KaTeX expresses in the current
 * font's em must therefore be multiplied by [sizeMultiplier]; [fm] does that
 * for the TeX font parameters.
 */
internal class Options(
    val style: Style,
    @get:JvmName("colorValue") val color: String? = null,
    val size: Int = BASESIZE,
    val textSize: Int = size,
    val phantom: Boolean = false,
    val font: String = "",
    val fontFamily: String = "",
    val fontWeight: String = "",
    val fontShape: String = "",
    val maxSize: Float = Float.POSITIVE_INFINITY,
    val minRuleThickness: Float = 0f,
    val measurer: GlyphMeasurer = GlyphMeasurer.Approximate,
) {
    val sizeMultiplier: Float = sizeMultipliers[size - 1]

    private fun copy(
        style: Style = this.style,
        color: String? = this.color,
        size: Int = this.size,
        textSize: Int = this.textSize,
        phantom: Boolean = this.phantom,
        font: String = this.font,
        fontFamily: String = this.fontFamily,
        fontWeight: String = this.fontWeight,
        fontShape: String = this.fontShape,
    ) = Options(
        style, color, size, textSize, phantom, font, fontFamily, fontWeight, fontShape,
        maxSize, minRuleThickness, measurer,
    )

    fun havingStyle(style: Style): Options =
        if (this.style === style) this else copy(style = style, size = sizeAtStyle(textSize, style))

    fun havingCrampedStyle(): Options = havingStyle(style.cramp())

    fun havingSize(size: Int): Options =
        if (this.size == size && textSize == size) this
        else copy(style = style.text(), size = size, textSize = size)

    /** Like [havingSize] (BASESIZE), but the resulting style is [style] (default: text version of current). */
    fun havingBaseStyle(style: Style? = null): Options {
        val target = style ?: this.style.text()
        val wantSize = sizeAtStyle(BASESIZE, target)
        return if (size == wantSize && textSize == BASESIZE && this.style === target) this
        else copy(style = target, size = wantSize)
    }

    /** Remove the effect of sizing changes such as `\Huge`, keeping the current style. */
    fun havingBaseSizing(): Options {
        val size = when (style.id) {
            4, 5 -> 3
            6, 7 -> 1
            else -> 6
        }
        return copy(style = style.text(), size = size)
    }

    fun withColor(color: String): Options = copy(color = color)
    fun withPhantom(): Options = copy(phantom = true)
    fun withFont(font: String): Options = copy(font = font)
    fun withTextFontFamily(fontFamily: String): Options = copy(fontFamily = fontFamily, font = "")
    fun withTextFontWeight(fontWeight: String): Options = copy(fontWeight = fontWeight, font = "")
    fun withTextFontShape(fontShape: String): Options = copy(fontShape = fontShape, font = "")

    /** TeX font parameters for the current size, in em of the current font. */
    fun fontMetrics(): GlobalMetrics = GlobalMetrics.forSize(size)

    /** Shorthand for [fontMetrics] scaled to absolute em (the units of [Box]). */
    val fm: ScaledMetrics get() = ScaledMetrics(fontMetrics(), sizeMultiplier)

    /** The color to draw with; `"transparent"` inside `\phantom`. */
    fun getColor(): String? = if (phantom) "transparent" else color

    /** Converts a length in em of the current font to absolute em. */
    fun em(value: Float): Float = value * sizeMultiplier

    companion object {
        const val BASESIZE = 6

        private val sizeStyleMap = arrayOf(
            intArrayOf(1, 1, 1), intArrayOf(2, 1, 1), intArrayOf(3, 1, 1),
            intArrayOf(4, 2, 1), intArrayOf(5, 2, 1), intArrayOf(6, 3, 1),
            intArrayOf(7, 4, 2), intArrayOf(8, 6, 3), intArrayOf(9, 7, 6),
            intArrayOf(10, 8, 7), intArrayOf(11, 10, 9),
        )

        val sizeMultipliers = floatArrayOf(0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1.0f, 1.2f, 1.44f, 1.728f, 2.074f, 2.488f)

        fun sizeAtStyle(size: Int, style: Style): Int =
            if (style.size < 2) size else sizeStyleMap[size - 1][style.size - 1]
    }
}

/** [GlobalMetrics] multiplied by a size multiplier, i.e. in absolute em. */
@Suppress("unused")
internal class ScaledMetrics(private val m: GlobalMetrics, private val k: Float) {
    val slant get() = m.slant
    val xHeight get() = m.xHeight * k
    val quad get() = m.quad * k
    val num1 get() = m.num1 * k
    val num2 get() = m.num2 * k
    val num3 get() = m.num3 * k
    val denom1 get() = m.denom1 * k
    val denom2 get() = m.denom2 * k
    val sup1 get() = m.sup1 * k
    val sup2 get() = m.sup2 * k
    val sup3 get() = m.sup3 * k
    val sub1 get() = m.sub1 * k
    val sub2 get() = m.sub2 * k
    val supDrop get() = m.supDrop * k
    val subDrop get() = m.subDrop * k
    val delim1 get() = m.delim1 * k
    val delim2 get() = m.delim2 * k
    val axisHeight get() = m.axisHeight * k
    val defaultRuleThickness get() = m.defaultRuleThickness * k
    val bigOpSpacing1 get() = m.bigOpSpacing1 * k
    val bigOpSpacing2 get() = m.bigOpSpacing2 * k
    val bigOpSpacing3 get() = m.bigOpSpacing3 * k
    val bigOpSpacing4 get() = m.bigOpSpacing4 * k
    val bigOpSpacing5 get() = m.bigOpSpacing5 * k
    val sqrtRuleThickness get() = m.sqrtRuleThickness * k
    val doubleRuleSep get() = m.doubleRuleSep * k
    val arrayRuleWidth get() = m.arrayRuleWidth * k
    val fboxsep get() = m.fboxsep * k
    val fboxrule get() = m.fboxrule * k
}
