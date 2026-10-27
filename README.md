# BetterText

A Jetpack Compose library for rich text with inline math, aimed at markdown rendering in apps like LLM chat clients.

Compose's `Text` makes inline formulas and other inline components awkward, and existing math renderers such as JLatexMath can't wrap a long inline formula across lines the way KaTeX does in a browser. BetterText addresses both.

## Status

- **`:latex`**: ready. A Kotlin port of KaTeX 0.19.1's parser and layout engine that draws on an Android `Canvas` with the bundled KaTeX fonts. It supports KaTeX's functions, environments, macros and symbols, except `\includegraphics` and `CD`.
- **`:text`**: in progress. `BetterText` lays out text with inline formulas, custom drawn items and composables such as citation tags; lines grow to fit tall items. Because of the [Compose limitations](#compose-limitations) below, it lays out text itself; `BetterSelectionContainer` selects across paragraphs and copies formulas as their LaTeX source. The markdown mapping is not written yet.
- **`:app`**: a demo gallery with categorized samples, a rich text demo, a line-wrapping demo and a live editor.

## Usage

```kotlin
// Text with inline content
val text = buildAnnotatedString {
    append("The roots are ")
    appendInline("roots", altText = "\$x_{1,2}\$")
    append(" and the accent color is ")
    appendInline("swatch", altText = "#6750A4")
    append(" #6750A4")
    appendInline("cite", altText = "[1]")
}
val roots = rememberLatexInline("""x_{1,2} = \frac{-b \pm \sqrt{b^2 - 4ac}}{2a}""")
// Remember the map: a new one makes BetterText prepare the text again.
val inlineContent = remember(roots) {
    mapOf(
        "roots" to roots,
        "swatch" to Swatch(Color(0xFF6750A4).toArgb()),
        "cite" to ComposableInline(InlineAlign.Center) { CitationChip(1) },
    )
}
BetterSelectionContainer {
    BetterText(text, style = MaterialTheme.typography.bodyLarge, inlineContent = inlineContent)
}

// A custom inline drawn with the text, sized by the surrounding font
class Swatch(private val argb: Int) : DrawnInline() {
    override fun measure(segment: Int, paint: Paint, metrics: InlineMetrics) {
        val size = paint.textSize * 0.8f
        val center = (paint.ascent() + paint.descent()) / 2
        metrics.width = size
        metrics.ascent = size / 2 - center
        metrics.descent = size / 2 + center
    }

    override fun draw(segment: Int, canvas: Canvas, x: Float, baseline: Float, paint: Paint) {
        val size = paint.textSize * 0.8f
        val top = baseline + (paint.ascent() + paint.descent()) / 2 - size / 2
        paint.color = argb
        canvas.drawRect(x, top, x + size, top + size, paint)
    }
}
```

```kotlin
// Inline: wraps after relations and binary operators when space runs out
Latex("a^2 + b^2 = c^2", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)

// Display: centered, scrolls horizontally when too wide
LatexBlock("""\sum_{i=1}^n i = \frac{n(n+1)}{2} \tag{1}""")

// Custom settings
val layout = rememberLatexLayout(
    """f: \RR^n \to \RR""",
    LatexSettings(displayMode = true, macros = mapOf("\\RR" to "\\mathbb{R}")),
)
LatexBlock(layout)
```

Parse errors never throw. The source is shown in red, and `LatexLayout.error` describes the problem.

To place math inside your own text layout, split a formula into unbreakable chunks with `LatexLayout.chunkCount` and `chunkWidth`/`chunkHeight`/`chunkDepth`, then draw each chunk with `LatexChunk`.

## Compose limitations

Two open Compose issues prevent the text layer from building on `BasicText` and `SelectionContainer`:

- [427657659](https://issuetracker.google.com/issues/427657659): when `lineHeight` is set, every line gets the same height. A tall inline formula, or a span with a larger font, overlaps the lines next to it. Material 3 typography always sets `lineHeight`.
- [233102278](https://issuetracker.google.com/issues/233102278): `SelectionRegistrar` and `SelectionManager` are internal, so a custom text layout can't take part in `SelectionContainer`.

Until they are fixed, BetterText works around them with its own layout and selection. Once Compose fixes them, BetterText will switch to the built-in APIs.

## Building

Requires JDK 25.

```bash
./gradlew :latex:testDebugUnitTest   # unit tests (run on the JVM)
./gradlew :app:installDebug          # install the demo
```

See [AGENTS.md](AGENTS.md) for the architecture and [latex/PORTING.md](latex/PORTING.md) for the porting conventions.

## License

BetterText is released under the [MIT License](LICENSE). The KaTeX code that `:latex` is ported from is also MIT-licensed (see [latex/KATEX_LICENSE](latex/KATEX_LICENSE)). The bundled KaTeX fonts are under the SIL Open Font License.
