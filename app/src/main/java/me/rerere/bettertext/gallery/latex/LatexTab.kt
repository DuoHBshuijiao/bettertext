package me.rerere.bettertext.gallery.latex

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import androidx.navigation3.runtime.NavKey
import me.rerere.bettertext.R
import me.rerere.bettertext.gallery.PlaygroundRoute
import me.rerere.bettertext.gallery.SectionRoute
import me.rerere.bettertext.gallery.WrappingRoute
import me.rerere.bettertext.ui.components.GroupHeader
import me.rerere.bettertext.ui.components.ShapeIcon

/** The interactive LaTeX demos and the sample sections. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LatexTab(contentPadding: PaddingValues, onNavigate: (NavKey) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        item { GroupHeader("交互演示") }
        item {
            EntryItem(
                title = "编辑器",
                description = "输入 LaTeX 实时预览，可切换块级、leqno、fleqn 和字号",
                preview = Sample("""\LaTeX\ \text{playground}"""),
                index = 0,
                count = 2,
                icon = R.drawable.ic_edit to MaterialShapes.Cookie9Sided,
                onClick = { onNavigate(PlaygroundRoute()) },
            )
        }
        item {
            EntryItem(
                title = "换行",
                description = "拖动宽度观察行内公式断行、chunk 划分和编号位置",
                preview = Sample("""a + b = c \allowbreak + d"""),
                index = 1,
                count = 2,
                icon = R.drawable.ic_wrap_text to MaterialShapes.Clover4Leaf,
                onClick = { onNavigate(WrappingRoute) },
            )
        }
        item { GroupHeader("功能示例") }
        itemsIndexed(sections) { i, section ->
            EntryItem(
                title = section.title,
                description = "${section.description} · ${section.samples.size} 个示例",
                preview = section.samples.first(),
                index = i,
                count = sections.size,
                onClick = { onNavigate(SectionRoute(i)) },
            )
        }
    }
}

@Composable
private fun EntryItem(
    title: String,
    description: String,
    preview: Sample,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    /** A drawable on a shape. */
    icon: Pair<Int, RoundedPolygon>? = null,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        leadingContent = icon?.let { (res, shape) -> { ShapeIcon(res, shape) } },
        trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(description)
                SampleFormula(preview, Modifier.padding(bottom = 4.dp))
            }
        },
    ) {
        Text(title)
    }
}
