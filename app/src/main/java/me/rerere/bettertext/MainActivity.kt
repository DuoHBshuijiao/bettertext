package me.rerere.bettertext

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import me.rerere.bettertext.gallery.HomeRoute
import me.rerere.bettertext.gallery.HomeScreen
import me.rerere.bettertext.gallery.MarkdownRoute
import me.rerere.bettertext.gallery.MarkdownScreen
import me.rerere.bettertext.gallery.PlaygroundRoute
import me.rerere.bettertext.gallery.PlaygroundScreen
import me.rerere.bettertext.gallery.RichTextRoute
import me.rerere.bettertext.gallery.RichTextScreen
import me.rerere.bettertext.gallery.SectionRoute
import me.rerere.bettertext.gallery.SectionScreen
import me.rerere.bettertext.gallery.WrappingRoute
import me.rerere.bettertext.gallery.WrappingScreen
import me.rerere.bettertext.ui.theme.BettertextTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BettertextTheme {
                GalleryApp()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun GalleryApp() {
    val backStack = rememberNavBackStack(HomeRoute)
    val back: () -> Unit = { backStack.removeLastOrNull() }
    NavDisplay(
        backStack = backStack,
        onBack = back,
        entryProvider = entryProvider {
            entry<HomeRoute> { HomeScreen(onNavigate = { backStack.add(it) }) }
            entry<SectionRoute> { route ->
                SectionScreen(
                    index = route.index,
                    onBack = back,
                    onOpen = { sample -> backStack.add(PlaygroundRoute(route.index, sample)) },
                )
            }
            entry<WrappingRoute> { WrappingScreen(onBack = back) }
            entry<RichTextRoute> { RichTextScreen(onBack = back) }
            entry<MarkdownRoute> { MarkdownScreen(onBack = back) }
            entry<PlaygroundRoute> { route -> PlaygroundScreen(route, onBack = back) }
        },
    )
}
