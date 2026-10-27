package me.rerere.bettertext.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import me.rerere.bettertext.latex.Latex
import me.rerere.bettertext.latex.LatexBlock
import me.rerere.bettertext.latex.rememberLatexLayout

/** The samples of one section; tapping a sample opens it in the editor. */
@Composable
internal fun SectionScreen(index: Int, onBack: () -> Unit, onOpen: (sample: Int) -> Unit) {
    val section = sections[index]
    GalleryScaffold(title = section.title, onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "${section.description}。点击示例可在编辑器中修改。",
                    modifier = Modifier.padding(bottom = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            itemsIndexed(section.samples) { i, sample ->
                SampleCard(sample, onClick = { onOpen(i) })
            }
        }
    }
}

@Composable
private fun SampleCard(sample: Sample, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                sample.latex,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            sample.note?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
            }
            SampleFormula(sample)
        }
    }
}

/** Renders [sample] inline or as a scrolling block, followed by its parse error if any. */
@Composable
internal fun SampleFormula(sample: Sample, modifier: Modifier = Modifier) {
    val layout = rememberLatexLayout(sample.latex, sample.settings)
    val fontSize = MaterialTheme.typography.bodyLarge.fontSize
    val color = MaterialTheme.colorScheme.onSurface
    Column(modifier) {
        if (sample.display) {
            LatexBlock(layout, fontSize = fontSize, color = color)
        } else {
            Latex(layout, fontSize = fontSize, color = color)
        }
        layout.error?.let {
            Text(it.message.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}
