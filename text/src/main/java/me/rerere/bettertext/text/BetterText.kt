package me.rerere.bettertext.text

import android.graphics.Typeface
import android.text.StaticLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints

/**
 * Displays [text] with inline content, laid out with Android's `StaticLayout`.
 *
 * Inline items are added with [appendInline] and resolved through
 * [inlineContent]; remember the map, since a new map re-prepares the text.
 * Each line is at least `style.lineHeight` tall and grows to fit taller
 * inline items or larger text, instead of overlapping its neighbors.
 *
 * @param maxLines lines beyond this are cut off with an ellipsis.
 */
@Composable
fun BetterText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    inlineContent: Map<String, BetterInline> = emptyMap(),
    maxLines: Int = Int.MAX_VALUE,
) {
    require(maxLines > 0) { "maxLines must be positive" }
    val density = LocalDensity.current
    val fontFamilyResolver = LocalFontFamilyResolver.current
    val prepared = remember(text, inlineContent, style, density, fontFamilyResolver) {
        PreparedText.create(text, inlineContent, style, density) { family, weight, fontStyle, synthesis ->
            fontFamilyResolver.resolve(family, weight, fontStyle, synthesis).value as Typeface
        }
    }
    val cache = remember(prepared) { TextLayoutCache(prepared) }
    val holder = remember(prepared) { LayoutHolder() }

    Layout(
        contents = prepared.composables.map { it.inline.content },
        modifier = modifier
            .semantics { this.text = text }
            .drawBehind {
                val layout = holder.layout ?: return@drawBehind
                drawIntoCanvas { layout.draw(it.nativeCanvas) }
            },
    ) { measurables, constraints ->
        val loose = Constraints(maxWidth = constraints.maxWidth)
        val placeables = measurables.map { group -> group.map { it.measure(loose) } }
        val sizes = IntArray(placeables.size * 3)
        for ((i, group) in placeables.withIndex()) {
            val height = group.maxOfOrNull { it.height } ?: 0
            val baseline = group.firstOrNull()?.get(FirstBaseline)?.takeIf { it != AlignmentLine.Unspecified }
            sizes[i * 3] = group.maxOfOrNull { it.width } ?: 0
            sizes[i * 3 + 1] = height
            sizes[i * 3 + 2] = baseline ?: height
        }
        cache.measureComposables(sizes)
        val maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
        val layout = cache.layout(constraints.minWidth, maxWidth, maxLines)
        holder.layout = layout
        val width = layout.width.coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = layout.height.coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(
            width,
            height,
            mapOf(
                FirstBaseline to layout.getLineBaseline(0),
                LastBaseline to layout.getLineBaseline(layout.lineCount - 1),
            ),
        ) {
            for ((i, group) in placeables.withIndex()) {
                val (x, y) = cache.composablePosition(layout, i) ?: continue
                for (placeable in group) placeable.place(x, y)
            }
        }
    }
}

/** Displays plain [text]; see the [AnnotatedString] overload. */
@Composable
fun BetterText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    maxLines: Int = Int.MAX_VALUE,
) {
    BetterText(remember(text) { AnnotatedString(text) }, modifier, style, maxLines = maxLines)
}

/** The last layout, observable so drawing follows relayouts. */
private class LayoutHolder {
    var layout: StaticLayout? by mutableStateOf(null)
}
