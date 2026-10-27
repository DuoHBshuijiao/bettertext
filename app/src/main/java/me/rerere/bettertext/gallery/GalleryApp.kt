package me.rerere.bettertext.gallery

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import kotlinx.serialization.Serializable
import me.rerere.bettertext.gallery.latex.PlaygroundScreen
import me.rerere.bettertext.gallery.latex.SectionScreen
import me.rerere.bettertext.gallery.latex.WrappingScreen
import me.rerere.bettertext.gallery.latex.sections
import me.rerere.bettertext.gallery.markdown.ChatScreen

/** The tabs of the home screen. */
@Serializable
internal data object HomeRoute : NavKey

/** The samples of `sections[index]`. */
@Serializable
internal data class SectionRoute(val index: Int) : NavKey

@Serializable
internal data object WrappingRoute : NavKey

/** The editor, prefilled with `sections[section].samples[sample]` when both are valid. */
@Serializable
internal data class PlaygroundRoute(val section: Int = -1, val sample: Int = -1) : NavKey {
    val initial get() = sections.getOrNull(section)?.samples?.getOrNull(sample)
}

/** A long conversation in a lazy list, selectable across messages. */
@Serializable
internal data object ChatRoute : NavKey

@Composable
internal fun GalleryApp() {
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
            entry<PlaygroundRoute> { route -> PlaygroundScreen(route.initial, onBack = back) }
            entry<ChatRoute> { ChatScreen(onBack = back) }
        },
    )
}
