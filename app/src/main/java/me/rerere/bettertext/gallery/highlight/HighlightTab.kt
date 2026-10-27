package me.rerere.bettertext.gallery.highlight

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.rerere.bettertext.text.BetterSelectionContainer

/** Code in several languages, highlighted by the `:highlight` module. */
@Composable
internal fun HighlightTab(contentPadding: PaddingValues) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var theme by rememberSaveable { mutableStateOf(CodeTheme.System) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            codeSamples.forEachIndexed { i, sample ->
                FilterChip(selected = i == selected, onClick = { selected = i }, label = { Text(sample.label) })
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            val themes = CodeTheme.entries
            themes.forEachIndexed { i, item ->
                ToggleButton(
                    checked = item == theme,
                    onCheckedChange = { theme = item },
                    modifier = Modifier.weight(1f),
                    // The default unchecked color is the surface container, which is the page background here.
                    colors = ToggleButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                    shapes = when (i) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        themes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                ) {
                    Text(item.label, maxLines = 1)
                }
            }
        }
        val sample = codeSamples[selected]
        BetterSelectionContainer(Modifier.padding(horizontal = 16.dp), obscuredPadding = contentPadding) {
            CodeCard(sample.code, sample.language, theme = theme)
        }
        Text(
            "Atom One 配色，语法规则移植自 highlight.js 11.11.1 · 长按可以选择代码",
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
