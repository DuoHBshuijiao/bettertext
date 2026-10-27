package me.rerere.bettertext.latex.functions

import me.rerere.bettertext.latex.ParseError
import me.rerere.bettertext.latex.parse.EnvironmentNode
import me.rerere.bettertext.latex.parse.OrdGroupNode
import me.rerere.bettertext.latex.parse.ParseNode
import me.rerere.bettertext.latex.parse.SpacingNode
import me.rerere.bettertext.latex.parse.TextOrdNode

/**
 * KaTeX's `assertCharacterGroup` (named differently to avoid clashing with
 * other ports). Returns the string spelled out by a group of plain characters, throwing the
 * given ParseError if the group holds anything else. With [allowSpaces], a
 * literal space counts as a character; `~` and `\ ` do not.
 */
internal fun envCharacterGroup(group: ParseNode, errorMessage: String, allowSpaces: Boolean = false): String {
    if (group !is OrdGroupNode) throw ParseError(errorMessage)
    val text = StringBuilder()
    for (node in group.body) {
        if (node is TextOrdNode) {
            text.append(node.text)
        } else if (allowSpaces && node is SpacingNode && node.text == " ") {
            text.append(' ')
        } else {
            throw ParseError(errorMessage)
        }
    }
    return text.toString()
}

/**
 * environment.ts: environment delimiters. HTML rendering is defined in the
 * corresponding [defineEnvironment] definitions.
 */
internal fun registerEnvironment() {
    defineFunction(
        names = listOf("\\begin", "\\end"),
        numArgs = 1,
        argTypes = listOf(ArgType.TEXT),
    ) { ctx, args, _ ->
        val parser = ctx.parser
        val nameGroup = args[0]
        if (nameGroup !is OrdGroupNode) throw ParseError("Invalid environment name")
        val envName = envCharacterGroup(
            nameGroup,
            "Environment name should contain only text characters and spaces",
            true,
        )

        if (ctx.funcName == "\\begin") {
            // begin...end is similar to left...right
            val env = Registry.environments[envName]
                ?: throw ParseError("No such environment: $envName", ctx.token)
            // Build the environment object. Arguments and other information will
            // be made available to the begin and end methods using properties.
            val (envArgs, optArgs) = parser.parseArguments(
                "\\begin{$envName}",
                env.numArgs,
                env.numOptionalArgs,
                env.argTypes,
            )
            val context = EnvContext(parser.mode, envName, parser)
            val result = env.handler(context, envArgs, optArgs)
            parser.expect("\\end", false)
            val endNameToken = parser.fetch()
            // parseGroup("atom") parses the \end function with its argument.
            val end = parser.parseGroup("atom").assertType<EnvironmentNode>()
            if (end.name != envName) {
                throw ParseError("Mismatch: \\begin{$envName} matched by \\end{${end.name}}", endNameToken)
            }
            return@defineFunction result
        }

        EnvironmentNode(parser.mode, envName, nameGroup)
    }
}
