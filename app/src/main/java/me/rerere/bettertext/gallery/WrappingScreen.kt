package me.rerere.bettertext.gallery

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import me.rerere.bettertext.latex.Latex
import me.rerere.bettertext.latex.LatexChunk
import me.rerere.bettertext.latex.LatexSettings
import me.rerere.bettertext.latex.rememberLatexLayout
import kotlin.math.roundToInt

/** Shows how formulas break into lines as the available width changes. */
@Composable
internal fun WrappingScreen(onBack: () -> Unit) {
    var fraction by rememberSaveable { mutableFloatStateOf(0.6f) }
    var formula by rememberSaveable { mutableIntStateOf(0) }
    var leqno by rememberSaveable { mutableStateOf(false) }
    val fontSize = MaterialTheme.typography.bodyLarge.fontSize

    GalleryScaffold(title = "换行与混排", onBack = onBack) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val width = (maxWidth - 32.dp) * fraction
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("容器宽度 ${width.value.roundToInt()} dp", style = MaterialTheme.typography.labelLarge)
                Slider(value = fraction, onValueChange = { fraction = it }, valueRange = 0.2f..1f)

                Heading("行内公式自动换行", "只在顶层关系符、二元运算符和 \\allowbreak 之后断行，与浏览器中的 KaTeX 一致")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    wrapSamples.forEachIndexed { i, (label, _) ->
                        FilterChip(selected = i == formula, onClick = { formula = i }, label = { Text(label) })
                    }
                }
                val latex = wrapSamples[formula].second
                Frame(width) {
                    Latex(latex, fontSize = fontSize, color = MaterialTheme.colorScheme.onSurface)
                }

                Heading("断点（chunk）", "每个方框是一个不可再分的 chunk，BetterText 会为每个 chunk 生成一个占位符")
                ChunkFlow(latex, width, fontSize)

                Heading("块级公式与编号", "放不下时编号移到下一行；leqno 时编号在左侧，移到上一行")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("leqno", Modifier.weight(1f))
                    Switch(checked = leqno, onCheckedChange = { leqno = it })
                }
                val tagged = rememberLatexLayout(TAGGED_DISPLAY, LatexSettings(displayMode = true, leqno = leqno))
                Frame(width) {
                    Latex(tagged, Modifier.fillMaxWidth(), fontSize = fontSize, color = MaterialTheme.colorScheme.onSurface)
                }

                Heading("文字混排预览", "用 FlowRow 模拟 BetterText：文字和公式 chunk 按基线对齐，一起换行")
                MixedParagraph(width, fontSize)
            }
        }
    }
}

@Composable
private fun Heading(title: String, description: String) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A bordered box of the given width, so the available space is visible. */
@Composable
private fun Frame(width: Dp, content: @Composable () -> Unit) {
    Box(Modifier.width(width).border(1.dp, MaterialTheme.colorScheme.outlineVariant)) { content() }
}

@Composable
private fun ChunkFlow(latex: String, width: Dp, fontSize: TextUnit) {
    val layout = rememberLatexLayout(latex)
    val outline = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    FlowRow(
        Modifier.width(width),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(layout.chunkCount) { i ->
            LatexChunk(
                layout,
                i,
                Modifier
                    .alignByBaseline()
                    .border(0.5.dp, outline),
                fontSize = fontSize,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
    Text(
        "共 ${layout.chunkCount} 个 chunk，单行宽 ${"%.2f".format(layout.width)} em",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun MixedParagraph(width: Dp, fontSize: TextUnit) {
    val color = MaterialTheme.colorScheme.onSurface
    val style = MaterialTheme.typography.bodyLarge
    Frame(width) {
        FlowRow(Modifier.fillMaxWidth().padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            mixedParagraph.forEachIndexed { i, piece ->
                if (i % 2 == 0) {
                    // One item per character so CJK text can break anywhere.
                    for (ch in piece) Text(ch.toString(), Modifier.alignByBaseline(), style = style, color = color)
                } else {
                    val layout = rememberLatexLayout(piece)
                    repeat(layout.chunkCount) { c ->
                        LatexChunk(layout, c, Modifier.alignByBaseline(), fontSize = fontSize, color = color)
                    }
                }
            }
        }
    }
}
