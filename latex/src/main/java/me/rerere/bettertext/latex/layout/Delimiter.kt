package me.rerere.bettertext.latex.layout

import me.rerere.bettertext.latex.ParseError
import me.rerere.bettertext.latex.parse.Mode
import me.rerere.bettertext.latex.parse.Symbols
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/*
 * Ported from KaTeX's delimiter.ts. This file deals with creating delimiters
 * of various sizes. The TeXbook discusses these routines on page 441-442, in
 * the "Another subroutine sets box x to a specified variable delimiter"
 * paragraph.
 *
 * There are three main routines here. `makeSmallDelim` makes a delimiter in the
 * normal font, but in either text, script, or scriptscript style.
 * `makeLargeDelim` makes a delimiter in textstyle, but in one of the Size1,
 * Size2, Size3, or Size4 fonts. `makeStackedDelim` makes a delimiter out of
 * smaller pieces that are stacked on top of one another.
 *
 * The functions take a parameter `center`, which determines if the delimiter
 * should be centered around the axis.
 *
 * Then, there are three exposed functions. `makeSizedDelim` makes a delimiter in
 * one of the given sizes. This is used for things like `\bigl`.
 * `makeCustomSizedDelim` makes a delimiter with a given total height+depth. It is
 * called in places like `\sqrt`. `makeLeftRightDelim` makes an appropriate
 * delimiter which surrounds an expression of a given height an depth. It is
 * used in `\left` and `\right`.
 *
 * Units: KaTeX already treats the requested delimiter heights as absolute
 * (base-size) em, and returns spans measured in the current font's em. Here
 * every returned dimension is absolute em, so the `delimSizeMultiplier`
 * rescaling of `styleWrap` is dropped and the glyphs are built directly with
 * the options of the size they are drawn at.
 */

/**
 * Get the metrics for a given symbol and font, after transformation (i.e.
 * after following replacement from symbols.js)
 */
private fun getMetrics(symbol: String, font: String, mode: Mode): CharacterMetrics {
    val replace = Symbols.math[symbol]?.replace
    return FontMetrics.getCharacterMetrics(replace ?: symbol, font, mode)
        ?: throw ParseError("Unsupported symbol $symbol and font size $font.")
}

/**
 * Puts a delimiter in a given style, and adds appropriate maxFontSize. The
 * delimiter must already be built with `options.havingBaseStyle(toStyle)`.
 */
private fun styleWrap(delim: Box, toStyle: Style, options: Options, classes: List<String>): SpanBox {
    val newOptions = options.havingBaseStyle(toStyle)
    val span = makeSpan(classes, listOf(delim), options)
    span.maxFontSize = newOptions.sizeMultiplier
    return span
}

private fun centerSpan(span: SpanBox, options: Options, style: Style) {
    val newOptions = options.havingBaseStyle(style)
    val shift = (1 - options.sizeMultiplier / newOptions.sizeMultiplier) * options.fontMetrics().axisHeight

    span.classes.add("delimcenter")
    // CSS `top` is in the em of the span itself, which carries the sizing
    // classes of newOptions; KaTeX's height bookkeeping uses the outer em.
    span.shiftY = shift * newOptions.sizeMultiplier
    span.height -= shift * options.sizeMultiplier
    span.depth += shift * options.sizeMultiplier
}

/**
 * Makes a small delimiter. This is a delimiter that comes in the Main-Regular
 * font, but is restyled to either be in textstyle, scriptstyle, or
 * scriptscriptstyle.
 */
private fun makeSmallDelim(
    delim: String,
    style: Style,
    center: Boolean,
    options: Options,
    mode: Mode,
    classes: List<String>,
): SpanBox {
    val text = makeSymbol(delim, "Main-Regular", mode, options.havingBaseStyle(style))
    val span = styleWrap(text, style, options, classes)
    if (center) centerSpan(span, options, style)
    return span
}

/** Builds a symbol in the given font size (note size is an integer). */
private fun mathrmSize(value: String, size: Int, mode: Mode, options: Options): SymbolBox =
    makeSymbol(value, "Size$size-Regular", mode, options)

/**
 * Makes a large delimiter. This is a delimiter that comes in the Size1, Size2,
 * Size3, or Size4 fonts. It is always rendered in textstyle.
 */
private fun makeLargeDelim(
    delim: String,
    size: Int,
    center: Boolean,
    options: Options,
    mode: Mode,
    classes: List<String>,
): SpanBox {
    val inner = mathrmSize(delim, size, mode, options.havingBaseStyle(Style.TEXT))
    val span = styleWrap(
        makeSpan(listOf("delimsizing", "size$size"), listOf(inner), options),
        Style.TEXT, options, classes,
    )
    if (center) centerSpan(span, options, Style.TEXT)
    return span
}

/**
 * Make a span from a font glyph with the given offset and in the given font.
 * This is used in makeStackedDelim to make the stacking pieces for the delimiter.
 */
private fun makeGlyphSpan(symbol: String, font: String, mode: Mode, options: Options): VListChild {
    // Apply the correct CSS class to choose the right font.
    val sizeClass = if (font == "Size1-Regular") "delim-size1" else "delim-size4"
    val corner = makeSpan(
        listOf("delimsizinginner", sizeClass),
        listOf(makeSpan(emptyList(), listOf(makeSymbol(symbol, font, mode, options)))),
    )
    // Since this will be passed into `makeVList` in the end, wrap the element
    // in the appropriate tag that VList uses.
    return VListChild.Elem(corner)
}

private fun makeInner(ch: String, height: Float, options: Options): VListChild {
    // Create a span with inline SVG for the inner part of a tall stacked delimiter.
    val width = (
        if (FontMetrics.hasGlyph(ch, "Size4-Regular")) {
            FontMetrics.getCharacterMetrics(ch, "Size4-Regular", Mode.MATH)
        } else {
            FontMetrics.getCharacterMetrics(ch, "Size1-Regular", Mode.MATH)
        }
        )?.width ?: 0f
    val k = options.sizeMultiplier
    val viewBoxHeight = (1000 * height).roundToInt()
    val svg = SvgBox(
        listOf(SvgElement.Path(innerPath(ch, viewBoxHeight))),
        floatArrayOf(0f, 0f, 1000 * width, viewBoxHeight.toFloat()),
        svgHeight = height * k,
        preserveAspectRatio = "xMinYMin",
    )
    options.getColor()?.let { svg.color = CssColor.parse(it) }
    svg.width = width * k
    svg.height = height * k
    return VListChild.Elem(svg)
}

// Helpers for makeStackedDelim
private const val LAP_IN_EMS = 0.008f
private val verts = setOf("|", "\\lvert", "\\rvert", "\\vert")
private val doubleVerts = setOf("\\|", "\\lVert", "\\rVert", "\\Vert")

/**
 * Make a stacked delimiter out of a given delimiter, with the total height at
 * least `heightTotal`. This routine is mentioned on page 442 of the TeXbook.
 */
private fun makeStackedDelim(
    delim: String,
    heightTotal: Float,
    center: Boolean,
    options: Options,
    mode: Mode,
    classes: List<String>,
): SpanBox {
    // There are four parts, the top, an optional middle, a repeated part, and a
    // bottom.
    var top = delim
    var repeat = delim
    var bottom = delim
    var middle: String? = null
    var svgLabel = ""
    var viewBoxWidth = 0
    // Also keep track of what font the delimiters are in
    var font = "Size1-Regular"

    // We set the parts and font based on the symbol. Note that we use
    // '⏐' instead of '|' and '‖' instead of '\\|' for the
    // repeats of the arrows
    when {
        delim == "\\uparrow" -> {
            repeat = "⏐"; bottom = "⏐"
        }

        delim == "\\Uparrow" -> {
            repeat = "‖"; bottom = "‖"
        }

        delim == "\\downarrow" -> {
            top = "⏐"; repeat = "⏐"
        }

        delim == "\\Downarrow" -> {
            top = "‖"; repeat = "‖"
        }

        delim == "\\updownarrow" -> {
            top = "\\uparrow"; repeat = "⏐"; bottom = "\\downarrow"
        }

        delim == "\\Updownarrow" -> {
            top = "\\Uparrow"; repeat = "‖"; bottom = "\\Downarrow"
        }

        delim in verts -> {
            repeat = "∣"; svgLabel = "vert"; viewBoxWidth = 333
        }

        delim in doubleVerts -> {
            repeat = "∥"; svgLabel = "doublevert"; viewBoxWidth = 556
        }

        delim == "[" || delim == "\\lbrack" -> {
            top = "⎡"; repeat = "⎢"; bottom = "⎣"
            font = "Size4-Regular"; svgLabel = "lbrack"; viewBoxWidth = 667
        }

        delim == "]" || delim == "\\rbrack" -> {
            top = "⎤"; repeat = "⎥"; bottom = "⎦"
            font = "Size4-Regular"; svgLabel = "rbrack"; viewBoxWidth = 667
        }

        delim == "\\lfloor" || delim == "⌊" -> {
            repeat = "⎢"; top = "⎢"; bottom = "⎣"
            font = "Size4-Regular"; svgLabel = "lfloor"; viewBoxWidth = 667
        }

        delim == "\\lceil" || delim == "⌈" -> {
            top = "⎡"; repeat = "⎢"; bottom = "⎢"
            font = "Size4-Regular"; svgLabel = "lceil"; viewBoxWidth = 667
        }

        delim == "\\rfloor" || delim == "⌋" -> {
            repeat = "⎥"; top = "⎥"; bottom = "⎦"
            font = "Size4-Regular"; svgLabel = "rfloor"; viewBoxWidth = 667
        }

        delim == "\\rceil" || delim == "⌉" -> {
            top = "⎤"; repeat = "⎥"; bottom = "⎥"
            font = "Size4-Regular"; svgLabel = "rceil"; viewBoxWidth = 667
        }

        delim == "(" || delim == "\\lparen" -> {
            top = "⎛"; repeat = "⎜"; bottom = "⎝"
            font = "Size4-Regular"; svgLabel = "lparen"; viewBoxWidth = 875
        }

        delim == ")" || delim == "\\rparen" -> {
            top = "⎞"; repeat = "⎟"; bottom = "⎠"
            font = "Size4-Regular"; svgLabel = "rparen"; viewBoxWidth = 875
        }

        delim == "\\{" || delim == "\\lbrace" -> {
            top = "⎧"; middle = "⎨"; bottom = "⎩"; repeat = "⎪"
            font = "Size4-Regular"
        }

        delim == "\\}" || delim == "\\rbrace" -> {
            top = "⎫"; middle = "⎬"; bottom = "⎭"; repeat = "⎪"
            font = "Size4-Regular"
        }

        delim == "\\lgroup" || delim == "⟮" -> {
            top = "⎧"; bottom = "⎩"; repeat = "⎪"; font = "Size4-Regular"
        }

        delim == "\\rgroup" || delim == "⟯" -> {
            top = "⎫"; bottom = "⎭"; repeat = "⎪"; font = "Size4-Regular"
        }

        delim == "\\lmoustache" || delim == "⎰" -> {
            top = "⎧"; bottom = "⎭"; repeat = "⎪"; font = "Size4-Regular"
        }

        delim == "\\rmoustache" || delim == "⎱" -> {
            top = "⎫"; bottom = "⎩"; repeat = "⎪"; font = "Size4-Regular"
        }
    }

    // Get the metrics of the four sections
    val topMetrics = getMetrics(top, font, mode)
    val topHeightTotal = topMetrics.height + topMetrics.depth
    val repeatMetrics = getMetrics(repeat, font, mode)
    val repeatHeightTotal = repeatMetrics.height + repeatMetrics.depth
    val bottomMetrics = getMetrics(bottom, font, mode)
    val bottomHeightTotal = bottomMetrics.height + bottomMetrics.depth
    var middleHeightTotal = 0f
    var middleFactor = 1
    if (middle != null) {
        val middleMetrics = getMetrics(middle, font, mode)
        middleHeightTotal = middleMetrics.height + middleMetrics.depth
        middleFactor = 2 // repeat symmetrically above and below middle
    }

    // Calculate the minimal height that the delimiter can have.
    // It is at least the size of the top, bottom, and optional middle combined.
    val minHeight = topHeightTotal + bottomHeightTotal + middleHeightTotal

    // Compute the number of copies of the repeat symbol we will need
    val repeatCount = maxOf(0f, ceil((heightTotal - minHeight) / (middleFactor * repeatHeightTotal)))

    // Compute the total height of the delimiter including all the symbols
    val realHeightTotal = minHeight + repeatCount * middleFactor * repeatHeightTotal

    // The center of the delimiter is placed at the center of the axis. Note
    // that in this context, "center" means that the delimiter should be
    // centered around the axis in the current style, while normally it is
    // centered around the axis in textstyle.
    var axisHeight = options.fontMetrics().axisHeight
    if (center) axisHeight *= options.sizeMultiplier
    // Calculate the depth
    val depth = realHeightTotal / 2 - axisHeight

    // The pieces are drawn in textstyle at the base size (KaTeX wraps them in
    // sizing classes of `options.havingBaseStyle(Style.TEXT)`).
    val newOptions = options.havingBaseStyle(Style.TEXT)
    val k = newOptions.sizeMultiplier

    // Now, we start building the pieces that will go into the vlist
    // Keep a list of the pieces of the stacked delimiter
    val stack = ArrayList<VListChild>()

    if (svgLabel.isNotEmpty()) {
        // Instead of stacking glyphs, create a single SVG.
        // This evades browser problems with imprecise positioning of spans.
        val midHeight = realHeightTotal - topHeightTotal - bottomHeightTotal
        val viewBoxHeight = (realHeightTotal * 1000).roundToInt()
        val pathStr = tallDelim(svgLabel, (midHeight * 1000).roundToInt())
        val width = viewBoxWidth / 1000f
        val height = viewBoxHeight / 1000f
        val svg = SvgBox(
            listOf(SvgElement.Path(pathStr)),
            floatArrayOf(0f, 0f, viewBoxWidth.toFloat(), viewBoxHeight.toFloat()),
            svgHeight = height * k,
        )
        options.getColor()?.let { svg.color = CssColor.parse(it) }
        svg.width = width * k
        svg.height = height * k
        stack.add(VListChild.Elem(svg))
    } else {
        val lap = VListChild.Kern(-LAP_IN_EMS * k)
        // Stack glyphs
        // Start by adding the bottom symbol
        stack.add(makeGlyphSpan(bottom, font, mode, newOptions))
        stack.add(lap) // overlap

        if (middle == null) {
            // The middle section will be an SVG. Make it an extra 0.016em tall.
            // We'll overlap by 0.008em at top and bottom.
            val innerHeight = realHeightTotal - topHeightTotal - bottomHeightTotal + 2 * LAP_IN_EMS
            stack.add(makeInner(repeat, innerHeight, newOptions))
        } else {
            // When there is a middle bit, we need the middle part and two repeated
            // sections
            val innerHeight =
                (realHeightTotal - topHeightTotal - bottomHeightTotal - middleHeightTotal) / 2 + 2 * LAP_IN_EMS
            stack.add(makeInner(repeat, innerHeight, newOptions))
            // Now insert the middle of the brace.
            stack.add(lap)
            stack.add(makeGlyphSpan(middle, font, mode, newOptions))
            stack.add(lap)
            stack.add(makeInner(repeat, innerHeight, newOptions))
        }

        // Add the top symbol
        stack.add(lap)
        stack.add(makeGlyphSpan(top, font, mode, newOptions))
    }

    // Finally, build the vlist
    val inner = makeVList(VListPosition.Bottom(depth * k), stack)

    return styleWrap(
        makeSpan(listOf("delimsizing", "mult"), listOf(inner), newOptions),
        Style.TEXT, options, classes,
    )
}

// All surds have 0.08em padding above the vinculum inside the SVG.
// That keeps browser span height rounding error from pinching the line.
private const val VB_PAD = 80f // padding above the surd, measured inside the viewBox.
private const val EM_PAD = 0.08f // padding, in ems, measured in the document.

/** The surd and the dimensions [makeSqrtImage] computes, all in absolute em. */
internal class SqrtImage(val span: SvgBox, val ruleWidth: Float, val advanceWidth: Float, val minWidth: Float)

/**
 * Make a sqrt image of the given [height] (absolute em, like every box
 * dimension). The returned span is a `hide-tail` svg: `xMinYMin slice` with
 * [Box.fillWidth], so its 400em vinculum is cut off at the width of the
 * enclosing vlist.
 */
internal fun makeSqrtImage(height: Float, options: Options): SqrtImage {
    // KaTeX works in the em of the current font here; convert at the end.
    val hgt = height / options.sizeMultiplier

    // Define a newOptions that removes the effect of size changes such as \Huge.
    // We don't pick different a height surd for \Huge. For it, we scale up.
    val newOptions = options.havingBaseSizing()

    // Pick the desired surd glyph from a sequence of surds.
    val delim = traverseSequence("\\surd", hgt * newOptions.sizeMultiplier, stackLargeDelimiterSequence, newOptions)

    var sizeMultiplier = newOptions.sizeMultiplier // default

    // The standard sqrt SVGs each have a 0.04em thick vinculum.
    // If Settings.minRuleThickness is larger than that, we add extraVinculum.
    val extraVinculum = maxOf(0f, options.minRuleThickness - options.fontMetrics().sqrtRuleThickness)

    // We create viewBoxes with 80 units of "padding" above each surd.
    // Then browser rounding error on the parent span height will not
    // encroach on the ink of the vinculum. But that padding is not
    // included in the TeX-like `height` used for calculation of
    // vertical alignment. So texHeight = span.height < span.style.height.
    val sqrtName: String
    val spanHeight: Float
    val texHeight: Float
    val viewBoxHeight: Float
    val advanceWidth: Float
    val minWidth: Float
    when (delim) {
        is DelimType.Small -> {
            // Get an SVG that is derived from glyph U+221A in font KaTeX-Main.
            // 1000 unit normal glyph height.
            viewBoxHeight = 1000 + 1000 * extraVinculum + VB_PAD
            if (hgt < 1.0f) {
                sizeMultiplier = 1.0f // mimic a \textfont radical
            } else if (hgt < 1.4f) {
                sizeMultiplier = 0.7f // mimic a \scriptfont radical
            }
            spanHeight = (1.0f + extraVinculum + EM_PAD) / sizeMultiplier
            texHeight = (1.00f + extraVinculum) / sizeMultiplier
            sqrtName = "sqrtMain"
            minWidth = 0.853f
            advanceWidth = 0.833f / sizeMultiplier // from the font.
        }

        is DelimType.Large -> {
            // These SVGs come from fonts: KaTeX_Size1, _Size2, etc.
            viewBoxHeight = (1000 + VB_PAD) * sizeToMaxHeight[delim.size]
            texHeight = (sizeToMaxHeight[delim.size] + extraVinculum) / sizeMultiplier
            spanHeight = (sizeToMaxHeight[delim.size] + extraVinculum + EM_PAD) / sizeMultiplier
            sqrtName = "sqrtSize${delim.size}"
            minWidth = 1.02f
            advanceWidth = 1.0f / sizeMultiplier // 1.0 from the font.
        }

        DelimType.Stack -> {
            // Tall sqrt. In TeX, this would be stacked using multiple glyphs.
            // We'll use a single SVG to accomplish the same thing.
            spanHeight = hgt + extraVinculum + EM_PAD
            texHeight = hgt + extraVinculum
            viewBoxHeight = floor(1000 * hgt + extraVinculum) + VB_PAD
            sqrtName = "sqrtTall"
            minWidth = 0.742f
            advanceWidth = 1.056f
        }
    }

    val k = options.sizeMultiplier
    val span = SvgBox(
        // Note: 1000:1 ratio of viewBox to document em width.
        listOf(SvgElement.Path(sqrtPath(sqrtName, extraVinculum, viewBoxHeight))),
        floatArrayOf(0f, 0f, 400000f, viewBoxHeight),
        svgHeight = spanHeight * k,
        preserveAspectRatio = "xMinYMin slice",
        classes = listOf("hide-tail"),
    )
    options.getColor()?.let { span.color = CssColor.parse(it) }
    // `.hide-tail { width: 100%; overflow: hidden }`: the svg rect is clipped.
    span.fillWidth = true
    span.clip = true
    span.height = texHeight * k

    return SqrtImage(
        span,
        // Calculate the actual line width.
        // This actually should depend on the chosen font -- e.g. \boldmath
        // should use the thicker surd symbols from e.g. KaTeX_Main-Bold, and
        // have thicker rules.
        ruleWidth = (options.fontMetrics().sqrtRuleThickness + extraVinculum) * sizeMultiplier * k,
        advanceWidth = advanceWidth * k,
        minWidth = minWidth * k,
    )
}

// There are three kinds of delimiters, delimiters that stack when they become
// too large
private val stackLargeDelimiters = setOf(
    "(", "\\lparen", ")", "\\rparen",
    "[", "\\lbrack", "]", "\\rbrack",
    "\\{", "\\lbrace", "\\}", "\\rbrace",
    "\\lfloor", "\\rfloor", "⌊", "⌋",
    "\\lceil", "\\rceil", "⌈", "⌉",
    "\\surd",
)

// delimiters that always stack
private val stackAlwaysDelimiters = setOf(
    "\\uparrow", "\\downarrow", "\\updownarrow",
    "\\Uparrow", "\\Downarrow", "\\Updownarrow",
    "|", "\\|", "\\vert", "\\Vert",
    "\\lvert", "\\rvert", "\\lVert", "\\rVert",
    "\\lgroup", "\\rgroup", "⟮", "⟯",
    "\\lmoustache", "\\rmoustache", "⎰", "⎱",
)

// and delimiters that never stack
private val stackNeverDelimiters = setOf(
    "<", ">", "\\langle", "\\rangle", "/", "\\backslash", "\\lt", "\\gt",
)

/**
 * Metrics of the different sizes. Found by looking at TeX's output of
 * `$\bigl| // \Bigl| \biggl| \Biggl| \showlists$`.
 * Used to create stacked delimiters of appropriate sizes in makeSizedDelim.
 */
internal val sizeToMaxHeight = floatArrayOf(0f, 1.2f, 1.8f, 2.4f, 3.0f)

private fun normalizeAngle(delim: String): String = when (delim) {
    "<", "\\lt", "⟨" -> "\\langle"
    ">", "\\gt", "⟩" -> "\\rangle"
    else -> delim
}

/** Used to create a delimiter of a specific size, where `size` is 1, 2, 3, or 4. */
internal fun makeSizedDelim(
    delimIn: String,
    size: Int,
    options: Options,
    mode: Mode,
    classes: List<String>,
): SpanBox {
    // < and > turn into \langle and \rangle in delimiters
    val delim = normalizeAngle(delimIn)

    // Sized delimiters are never centered.
    return if (delim in stackLargeDelimiters || delim in stackNeverDelimiters) {
        makeLargeDelim(delim, size, false, options, mode, classes)
    } else if (delim in stackAlwaysDelimiters) {
        makeStackedDelim(delim, sizeToMaxHeight[size], false, options, mode, classes)
    } else {
        throw ParseError("Illegal delimiter: '$delim'")
    }
}

/*
 * There are three different sequences of delimiter sizes that the delimiters
 * follow depending on the kind of delimiter. This is used when creating custom
 * sized delimiters to decide whether to create a small, large, or stacked
 * delimiter.
 *
 * In real TeX, these sequences aren't explicitly defined, but are instead
 * defined inside the font metrics. Since there are only three sequences that
 * are possible for the delimiters that TeX defines, it is easier to just encode
 * them explicitly here.
 */

private sealed class DelimType {
    class Small(val style: Style) : DelimType()
    class Large(val size: Int) : DelimType()
    object Stack : DelimType()
}

// Delimiters that never stack try small delimiters and large delimiters only
private val stackNeverDelimiterSequence = listOf(
    DelimType.Small(Style.SCRIPTSCRIPT),
    DelimType.Small(Style.SCRIPT),
    DelimType.Small(Style.TEXT),
    DelimType.Large(1),
    DelimType.Large(2),
    DelimType.Large(3),
    DelimType.Large(4),
)

// Delimiters that always stack try the small delimiters first, then stack
private val stackAlwaysDelimiterSequence = listOf(
    DelimType.Small(Style.SCRIPTSCRIPT),
    DelimType.Small(Style.SCRIPT),
    DelimType.Small(Style.TEXT),
    DelimType.Stack,
)

// Delimiters that stack when large try the small and then large delimiters, and
// stack afterwards
private val stackLargeDelimiterSequence = listOf(
    DelimType.Small(Style.SCRIPTSCRIPT),
    DelimType.Small(Style.SCRIPT),
    DelimType.Small(Style.TEXT),
    DelimType.Large(1),
    DelimType.Large(2),
    DelimType.Large(3),
    DelimType.Large(4),
    DelimType.Stack,
)

/** Get the font used in a delimiter based on what kind of delimiter it is. */
private fun delimTypeToFont(type: DelimType): String = when (type) {
    is DelimType.Small -> "Main-Regular"
    is DelimType.Large -> "Size${type.size}-Regular"
    DelimType.Stack -> "Size4-Regular"
}

/**
 * Traverse a sequence of types of delimiters to decide what kind of delimiter
 * should be used to create a delimiter of the given height+depth.
 */
private fun traverseSequence(delim: String, height: Float, sequence: List<DelimType>, options: Options): DelimType {
    // Here, we choose the index we should start at in the sequences. In smaller
    // sizes (which correspond to larger numbers in style.size) we start earlier
    // in the sequence. Thus, scriptscript starts at index 3-3=0, script starts
    // at index 3-2=1, text starts at 3-1=2, and display starts at min(2,3-0)=2
    val start = minOf(2, 3 - options.style.size)
    for (i in start until sequence.size) {
        val delimType = sequence[i]
        // This is always the last delimiter, so we just break the loop now.
        if (delimType is DelimType.Stack) break

        val metrics = getMetrics(delim, delimTypeToFont(delimType), Mode.MATH)
        var heightDepth = metrics.height + metrics.depth

        // Small delimiters are scaled down versions of the same font, so we
        // account for the style change size.
        if (delimType is DelimType.Small) {
            val newOptions = options.havingBaseStyle(delimType.style)
            heightDepth *= newOptions.sizeMultiplier
        }

        // Check if the delimiter at this size works for the given height.
        if (heightDepth > height) return delimType
    }

    // If we reached the end of the sequence, return the last sequence element.
    return sequence.last()
}

/**
 * Make a delimiter of a given height+depth (absolute em), with optional
 * centering. Here, we traverse the sequences, and create a delimiter that the
 * sequence tells us to.
 */
internal fun makeCustomSizedDelim(
    delimIn: String,
    height: Float,
    center: Boolean,
    options: Options,
    mode: Mode,
    classes: List<String>,
): SpanBox {
    val delim = normalizeAngle(delimIn)

    // Decide what sequence to use
    val sequence = when (delim) {
        in stackNeverDelimiters -> stackNeverDelimiterSequence
        in stackLargeDelimiters -> stackLargeDelimiterSequence
        else -> stackAlwaysDelimiterSequence
    }

    // Look through the sequence
    // Get the delimiter from font glyphs.
    // Depending on the sequence element we decided on, call the
    // appropriate function.
    return when (val delimType = traverseSequence(delim, height, sequence, options)) {
        is DelimType.Small -> makeSmallDelim(delim, delimType.style, center, options, mode, classes)
        is DelimType.Large -> makeLargeDelim(delim, delimType.size, center, options, mode, classes)
        DelimType.Stack -> makeStackedDelim(delim, height, center, options, mode, classes)
    }
}

/**
 * Make a delimiter for use with `\left` and `\right`, given a height and depth
 * of an expression that the delimiters surround.
 */
internal fun makeLeftRightDelim(
    delim: String,
    height: Float,
    depth: Float,
    options: Options,
    mode: Mode,
    classes: List<String>,
): SpanBox {
    // We always center \left/\right delimiters, so the axis is always shifted
    val axisHeight = options.fontMetrics().axisHeight * options.sizeMultiplier

    // Taken from TeX source, tex.web, function make_left_right
    val delimiterFactor = 901
    val delimiterExtend = 5.0f / options.fontMetrics().ptPerEm

    val maxDistFromAxis = maxOf(height - axisHeight, depth + axisHeight)

    val totalHeight = maxOf(
        // In real TeX, calculations are done using integral values which are
        // 65536 per pt, or 655360 per em. So, the division here truncates in
        // TeX but doesn't here, producing different results. If we wanted to
        // exactly match TeX's calculation, we could do
        //   Math.floor(655360 * maxDistFromAxis / 500) *
        //    delimiterFactor / 655360
        // (To see the difference, compare
        //    x^{x^{\left(\rule{0.1em}{0.68em}\right)}}
        // in TeX and KaTeX)
        maxDistFromAxis / 500 * delimiterFactor,
        2 * maxDistFromAxis - delimiterExtend,
    )

    // Finally, we defer to `makeCustomSizedDelim` with our calculated total
    // height
    return makeCustomSizedDelim(delim, totalHeight, true, options, mode, classes)
}
