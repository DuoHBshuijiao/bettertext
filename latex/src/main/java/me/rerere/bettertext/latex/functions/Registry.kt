package me.rerere.bettertext.latex.functions

import me.rerere.bettertext.latex.layout.Box
import me.rerere.bettertext.latex.layout.Options
import me.rerere.bettertext.latex.parse.Mode
import me.rerere.bettertext.latex.parse.OrdGroupNode
import me.rerere.bettertext.latex.parse.ParseNode
import me.rerere.bettertext.latex.parse.Parser
import me.rerere.bettertext.latex.parse.Token
import kotlin.reflect.KClass

/** How an argument of a function is parsed. Mirrors KaTeX's `ArgType`. */
internal enum class ArgType { COLOR, SIZE, URL, RAW, ORIGINAL, HBOX, PRIMITIVE, MATH, TEXT }

internal class FunctionContext(
    val funcName: String,
    val parser: Parser,
    val token: Token? = null,
    val breakOnTokenText: String? = null,
)

internal typealias FunctionHandler = (ctx: FunctionContext, args: List<ParseNode>, optArgs: List<ParseNode?>) -> ParseNode

internal class FunctionSpec(
    val numArgs: Int = 0,
    val argTypes: List<ArgType?>? = null,
    val allowedInArgument: Boolean = false,
    val allowedInText: Boolean = false,
    val allowedInMath: Boolean = true,
    val numOptionalArgs: Int = 0,
    val infix: Boolean = false,
    val primitive: Boolean = false,
    val handler: FunctionHandler? = null,
)

internal class EnvContext(val mode: Mode, val envName: String, val parser: Parser)

internal typealias EnvHandler = (ctx: EnvContext, args: List<ParseNode>, optArgs: List<ParseNode?>) -> ParseNode

internal class EnvSpec(
    val numArgs: Int = 0,
    val argTypes: List<ArgType?>? = null,
    val allowedInText: Boolean = false,
    val numOptionalArgs: Int = 0,
    val handler: EnvHandler,
)

internal typealias HtmlBuilder = (group: ParseNode, options: Options) -> Box

/** Global tables of functions, environments and HTML builders, filled by [Builtins]. */
internal object Registry {
    val functions = HashMap<String, FunctionSpec>(512)
    val environments = HashMap<String, EnvSpec>(64)
    val htmlBuilders = HashMap<KClass<out ParseNode>, HtmlBuilder>(64)

    fun defineFunction(names: List<String>, spec: FunctionSpec) {
        for (name in names) functions[name] = spec
    }

    fun defineEnvironment(names: List<String>, spec: EnvSpec) {
        for (name in names) environments[name] = spec
    }

    inline fun <reified T : ParseNode> defineHtmlBuilder(noinline builder: (T, Options) -> Box) {
        @Suppress("UNCHECKED_CAST")
        htmlBuilders[T::class] = builder as HtmlBuilder
    }
}

internal fun defineFunction(
    names: List<String>,
    numArgs: Int = 0,
    argTypes: List<ArgType?>? = null,
    allowedInArgument: Boolean = false,
    allowedInText: Boolean = false,
    allowedInMath: Boolean = true,
    numOptionalArgs: Int = 0,
    infix: Boolean = false,
    primitive: Boolean = false,
    handler: FunctionHandler?,
) = Registry.defineFunction(
    names,
    FunctionSpec(
        numArgs, argTypes, allowedInArgument, allowedInText, allowedInMath,
        numOptionalArgs, infix, primitive, handler,
    ),
)

internal fun defineEnvironment(
    names: List<String>,
    numArgs: Int = 0,
    argTypes: List<ArgType?>? = null,
    allowedInText: Boolean = false,
    numOptionalArgs: Int = 0,
    handler: EnvHandler,
) = Registry.defineEnvironment(names, EnvSpec(numArgs, argTypes, allowedInText, numOptionalArgs, handler))

internal inline fun <reified T : ParseNode> defineHtmlBuilder(noinline builder: (T, Options) -> Box) =
    Registry.defineHtmlBuilder(builder)

/** Unwraps a single-element ordgroup. */
internal fun normalizeArgument(arg: ParseNode): ParseNode =
    if (arg is OrdGroupNode && arg.body.size == 1) arg.body[0] else arg

/** Casts a node to the expected type or fails with a parse error, like KaTeX's `assertNodeType`. */
internal inline fun <reified T : ParseNode> ParseNode?.assertType(): T =
    this as? T ?: throw me.rerere.bettertext.latex.ParseError(
        "Expected node of type ${T::class.simpleName}, but got ${this?.let { it::class.simpleName }}",
    )
