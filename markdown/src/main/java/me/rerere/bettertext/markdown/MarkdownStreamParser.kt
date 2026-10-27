package me.rerere.bettertext.markdown

import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.parser.LinkMap

/**
 * Parses Markdown that is streamed in, reusing work from the previous call.
 *
 * The document is split into chunks of top-level blocks. When the new content
 * shares a prefix with the previous one, as it does while a reply streams in,
 * chunks inside that prefix are kept and only the rest is parsed again. The
 * last two kept chunks are parsed again too, since text appended after a block
 * can change how it's read, e.g. a list that gains another item.
 *
 * The result equals [parseMarkdown] except for link reference definitions,
 * which don't apply to links in earlier chunks.
 *
 * Not thread-safe: call [parse] from one thread at a time.
 */
class MarkdownStreamParser(private val softBreakAsLineBreak: Boolean = true) {
    /** A run of top-level blocks covering `[start, end)` of the normalized text, with the link definitions in it. */
    private class Chunk(
        val start: Int,
        val end: Int,
        val blocks: List<MarkdownBlock>,
        val definitions: Map<CharSequence, LinkMap.LinkInfo>,
    )

    /** A run of top-level blocks covering `[0, end)` of the source before delimiter conversion, with its code. */
    private class CodeChunk(val end: Int, val code: List<IntRange>)

    private var text = ""
    private var chunks: List<Chunk> = emptyList()
    private var source = ""
    private var codeChunks: List<CodeChunk> = emptyList()

    fun parse(content: String): MarkdownDocument {
        val lines = normalizeLineEndings(content)
        val normalized = if (hasLatexDelimiters(lines)) normalizeLatexDelimiters(lines, sourceCodeRanges(lines)) else lines
        if (normalized == text && chunks.isNotEmpty()) {
            return MarkdownDocument(source = content, blocks = chunks.flatMap { it.blocks })
        }
        val common = commonPrefixLength(text, normalized)
        val kept = chunks.takeWhile { it.end <= common }.dropLast(REPARSED_CHUNKS)
        val tailStart = kept.lastOrNull()?.end ?: 0
        val tail = normalized.substring(tailStart)

        val tree = parseTree(tail)
        val groups = group(tree, tail)
        val groupDefinitions = groups.map { nodes ->
            HashMap<CharSequence, LinkMap.LinkInfo>().also { collectLinkDefinitions(tail, nodes, it) }
        }
        // Links in the tail can refer to definitions in kept chunks too.
        val definitions = HashMap<CharSequence, LinkMap.LinkInfo>()
        for (map in kept.map { it.definitions } + groupDefinitions) {
            for ((label, info) in map) definitions.getOrPut(label) { info }
        }
        val linkMap = LinkMap(definitions)
        val parsed = groups.mapIndexed { i, nodes ->
            Chunk(
                start = tailStart + nodes.first().startOffset,
                end = tailStart + nodes.last().endOffset,
                blocks = convertNodes(tail, tree, nodes, linkMap, softBreakAsLineBreak),
                definitions = groupDefinitions[i].ifEmpty { emptyMap() },
            )
        }
        text = normalized
        chunks = kept + parsed
        return MarkdownDocument(source = content, blocks = chunks.flatMap { it.blocks })
    }

    /**
     * Code ranges in [newSource] parsed as it is, for [normalizeLatexDelimiters].
     * Parsed incrementally like the blocks in [parse].
     */
    private fun sourceCodeRanges(newSource: String): List<IntRange> {
        val common = commonPrefixLength(source, newSource)
        val kept = codeChunks.takeWhile { it.end <= common }.dropLast(REPARSED_CHUNKS)
        val tailStart = kept.lastOrNull()?.end ?: 0
        val tail = newSource.substring(tailStart)
        val tree = parseTree(tail)
        val parsed = group(tree, tail).map { nodes ->
            CodeChunk(
                end = tailStart + nodes.last().endOffset,
                code = nodes.flatMap { codeRanges(it, tailStart) },
            )
        }
        source = newSource
        codeChunks = kept + parsed
        return codeChunks.flatMap { it.code }
    }

    private companion object {
        const val REPARSED_CHUNKS = 2

        // Elements that never have a closing tag.
        val VOID_TAGS = setOf(
            "area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "source", "track", "wbr",
        )
        val TAG_REGEX = Regex("""<(/?)([a-zA-Z][a-zA-Z0-9-]*)[^>]*?(/?)>""")

        fun commonPrefixLength(a: String, b: String): Int {
            val n = minOf(a.length, b.length)
            var i = 0
            while (i < n && a[i] == b[i]) i++
            return i
        }

        /**
         * Splits the top-level blocks of [tree] into groups that convert to the same
         * blocks on their own. A group doesn't end while an HTML element is open, and
         * HTML blocks stay with their neighbors: loose inline HTML on either side of a
         * boundary would otherwise not be merged into one paragraph.
         */
        fun group(tree: ASTNode, text: String): List<List<ASTNode>> {
            val groups = ArrayList<List<ASTNode>>()
            var current = ArrayList<ASTNode>()
            var depth = 0
            for (node in tree.children) {
                if (node.type == MarkdownTokenTypes.EOL || node.type == MarkdownTokenTypes.WHITE_SPACE) continue
                val html = node.type == MarkdownElementTypes.HTML_BLOCK
                val previousHtml = current.lastOrNull()?.type == MarkdownElementTypes.HTML_BLOCK
                if (current.isNotEmpty() && depth == 0 && !html && !previousHtml) {
                    groups.add(current)
                    current = ArrayList()
                }
                current.add(node)
                depth = maxOf(0, depth + tagBalance(node, text))
            }
            if (current.isNotEmpty()) groups.add(current)
            return groups
        }

        /** Opened minus closed HTML elements in [node], counting raw HTML only, not code. */
        fun tagBalance(node: ASTNode, text: String): Int = when (node.type) {
            MarkdownElementTypes.HTML_BLOCK, MarkdownTokenTypes.HTML_TAG -> TAG_REGEX.findAll(node.getTextInNode(text)).sumOf {
                val name = it.groupValues[2].lowercase()
                when {
                    name in VOID_TAGS || it.groupValues[3] == "/" -> 0
                    it.groupValues[1] == "/" -> -1
                    else -> 1
                }.toInt()
            }

            MarkdownElementTypes.CODE_SPAN, MarkdownElementTypes.CODE_FENCE, MarkdownElementTypes.CODE_BLOCK -> 0
            else -> node.children.sumOf { tagBalance(it, text) }
        }
    }
}
