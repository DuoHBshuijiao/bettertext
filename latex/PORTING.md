# Porting KaTeX to `:latex`

This module is a Kotlin port of KaTeX 0.19.1's parser and HTML builder. KaTeX
sources for reference live in
`/private/tmp/claude-501/-Users-reovo-StudioProjects-bettertext/31014936-1281-45a6-8956-6de9a3b74eea/scratchpad/katex-dl/package/src`
(TypeScript). `dist/katex.css` next to it holds the CSS rules the builders rely on.
MathML builders are **not** ported; skip them.

Package `me.rerere.bettertext.latex`:

| KaTeX                         | Kotlin                                              |
|-------------------------------|-----------------------------------------------------|
| `Lexer`, `MacroExpander`, `Parser`, `macros.ts` | `parse/` (done)                   |
| `types/nodes.ts`              | `parse/ParseNode.kt` (all node classes, done)       |
| `defineFunction`, `defineEnvironment` | `functions/Registry.kt` (done)              |
| `Options`, `Style`, `units`, `fontMetrics` | `layout/` (done)                       |
| `domTree` spans/symbols/svg   | `layout/Box.kt` (done)                              |
| `buildCommon.ts`              | `layout/BuildCommon.kt` (done)                      |
| `buildHTML.ts`                | `layout/BuildHtml.kt` (done)                        |
| `functions/*.ts`              | `functions/*.kt`, one `registerXxx()` per file      |

Read `layout/Box.kt`, `layout/BuildCommon.kt`, `layout/Options.kt`,
`functions/Registry.kt`, `functions/SupSub.kt` and `functions/Ord.kt` before
porting: they are the reference for style.

## Units: everything is absolute em

KaTeX expresses each span's height/depth in em of *that span's* font size and
rescales in `buildGroup` when the size changes. Here **every `Box` dimension
is in em of the base font size** (normalsize textstyle). Consequences:

- `options.fontMetrics().X` (current-font em) → use `options.fm.X`, which is
  already multiplied by `options.sizeMultiplier`.
- `calculateSize(m, options)` → `calculateSizeAbs(m, options)`.
- A CSS length `makeEm(v)` written on a span built with options `o` is in `o`'s
  font em → multiply by `o.sizeMultiplier` (helper: `o.em(v)`).
- Drop every manual rescale such as `x *= newOptions.sizeMultiplier / options.sizeMultiplier`
  — children built with other options are already absolute.
- `buildGroup(group, newOptions, options)` keeps the signature; the third
  argument is ignored.

## Box model (CSS → fields)

KaTeX lets the browser lay out horizontally; we compute widths ourselves.

- `makeSpan(classes, children, options)` → `SpanBox`. Its width is the sum of
  its children's `advance` (= `marginLeft + width + marginRight`) unless an
  explicit width is set (`span.width = x`, i.e. CSS `width`).
- `span.style.marginLeft/Right` → `box.marginLeft/marginRight`.
- `style.top = makeEm(x)` / `position: relative; top` → `box.shiftY = x` (positive moves
  down; drawing only). CSS `vertical-align: -x` likewise → `shiftY = x`.
- `style.color` → `box.color` (ARGB, `CssColor.parse(...)`); null inherits.
- `makeVList(params, options)` → `makeVList(position, children, align)`. Children
  are listed bottom to top exactly like KaTeX. `VListChild.Elem(elem, marginLeft,
  marginRight, align, shift)`, `VListChild.Kern(size)`. `wrapperClasses: ["svg-align"]`
  → `align = HAlign.LEFT` on that child.
- CSS `text-align` on a vlist (`.mfrac`, `.op-limits`, `.katex-accent`,
  `.mtable .col-align-*`): pass `HAlign.CENTER/LEFT/RIGHT` as `align` to makeVList.
  Check `dist/katex.css` for the class you are porting.
- CSS `width: 100%` children of a vlist (frac lines, `hline`, stretchy svgs,
  `hide-tail`): set `fillWidth = true`; the vlist assigns the width.
  `makeLineSpan` already does this.
- Borders/backgrounds (`\fbox`, `\colorbox`): `backgroundColor`, `borderWidth`,
  `borderColor` on any box; they are drawn over the box rect `[0, -height]..[width, depth]`,
  so include padding in the box size yourself (e.g. with kerns / margins).
- `overflow: hidden` → `box.clip = true`.
- SVG: `SvgBox(elements, viewBox, svgHeight, preserveAspectRatio)` draws the viewBox
  into `[0, -svgHeight]..[width, 0]` relative to the box baseline — the same as
  a KaTeX svg span with `style.height = svgHeight` sitting on the baseline. Set
  `width` (or `fillWidth` for `width:100%`), `height`, `depth` explicitly; use
  `shiftY` for KaTeX's vertical-align/top. `SvgElement.Path(d)` uses viewBox
  coords; `SvgElement.Line` uses 0..1 fractions of the svg rect (KaTeX's `%`).
  Static paths: `SvgPaths["name"]` (from `svgGeometry.ts`'s `path` map).
- `DocumentFragment` → `FragmentBox` via `makeFragment`; `Anchor` → `AnchorBox`.
- `SymbolBox` = KaTeX `SymbolNode`; its italic correction is added to its advance.
- Classes still matter: the first class is the math class (`mord`, `mbin`, ...)
  used for spacing, plus `mspace`, `mtight`, `nobreak`, `allowbreak`,
  `katex-newline`, `enclosing`.

## Functions

```kotlin
internal fun registerFoo() {
    defineFunction(
        names = listOf("\\foo"),
        numArgs = 1,
        argTypes = listOf(ArgType.MATH),
        allowedInText = true,
    ) { ctx, args, optArgs ->
        FooNode(ctx.parser.mode, ...)
    }
    defineHtmlBuilder<FooNode> { group, options -> ... }
}
```

- `ctx.parser` gives `mode`, `gullet`, `parseExpression`, `parseArguments`,
  `callFunction`, `consumeSpaces`, `fetch`, `consume`, `expect`, `settings`.
- `assertNodeType(x, "foo")` → `x.assertType<FooNode>()`; `ordargument` → `ordArgument`;
  `normalizeArgument` exists; `isCharacterBox`, `getBaseElem` are in `functions/Utils.kt`.
- `html.buildExpression(body, options, isRealGroup, surrounding)` →
  `buildExpression(body, options, isRealGroup, surrounding)` where `isRealGroup`
  is `true`, `false` or `ROOT`; `surrounding` is a `Pair<String?, String?>`.
- If a KaTeX node type has a field missing from `ParseNode.kt`, add it there
  (keep changes minimal; other people edit other files concurrently).
- Builders that handle super/subscripts themselves (op, operatorname, accent,
  horizBrace) must also install a delegate in `SupSubDelegates` (see SupSub.kt).
  KaTeX's `if (grp.type === "supsub")` branches become
  `if (grp is SupSubNode)`, unwrapping `grp.base`.
- `options.getColor()` returns a CSS string; convert with `CssColor.parse`.
- Throw `ParseError(message, token)` for parse errors.
- `\includegraphics`, `\href` trust checks, MathML and `CD` environments: skip.

## Working rules

- Several people port different files **at the same time in the same tree**.
  Only edit the files you own (listed in your task), plus small additive edits to
  `parse/ParseNode.kt` if needed. Never reformat or rewrite others' files.
- Build: `./gradlew :latex:compileDebugKotlin -q` and tests:
  `./gradlew :latex:testDebugUnitTest -q`. If a failure is in a file you don't
  own, it's someone else's work in progress: wait a bit and retry, don't touch it.
- Put tests in your own file under `latex/src/test/java/me/rerere/bettertext/latex/`.
  `LatexLayout.create(latex, LatexSettings(...))` runs on the JVM; check
  `error == null` and sensible dimensions (compare with KaTeX's numbers when you can).
  To inspect the box tree in tests, call internal `parseTree` + `buildHTML` directly.
- Match KaTeX's logic and constants faithfully; keep KaTeX's comments where they
  explain TeX rules.
