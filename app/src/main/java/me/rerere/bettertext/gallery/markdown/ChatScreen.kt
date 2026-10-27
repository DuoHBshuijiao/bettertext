package me.rerere.bettertext.gallery.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import me.rerere.bettertext.markdown.Markdown
import me.rerere.bettertext.text.BetterSelectionContainer
import me.rerere.bettertext.text.BetterText
import me.rerere.bettertext.ui.components.GalleryScaffold

/**
 * A long conversation in a lazy list inside one selection container: the
 * selection can span messages, and dragging to the top or bottom scrolls the
 * list. The list scrolls behind the app bar, so the container gets the
 * scaffold's padding as the part of the window it can't see.
 */
@Composable
internal fun ChatScreen(onBack: () -> Unit) {
    GalleryScaffold(title = "长对话选择", subtitle = "LazyColumn 里跨消息选择", onBack = onBack) { padding ->
        val style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)
        val theme = rememberMaterialMarkdownTheme()
        val layoutDirection = LocalLayoutDirection.current
        BetterSelectionContainer(Modifier.fillMaxSize(), obscuredPadding = padding) {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = padding.calculateStartPadding(layoutDirection) + 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    end = padding.calculateEndPadding(layoutDirection) + 16.dp,
                    bottom = padding.calculateBottomPadding() + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Text(
                        "长按开始选择，拖动手柄可以跨消息选择；拖到顶栏或屏幕底部附近，列表会自动滚动。滚出屏幕的已选消息不会被回收",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                itemsIndexed(chatMessages) { _, message ->
                    if (message.fromUser) {
                        UserMessage(message.text, style)
                    } else {
                        Markdown(content = message.text, style = style, theme = theme, components = materialMarkdownComponents)
                    }
                }
            }
        }
    }
}

@Composable
private fun UserMessage(text: String, style: TextStyle) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Box(
            Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(20.dp, 4.dp, 20.dp, 20.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            BetterText(text, style = style.copy(color = MaterialTheme.colorScheme.onPrimaryContainer))
        }
    }
}
