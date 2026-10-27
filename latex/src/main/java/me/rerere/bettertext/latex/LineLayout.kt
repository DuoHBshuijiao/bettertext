package me.rerere.bettertext.latex

/** A line of chunks `[start, end)`, drawn with its left edge at [x] and baseline at [baseline] (em). */
internal class Line(val start: Int, val end: Int, val width: Float, val x: Float, val baseline: Float)

/**
 * Positioned lines of a [LatexLayout]. [tagX]/[tagBaseline] place the equation
 * tag, if any; [width] is the final width the lines were positioned in.
 */
internal class LineResult(
    val lines: List<Line>,
    val width: Float,
    val totalHeight: Float,
    val tagX: Float,
    val tagBaseline: Float,
)

/**
 * Breaks a layout's chunks into lines and positions them, cached by the
 * available width.
 *
 * Display math is centered (or indented with `fleqn`). The equation tag gets
 * its own space at the right (left with `leqno`), at least [TAG_GAP] away from
 * the body; when both don't fit, the tag moves to a line of its own, below the
 * body (above with `leqno`), like amsmath does.
 */
internal class LineCache(private val layout: LatexLayout) {
    private var cachedMax = Float.NaN
    private var cachedMin = Float.NaN
    private var cached: LineResult? = null

    /** The result of the most recent measurement. */
    val last: LineResult? get() = cached

    /**
     * @param maxWidth available width in em, may be infinite.
     * @param minWidth minimum width in em; display math is centered in at least this width.
     */
    fun measure(maxWidth: Float, minWidth: Float = 0f): LineResult {
        val c = cached
        if (c != null && cachedMax == maxWidth && cachedMin == minWidth) return c
        return compute(maxWidth, minWidth).also {
            cached = it
            cachedMax = maxWidth
            cachedMin = minWidth
        }
    }

    private class Metrics(val range: IntRange, val width: Float, val height: Float, val depth: Float)

    private fun compute(maxWidth: Float, minWidth: Float): LineResult {
        val chunks = layout.chunks
        val display = layout.displayMode

        // Break: inline math wraps greedily, display math only at explicit newlines.
        val ranges = ArrayList<IntRange>()
        var start = 0
        var lineWidth = 0f
        for (i in chunks.indices) {
            val w = chunks[i].advance
            val forced = layout.isLineBreakBefore(i)
            if (i > start && (forced || (!display && lineWidth + w > maxWidth + EPSILON))) {
                ranges.add(start until i)
                start = i
                lineWidth = 0f
            }
            lineWidth += w
        }
        if (start < chunks.size || ranges.isEmpty()) ranges.add(start until chunks.size)

        val metrics = ranges.map { range ->
            var width = 0f
            var height = MIN_ASCENT
            var depth = MIN_DESCENT
            for (i in range) {
                val chunk = chunks[i]
                width += chunk.advance
                height = maxOf(height, chunk.height)
                depth = maxOf(depth, chunk.depth)
            }
            Metrics(range, width, height, depth)
        }

        val tag = layout.tag
        val tagWidth = tag?.advance ?: 0f
        val leqno = layout.leqno
        val indent = if (display && layout.fleqn) FLEQN_INDENT else 0f
        // The tag shares the last line (first with leqno), like KaTeX's absolutely positioned tag.
        val tagLine = if (tag == null) -1 else if (leqno) 0 else metrics.lastIndex
        val tagSpace = if (tag == null) 0f else tagWidth + TAG_GAP

        val natural = metrics.withIndex().maxOf { (i, m) -> m.width + indent + if (i == tagLine) tagSpace else 0f }
        val width = when {
            display && maxWidth.isFinite() -> maxWidth
            else -> minOf(natural, maxWidth)
        }.coerceAtLeast(minWidth)
        val tagInline = tag != null && metrics[tagLine].width + indent + tagSpace <= width + EPSILON

        val lines = ArrayList<Line>()
        var y = 0f
        var tagBaseline = 0f

        fun addTagLine() {
            val t = tag ?: return
            val height = maxOf(MIN_ASCENT, t.height)
            tagBaseline = y + height
            y += height + maxOf(MIN_DESCENT, t.depth)
        }

        if (tag != null && !tagInline && leqno) addTagLine()
        for ((i, m) in metrics.withIndex()) {
            val withTag = tagInline && i == tagLine
            val height = if (withTag) maxOf(m.height, tag!!.height) else m.height
            val depth = if (withTag) maxOf(m.depth, tag!!.depth) else m.depth
            val x = if (display) {
                val left = if (withTag && leqno) tagSpace else 0f
                val right = if (withTag && !leqno) width - tagSpace else width
                val preferred = if (layout.fleqn) indent else (width - m.width) / 2f
                preferred.coerceIn(left, maxOf(left, right - m.width))
            } else {
                if (withTag && leqno) tagSpace else 0f
            }
            val baseline = y + height
            if (withTag) tagBaseline = baseline
            lines.add(Line(m.range.first, m.range.last + 1, m.width, x, baseline))
            y += height + depth
        }
        if (tag != null && !tagInline && !leqno) addTagLine()

        val tagX = if (leqno) 0f else width - tagWidth
        return LineResult(lines, width, y, tagX, tagBaseline)
    }

    internal companion object {
        // A strut like CSS line-height: 1.2 around the math axis.
        const val MIN_ASCENT = 0.75f
        const val MIN_DESCENT = 0.25f

        /** Minimum space between the body and the tag, amsmath's `\mintagsep` (0.5em). */
        const val TAG_GAP = 0.5f

        /** Left indent of `fleqn` display math, like KaTeX's `padding-left: 2em`. */
        const val FLEQN_INDENT = 2f

        private const val EPSILON = 1e-4f
    }
}
