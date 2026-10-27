package me.rerere.bettertext.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.rerere.bettertext.components.GalleryScaffold
import me.rerere.bettertext.components.materialMarkdownComponents
import me.rerere.bettertext.components.rememberMaterialMarkdownTheme
import me.rerere.bettertext.markdown.Markdown
import me.rerere.bettertext.markdown.MarkdownDefaults
import me.rerere.bettertext.text.BetterSelectionContainer

/** Renders a chat-style reply with the Markdown module, optionally streaming it in. */
@Composable
internal fun MarkdownScreen(onBack: () -> Unit) {
    var shown by rememberSaveable { mutableIntStateOf(MARKDOWN_SAMPLE.length) }
    var streaming by rememberSaveable { mutableStateOf(false) }
    var registered by rememberSaveable { mutableStateOf(true) }
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

    GalleryScaffold(title = "Markdown", onBack = onBack) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { streaming = true }, enabled = !streaming) { Text("模拟流式输出") }
                OutlinedButton(onClick = {
                    streaming = false
                    shown = MARKDOWN_SAMPLE.length
                }) { Text("显示全部") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("注册 Material 3 组件")
                    Text(
                        "代码块、表格、图片和折叠块由应用注册；关闭后显示库的兜底效果",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = registered, onCheckedChange = { registered = it })
            }
            Text(
                "${shown} / ${MARKDOWN_SAMPLE.length} 字符 · 长按可以跨块选择，公式复制为 LaTeX 源码",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BetterSelectionContainer(Modifier.fillMaxWidth()) {
                Markdown(
                    content = MARKDOWN_SAMPLE.substring(0, shown),
                    style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    theme = rememberMaterialMarkdownTheme(),
                    components = if (registered) materialMarkdownComponents else MarkdownDefaults.components,
                )
            }
        }
    }
}
