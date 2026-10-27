package me.rerere.bettertext.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import me.rerere.bettertext.R

/**
 * A screen with a collapsing top app bar and a back button, on the surface container
 * color so that segmented list items stand out.
 */
@Composable
internal fun GalleryScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(title) },
                subtitle = subtitle?.let { { Text(it) } },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "返回")
                    }
                },
                colors = galleryTopAppBarColors(),
                scrollBehavior = scrollBehavior,
            )
        },
        content = content,
    )
}

/** Top app bar colors that match the surface container background. */
@Composable
internal fun galleryTopAppBarColors(): TopAppBarColors {
    val color = MaterialTheme.colorScheme.surfaceContainer
    return TopAppBarDefaults.topAppBarColors(containerColor = color, scrolledContainerColor = color)
}
