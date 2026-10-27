package me.rerere.bettertext.gallery.markdown

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.delay
import me.rerere.bettertext.R
import me.rerere.bettertext.gallery.ChatRoute
import me.rerere.bettertext.markdown.Markdown
import me.rerere.bettertext.markdown.MarkdownDefaults
import me.rerere.bettertext.text.BetterSelectionContainer
import me.rerere.bettertext.ui.components.SegmentedGroup
import me.rerere.bettertext.ui.components.SwitchItem

/** Renders a chat-style reply with the Markdown module, optionally streaming it in. */
@Composable
internal fun MarkdownTab(contentPadding: PaddingValues, onNavigate: (NavKey) -> Unit) {
    var shown by rememberSaveable { mutableIntStateOf(MARKDOWN_SAMPLE.length) }
    var streaming by rememberSaveable { mutableStateOf(false) }
    var registered by rememberSaveable { mutableStateOf(true) }
    var completeEnd by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(streaming) {
        if (!streaming) return@LaunchedEffect
        shown = 0
        while (shown < MARKDOWN_SAMPLE.length) {
            delay(30)
            // A few characters at a time, like tokens.
            shown = minOf(MARKDOWN_SAMPLE.length, shown + 4)
        }
        streaming = false
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { streaming = true },
                modifier = Modifier.weight(1f),
                enabled = !streaming,
                shapes = ButtonDefaults.shapes(),
            ) { Text("模拟流式输出") }
            OutlinedButton(
                onClick = {
                    streaming = false
                    shown = MARKDOWN_SAMPLE.length
                },
                modifier = Modifier.weight(1f),
                shapes = ButtonDefaults.shapes(),
            ) { Text("显示全部") }
        }
        SegmentedGroup {
            SwitchItem(
                title = "注册 Material 3 组件",
                checked = registered,
                onCheckedChange = { registered = it },
                index = 0,
                count = 2,
                description = "代码块、公式、表格、图片和折叠块由应用注册；关闭后显示库的兜底效果",
            )
            SwitchItem(
                title = "流式时处理未完成的语法",
                checked = completeEnd,
                onCheckedChange = { completeEnd = it },
                index = 1,
                count = 2,
                description = "隐藏或补全末尾没写完的公式、加粗、链接等；关闭后它们会先显示成原文",
            )
        }
        SegmentedListItem(
            onClick = { onNavigate(ChatRoute) },
            shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
            supportingContent = { Text("多轮对话放在 LazyColumn 里，选择可以跨消息，拖到边缘自动滚动") },
            trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null) },
        ) {
            Text("长对话选择")
        }
        Text(
            "${shown} / ${MARKDOWN_SAMPLE.length} 字符 · 长按可以跨块选择，公式复制为 LaTeX 源码",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BetterSelectionContainer(Modifier.fillMaxWidth(), obscuredPadding = contentPadding) {
            Markdown(
                content = MARKDOWN_SAMPLE.substring(0, shown),
                style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                theme = rememberMaterialMarkdownTheme(),
                components = if (registered) materialMarkdownComponents else MarkdownDefaults.components,
                streaming = streaming && completeEnd,
            )
        }
    }
}
