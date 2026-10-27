package me.rerere.bettertext.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A section title with a one-line description. */
@Composable
internal fun Heading(title: String, description: String) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A bordered box of the given width, so the available space is visible. */
@Composable
internal fun Frame(width: Dp, content: @Composable () -> Unit) {
    Box(Modifier.width(width).border(1.dp, MaterialTheme.colorScheme.outlineVariant)) { content() }
}
