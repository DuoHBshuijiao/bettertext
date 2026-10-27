# BetterText

A Jetpack Compose library for rich text with inline math, aimed at markdown rendering in apps like LLM chat clients.

Compose's `Text` makes inline formulas and other inline components awkward, and existing math renderers such as JLatexMath can't wrap a long inline formula across lines the way KaTeX does in a browser. BetterText addresses both.

## Status

- **`:latex`**: ready. A Kotlin port of KaTeX 0.19.1's parser and layout engine that draws on an Android `Canvas` with the bundled KaTeX fonts. It supports KaTeX's functions, environments, macros and symbols, except `\includegraphics` and `CD`.
- **Text layer**: not started. It will be a `BasicText` replacement that places math chunks inline with text.
- **`:app`**: a demo gallery with categorized samples, a line-wrapping demo and a live editor.

## Usage

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

## Building

Requires JDK 25.

```bash
./gradlew :latex:testDebugUnitTest   # unit tests (run on the JVM)
./gradlew :app:installDebug          # install the demo
```

See [AGENTS.md](AGENTS.md) for the architecture and [latex/PORTING.md](latex/PORTING.md) for the porting conventions.

## License

BetterText is released under the [MIT License](LICENSE). The KaTeX code that `:latex` is ported from is also MIT-licensed (see [latex/KATEX_LICENSE](latex/KATEX_LICENSE)). The bundled KaTeX fonts are under the SIL Open Font License.
