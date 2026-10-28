# RikkaHub compatibility branch

Based on `v0.1.3` (`aafb4cf15dee030e516fa204398097a6916acc8f`).

This branch adds two general extension points. It contains no RikkaHub search models.

* `BetterText(..., onTextLayout = ...)` reports geometry in offsets of the input
  `AnnotatedString`, including the end offset. Internal inline expansion and empty
  decorations are mapped by the library. Existing overloads remain available.
* `MarkdownComponents.inlineText` receives inline nodes, a modifier, and a stable
  path. The current style remains available from `LocalMarkdownTextStyle`.
  `LocalMarkdownBlockPath` lets table/detail/code components address their children.

Paths use `/block`, `/item/index`, `/text/index`, and `/summary`. Applications supply
`/header/index` and `/row/index/index` scopes for their own table cells. Paths refer
to parsed structure, never font metrics, wrapping, or occurrence text.

The host owns search matching, source-to-rendered offsets, highlighting and scroll
behavior. Keep all four modules and the BOM on the same commit. Geometry regression
cases live in `text/src/androidTest`; they require Android's real `StaticLayout`.
