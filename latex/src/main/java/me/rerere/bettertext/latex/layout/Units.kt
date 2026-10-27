package me.rerere.bettertext.latex.layout

import me.rerere.bettertext.latex.ParseError
import me.rerere.bettertext.latex.parse.Measurement

private val ptPerUnit = mapOf(
    "pt" to 1f,
    "mm" to 7227f / 2540f,
    "cm" to 7227f / 254f,
    "in" to 72.27f,
    "bp" to 803f / 800f,
    "pc" to 12f,
    "dd" to 1238f / 1157f,
    "cc" to 14856f / 1157f,
    "nd" to 685f / 642f,
    "nc" to 1370f / 107f,
    "sp" to 1f / 65536f,
    "px" to 803f / 800f,
)

internal fun validUnit(unit: String): Boolean =
    unit in ptPerUnit || unit == "ex" || unit == "em" || unit == "mu"

/**
 * Converts a measurement into em of the current font, like KaTeX's `calculateSize`.
 * Multiply by [Options.sizeMultiplier] (or use [calculateSizeAbs]) for box units.
 */
internal fun calculateSize(sizeValue: Measurement, options: Options): Float {
    val scale: Float
    val pt = ptPerUnit[sizeValue.unit]
    if (pt != null) {
        scale = pt / options.fontMetrics().ptPerEm / options.sizeMultiplier
    } else if (sizeValue.unit == "mu") {
        scale = options.fontMetrics().cssEmPerMu
    } else {
        val unitOptions = if (options.style.isTight()) options.havingStyle(options.style.text()) else options
        var s = when (sizeValue.unit) {
            "ex" -> unitOptions.fontMetrics().xHeight
            "em" -> unitOptions.fontMetrics().quad
            else -> throw ParseError("Invalid unit: '${sizeValue.unit}'")
        }
        if (unitOptions !== options) s *= unitOptions.sizeMultiplier / options.sizeMultiplier
        scale = s
    }
    return minOf(sizeValue.number * scale, options.maxSize)
}

/** [calculateSize] in absolute em, the unit of [Box] dimensions. */
internal fun calculateSizeAbs(sizeValue: Measurement, options: Options): Float =
    calculateSize(sizeValue, options) * options.sizeMultiplier
