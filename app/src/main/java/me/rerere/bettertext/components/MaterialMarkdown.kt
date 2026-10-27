package me.rerere.bettertext.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import me.rerere.bettertext.markdown.MarkdownBlock
import me.rerere.bettertext.markdown.MarkdownComponents
import me.rerere.bettertext.markdown.MarkdownInlineContent
import me.rerere.bettertext.markdown.MarkdownTheme

/** The Markdown theme in Material 3 colors. */
@Composable
internal fun rememberMaterialMarkdownTheme(): MarkdownTheme {
    val colors = MaterialTheme.colorScheme
    return remember(colors) {
        MarkdownTheme(
            link = SpanStyle(color = colors.primary, textDecoration = TextDecoration.Underline),
            inlineCode = SpanStyle(color = colors.primary, fontFamily = FontFamily.Monospace, fontSize = 0.9.em),
            markerColor = colors.primary,
            quoteColor = colors.outlineVariant,
            dividerColor = colors.outlineVariant,
        )
    }
}

/** Components an app registers, here built with Material 3. */
internal val materialMarkdownComponents = MarkdownComponents(
    codeBlock = { CodeBlock(it) },
    table = { MaterialTable(it) },
    image = { image, _ -> ImagePlaceholder(image.alt ?: image.url) },
    details = { block, summary, content -> CollapsibleDetails(block.open, summary, content) },
)

@Composable
private fun CodeBlock(block: MarkdownBlock.CodeBlock) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            block.language?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                text = block.code,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                softWrap = false,
            )
        }
    }
}

@Composable
private fun ImagePlaceholder(label: String) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Text("🖼 $label", Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun CollapsibleDetails(open: Boolean, summary: @Composable () -> Unit, content: @Composable () -> Unit) {
    // The tag streams in piece by piece, so follow `open` if it arrives later.
    var expanded by rememberSaveable(open) { mutableStateOf(open) }
    val rotation by animateFloatAsState(if (expanded) 90f else 0f)
    val color = MaterialTheme.colorScheme.onSurface
    Column {
        Row(
            Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(
                Modifier
                    .size(18.dp)
                    .rotate(rotation)
                    .drawBehind {
                        val path = Path().apply {
                            moveTo(size.width * 0.4f, size.height * 0.25f)
                            lineTo(size.width * 0.65f, size.height * 0.5f)
                            lineTo(size.width * 0.4f, size.height * 0.75f)
                        }
                        drawPath(path, color, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    },
            )
            summary()
        }
        AnimatedVisibility(visible = expanded) {
            Box(Modifier.padding(start = 22.dp, top = 6.dp)) { content() }
        }
    }
}

/**
 * Columns as wide as their widest cell within 80–200 dp, rows as tall as their
 * tallest cell, scrolling horizontally when too wide.
 */
@Composable
private fun MaterialTable(table: MarkdownBlock.Table) {
    val rows = listOf(table.header) + table.rows
    val columns = rows.maxOf { it.size }
    val grid = MaterialTheme.colorScheme.outlineVariant
    // Column and row boundaries from the last measurement, for drawing the grid.
    val lines = remember { arrayOf(IntArray(0), IntArray(0)) }
    Box(
        Modifier
            .border(1.dp, grid, MaterialTheme.shapes.small)
            .clip(MaterialTheme.shapes.small)
            .horizontalScroll(rememberScrollState()),
    ) {
        Layout(
            content = {
                rows.forEachIndexed { r, row ->
                    for (c in 0 until columns) {
                        val cell = row.getOrNull(c)
                        val align = cell?.style?.textAlign?.takeIf { it != TextAlign.Unspecified }
                            ?: table.alignments.getOrNull(c) ?: TextAlign.Start
                        Box(
                            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = when (align) {
                                TextAlign.Center -> Alignment.Center
                                TextAlign.End, TextAlign.Right -> Alignment.CenterEnd
                                else -> Alignment.CenterStart
                            },
                        ) {
                            if (cell != null) {
                                MarkdownInlineContent(
                                    cell.content,
                                    style = TextStyle(fontWeight = if (r == 0) FontWeight.SemiBold else null).merge(cell.style),
                                )
                            }
                        }
                    }
                }
            },
            modifier = Modifier.drawBehind {
                val stroke = 1.dp.toPx()
                for (i in 1 until lines[0].size - 1) {
                    val x = lines[0][i].toFloat()
                    drawLine(grid, Offset(x, 0f), Offset(x, size.height), stroke)
                }
                for (i in 1 until lines[1].size - 1) {
                    val y = lines[1][i].toFloat()
                    drawLine(grid, Offset(0f, y), Offset(size.width, y), stroke)
                }
            },
        ) { measurables, _ ->
            val minWidth = 80.dp.roundToPx()
            val maxWidth = 200.dp.roundToPx()
            val widths = IntArray(columns) { c ->
                rows.indices.maxOf { r -> measurables[r * columns + c].maxIntrinsicWidth(Constraints.Infinity) }
                    .coerceIn(minWidth, maxWidth)
            }
            val placeables = measurables.mapIndexed { i, m -> m.measure(Constraints.fixedWidth(widths[i % columns])) }
            val heights = IntArray(rows.size) { r -> (0 until columns).maxOf { placeables[r * columns + it].height } }
            val xs = IntArray(columns + 1).also { for (i in 0 until columns) it[i + 1] = it[i] + widths[i] }
            val ys = IntArray(rows.size + 1).also { for (i in rows.indices) it[i + 1] = it[i] + heights[i] }
            lines[0] = xs
            lines[1] = ys
            layout(xs.last(), ys.last()) {
                placeables.forEachIndexed { i, p -> p.place(xs[i % columns], ys[i / columns]) }
            }
        }
    }
}
