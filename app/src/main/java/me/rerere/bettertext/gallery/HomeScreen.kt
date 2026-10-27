package me.rerere.bettertext.gallery

import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.navigation3.runtime.NavKey
import me.rerere.bettertext.R
import me.rerere.bettertext.gallery.highlight.HighlightTab
import me.rerere.bettertext.gallery.latex.LatexTab
import me.rerere.bettertext.gallery.markdown.MarkdownTab
import me.rerere.bettertext.gallery.text.RichTextTab
import me.rerere.bettertext.ui.components.galleryTopAppBarColors

/** One tab per library module. */
internal enum class HomeTab(val label: String, val title: String, val subtitle: String, @DrawableRes val icon: Int) {
    Latex("LaTeX", "LaTeX", "KaTeX 的 Kotlin 移植", R.drawable.ic_functions),
    RichText("富文本", "富文本", "BetterText 混排文字、公式和组件", R.drawable.ic_text_fields),
    Markdown("Markdown", "Markdown", "渲染一段 AI 回复，可模拟流式输出", R.drawable.ic_article),
    Highlight("高亮", "代码高亮", "highlight.js 的 Kotlin 移植，30 种语言", R.drawable.ic_code),
}

@Composable
internal fun HomeScreen(onNavigate: (NavKey) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(HomeTab.Latex) }
    // Each tab keeps its own scroll position and settings.
    val tabStates = rememberSaveableStateHolder()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(tab.title) },
                subtitle = { Text(tab.subtitle) },
                colors = galleryTopAppBarColors(),
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            ShortNavigationBar {
                for (item in HomeTab.entries) {
                    ShortNavigationBarItem(
                        selected = item == tab,
                        onClick = { tab = item },
                        icon = { Icon(painterResource(item.icon), contentDescription = null) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        tabStates.SaveableStateProvider(tab.name) {
            when (tab) {
                HomeTab.Latex -> LatexTab(padding, onNavigate)
                HomeTab.RichText -> RichTextTab(padding)
                HomeTab.Markdown -> MarkdownTab(padding, onNavigate)
                HomeTab.Highlight -> HighlightTab(padding)
            }
        }
    }
}
