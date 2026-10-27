package me.rerere.bettertext.latex

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.sp
import me.rerere.bettertext.latex.render.MathRenderer
import me.rerere.bettertext.latex.render.MathTypefaces
import kotlin.math.ceil
import kotlin.math.roundToInt

/** KaTeX renders math at 1.21 times the surrounding font size. */
const val DefaultMathScale = 1.21f

/** Parses and lays out [latex], measuring glyphs with the bundled fonts. */
@Composable
fun rememberLatexLayout(latex: String, settings: LatexSettings = LatexSettings()): LatexLayout {
    val typefaces = MathTypefaces.get(LocalContext.current)
    return remember(latex, settings) { LatexLayout.create(latex, settings, typefaces.measurer) }
}

/**
 * Renders a LaTeX formula.
 *
 * Inline formulas wrap after top-level relations and binary operators when
 * they don't fit the available width, like KaTeX in a browser. Display
 * formulas are centered; use [LatexBlock] to make wide ones scroll.
 *
 * @param fontSize size of the surrounding text; the formula is drawn [scale] times larger.
 */
@Composable
fun Latex(
    latex: String,
    modifier: Modifier = Modifier,
    displayMode: Boolean = false,
    fontSize: TextUnit = 16.sp,
    color: Color = Color.Black,
    scale: Float = DefaultMathScale,
    macros: Map<String, String> = emptyMap(),
) {
    val layout = rememberLatexLayout(latex, LatexSettings(displayMode = displayMode, macros = macros))
    Latex(layout, modifier, fontSize, color, scale)
}

/** Renders an already laid-out formula. */
@Composable
fun Latex(
    layout: LatexLayout,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 16.sp,
    color: Color = Color.Black,
    scale: Float = DefaultMathScale,
) {
    val renderer = rememberRenderer()
    val lines = remember(layout) { LineCache(layout) }
    val argb = (if (color.isSpecified) color else Color.Black).toArgb()
    Layout(
        modifier = modifier.drawBehind {
            val em = emPx(fontSize, scale)
            val result = lines.last ?: lines.measure(size.width / em, size.width / em)
            drawLines(renderer, layout, result, em, argb)
        },
    ) { _, constraints ->
        val em = fontSize.toPx() * scale
        val maxWidthEm = if (constraints.hasBoundedWidth) constraints.maxWidth / em else Float.POSITIVE_INFINITY
        val result = lines.measure(maxWidthEm, constraints.minWidth / em)
        val width = ceil(result.width * em).toInt().coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = ceil(result.totalHeight * em).toInt().coerceIn(constraints.minHeight, constraints.maxHeight)
        val first = result.lines.firstOrNull()
        val last = result.lines.lastOrNull()
        this.layout(
            width,
            height,
            mapOf(
                FirstBaseline to ((first?.baseline ?: 0f) * em).roundToInt(),
                LastBaseline to ((last?.baseline ?: 0f) * em).roundToInt(),
            ),
        ) {}
    }
}

/** A display formula that scrolls horizontally when wider than the available width. */
@Composable
fun LatexBlock(
    latex: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 16.sp,
    color: Color = Color.Black,
    scale: Float = DefaultMathScale,
    macros: Map<String, String> = emptyMap(),
) {
    val layout = rememberLatexLayout(latex, LatexSettings(displayMode = true, macros = macros))
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // At least as wide as the viewport, so the body is centered and the tag
        // sits at the edge; wider formulas scroll.
        Latex(
            layout,
            Modifier
                .horizontalScroll(rememberScrollState())
                .widthIn(min = maxWidth),
            fontSize,
            color,
            scale,
        )
    }
}

/**
 * Renders a single unbreakable chunk of [layout]. Hosts that interleave math with
 * text (one placeholder per chunk) use this to draw each piece.
 * The composable reports [FirstBaseline] at the chunk's baseline.
 */
@Composable
fun LatexChunk(
    layout: LatexLayout,
    index: Int,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 16.sp,
    color: Color = Color.Black,
    scale: Float = DefaultMathScale,
) {
    val renderer = rememberRenderer()
    val argb = (if (color.isSpecified) color else Color.Black).toArgb()
    Layout(
        modifier = modifier.drawBehind {
            val em = emPx(fontSize, scale)
            val chunk = layout.chunks[index]
            drawIntoCanvas {
                renderer.draw(it.nativeCanvas, chunk, 0f, chunk.height * em, em, argb)
            }
        },
    ) { _, constraints ->
        val em = fontSize.toPx() * scale
        val w = ceil(layout.chunkWidth(index) * em).toInt()
        val h = ceil((layout.chunkHeight(index) + layout.chunkDepth(index)) * em).toInt()
        val baseline = (layout.chunkHeight(index) * em).roundToInt()
        this.layout(
            w.coerceIn(constraints.minWidth, maxOf(constraints.minWidth, constraints.maxWidth)),
            h.coerceIn(constraints.minHeight, maxOf(constraints.minHeight, constraints.maxHeight)),
            mapOf(FirstBaseline to baseline, LastBaseline to baseline),
        ) {}
    }
}

@Composable
private fun rememberRenderer(): MathRenderer {
    val context = LocalContext.current
    return remember { MathRenderer(MathTypefaces.get(context)) }
}

private fun DrawScope.emPx(fontSize: TextUnit, scale: Float) = fontSize.toPx() * scale

private fun DrawScope.drawLines(renderer: MathRenderer, layout: LatexLayout, result: LineResult, em: Float, argb: Int) {
    drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas
        for (line in result.lines) {
            var x = line.x
            for (i in line.start until line.end) {
                val chunk = layout.chunks[i]
                renderer.draw(native, chunk, x * em, line.baseline * em, em, argb)
                x += chunk.advance
            }
        }
        layout.tag?.let { renderer.draw(native, it, result.tagX * em, result.tagBaseline * em, em, argb) }
    }
}

