package me.rerere.bettertext.latex.layout

/**
 * The layout tree, the counterpart of KaTeX's HTML DOM tree.
 *
 * KaTeX only tracks heights and depths and lets the browser compute widths and
 * horizontal positions; here every box knows its width so it can be drawn
 * directly. All dimensions are absolute em of the base font size, with y
 * pointing down: a box spans `[-height, depth]` around its baseline.
 *
 * CSS behaviors KaTeX relies on map to fields:
 * - `margin-left/right` → [marginLeft], [marginRight] (horizontal advance only)
 * - `position: relative; top` / `vertical-align` → [shiftY] (drawing only, no layout effect)
 * - `width: 100%` inside a vlist → [fillWidth]
 * - `color` → [color], inherited by children when null
 */
internal sealed class Box(classes: List<String> = emptyList()) {
    val classes: MutableList<String> = classes.toMutableList()
    open var width: Float = 0f
    var height: Float = 0f
    var depth: Float = 0f
    var maxFontSize: Float = 0f

    /** Italic correction; on non-symbols it is carried over for supsub (e.g. \oiint). */
    var italic: Float = 0f

    var marginLeft: Float = 0f
    var marginRight: Float = 0f

    /** Vertical drawing offset, positive moves down. Does not affect height/depth. */
    var shiftY: Float = 0f

    /** ARGB color, or null to inherit. */
    var color: Int? = null

    /** Stretch to the width of the enclosing vlist, like CSS `width: 100%`. */
    var fillWidth: Boolean = false

    var backgroundColor: Int? = null
    var borderWidth: Float = 0f
    var borderColor: Int? = null

    /** Clip children to the box bounds, like CSS `overflow: hidden`. */
    var clip: Boolean = false

    /** Horizontal space taken in a horizontal list. */
    open val advance: Float get() = marginLeft + width + marginRight

    fun hasClass(c: String) = c in classes

    /** Assign the width resolved by an enclosing vlist to this `width: 100%` box. */
    open fun assignFillWidth(w: Float) {
        width = w
    }
}

/** A run of text drawn in a single font, KaTeX's `SymbolNode`. */
internal class SymbolBox(
    var text: String,
    /** KaTeX font name, e.g. `Main-Regular`, or empty when no metrics were found. */
    val font: String,
    height: Float = 0f,
    depth: Float = 0f,
    italic: Float = 0f,
    var skew: Float = 0f,
    width: Float = 0f,
    classes: List<String> = emptyList(),
    /** Font size in em of the base size. */
    val scale: Float = 1f,
) : Box(classes) {
    init {
        this.height = height
        this.depth = depth
        this.width = width
        this.italic = italic
    }

    /** KaTeX adds the italic correction as right margin. */
    override val advance: Float get() = marginLeft + width + marginRight + if (italic > 0) italic else 0f
}

/** A horizontal list (KaTeX's span). Width defaults to the sum of the children's advances. */
internal open class SpanBox(
    classes: List<String> = emptyList(),
    val children: MutableList<Box> = ArrayList(),
) : Box(classes) {
    /** Explicit CSS width; null means shrink to content. */
    var explicitWidth: Float? = null

    override var width: Float
        get() = explicitWidth ?: children.sumOf { it.advance.toDouble() }.toFloat()
        set(value) {
            explicitWidth = value
        }

    override fun assignFillWidth(w: Float) {
        explicitWidth = w
        for (child in children) if (child.fillWidth) child.assignFillWidth(w)
    }

    /** Set height, depth and maxFontSize from the children, like KaTeX's `sizeElementFromChildren`. */
    fun sizeFromChildren() {
        var h = 0f
        var d = 0f
        var m = 0f
        for (child in children) {
            if (child.height > h) h = child.height
            if (child.depth > d) d = child.depth
            if (child.maxFontSize > m) m = child.maxFontSize
        }
        height = h
        depth = d
        maxFontSize = m
    }
}

/** KaTeX's DocumentFragment: flattened into the parent by `buildExpression`. */
internal class FragmentBox(children: MutableList<Box>) : SpanBox(emptyList(), children)

/** A hyperlink; transparent for spacing like KaTeX's Anchor. */
internal class AnchorBox(val href: String, classes: List<String>, children: MutableList<Box>) :
    SpanBox(classes, children)

internal enum class HAlign { LEFT, CENTER, RIGHT }

/** A child of a [VListBox], positioned with its baseline at [y] (positive down) from the vlist baseline. */
internal class VListItem(
    val box: Box,
    val y: Float,
    val marginLeft: Float = 0f,
    val marginRight: Float = 0f,
    val align: HAlign? = null,
)

/** A vertical list (KaTeX's vlist table). Children are stacked by [makeVList]. */
internal class VListBox(
    val items: List<VListItem>,
    /** CSS `text-align` of the vlist: how narrower children are placed. */
    val align: HAlign = HAlign.LEFT,
    classes: List<String> = emptyList(),
) : Box(classes) {
    private var resolvedWidth: Float = -1f

    override var width: Float
        get() {
            if (resolvedWidth < 0f) resolve()
            return resolvedWidth
        }
        set(value) {
            resolvedWidth = value
            assignFills(value)
        }

    private fun resolve() {
        var w = 0f
        for (item in items) {
            if (item.box.fillWidth) {
                w = maxOf(w, item.marginLeft + item.marginRight)
            } else {
                w = maxOf(w, item.marginLeft + item.box.advance + item.marginRight)
            }
        }
        resolvedWidth = w
        assignFills(w)
    }

    private fun assignFills(w: Float) {
        for (item in items) {
            if (item.box.fillWidth) item.box.assignFillWidth(w - item.marginLeft - item.marginRight)
        }
    }

    /** Recompute the width after children changed. */
    fun invalidate() {
        resolvedWidth = -1f
    }

    /** Horizontal offset of an item's box inside the vlist. */
    fun itemX(item: VListItem): Float {
        val w = width
        val avail = w - item.marginLeft - item.marginRight
        val slack = avail - item.box.advance
        return item.marginLeft + when (item.align ?: align) {
            HAlign.LEFT -> 0f
            HAlign.CENTER -> slack / 2f
            HAlign.RIGHT -> slack
        }
    }
}

/** A filled rectangle covering the whole box, used for fraction bars, rules and `\hline`. */
internal class RuleBox(classes: List<String> = emptyList(), val dashed: Boolean = false) : Box(classes)

internal sealed class SvgElement {
    /** An SVG path in viewBox coordinates. */
    class Path(val d: String) : SvgElement()

    /**
     * A stroked line. Coordinates are fractions of the svg rect (KaTeX uses
     * percentages for `\cancel`); [strokeWidth] is in absolute em.
     */
    class Line(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        val strokeWidth: Float,
    ) : SvgElement()
}

/**
 * An inline SVG image, like a KaTeX svg span with `style.height = svgHeight`:
 * the viewBox is mapped into the rect `[0, -svgHeight]..[width, 0]` relative
 * to the baseline, according to [preserveAspectRatio].
 */
internal class SvgBox(
    val elements: List<SvgElement>,
    /** minX, minY, width, height; null for percentage-based lines. */
    val viewBox: FloatArray?,
    var svgHeight: Float,
    val preserveAspectRatio: String = "xMidYMid meet",
    classes: List<String> = emptyList(),
) : Box(classes)

/** Reads the width of a box including its margins. */
internal val Box.outerWidth: Float get() = advance
