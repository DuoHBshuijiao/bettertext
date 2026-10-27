package me.rerere.bettertext.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey

@Composable
internal fun HomeScreen(onNavigate: (NavKey) -> Unit) {
    GalleryScaffold(title = "BetterText", onBack = null) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { GroupHeader("交互演示") }
            item {
                EntryCard(
                    title = "编辑器",
                    description = "输入 LaTeX 实时预览，可切换块级、leqno、fleqn 和字号",
                    preview = Sample("""\LaTeX\ \text{playground}"""),
                    onClick = { onNavigate(PlaygroundRoute()) },
                )
            }
            item {
                EntryCard(
                    title = "富文本",
                    description = "BetterText 混排文字、公式、颜色方块和引用标记，并与 Compose Text 对比",
                    preview = Sample("""x_{1,2} = \frac{-b \pm \sqrt{\Delta}}{2a}"""),
                    onClick = { onNavigate(RichTextRoute) },
                )
            }
            item {
                EntryCard(
                    title = "换行",
                    description = "拖动宽度观察行内公式断行、chunk 划分和编号位置",
                    preview = Sample("""a + b = c \allowbreak + d"""),
                    onClick = { onNavigate(WrappingRoute) },
                )
            }
            item { GroupHeader("功能示例") }
            itemsIndexed(sections) { i, section ->
                EntryCard(
                    title = section.title,
                    description = "${section.description} · ${section.samples.size} 个示例",
                    preview = section.samples.first(),
                    onClick = { onNavigate(SectionRoute(i)) },
                )
            }
        }
    }
}

@Composable
private fun GroupHeader(text: String) {
    Text(
        text,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun EntryCard(title: String, description: String, preview: Sample, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SampleFormula(preview, Modifier.padding(top = 8.dp))
        }
    }
}
