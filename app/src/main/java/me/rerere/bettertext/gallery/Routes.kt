package me.rerere.bettertext.gallery

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
internal data object HomeRoute : NavKey

/** The samples of `sections[index]`. */
@Serializable
internal data class SectionRoute(val index: Int) : NavKey

@Serializable
internal data object WrappingRoute : NavKey

@Serializable
internal data object RichTextRoute : NavKey

@Serializable
internal data object MarkdownRoute : NavKey

/** The editor, prefilled with `sections[section].samples[sample]` when both are valid. */
@Serializable
internal data class PlaygroundRoute(val section: Int = -1, val sample: Int = -1) : NavKey {
    val initial: Sample? get() = sections.getOrNull(section)?.samples?.getOrNull(sample)
}
