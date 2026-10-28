package me.rerere.bettertext.text

import android.text.StaticLayout
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.text.AnnotatedString

/**
 * Geometry in local pixels, addressed by offsets in the original [text].
 * Inline formulas may occupy several layout characters and decorations none of the input;
 * callers never need to know those expanded offsets.
 */
class BetterTextLayoutResult internal constructor(
    val text: AnnotatedString,
    private val layout: StaticLayout,
    private val starts: IntArray,
    private val ends: IntArray,
) {
    val lineCount: Int get() = layout.lineCount

    fun getPathForRange(start: Int, end: Int): Path {
        require(start in 0..text.length && end in start..text.length)
        val path = android.graphics.Path()
        val from = starts[start]
        val to = ends[end].coerceAtLeast(from)
        if (from < to) layout.getSelectionPath(from, to, path)
        return path.asComposePath()
    }

    fun getLineForOffset(offset: Int): Int {
        require(offset in 0..text.length)
        return layout.getLineForOffset(starts[offset])
    }

    fun getLineTop(line: Int): Float = layout.getLineTop(line).toFloat()

    fun getLineBottom(line: Int): Float = layout.getLineBottom(line).toFloat()
}
