# AGENTS.md

## Project

BetterText is a Compose library that replaces `BasicText` to improve rich markdown rendering, especially inline formulas and other inline components. It lays out text itself with `StaticLayout` instead of building on `BasicText` and `SelectionContainer`, because of two Compose issues: a uniform `lineHeight` makes tall inline content overlap adjacent lines ([427657659](https://issuetracker.google.com/issues/427657659)), and `SelectionRegistrar` is internal ([233102278](https://issuetracker.google.com/issues/233102278)). Switch to the built-in APIs once those are fixed.

- `:latex`: a Kotlin port of the KaTeX 0.19.1 parser and HTML builder. It renders LaTeX inline and in display mode on an Android `Canvas` with the bundled KaTeX fonts.
- `:text`: the `BetterText` composable and `BetterSelectionContainer`.
- `:markdown`: parses Markdown (with math and embedded HTML) and renders it with `BetterText`. It doesn't depend on Material 3; apps register code blocks, tables, images and details blocks.
- `:highlight`: a pure Kotlin syntax highlighter, ported from highlight.js 11.11.1 (30 languages). It turns code into an `AnnotatedString` for `BetterText`; the gallery's code block uses it.
- `:app`: a demo gallery built with Navigation 3. It has categorized samples, a Markdown demo with simulated streaming, a rich text demo, a wrapping demo, and a live editor. Samples live in `gallery/Samples.kt`, and `GallerySamplesTest` checks that they all parse. `gallery/MaterialMarkdown.kt` shows how an app registers Markdown components and maps its theme.

## Commands

```bash
./gradlew :latex:compileDebugKotlin                       # compile the library
./gradlew :latex:testDebugUnitTest                        # JVM unit tests (all)
./gradlew :latex:testDebugUnitTest --tests '*MiscTest*'   # one class (append .methodName for one test)
./gradlew :text:testDebugUnitTest                         # text layer unit tests
./gradlew :markdown:testDebugUnitTest                     # Markdown parser tests
./gradlew :highlight:testDebugUnitTest                    # highlighter tests, including highlight.js parity
./gradlew :app:installDebug                               # install the demo on a device/emulator
```

The unit tests run on the plain JVM: parsing and layout don't depend on Android, and `LatexLayout.create(...)` uses an approximate glyph measurer there. Gradle caches test results, so add `--rerun` to force tests to run again when only an external input changed.

## Architecture of `:latex`

Package: `me.rerere.bettertext.latex`. Each stage below mirrors the KaTeX TypeScript file with the same name.

1. **Parse** (`parse/`): `Lexer` → `MacroExpander` (macro expansion, `Namespace` scoping, built-in macros in `Macros.kt`) → `Parser` (recursive descent) → `ParseNode` tree. `parseTree()` wraps the result for `\tag`. Each command's parse handler lives in `functions/*.kt` and is looked up through `functions/Registry.kt`.
2. **Build** (`layout/` + `functions/`): `buildHTML` → `buildExpression` (bin cancellation, inter-atom spacing) → `buildGroup` dispatches on the node class to the HTML builders in `Registry.htmlBuilders`. Builders compose boxes with `makeSymbol`, `makeSpan` and `makeVList` from `BuildCommon.kt`. `Options` carries style, size, color and font. `buildHTML` then splits the top level into unbreakable chunks after `mbin`/`mrel`/`\allowbreak`, as KaTeX does.
3. **Result**: `LatexLayout` holds the chunks, forced line breaks, the tag, and the `leqno`/`fleqn` settings. All sizes are in em. It never throws: parse errors produce a red source-text layout and set `error`.
4. **Compose**: in `Latex.kt`, `Latex`, `LatexBlock` and `LatexChunk` convert em to px (`fontSize × 1.21`, the KaTeX scale). `LineLayout.kt` (`LineCache`) breaks inline math greedily and positions display lines and the equation tag. `render/MathRenderer` draws the box tree recursively. `MathTypefaces` loads the fonts from `res/font` and provides the real glyph measurer.

### Box model

KaTeX leaves widths and horizontal layout to the browser's CSS. This port computes them itself (`layout/Box.kt`):

- **Units:** every dimension is **absolute em of the base font size**. KaTeX's current-font em values must be multiplied by `options.sizeMultiplier`. Use `options.fm.*`, `calculateSizeAbs` and `options.em()`.
- **CSS behavior maps to box fields:** margins → `marginLeft`/`marginRight`; `top`/`vertical-align` → `shiftY`; `width:100%` inside a vlist → `fillWidth`; `text-align` → the `HAlign` passed to `makeVList`; `overflow:hidden` → `clip`.
- **Fill widths are lazy:** `VListBox` resolves `fillWidth` children only when its own `width` is read.
- **Post-build text changes need re-measuring:** where KaTeX changes DOM text after building and relies on the browser to re-measure (e.g. the hyphen substitution in `\operatorname`), the port must update the `SymbolBox` metrics.

**Read `latex/PORTING.md` before porting or changing builders.** It holds the full conventions for units, the CSS → Box mapping, the registration API, and how to use the KaTeX sources as a reference.

### Generated data

`latex/src/main/java/.../data/*.kt` (font metrics, symbol table, string macros, SVG paths, Unicode tables) is generated by `latex/tools/gen.js` from the KaTeX npm package. Don't edit these files by hand. The payloads are split into string chunks to stay under the JVM constant-pool limit, and are parsed lazily at runtime.

### Known deviations from KaTeX

- Each environment numbers its equations from 1; KaTeX counts across the whole formula.
- The extra space in a top-level `\\[size]` is dropped.
- `\reflectbox` doesn't mirror its content.
- `\pmb` is approximated by drawing an offset copy.
- No strict mode or trust checks.
- `\includegraphics` and the `CD` environment are not supported.
- A tall tag makes its array row taller; KaTeX lets it overflow.

## Architecture of `:text`

Package: `me.rerere.bettertext.text`. Only the inline expansion is unit-tested; layout needs a device, so check changes in the gallery's rich text screen.

1. **Inline items**: `appendInline(id, altText)` marks alt text with a string annotation; `inlineContent` maps ids to a `BetterInline`. A `DrawnInline` (`LatexInline`, or a custom one such as the gallery's `ColorSwatch`) is measured and drawn with the paint of the surrounding text, so it follows its size and color, and may have several segments (one per LaTeX chunk) with line breaks allowed between them. A `ComposableInline` (e.g. a citation tag) is measured as a child of the `Layout` and placed over its span.
2. **Prepare** (`TextLayout.kt`, `PreparedText`): `expandInlines` replaces each item's alt text with one U+FFFC per segment and maps the original offsets. `SpanStyle`s become Android spans. Font attributes are flattened into one resolved typeface per range, as Compose does. Inline items become `ReplacementSpan`s that report their exact ascent/descent.
3. **Line height**: `StrutSpan` (a `LineHeightSpan`) makes each line at least `lineHeight` tall, with CSS-style half-leading, and lets taller lines grow. It measures each line's extent itself, because `StaticLayout` seeds a line that starts mid-run with the metrics the span returned for the previous line.
4. **Layout** (`TextLayoutCache`): the width is the text's desired width clamped to the constraints, like Compose. `ReplacementSpan.draw` only gets metric-affecting spans applied, so inline items look up `ForegroundColorSpan` themselves.
5. **Selection** (`Selection.kt`, `SelectionContainer.kt`): `BetterSelectionContainer` provides a `SelectionRegistry` through `LocalSelectionRegistry`; each `BetterText` inside registers a `SelectableText` with its layout and coordinates and draws its part of the highlight. The container handles long press and drag itself, draws handles in popups and shows `LocalTextToolbar`. Selection points are offsets in the laid-out text; ranges are snapped to whole inline items, which copy as their alt text. Everything uses public APIs (see issue 233102278). The container uses `focusTarget()`, not `focusable()`, which would scroll it into view when focused.

## Architecture of `:markdown`

Package: `me.rerere.bettertext.markdown`. Ported from RikkaHub's `ui/markdown`, which renders with Compose `Text`; keep the parser behavior in sync with it.

1. **Parse** (`MarkdownParser.kt`): Markdown → HTML with RikkaHub's fork of JetBrains markdown (`com.github.rikkahub:markdown` from JitPack) → DOM with Jsoup → `MarkdownDocument` (`MarkdownNode.kt`). Going through HTML lets hand-written HTML tags nest with Markdown. `\(…\)` and `\[…\]` are rewritten to `$…$` and `$$…$$` first, outside the code found by parsing the text as written, so it costs a second parse when the text contains them.
2. **Streaming** (`MarkdownStreamParser`): splits the top-level blocks into chunks, never across an open HTML element or next to an HTML block, and on the next call reparses only from two chunks before where the text changed. `MarkdownStreamParserTest` checks that every prefix of a document parses the same as `parseMarkdown`; keep that test passing when changing the parser. With `streaming = true`, `StreamingEnd.kt` hides or closes unfinished syntax in the last block (`**bold`, `$\frac{`, `[text](https://exa`, a table header without its delimiter row), working from the parse tree so code isn't touched.
3. **Render** (`Markdown.kt`, `MarkdownBlocks.kt`, `MarkdownInline.kt`): paragraphs, headings, lists, quotes and formulas are drawn with `BetterText`, `LatexInline` and `LatexBlock`. Everything else comes from `MarkdownComponents`, with plain fallbacks. Text style flows through `LocalMarkdownTextStyle`, not Material's `LocalTextStyle`. Nodes are immutable data classes, so blocks that didn't change skip recomposition.

## Architecture of `:highlight`

Package: `me.rerere.bettertext.highlight`. Moved from RikkaHub's `:highlight` module; its Material `CodeHighlightText` was replaced by `rememberHighlightedCode`, which returns an `AnnotatedString`.

- **Engine** (`core/`): `HighlightEngine` ports highlight.js's mode-stack parser (`_highlight()`), `ModeCompiler` its mode compilation, and `MultiRegex` its terminator matcher. A language is compiled the first time it's used. Compiled modes keep matcher state, so a `CodeHighlighter` must not be used from two threads at once.
- **Grammars** (`languages/`): one file per highlight.js language, registered in `Languages.kt`. Port a new one from the highlight.js source, then add it to `tools/languages.mjs` and add fixtures.
- **Tokens and colors**: `HighlightToken` carries highlight.js scopes such as `title.function`; `HighlightStyle.kt` maps them to a `HighlightTextColorPalette` (Atom One Dark and Light), falling back to the parent scope.
- **Tests**: `LanguageFixtureTest` checks every language against token streams from highlight.js itself, in `src/test/resources/hljs/<language>/` (`<name>.txt` and `<name>.tokens`). After adding or changing a fixture source, regenerate the tokens with `cd highlight/tools && npm install && npm run generate`; don't edit `.tokens` files by hand.
