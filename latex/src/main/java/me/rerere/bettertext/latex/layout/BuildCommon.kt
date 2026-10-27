package me.rerere.bettertext.latex.layout

import me.rerere.bettertext.latex.ParseError
import me.rerere.bettertext.latex.data.SvgPathData
import me.rerere.bettertext.latex.parse.Measurement
import me.rerere.bettertext.latex.parse.Mode
import me.rerere.bettertext.latex.parse.MathOrdNode
import me.rerere.bettertext.latex.parse.SymbolParseNode
import me.rerere.bettertext.latex.parse.Symbols

/*
 * Ported from KaTeX's buildCommon.ts. Functions keep KaTeX's names, but every
 * returned dimension is in absolute em (see [Options]).
 */

internal class SymbolLookup(val value: String, val metrics: CharacterMetrics?)

/** Looks up the given symbol in the font metrics, after applying symbol replacements. */
internal fun lookupSymbol(value: String, fontName: String, mode: Mode): SymbolLookup {
    val replacement = Symbols[mode][value]?.replace
    val v = replacement ?: value
    return SymbolLookup(v, FontMetrics.getCharacterMetrics(v, fontName, mode))
}

/** Makes a symbol box after translation via the symbol table, with correct metrics. */
internal fun makeSymbol(
    value: String,
    fontName: String,
    mode: Mode,
    options: Options,
    classes: List<String> = emptyList(),
): SymbolBox {
    val lookup = lookupSymbol(value, fontName, mode)
    val metrics = lookup.metrics
    val text = lookup.value
    val k = options.sizeMultiplier
    val symbol = if (metrics != null) {
        var italic = metrics.italic
        if (mode == Mode.TEXT || options.font == "mathit") italic = 0f
        // Unicode text without own metrics (e.g. CJK borrowing 'M') must be measured.
        val width = if (!FontMetrics.hasGlyph(text, fontName)) {
            options.measurer.measureWidth(text, fontName)
        } else {
            metrics.width
        }
        SymbolBox(text, fontName, metrics.height * k, metrics.depth * k, italic * k, metrics.skew * k, width * k, classes, k)
    } else {
        // No metrics: the browser would still draw it in a fallback font.
        val width = options.measurer.measureWidth(text, fontName)
        SymbolBox(text, fontName, 0.7f * k, 0.2f * k, 0f, 0f, width * k, classes, k)
    }
    symbol.maxFontSize = options.sizeMultiplier
    if (options.style.isTight()) symbol.classes.add("mtight")
    options.getColor()?.let { symbol.color = CssColor.parse(it) }
    return symbol
}

/** Makes a symbol in Main-Regular or AMS-Regular. Used for rel, bin, open, close, inner, and punct. */
internal fun mathsym(value: String, mode: Mode, options: Options, classes: List<String> = emptyList()): SymbolBox {
    return if (options.font == "boldsymbol" && lookupSymbol(value, "Main-Bold", mode).metrics != null) {
        makeSymbol(value, "Main-Bold", mode, options, classes + "mathbf")
    } else if (value == "\\" || Symbols[mode][value]?.font == "main") {
        makeSymbol(value, "Main-Regular", mode, options, classes)
    } else {
        makeSymbol(value, "AMS-Regular", mode, options, classes + "amsrm")
    }
}

private fun boldSymbol(value: String, mode: Mode, isTextord: Boolean): Pair<String, String> =
    if (!isTextord && lookupSymbol(value, "Math-BoldItalic", mode).metrics != null) {
        "Math-BoldItalic" to "boldsymbol"
    } else {
        "Main-Bold" to "mathbf"
    }

/** Makes either a mathord or textord in the correct font and color. */
internal fun makeOrd(group: SymbolParseNode, options: Options): Box {
    val isMathord = group is MathOrdNode
    val mode = group.mode
    val text = group.text
    val classes = listOf("mord")
    val font = options.font
    val useFont = mode == Mode.MATH || (mode == Mode.TEXT && font.isNotEmpty())
    val fontOrFamily = if (useFont) font else options.fontFamily

    var wideFontName = ""
    var wideFontClass = ""
    if (text.isNotEmpty() && text[0].code == 0xD835) {
        val wide = wideCharacterFont(text)
        wideFontName = wide.font
        wideFontClass = if (mode == Mode.MATH) wide.mathClass else wide.textClass
    }

    if (wideFontName.isNotEmpty()) {
        return makeSymbol(text, wideFontName, mode, options, classes + wideFontClass)
    } else if (fontOrFamily.isNotEmpty()) {
        val fontName: String
        val fontClasses: List<String>
        if (fontOrFamily == "boldsymbol") {
            val (name, cls) = boldSymbol(text, mode, !isMathord)
            fontName = name
            fontClasses = listOf(cls)
        } else if (useFont) {
            fontName = fontMap[font]?.fontName ?: "Main-Regular"
            fontClasses = listOf(font)
        } else {
            fontName = retrieveTextFontName(options.fontFamily, options.fontWeight, options.fontShape)
            fontClasses = listOf(options.fontFamily, options.fontWeight, options.fontShape)
        }
        if (lookupSymbol(text, fontName, mode).metrics != null) {
            return makeSymbol(text, fontName, mode, options, classes + fontClasses)
        } else if (text in Symbols.ligatures && fontName.startsWith("Typewriter")) {
            // Deconstruct ligatures in monospace fonts (\texttt, \tt).
            return makeFragment(text.map { makeSymbol(it.toString(), fontName, mode, options, classes + fontClasses) })
        }
    }

    if (isMathord) {
        return makeSymbol(text, "Math-Italic", mode, options, classes + "mathnormal")
    }
    val symFont = Symbols[mode][text]?.font
    return if (symFont == "ams") {
        val fontName = retrieveTextFontName("amsrm", options.fontWeight, options.fontShape)
        makeSymbol(text, fontName, mode, options, classes + listOf("amsrm", options.fontWeight, options.fontShape))
    } else {
        val fontName = retrieveTextFontName("textrm", options.fontWeight, options.fontShape)
        makeSymbol(text, fontName, mode, options, classes + listOf(options.fontWeight, options.fontShape))
    }
}

/** Makes a span with the given classes and children; sized from its children. */
internal fun makeSpan(
    classes: List<String> = emptyList(),
    children: List<Box> = emptyList(),
    options: Options? = null,
): SpanBox {
    val span = SpanBox(classes.filter { it.isNotEmpty() }, children.toMutableList())
    if (options != null) {
        if (options.style.isTight()) span.classes.add("mtight")
        options.getColor()?.let { span.color = CssColor.parse(it) }
    }
    span.sizeFromChildren()
    return span
}

/** A horizontal line as wide as its vlist, e.g. a fraction bar. */
internal fun makeLineSpan(className: String, options: Options, thickness: Float? = null): RuleBox {
    val line = RuleBox(listOf(className))
    options.getColor()?.let { line.color = CssColor.parse(it) }
    line.height = maxOf(thickness ?: options.fm.defaultRuleThickness, options.em(options.minRuleThickness))
    line.fillWidth = true
    line.maxFontSize = 1.0f
    return line
}

internal fun makeAnchor(href: String, classes: List<String>, children: List<Box>, options: Options): AnchorBox {
    val anchor = AnchorBox(href, classes, children.toMutableList())
    options.getColor()?.let { anchor.color = CssColor.parse(it) }
    anchor.sizeFromChildren()
    return anchor
}

internal fun makeFragment(children: List<Box>): FragmentBox =
    FragmentBox(children.toMutableList()).also { it.sizeFromChildren() }

/** Wraps a fragment in a span, so classes and styles can be applied. */
internal fun wrapFragment(group: Box, options: Options): Box =
    if (group is FragmentBox) makeSpan(emptyList(), listOf(group), options) else group

/** A child of a vlist before positioning. */
internal sealed class VListChild {
    class Elem(
        val elem: Box,
        val marginLeft: Float = 0f,
        val marginRight: Float = 0f,
        /** Overrides the vlist's alignment, e.g. KaTeX's `svg-align` wrapper class. */
        val align: HAlign? = null,
        /** Used with [VListPosition.IndividualShift]: downward shift of this child's baseline. */
        val shift: Float = 0f,
    ) : VListChild()

    class Kern(val size: Float) : VListChild()
}

internal sealed class VListPosition {
    /** Each [VListChild.Elem.shift] gives how far its baseline is shifted downward. */
    object IndividualShift : VListPosition()

    /** [top] is the topmost point of the vlist (a height: positive moves up). */
    class Top(val top: Float) : VListPosition()

    /** [bottom] is the bottommost point of the vlist (a depth: positive moves down). */
    class Bottom(val bottom: Float) : VListPosition()

    /** The baseline is [shift] away from the first child's baseline (positive moves down). */
    class Shift(val shift: Float) : VListPosition()

    /** The baseline is aligned with the first child's baseline. */
    object FirstBaseline : VListPosition()
}

/**
 * Makes a vertical list by stacking elements and kerns on top of each other,
 * like KaTeX's `makeVList`. Children are listed bottom to top.
 */
internal fun makeVList(
    position: VListPosition,
    children: List<VListChild>,
    align: HAlign = HAlign.LEFT,
    classes: List<String> = emptyList(),
): VListBox {
    var list: List<VListChild> = children
    val depth: Float
    when (position) {
        VListPosition.IndividualShift -> {
            val old = children.map { it as VListChild.Elem }
            val expanded = ArrayList<VListChild>()
            expanded.add(old[0])
            depth = -old[0].shift - old[0].elem.depth
            var currPos = depth
            for (i in 1 until old.size) {
                val diff = -old[i].shift - currPos - old[i].elem.depth
                val size = diff - (old[i - 1].elem.height + old[i - 1].elem.depth)
                currPos += diff
                expanded.add(VListChild.Kern(size))
                expanded.add(old[i])
            }
            list = expanded
        }

        is VListPosition.Top -> {
            var bottom = position.top
            for (child in children) {
                bottom -= when (child) {
                    is VListChild.Kern -> child.size
                    is VListChild.Elem -> child.elem.height + child.elem.depth
                }
            }
            depth = bottom
        }

        is VListPosition.Bottom -> depth = -position.bottom
        is VListPosition.Shift -> {
            val first = children[0] as? VListChild.Elem ?: error("First child must have type \"elem\".")
            depth = -first.elem.depth - position.shift
        }

        VListPosition.FirstBaseline -> {
            val first = children[0] as? VListChild.Elem ?: error("First child must have type \"elem\".")
            depth = -first.elem.depth
        }
    }

    // currPos is the distance from the vlist's baseline to the bottom of the current
    // child, measured upward.
    val items = ArrayList<VListItem>()
    var minPos = depth
    var maxPos = depth
    var currPos = depth
    var maxFontSize = 0f
    for (child in list) {
        when (child) {
            is VListChild.Kern -> currPos += child.size
            is VListChild.Elem -> {
                val elem = child.elem
                // Baseline of this child, measured downward from the vlist baseline.
                val y = -(currPos + elem.depth)
                items.add(VListItem(elem, y, child.marginLeft, child.marginRight, child.align))
                currPos += elem.height + elem.depth
                maxFontSize = maxOf(maxFontSize, elem.maxFontSize)
            }
        }
        minPos = minOf(minPos, currPos)
        maxPos = maxOf(maxPos, currPos)
    }
    val vlist = VListBox(items, align, classes)
    vlist.height = maxPos
    vlist.depth = -minPos
    vlist.maxFontSize = maxFontSize
    return vlist
}

/** Makes horizontal glue (an empty span with a margin), e.g. for `\,` or atom spacing. */
internal fun makeGlue(measurement: Measurement, options: Options): SpanBox {
    val rule = makeSpan(listOf("mspace"), emptyList(), options)
    rule.marginRight = calculateSizeAbs(measurement, options)
    return rule
}

internal fun retrieveTextFontName(fontFamily: String, fontWeight: String, fontShape: String): String {
    val base = when (fontFamily) {
        "amsrm" -> "AMS"
        "textrm" -> "Main"
        "textsf" -> "SansSerif"
        "texttt" -> "Typewriter"
        else -> fontFamily
    }
    val styles = when {
        fontWeight == "textbf" && fontShape == "textit" -> "BoldItalic"
        fontWeight == "textbf" -> "Bold"
        fontShape == "textit" -> "Italic"
        else -> "Regular"
    }
    return "$base-$styles"
}

internal class FontMapEntry(val variant: String, val fontName: String)

/** Maps TeX font commands to the font used for metrics and drawing. */
internal val fontMap: Map<String, FontMapEntry> = mapOf(
    "mathbf" to FontMapEntry("bold", "Main-Bold"),
    "mathrm" to FontMapEntry("normal", "Main-Regular"),
    "textit" to FontMapEntry("italic", "Main-Italic"),
    "mathit" to FontMapEntry("italic", "Main-Italic"),
    "mathnormal" to FontMapEntry("italic", "Math-Italic"),
    "mathsfit" to FontMapEntry("sans-serif-italic", "SansSerif-Italic"),
    "mathbb" to FontMapEntry("double-struck", "AMS-Regular"),
    "mathcal" to FontMapEntry("script", "Caligraphic-Regular"),
    "mathfrak" to FontMapEntry("fraktur", "Fraktur-Regular"),
    "mathscr" to FontMapEntry("script", "Script-Regular"),
    "mathsf" to FontMapEntry("sans-serif", "SansSerif-Regular"),
    "mathtt" to FontMapEntry("monospace", "Typewriter-Regular"),
)

/** Static SVG paths from svgGeometry.ts, keyed by name. */
internal object SvgPaths {
    val paths: Map<String, String> by lazy {
        SvgPathData.raw.split('\u0002').associate {
            val idx = it.indexOf('\u0001')
            it.substring(0, idx) to it.substring(idx + 1)
        }
    }

    operator fun get(name: String): String = paths[name] ?: throw ParseError("Unknown SVG path: $name")
}

private class StaticSvg(val path: String, val width: Float, val height: Float)

private val svgData = mapOf(
    "vec" to StaticSvg("vec", 0.471f, 0.714f),
    "oiintSize1" to StaticSvg("oiintSize1", 0.957f, 0.499f),
    "oiintSize2" to StaticSvg("oiintSize2", 1.472f, 0.659f),
    "oiiintSize1" to StaticSvg("oiiintSize1", 1.304f, 0.499f),
    "oiiintSize2" to StaticSvg("oiiintSize2", 1.98f, 0.659f),
)

/** A fixed-size SVG image (KaTeX's `staticSvg`), e.g. the arrow of `\vec`. */
internal fun staticSvg(value: String, options: Options): SvgBox {
    val data = svgData.getValue(value)
    val k = options.sizeMultiplier
    val box = SvgBox(
        listOf(SvgElement.Path(SvgPaths[data.path])),
        floatArrayOf(0f, 0f, 1000f * data.width, 1000f * data.height),
        svgHeight = data.height * k,
        preserveAspectRatio = "xMinYMin",
        classes = listOf("katex-overlay"),
    )
    options.getColor()?.let { box.color = CssColor.parse(it) }
    box.height = data.height * k
    box.width = data.width * k
    return box
}

internal class WideChar(val mathClass: String, val textClass: String, val font: String)

private val boldUpright = WideChar("mathbf", "textbf", "Main-Bold")
private val italicWide = WideChar("mathnormal", "textit", "Math-Italic")
private val boldItalicWide = WideChar("boldsymbol", "boldsymbol", "Main-BoldItalic")
private val scriptWide = WideChar("mathscr", "textscr", "Script-Regular")
private val noFont = WideChar("", "", "")
private val frakturWide = WideChar("mathfrak", "textfrak", "Fraktur-Regular")
private val doubleStruck = WideChar("mathbb", "textbb", "AMS-Regular")
private val boldFraktur = WideChar("mathboldfrak", "textboldfrak", "Fraktur-Regular")
private val sansSerif = WideChar("mathsf", "textsf", "SansSerif-Regular")
private val boldSansSerif = WideChar("mathboldsf", "textboldsf", "SansSerif-Bold")
private val italicSansSerif = WideChar("mathitsf", "textitsf", "SansSerif-Italic")
private val monospace = WideChar("mathtt", "texttt", "Typewriter-Regular")

private val wideLatinLetterData = listOf(
    boldUpright, boldUpright, italicWide, italicWide, boldItalicWide, boldItalicWide,
    scriptWide, noFont, noFont, noFont, frakturWide, frakturWide, doubleStruck, doubleStruck,
    boldFraktur, boldFraktur, sansSerif, sansSerif, boldSansSerif, boldSansSerif,
    italicSansSerif, italicSansSerif, noFont, noFont, monospace, monospace,
)

private val wideNumeralData = listOf(boldUpright, noFont, sansSerif, boldSansSerif, monospace)

/** Font information for a character in the Mathematical Alphanumeric Symbols block. */
internal fun wideCharacterFont(wideChar: String): WideChar {
    val codePoint = wideChar.codePointAt(0)
    return when {
        codePoint in 0x1D400 until 0x1D6A4 -> wideLatinLetterData[(codePoint - 0x1D400) / 26]
        codePoint in 0x1D7CE..0x1D7FF -> wideNumeralData[(codePoint - 0x1D7CE) / 10]
        codePoint == 0x1D6A5 || codePoint == 0x1D6A6 -> wideLatinLetterData[0]
        codePoint in 0x1D6A7 until 0x1D7CE -> noFont
        else -> throw ParseError("Unsupported character: $wideChar")
    }
}
