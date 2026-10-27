package me.rerere.bettertext.markdown

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapNotNull

/**
 * Renders Markdown: GFM (tables, task lists, strikethrough), LaTeX formulas and embedded HTML.
 *
 * Text is drawn with `BetterText`, so wrap the message in a `BetterSelectionContainer`
 * to make it selectable. Code blocks, tables, images and details blocks are
 * registered by the app through [components]; visual parameters go in [theme].
 * [style] is the body text style, including its color. [content] may change often while
 * a reply streams in: it is parsed in the background, reusing the unchanged
 * beginning, and blocks that didn't change don't recompose.
 *
 * @param softBreakAsLineBreak render a single newline in a paragraph as a line break.
 * @param onLinkClick called when a link is clicked; null opens it with the system UriHandler.
 */
@Composable
fun Markdown(
    content: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    theme: MarkdownTheme = MarkdownDefaults.theme,
    components: MarkdownComponents = MarkdownDefaults.components,
    softBreakAsLineBreak: Boolean = true,
    onLinkClick: ((url: String) -> Unit)? = null,
) {
    Markdown(
        document = rememberMarkdownDocument(content, softBreakAsLineBreak),
        modifier = modifier,
        style = style,
        theme = theme,
        components = components,
        onLinkClick = onLinkClick,
    )
}

/** Renders an already parsed [document], e.g. one parsed ahead of time with [parseMarkdown]. */
@Composable
fun Markdown(
    document: MarkdownDocument,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    theme: MarkdownTheme = MarkdownDefaults.theme,
    components: MarkdownComponents = MarkdownDefaults.components,
    onLinkClick: ((url: String) -> Unit)? = null,
) {
    val currentOnLinkClick by rememberUpdatedState(onLinkClick)
    val handlesLinks = onLinkClick != null
    val linkListener = remember(handlesLinks) {
        if (handlesLinks) {
            LinkInteractionListener { link ->
                if (link is LinkAnnotation.Url) currentOnLinkClick?.invoke(link.url)
            }
        } else {
            null
        }
    }
    val context = remember(theme, components, linkListener) {
        MarkdownContext(theme, components, linkListener)
    }
    CompositionLocalProvider(LocalMarkdownContext provides context) {
        ProvideMarkdownTextStyle(style) {
            MarkdownBlocks(blocks = document.blocks, modifier = modifier)
        }
    }
}

/**
 * Parses [content] and follows its changes.
 *
 * The first parse is synchronous so the first frame has content; later ones run
 * in the background with a [MarkdownStreamParser]. When updates come faster than
 * parsing, only the latest content is parsed.
 */
@Composable
fun rememberMarkdownDocument(content: String, softBreakAsLineBreak: Boolean = true): MarkdownDocument {
    val parser = remember(softBreakAsLineBreak) { MarkdownStreamParser(softBreakAsLineBreak) }
    val state = remember(parser) { mutableStateOf(parser.parse(content)) }
    val currentContent by rememberUpdatedState(content)
    LaunchedEffect(parser) {
        snapshotFlow { currentContent }
            .conflate()
            .filter { it != state.value.source }
            .mapNotNull { runCatching { parser.parse(it) }.getOrNull() }
            .flowOn(Dispatchers.Default)
            .collect { state.value = it }
    }
    return state.value
}
