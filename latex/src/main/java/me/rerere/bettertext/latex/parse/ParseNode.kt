package me.rerere.bettertext.latex.parse

/** Parsing mode: inside math or inside `\text{}`. */
internal enum class Mode { MATH, TEXT }

/** A dimension such as `3mu` or `-0.5em`. */
internal data class Measurement(val number: Float, val unit: String)

/** Column specification of an array environment. */
internal sealed class AlignSpec {
    data class Separator(val separator: String) : AlignSpec()
    data class Align(
        val align: String,
        val pregap: Float? = null,
        val postgap: Float? = null,
    ) : AlignSpec()
}

/**
 * The parse tree. Mirrors KaTeX's `AnyParseNode` union; class names follow the
 * KaTeX node `type` strings so the ported builders read the same.
 */
internal sealed class ParseNode {
    abstract val mode: Mode
    var loc: SourceLocation? = null
}

/** Nodes produced from a single entry of the symbol table. */
internal sealed class SymbolParseNode : ParseNode() {
    abstract val text: String
}

internal class AtomNode(override val mode: Mode, val family: String, override val text: String) : SymbolParseNode()
internal class MathOrdNode(override val mode: Mode, override val text: String) : SymbolParseNode()
internal class TextOrdNode(override val mode: Mode, override val text: String) : SymbolParseNode()
internal class SpacingNode(override val mode: Mode, override val text: String) : SymbolParseNode()
internal class AccentTokenNode(override val mode: Mode, override val text: String) : SymbolParseNode()
internal class OpTokenNode(override val mode: Mode, override val text: String) : SymbolParseNode()

internal class ArrayNode(
    override val mode: Mode,
    var colSeparationType: String? = null,
    var hskipBeforeAndAfter: Boolean? = null,
    var addJot: Boolean? = null,
    var cols: List<AlignSpec>? = null,
    var arraystretch: Float,
    var body: List<List<ParseNode>>,
    var rowGaps: List<Measurement?>,
    var hLinesBeforeRow: List<List<Boolean>>,
    /** Per row: `Boolean` (auto-number or not) or `List<ParseNode>` (explicit tag). */
    var tags: List<Any>? = null,
    var leqno: Boolean? = null,
) : ParseNode()

internal class ColorNode(override val mode: Mode, val color: String, val body: List<ParseNode>) : ParseNode()
internal class ColorTokenNode(override val mode: Mode, val color: String) : ParseNode()

internal class OpNode(
    override val mode: Mode,
    var limits: Boolean,
    var alwaysHandleSupSub: Boolean = false,
    var suppressBaseShift: Boolean = false,
    var parentIsSupSub: Boolean = false,
    val symbol: Boolean,
    val name: String? = null,
    val body: List<ParseNode>? = null,
) : ParseNode()

internal class OrdGroupNode(
    override val mode: Mode,
    val body: List<ParseNode>,
    val semisimple: Boolean = false,
) : ParseNode()

internal class RawNode(override val mode: Mode, val string: String) : ParseNode()
internal class SizeNode(override val mode: Mode, val value: Measurement, val isBlank: Boolean) : ParseNode()

internal class StylingNode(
    override val mode: Mode,
    val style: String,
    val body: List<ParseNode>,
    val resetFont: Boolean = false,
) : ParseNode()

internal class SupSubNode(
    override val mode: Mode,
    val base: ParseNode?,
    val sup: ParseNode?,
    val sub: ParseNode?,
) : ParseNode()

internal class TagNode(override val mode: Mode, val body: List<ParseNode>, val tag: List<ParseNode>) : ParseNode()
internal class TextNode(override val mode: Mode, val body: List<ParseNode>, val font: String? = null) : ParseNode()
internal class UrlNode(override val mode: Mode, val url: String) : ParseNode()
internal class VerbNode(override val mode: Mode, val body: String, val star: Boolean) : ParseNode()

internal class AccentNode(
    override val mode: Mode,
    val label: String,
    val isStretchy: Boolean,
    val isShifty: Boolean,
    val base: ParseNode,
) : ParseNode()

internal class AccentUnderNode(
    override val mode: Mode,
    val label: String,
    val isStretchy: Boolean = false,
    val isShifty: Boolean = false,
    val base: ParseNode,
) : ParseNode()

internal class CrNode(override val mode: Mode, val newLine: Boolean, val size: Measurement?) : ParseNode()

internal class DelimSizingNode(
    override val mode: Mode,
    val size: Int,
    val mclass: String,
    val delim: String,
) : ParseNode()

internal class EncloseNode(
    override val mode: Mode,
    val label: String,
    val body: ParseNode,
    val backgroundColor: String? = null,
    val borderColor: String? = null,
) : ParseNode()

internal class EnvironmentNode(override val mode: Mode, val name: String, val nameGroup: ParseNode) : ParseNode()
internal class FontNode(override val mode: Mode, val font: String, val body: ParseNode) : ParseNode()

internal class GenFracNode(
    override val mode: Mode,
    val continued: Boolean,
    val numer: ParseNode,
    val denom: ParseNode,
    val hasBarLine: Boolean,
    val leftDelim: String?,
    val rightDelim: String?,
    val barSize: Measurement?,
    /** `"display"`, `"text"`, `"script"`, `"scriptscript"` or `"auto"`. */
    val size: String = "auto",
) : ParseNode()

internal class HBoxNode(override val mode: Mode, val body: List<ParseNode>) : ParseNode()

internal class HorizBraceNode(
    override val mode: Mode,
    val label: String,
    val isOver: Boolean,
    val base: ParseNode,
) : ParseNode()

internal class HrefNode(override val mode: Mode, val href: String, val body: List<ParseNode>) : ParseNode()
internal class HtmlNode(override val mode: Mode, val attributes: Map<String, String>, val body: List<ParseNode>) : ParseNode()
internal class HtmlMathmlNode(override val mode: Mode, val html: List<ParseNode>, val mathml: List<ParseNode>) : ParseNode()

internal class InfixNode(
    override val mode: Mode,
    val replaceWith: String,
    val size: Measurement? = null,
    val token: Token? = null,
) : ParseNode()

/** Nodes that do not appear in the final tree, e.g. `\relax` or `\def`. */
internal class InternalNode(override val mode: Mode) : ParseNode()

internal class KernNode(override val mode: Mode, val dimension: Measurement) : ParseNode()
internal class LapNode(override val mode: Mode, val alignment: String, val body: ParseNode) : ParseNode()

internal class LeftRightNode(
    override val mode: Mode,
    val body: List<ParseNode>,
    val left: String,
    val right: String,
    val rightColor: String? = null,
) : ParseNode()

internal class LeftRightRightNode(override val mode: Mode, val delim: String, val color: String? = null) : ParseNode()

internal class MathChoiceNode(
    override val mode: Mode,
    val display: List<ParseNode>,
    val text: List<ParseNode>,
    val script: List<ParseNode>,
    val scriptscript: List<ParseNode>,
) : ParseNode()

internal class MiddleNode(override val mode: Mode, val delim: String) : ParseNode()

internal class MClassNode(
    override val mode: Mode,
    val mclass: String,
    val body: List<ParseNode>,
    val isCharacterBox: Boolean,
) : ParseNode()

internal class OperatorNameNode(
    override val mode: Mode,
    val body: List<ParseNode>,
    var alwaysHandleSupSub: Boolean,
    var limits: Boolean,
    var parentIsSupSub: Boolean = false,
) : ParseNode()

internal class OverlineNode(override val mode: Mode, val body: ParseNode) : ParseNode()
internal class UnderlineNode(override val mode: Mode, val body: ParseNode) : ParseNode()
internal class PhantomNode(override val mode: Mode, val body: List<ParseNode>) : ParseNode()
internal class VPhantomNode(override val mode: Mode, val body: ParseNode) : ParseNode()
internal class PmbNode(override val mode: Mode, val mclass: String, val body: List<ParseNode>) : ParseNode()
internal class RaiseBoxNode(override val mode: Mode, val dy: Measurement, val body: ParseNode) : ParseNode()

internal class RuleNode(
    override val mode: Mode,
    val shift: Measurement?,
    val width: Measurement,
    val height: Measurement,
) : ParseNode()

internal class SizingNode(override val mode: Mode, val size: Int, val body: List<ParseNode>) : ParseNode()

internal class SmashNode(
    override val mode: Mode,
    val body: ParseNode,
    val smashHeight: Boolean,
    val smashDepth: Boolean,
) : ParseNode()

internal class SqrtNode(override val mode: Mode, val body: ParseNode, val index: ParseNode?) : ParseNode()
internal class VCenterNode(override val mode: Mode, val body: ParseNode) : ParseNode()

internal class XArrowNode(
    override val mode: Mode,
    val label: String,
    val body: ParseNode,
    val below: ParseNode?,
) : ParseNode()

internal class ReflectBoxNode(override val mode: Mode, val body: ParseNode) : ParseNode()

/** Unwraps a node into the list of its children, like KaTeX's `ordargument`. */
internal fun ordArgument(arg: ParseNode): List<ParseNode> =
    if (arg is OrdGroupNode) arg.body else listOf(arg)
