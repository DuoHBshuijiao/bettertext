package me.rerere.bettertext.latex.parse

import me.rerere.bettertext.latex.LatexSettings
import me.rerere.bettertext.latex.ParseError

/** Parses an expression and wraps it in a [TagNode] if it used `\tag`. Ported from parseTree.ts. */
internal fun parseTree(toParse: String, settings: LatexSettings): List<ParseNode> {
    val parser = Parser(toParse, settings)
    parser.gullet.macros.set("\\df@tag", null)
    var tree = parser.parse()
    parser.gullet.macros.set("\\current@color", null)
    parser.gullet.macros.set("\\color", null)
    if (parser.gullet.macros.get("\\df@tag") != null) {
        if (!settings.displayMode) throw ParseError("\\tag works only in display equations")
        tree = listOf(TagNode(Mode.TEXT, tree, parser.subparse(listOf(Token("\\df@tag")))))
    }
    return tree
}
