/*************************************************************
 *
 *  Kotlin port of KaTeX 0.19.1 contrib/mhchem (mhchem 3.3.0): the parser.
 *
 *  KaTeX mhchem.js
 *
 *  This file implements a KaTeX version of mhchem version 3.3.0.
 *  It is adapted from MathJax/extensions/TeX/mhchem.js
 *  It differs from the MathJax version as follows:
 *    1. The interface is changed so that it can be called from KaTeX, not MathJax.
 *    2. \rlap and \llap are replaced with \mathrlap and \mathllap.
 *    3. Four lines of code are edited in order to use \raisebox instead of \raise.
 *    4. The reaction arrow code is simplified. All reaction arrows are rendered
 *       using KaTeX extensible arrows instead of building non-extensible arrows.
 *    5. \tripledash vertical alignment is slightly adjusted.
 *
 *    This code, as other KaTeX code, is released under the MIT license.
 *
 * *************************************************************
 *
 *  MathJax/extensions/TeX/mhchem.js
 *
 *  Implements the \ce command for handling chemical formulas
 *  from the mhchem LaTeX package.
 *
 *  ---------------------------------------------------------------------
 *
 *  Copyright (c) 2011-2015 The MathJax Consortium
 *  Copyright (c) 2015-2018 Martin Hensel
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package me.rerere.bettertext.latex.contrib

import me.rerere.bettertext.latex.contrib.MhchemItem.Binary
import me.rerere.bettertext.latex.contrib.MhchemItem.Bond
import me.rerere.bettertext.latex.contrib.MhchemItem.ChemFive
import me.rerere.bettertext.latex.contrib.MhchemItem.Color
import me.rerere.bettertext.latex.contrib.MhchemItem.Color0
import me.rerere.bettertext.latex.contrib.MhchemItem.Nested
import me.rerere.bettertext.latex.contrib.MhchemItem.Operator
import me.rerere.bettertext.latex.contrib.MhchemItem.Plain
import me.rerere.bettertext.latex.contrib.MhchemItem.Raw
import me.rerere.bettertext.latex.contrib.MhchemItem.Text
import java.util.regex.Pattern

/** The characters of JavaScript's `\s`. */
private const val JS_SPACE =
    "\\t\\n\\u000B\\f\\r \\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF"

private fun isJsSpace(c: Char) =
    c in "\t\n\u000B\u000C\r \u00A0\u1680\u2028\u2029\u202F\u205F\u3000\uFEFF" || c in '\u2000'..'\u200A'

/**
 * A JavaScript regex (without flags) run with JavaScript semantics: `$` only matches at the end,
 * `.` and `\s` match what they match in JavaScript, literal braces need no escape, and the input
 * is matched by UTF-16 code unit rather than by code point. Android's ICU regex engine rejects
 * unescaped literal braces, so the translation escapes every brace outside a quantifier.
 */
private class JsRegex(source: String) {
    private val pattern: Pattern = Pattern.compile(translate(source))

    /** `input.match(regex)`: the groups, null for those that didn't participate, or null. */
    fun match(input: String): Array<String?>? {
        val m = pattern.matcher(codeUnits(input))
        if (!m.find()) return null
        return Array(m.groupCount() + 1) { g -> if (m.start(g) < 0) null else input.substring(m.start(g), m.end(g)) }
    }

    /** `input.replace(regex /g, replacement)` for a replacement without `$` patterns. */
    fun replaceAll(input: String, replacement: String): String {
        val m = pattern.matcher(codeUnits(input))
        val sb = StringBuilder()
        var last = 0
        while (m.find()) {
            sb.append(input, last, m.start()).append(replacement)
            last = m.end()
        }
        return sb.append(input, last, input.length).toString()
    }

    private companion object {
        private val quantifier = Regex("""\{\d+(,\d*)?\}""")

        /**
         * Java matches surrogate pairs as one code point, JavaScript (without the `u` flag) each
         * half on its own. Replace each half with a private use character that no pattern
         * mentions; the indices stay the same, so the groups are cut from the original.
         */
        fun codeUnits(input: String): String {
            if (input.none { it.isSurrogate() }) return input
            return String(CharArray(input.length) { if (input[it].isSurrogate()) '\uE000' else input[it] })
        }

        fun translate(source: String): String {
            val out = StringBuilder()
            var inClass = false
            var i = 0
            while (i < source.length) {
                val c = source[i]
                when {
                    c == '\\' -> {
                        val n = source[i + 1]
                        if (n == 's') out.append(if (inClass) JS_SPACE else "[$JS_SPACE]") else out.append(c).append(n)
                        i++
                    }

                    inClass -> when (c) {
                        ']' -> {
                            inClass = false
                            out.append(c)
                        }

                        '[', '&', '{', '}' -> out.append('\\').append(c)
                        else -> out.append(c)
                    }

                    c == '[' -> {
                        inClass = true
                        out.append(c)
                        if (source.getOrNull(i + 1) == '^') {
                            out.append('^')
                            i++
                        }
                    }

                    c == '$' -> out.append("\\z")
                    c == '.' -> out.append("[^\\n\\r\\u2028\\u2029]")
                    c == '{' -> {
                        val q = quantifier.matchAt(source, i)
                        if (q != null) {
                            out.append(q.value)
                            i = q.range.last
                        } else {
                            out.append("\\{")
                        }
                    }

                    c == '}' -> out.append("\\}")
                    else -> out.append(c)
                }
                i++
            }
            return out.toString()
        }
    }
}

/** A pattern match: [match] is a String or a List of (nullable) Strings. */
private class PatternMatch(val match: Any?, val remainder: String)

private val Any?.str: String? get() = this as String?

@Suppress("UNCHECKED_CAST")
private val Any?.list: List<String?> get() = this as List<String?>

/** JavaScript truthiness of a string. */
private val String?.t: Boolean get() = !isNullOrEmpty()

/** JavaScript's `s[i]` on a String, or the element of a List. */
private fun Any?.index(i: Int): String? = when (this) {
    is String -> getOrNull(i)?.toString()
    is List<*> -> getOrNull(i) as String?
    else -> null
}

/** `s.substr(start, length)`. */
private fun String.substr(start: Int, length: Int = this.length): String =
    substring(start, minOf(this.length, start + length))

/** The parser state shared by the actions; mhchem's `Buffer`. */
private class Buffer {
    var a: String? = null
    var b: String? = null
    var p: String? = null
    var o: String? = null
    var q: String? = null
    var d: String? = null
    var dType: String? = null
    var r: String? = null
    var rdt: String? = null
    var rd: String? = null
    var rqt: String? = null
    var rq: String? = null
    var text: String? = null
    var rm: String? = null
    var sb: Boolean? = null
    var parenthesisLevel = 0
    var beginsWithBond: Boolean? = null

    /** Deletes everything but parenthesisLevel and beginsWithBond. */
    fun clear() {
        a = null; b = null; p = null; o = null; q = null; d = null; dType = null
        r = null; rdt = null; rd = null; rqt = null; rq = null
        text = null; rm = null; sb = null
    }
}

private fun interface Action {
    /** Returns null, an item or a list of items. */
    fun run(buffer: Buffer, m: Any?, option: Any?): Any?
}

private class ActionCall(val type: String, val option: Any? = null)

private class Task(val actions: List<ActionCall>, val nextState: String?, val revisit: Boolean, val toContinue: Boolean)

private class Transition(val pattern: String, val task: Task)

private class Machine(val transitions: Map<String, List<Transition>>, val actions: Map<String, Action>)

private fun task(vararg actions: Any, next: String? = null, revisit: Boolean = false, toContinue: Boolean = false) =
    Task(actions.map { it as? ActionCall ?: ActionCall(it as String) }, next, revisit, toContinue)

private fun act(type: String, option: Any?) = ActionCall(type, option)

/**
 * Converts `pattern → (states → task)` to `state → [transitions]`, expanding `a|b` in both
 * patterns and states, and `*` to every state. Keeps the order of the definitions.
 */
private fun createTransitions(vararg spec: Pair<String, Map<String, Task>>): Map<String, List<Transition>> {
    val transitions = LinkedHashMap<String, MutableList<Transition>>()
    for ((_, states) in spec) {
        for (state in states.keys) for (s in state.split("|")) transitions[s] = ArrayList()
    }
    for ((pattern, states) in spec) {
        for ((state, task) in states) {
            for (s in state.split("|")) {
                for (p in pattern.split("|")) {
                    if (s == "*") {
                        for (list in transitions.values) list.add(Transition(p, task))
                    } else {
                        transitions.getValue(s).add(Transition(p, task))
                    }
                }
            }
        }
    }
    return transitions
}

/** Core parser for mhchem syntax (recursive). */
internal object MhchemParser {
    fun go(input: String?, stateMachine: String = "ce"): MutableList<MhchemItem> {
        if (input.isNullOrEmpty()) return ArrayList()
        var state = "0"
        val buffer = Buffer()
        var text: String = input.replace('\n', ' ')
            .replace('−', '-').replace('–', '-').replace('—', '-').replace('‐', '-')
            .replace("…", "...")
        val machine = requireNotNull(stateMachines[stateMachine]) { "Unknown mhchem state machine $stateMachine" }

        var lastInput: String? = null
        var watchdog = 10
        val output = ArrayList<MhchemItem>()
        while (true) {
            if (lastInput != text) {
                watchdog = 10
                lastInput = text
            } else {
                watchdog--
            }
            val t = machine.transitions[state] ?: machine.transitions.getValue("*")
            for (transition in t) {
                val matches = match(transition.pattern, text) ?: continue
                val task = transition.task
                for (call in task.actions) {
                    val action = machine.actions[call.type] ?: genericActions[call.type]
                        ?: throw MhchemException("MhchemBugA", "mhchem bug A. Please report. (${call.type})")
                    concat(output, action.run(buffer, matches.match, call.option))
                }
                // Apply only one transition per position.
                state = task.nextState ?: state
                if (text.isNotEmpty()) {
                    if (!task.revisit) text = matches.remainder
                    if (!task.toContinue) break
                } else {
                    return output
                }
            }
            if (watchdog <= 0) throw MhchemException("MhchemBugU", "mhchem bug U. Please report.")
        }
    }

    /** mhchem's `concatArray`: appends a list's items, or a single item. */
    private fun concat(a: MutableList<MhchemItem>, b: Any?) {
        when (b) {
            null -> {}
            is List<*> -> for (item in b) a.add(item as MhchemItem)
            else -> a.add(b as MhchemItem)
        }
    }

    // region Patterns

    /** Either a regex, whose match is reduced to group 1 and 2, group 1 or the whole match, or a function. */
    private val patterns: Map<String, (String) -> PatternMatch?> = buildMap {
        fun re(name: String, source: String) {
            val regex = JsRegex(source)
            put(name) { input ->
                regex.match(input)?.let { m ->
                    val mm: Any? = when {
                        m.getOrNull(2).t -> listOf(m[1], m[2])
                        m.getOrNull(1).t -> m[1]
                        else -> m[0]
                    }
                    PatternMatch(mm, input.substring(m[0]!!.length))
                }
            }
        }

        fun fn(name: String, f: (String) -> PatternMatch?) = put(name, f)

        // property names must not look like integers ("2") for correct property traversal order, later on
        re("empty", """^$""")
        re("else", """^.""")
        re("else2", """^.""")
        re("space", """^\s""")
        re("space A", """^\s(?=[A-Z\\$])""")
        re("space$", """^\s$""")
        re("a-z", """^[a-z]""")
        re("x", """^x""")
        re("x$", """^x$""")
        re("i$", """^i$""")
        re(
            "letters",
            """^(?:[a-zA-Zα-ωΑ-Ω?@]|(?:\\(?:alpha|beta|gamma|delta|epsilon|zeta|eta|theta|iota|kappa|lambda|mu|nu|xi|omicron|pi|rho|sigma|tau|upsilon|phi|chi|psi|omega|Gamma|Delta|Theta|Lambda|Xi|Pi|Sigma|Upsilon|Phi|Psi|Omega)(?:\s+|\{\}|(?![a-zA-Z]))))+""",
        )
        re(
            "\\greek",
            """^\\(?:alpha|beta|gamma|delta|epsilon|zeta|eta|theta|iota|kappa|lambda|mu|nu|xi|omicron|pi|rho|sigma|tau|upsilon|phi|chi|psi|omega|Gamma|Delta|Theta|Lambda|Xi|Pi|Sigma|Upsilon|Phi|Psi|Omega)(?:\s+|\{\}|(?![a-zA-Z]))""",
        )
        re("one lowercase latin letter $", """^(?:([a-z])(?:$|[^a-zA-Z]))$""")
        re("\$one lowercase latin letter\$ $", """^\$(?:([a-z])(?:$|[^a-zA-Z]))\$$""")
        re(
            "one lowercase greek letter $",
            """^(?:\$?[α-ω]\$?|\$?\\(?:alpha|beta|gamma|delta|epsilon|zeta|eta|theta|iota|kappa|lambda|mu|nu|xi|omicron|pi|rho|sigma|tau|upsilon|phi|chi|psi|omega)\s*\$?)(?:\s+|\{\}|(?![a-zA-Z]))$""",
        )
        re("digits", """^[0-9]+""")
        re("-9.,9", """^[+\-]?(?:[0-9]+(?:[,.][0-9]+)?|[0-9]*(?:\.[0-9]+))""")
        re("-9.,9 no missing 0", """^[+\-]?[0-9]+(?:[.,][0-9]+)?""")
        val numberWithExponent = JsRegex(
            """^(\+\-|\+\/\-|\+|\-|\\pm\s?)?([0-9]+(?:[,.][0-9]+)?|[0-9]*(?:\.[0-9]+))?(\((?:[0-9]+(?:[,.][0-9]+)?|[0-9]*(?:\.[0-9]+))\))?(?:([eE]|\s*(\*|x|\\times|×)\s*10\^)([+\-]?[0-9]+|\{[+\-]?[0-9]+\}))?""",
        )
        fn("(-)(9.,9)(e)(99)") { input ->
            val m = numberWithExponent.match(input)
            if (m != null && m[0].t) PatternMatch(m.drop(1), input.substring(m[0]!!.length)) else null
        }
        val numberPower = JsRegex(
            """^(\+\-|\+\/\-|\+|\-|\\pm\s?)?([0-9]+(?:[,.][0-9]+)?|[0-9]*(?:\.[0-9]+)?)\^([+\-]?[0-9]+|\{[+\-]?[0-9]+\})""",
        )
        fn("(-)(9)^(-9)") { input ->
            val m = numberPower.match(input)
            if (m != null && m[0].t) PatternMatch(m.drop(1), input.substring(m[0]!!.length)) else null
        }
        val aggregationStart = JsRegex("""^\([a-z]{1,3}(?=[\),])""")
        val phraseEnd = JsRegex("""^($|[\s,;\)\]\}])""")
        val crystalSystem = JsRegex("""^(?:\((?:\\ca\s?)?\$[amothc]\$\))""")
        fn("state of aggregation $") { input -> // ... or crystal system
            // (aq), (aq,$\infty$), (aq, sat)
            val a = findObserveGroups(input, "", aggregationStart, ")", "")
            if (a != null && phraseEnd.match(a.remainder) != null) return@fn a // AND end of 'phrase'
            // OR crystal system ($o$) (\ca$c$)
            val m = crystalSystem.match(input)
            if (m != null) PatternMatch(m[0], input.substring(m[0]!!.length)) else null
        }
        re("_{(state of aggregation)}$", """^_\{(\([a-z]{1,3}\))\}""")
        re("{[(", """^(?:\\\{|\[|\()""")
        re(")]}", """^(?:\)|\]|\\\})""")
        re(", ", """^[,;]\s*""")
        re(",", """^[,;]""")
        re(".", """^[.]""")
        re(". ", """^([.⋅·•])\s*""")
        re("...", """^\.\.\.(?=$|[^.])""")
        re("* ", """^([*])\s*""")
        fn("^{(...)}") { findObserveGroups(it, "^{", "", "", "}") }
        fn("^($...$)") { findObserveGroups(it, "^", "$", "$", "") }
        re("^a", """^\^([0-9]+|[^\\_])""")
        val commandWithBrace = JsRegex("""^\\[a-zA-Z]+\{""")
        fn("^\\x{}{}") { findObserveGroups(it, "^", commandWithBrace, "}", "", "", "{", "}", "", true) }
        fn("^\\x{}") { findObserveGroups(it, "^", commandWithBrace, "}", "") }
        re("^\\x", """^\^(\\[a-zA-Z]+)\s*""")
        re("^(-1)", """^\^(-?\d+)""")
        re("'", """^'""")
        fn("_{(...)}") { findObserveGroups(it, "_{", "", "", "}") }
        fn("_($...$)") { findObserveGroups(it, "_", "$", "$", "") }
        re("_9", """^_([+\-]?[0-9]+|[^\\])""")
        fn("_\\x{}{}") { findObserveGroups(it, "_", commandWithBrace, "}", "", "", "{", "}", "", true) }
        fn("_\\x{}") { findObserveGroups(it, "_", commandWithBrace, "}", "") }
        re("_\\x", """^_(\\[a-zA-Z]+)\s*""")
        re("^_", """^(?:\^(?=_)|\_(?=\^)|[\^_]$)""")
        re("{}", """^\{\}""")
        fn("{...}") { findObserveGroups(it, "", "{", "}", "") }
        fn("{(...)}") { findObserveGroups(it, "{", "", "", "}") }
        fn("$...$") { findObserveGroups(it, "", "$", "$", "") }
        fn("\${(...)}$") { findObserveGroups(it, "\${", "", "", "}$") }
        fn("$(...)$") { findObserveGroups(it, "$", "", "", "$") }
        re("=<>", """^[=<>]""")
        re("#", """^[#≡]""")
        re("+", """^\+""")
        re("-$", """^-(?=[\s_},;\]/]|$|\([a-z]+\))""") // -space -, -; -] -/ -$ -state-of-aggregation
        re("-9", """^-(?=[0-9])""")
        re("- orbital overlap", """^-(?=(?:[spd]|sp)(?:$|[\s,;\)\]\}]))""")
        re("-", """^-""")
        re("pm-operator", """^(?:\\pm|\$\\pm\$|\+-|\+\/-)""")
        re("operator", """^(?:\+|(?:[\-=<>]|<<|>>|\\approx|\$\\approx\$)(?=\s|$|-?[0-9]))""")
        re("arrowUpDown", """^(?:v|\(v\)|\^|\(\^\))(?=$|[\s,;\)\]\}])""")
        fn("\\bond{(...)}") { findObserveGroups(it, "\\bond{", "", "", "}") }
        re("->", """^(?:<->|<-->|->|<-|<=>>|<<=>|<=>|[→⟶⇌])""")
        re("CMT", """^[CMT](?=\[)""")
        fn("[(...)]") { findObserveGroups(it, "[", "", "", "]") }
        re("1st-level escape", """^(&|\\\\|\\hline)\s*""")
        re("\\,", """^(?:\\[,\ ;:])""") // \\x - but output no space before
        fn("\\x{}{}") { findObserveGroups(it, "", commandWithBrace, "}", "", "", "{", "}", "", true) }
        fn("\\x{}") { findObserveGroups(it, "", commandWithBrace, "}", "") }
        re("\\ca", """^\\ca(?:\s+|(?![a-zA-Z]))""")
        re("\\x", """^(?:\\[a-zA-Z]+\s*|\\[_&{}%])""")
        // only those with numbers in front, because the others will be formatted correctly anyway
        re("orbital", """^(?:[0-9]{1,2}[spdfgh]|[0-9]{0,2}sp)(?=$|[^a-zA-Z])""")
        re("others", """^[\/~|]""")
        fn("\\frac{(...)}") { findObserveGroups(it, "\\frac{", "", "", "}", "{", "", "", "}") }
        fn("\\overset{(...)}") { findObserveGroups(it, "\\overset{", "", "", "}", "{", "", "", "}") }
        fn("\\underset{(...)}") { findObserveGroups(it, "\\underset{", "", "", "}", "{", "", "", "}") }
        fn("\\underbrace{(...)}") { findObserveGroups(it, "\\underbrace{", "", "", "}_", "{", "", "", "}") }
        fn("\\color{(...)}0") { findObserveGroups(it, "\\color{", "", "", "}") }
        fn("\\color{(...)}{(...)}1") { findObserveGroups(it, "\\color{", "", "", "}", "{", "", "", "}") }
        val beforeBrace = JsRegex("""^(?=\{)""")
        fn("\\color(...){(...)}2") { findObserveGroups(it, "\\color", "\\", "", beforeBrace, "{", "", "", "}") }
        fn("\\ce{(...)}") { findObserveGroups(it, "\\ce{", "", "", "}") }
        re("oxidation$", """^(?:[+-][IVX]+|\\pm\s*0|\$\\pm\$\s*0)$""")
        re("d-oxidation$", """^(?:[+-]?\s?[IVX]+|\\pm\s*0|\$\\pm\$\s*0)$""") // 0 could be oxidation or charge
        re("roman numeral", """^[IVX]+""")
        re("1/2$", """^[+\-]?(?:[0-9]+|\$[a-z]\$|[a-z])\/[0-9]+(?:\$[a-z]\$|[a-z])?$""")
        val amount = JsRegex(
            """^(?:(?:(?:\([+\-]?[0-9]+\/[0-9]+\)|[+\-]?(?:[0-9]+|\$[a-z]\$|[a-z])\/[0-9]+|[+\-]?[0-9]+[.,][0-9]+|[+\-]?\.[0-9]+|[+\-]?[0-9]+)(?:[a-z](?=\s*[A-Z]))?)|[+\-]?[a-z](?=\s*[A-Z])|\+(?!\s))""",
        )
        val mathAmount = JsRegex("""^\$(?:\(?[+\-]?(?:[0-9]*[a-z]?[+\-])?[0-9]*[a-z](?:[+\-][0-9]*[a-z]?)?\)?|\+|-)\$$""")
        val amountFn: (String) -> PatternMatch? = fn@{ input ->
            // e.g. 2, 0.5, 1/2, -2, n/2, +;  $a$ could be added later in parsing
            val match = amount.match(input)
            if (match != null) return@fn PatternMatch(match[0], input.substring(match[0]!!.length))
            val a = findObserveGroups(input, "", "$", "$", "")
            if (a != null) { // e.g. $2n-1$, $-$
                val m = mathAmount.match(a.match as String)
                if (m != null) return@fn PatternMatch(m[0], input.substring(m[0]!!.length))
            }
            null
        }
        fn("amount", amountFn)
        fn("amount2", amountFn)
        re("(KV letters),", """^(?:[A-Z][a-z]{0,2}|i)(?=,)""")
        val stateOfAggregation = JsRegex("""^\([a-z]+\)$""")
        val formula = JsRegex(
            """^(?:[a-z]|(?:[0-9\ \+\-\,\.\(\)]+[a-z])+[0-9\ \+\-\,\.\(\)]*|(?:[a-z][0-9\ \+\-\,\.\(\)]+)+[a-z]?)$""",
        )
        fn("formula$") { input ->
            if (stateOfAggregation.match(input) != null) return@fn null // state of aggregation = no formula
            val m = formula.match(input)
            if (m != null) PatternMatch(m[0], input.substring(m[0]!!.length)) else null
        }
        re("uprightEntities", """^(?:pH|pOH|pC|pK|iPr|iBu)(?=$|[^a-zA-Z])""")
        re("/", """^\s*(\/)\s*""")
        re("//", """^\s*(\/\/)\s*""")
        re("*", """^\s*[*.]\s*""")
    }

    /** Matches a String prefix or a [JsRegex]; returns the matched text. */
    private fun matchStart(input: String, pattern: Any): String? = when (pattern) {
        is String -> if (input.startsWith(pattern)) pattern else null
        else -> (pattern as JsRegex).match(input)?.get(0)
    }

    private fun truthy(pattern: Any) = pattern !is String || pattern.isNotEmpty()

    /**
     * Matches `begExcl begIncl … endIncl endExcl` with balanced braces in between; the match
     * excludes the `Excl` parts. With `beg2…`, a second group follows, and the match is both
     * groups (joined if [combine]).
     */
    private fun findObserveGroups(
        input: String,
        begExcl: Any,
        begIncl: Any,
        endIncl: Any,
        endExcl: Any,
        beg2Excl: Any = "",
        beg2Incl: Any = "",
        end2Incl: Any = "",
        end2Excl: Any = "",
        combine: Boolean = false,
    ): PatternMatch? {
        var rest = input
        var match = matchStart(rest, begExcl) ?: return null
        rest = rest.substring(match.length)
        match = matchStart(rest, begIncl) ?: return null
        val endChars = if (truthy(endIncl)) endIncl else endExcl
        var endMatchBegin = -1
        var endMatchEnd = -1
        var braces = 0
        var i = match.length
        while (i < rest.length) {
            val a = rest[i]
            val end = matchStart(rest.substring(i), endChars)
            if (end != null && braces == 0) {
                endMatchBegin = i
                endMatchEnd = i + end.length
                break
            } else if (a == '{') {
                braces++
            } else if (a == '}') {
                if (braces == 0) {
                    throw MhchemException("ExtraCloseMissingOpen", "Extra close brace or missing open brace")
                } else {
                    braces--
                }
            }
            i++
        }
        if (endMatchBegin < 0) return null
        val match1 = rest.substring(0, if (truthy(endIncl)) endMatchEnd else endMatchBegin)
        if (!(truthy(beg2Excl) || truthy(beg2Incl))) {
            return PatternMatch(match1, rest.substring(endMatchEnd))
        }
        val group2 = findObserveGroups(rest.substring(endMatchEnd), beg2Excl, beg2Incl, end2Incl, end2Excl)
            ?: return null
        val matchRet = listOf(match1, group2.match as String)
        return PatternMatch(if (combine) matchRet.joinToString("") else matchRet, group2.remainder)
    }

    /** mhchem's `patterns.match_`: looks up the pattern by name and matches the start of [input]. */
    private fun match(name: String, input: String): PatternMatch? {
        val pattern = patterns[name] ?: throw MhchemException("MhchemBugP", "mhchem bug P. Please report. ($name)")
        return pattern(input)
    }

    // endregion

    // region Actions

    private val oneToTwoDigits = JsRegex("""^([0-9]+|\$[a-z]\$|[a-z])\/([0-9]+)(\$[a-z]\$|[a-z])?$""")

    /** Generic state machine actions. */
    private val genericActions: Map<String, Action> = mapOf(
        "a=" to Action { b, m, _ -> b.a = b.a.orEmpty() + m.str; null },
        "b=" to Action { b, m, _ -> b.b = b.b.orEmpty() + m.str; null },
        "p=" to Action { b, m, _ -> b.p = b.p.orEmpty() + m.str; null },
        "o=" to Action { b, m, _ -> b.o = b.o.orEmpty() + m.str; null },
        "q=" to Action { b, m, _ -> b.q = b.q.orEmpty() + m.str; null },
        "d=" to Action { b, m, _ -> b.d = b.d.orEmpty() + m.str; null },
        "rm=" to Action { b, m, _ -> b.rm = b.rm.orEmpty() + m.str; null },
        "text=" to Action { b, m, _ -> b.text = b.text.orEmpty() + m.str; null },
        "insert" to Action { _, _, a -> Plain(a as String) },
        "insert+p1" to Action { _, m, a -> Text(a as String, m.str!!) },
        "copy" to Action { _, m, _ -> m.str?.takeIf { it.isNotEmpty() }?.let(::Raw) },
        "rm" to Action { _, m, _ -> Text("rm", m.str.orEmpty()) },
        "text" to Action { _, m, _ -> go(m.str, "text") },
        "{text}" to Action { _, m, _ -> listOf<MhchemItem>(Raw("{")) + go(m.str, "text") + Raw("}") },
        "tex-math" to Action { _, m, _ -> go(m.str, "tex-math") },
        "tex-math tight" to Action { _, m, _ -> go(m.str, "tex-math tight") },
        "bond" to Action { _, m, k -> bond(m, k as String?) },
        "color0-output" to Action { _, m, _ -> Color0(m.index(0)) },
        "ce" to Action { _, m, _ -> go(m.str) },
        "1/2" to Action { _, m, _ ->
            var s = m.str!!
            val ret = ArrayList<MhchemItem>()
            if (s.startsWith("+") || s.startsWith("-")) {
                ret.add(Raw(s.substring(0, 1)))
                s = s.substring(1)
            }
            val n = oneToTwoDigits.match(s)!!
            ret.add(MhchemItem.Frac(n[1]!!.replace("$", ""), n[2]!!))
            if (n[3].t) ret.add(Text("tex-math", n[3]!!.replace("$", "")))
            ret
        },
        "9,9" to Action { _, m, _ -> go(m.str, "9,9") },
    )

    private fun bond(m: Any?, k: String?) = Bond(if (k.t) k else m.str)

    private fun operator(m: Any?, p1: Any?): Operator {
        val kind = p1 as String?
        return Operator(if (kind.t) kind else m.str)
    }

    /** The `output` action of the `ce` machine. */
    private fun ceOutput(buffer: Buffer, entityFollows: Int? = null): Any {
        // entityFollows:
        //   null = if we have nothing else to output, also ignore the just read space (buffer.sb)
        //   1 = an entity follows, never omit the space if there was one just read before (can only apply to state 1)
        //   2 = 1 + the entity can have an amount, so output a\, instead of converting it to o (can only apply to states a|as)
        val ret: Any
        with(buffer) {
            if (!r.t) {
                val list = ArrayList<MhchemItem>()
                if (!a.t && !b.t && !p.t && !o.t && !q.t && !d.t && (entityFollows ?: 0) == 0) {
                    // nothing
                } else {
                    if (sb == true) list.add(Plain("entitySkip"))
                    if (!o.t && !q.t && !d.t && !b.t && !p.t && entityFollows != 2) {
                        o = a
                        a = null
                    } else if (!o.t && !q.t && !d.t && (b.t || p.t)) {
                        o = a
                        d = b
                        q = p
                        a = null
                        b = null
                        p = null
                    } else {
                        if (o.t && dType == "kv" && match("d-oxidation$", d ?: "") != null) {
                            dType = "oxidation"
                        } else if (o.t && dType == "kv" && !q.t) {
                            dType = null
                        }
                    }
                    list.add(
                        ChemFive(
                            a = go(a, "a"),
                            b = go(b, "bd"),
                            p = go(p, "pq"),
                            o = go(o, "o"),
                            q = go(q, "pq"),
                            d = go(d, if (dType == "oxidation") "oxidation" else "bd"),
                            dType = dType,
                        ),
                    )
                }
                ret = list
            } else {
                val rdItems = when (rdt) {
                    "M" -> go(rd, "tex-math")
                    "T" -> listOf(Text("text", rd.orEmpty()))
                    else -> go(rd)
                }
                val rqItems = when (rqt) {
                    "M" -> go(rq, "tex-math")
                    "T" -> listOf(Text("text", rq.orEmpty()))
                    else -> go(rq)
                }
                ret = MhchemItem.Arrow(r!!, rdItems, rqItems)
            }
            clear()
        }
        return ret
    }

    private val ceActions: Map<String, Action> = mapOf(
        "o after d" to Action { b, m, _ ->
            val ret: Any
            val d = b.d.orEmpty()
            if (d.isNotEmpty() && d.all { it in '0'..'9' }) {
                b.d = null
                ret = ceOutput(b)
                b.b = d
            } else {
                ret = ceOutput(b)
            }
            genericActions.getValue("o=").run(b, m, null)
            ret
        },
        "d= kv" to Action { b, m, _ ->
            b.d = m.str
            b.dType = "kv"
            null
        },
        "charge or bond" to Action { b, m, _ ->
            if (b.beginsWithBond == true) {
                val ret = ArrayList<MhchemItem>()
                concat(ret, ceOutput(b))
                concat(ret, bond(m, "-"))
                ret
            } else {
                b.d = m.str
                null
            }
        },
        "- after o/d" to Action { b, m, isAfterD ->
            var c1 = match("orbital", b.o.orEmpty())
            val c2 = match("one lowercase greek letter $", b.o.orEmpty())
            val c3 = match("one lowercase latin letter $", b.o.orEmpty())
            val c4 = match("\$one lowercase latin letter\$ $", b.o.orEmpty())
            val hyphenFollows = m == "-" && (c1 != null && c1.remainder == "" || c2 != null || c3 != null || c4 != null)
            if (hyphenFollows && !b.a.t && !b.b.t && !b.p.t && !b.d.t && !b.q.t && c1 == null && c3 != null) {
                b.o = "$" + b.o + "$"
            }
            val ret = ArrayList<MhchemItem>()
            if (hyphenFollows) {
                concat(ret, ceOutput(b))
                ret.add(Plain("hyphen"))
            } else {
                c1 = match("digits", b.d.orEmpty())
                if (isAfterD == true && c1 != null && c1.remainder == "") {
                    concat(ret, genericActions.getValue("d=").run(b, m, null))
                    concat(ret, ceOutput(b))
                } else {
                    concat(ret, ceOutput(b))
                    concat(ret, bond(m, "-"))
                }
            }
            ret
        },
        "a to o" to Action { b, _, _ ->
            b.o = b.a
            b.a = null
            null
        },
        "sb=true" to Action { b, _, _ -> b.sb = true; null },
        "sb=false" to Action { b, _, _ -> b.sb = false; null },
        "beginsWithBond=true" to Action { b, _, _ -> b.beginsWithBond = true; null },
        "beginsWithBond=false" to Action { b, _, _ -> b.beginsWithBond = false; null },
        "parenthesisLevel++" to Action { b, _, _ -> b.parenthesisLevel++; null },
        "parenthesisLevel--" to Action { b, _, _ -> b.parenthesisLevel--; null },
        "state of aggregation" to Action { _, m, _ -> Nested("state of aggregation", go(m.str, "o")) },
        "comma" to Action { b, m, _ ->
            val s = m.str!!
            val a = s.trimEnd(::isJsSpace)
            val withSpace = a != s
            if (withSpace && b.parenthesisLevel == 0) {
                Text("comma enumeration L", a)
            } else {
                Text("comma enumeration M", a)
            }
        },
        "output" to Action { b, _, entityFollows -> ceOutput(b, entityFollows as Int?) },
        "oxidation-output" to Action { _, m, _ -> listOf<MhchemItem>(Raw("{")) + go(m.str, "oxidation") + Raw("}") },
        "frac-output" to Action { _, m, _ -> Binary("frac-ce", go(m.list[0]), go(m.list[1])) },
        "overset-output" to Action { _, m, _ -> Binary("overset", go(m.list[0]), go(m.list[1])) },
        "underset-output" to Action { _, m, _ -> Binary("underset", go(m.list[0]), go(m.list[1])) },
        "underbrace-output" to Action { _, m, _ -> Binary("underbrace", go(m.list[0]), go(m.list[1])) },
        "color-output" to Action { _, m, _ -> Color(m.list[0], go(m.list[1])) },
        "r=" to Action { b, m, _ -> b.r = m.str; null },
        "rdt=" to Action { b, m, _ -> b.rdt = m.str; null },
        "rd=" to Action { b, m, _ -> b.rd = m.str; null },
        "rqt=" to Action { b, m, _ -> b.rqt = m.str; null },
        "rq=" to Action { b, m, _ -> b.rq = m.str; null },
        "operator" to Action { _, m, p1 -> operator(m, p1) },
    )

    /** `output` of the `tex-math` machines. */
    private val texMathOutput = Action { b, _, _ ->
        if (b.o.t) {
            val ret = Text("tex-math", b.o!!)
            b.clear()
            ret
        } else {
            null
        }
    }

    private val puActions: Map<String, Action> = mapOf(
        "enumber" to Action { _, m, _ ->
            val g = m.list
            val ret = ArrayList<MhchemItem>()
            if (g[0] == "+-" || g[0] == "+/-") {
                ret.add(Raw("\\pm "))
            } else if (g[0].t) {
                ret.add(Raw(g[0]!!))
            }
            var g3 = g[3]
            if (g[1].t) {
                concat(ret, go(g[1], "pu-9,9"))
                if (g[2].t) {
                    if (g[2]!!.contains(',') || g[2]!!.contains('.')) {
                        concat(ret, go(g[2], "pu-9,9"))
                    } else {
                        ret.add(Raw(g[2]!!))
                    }
                }
                g3 = if (g[4].t) g[4] else g3
                if (g3.t) {
                    g3 = g3!!.trim(::isJsSpace)
                    if (g3 == "e" || g3.substr(0, 1) == "*") {
                        ret.add(Plain("cdot"))
                    } else {
                        ret.add(Plain("times"))
                    }
                }
            }
            if (g3.t) ret.add(Raw("10^{" + jsString(g[5]) + "}"))
            ret
        },
        "number^" to Action { _, m, _ ->
            val g = m.list
            val ret = ArrayList<MhchemItem>()
            if (g[0] == "+-" || g[0] == "+/-") {
                ret.add(Raw("\\pm "))
            } else if (g[0].t) {
                ret.add(Raw(g[0]!!))
            }
            concat(ret, go(g[1], "pu-9,9"))
            ret.add(Raw("^{" + jsString(g[2]) + "}"))
            ret
        },
        "operator" to Action { _, m, p1 -> operator(m, p1) },
        "space" to Action { _, _, _ -> Plain("pu-space-1") },
        "output" to Action { b, _, _ ->
            val ret: Any
            val md = match("{(...)}", b.d.orEmpty())
            if (md != null && md.remainder == "") b.d = md.match.str
            val mq = match("{(...)}", b.q.orEmpty())
            if (mq != null && mq.remainder == "") b.q = mq.match.str
            if (b.d.t) b.d = degrees(b.d!!)
            if (b.q.t) { // fraction
                b.q = degrees(b.q!!)
                val d = go(b.d, "pu")
                val q = go(b.q, "pu")
                if (b.o == "//") {
                    ret = Binary("pu-frac", d, q)
                } else {
                    ret = d
                    if (d.size > 1 || q.size > 1) d.add(Plain(" / ")) else d.add(Plain("/"))
                    d.addAll(q)
                }
            } else { // no fraction
                ret = go(b.d, "pu-2")
            }
            b.clear()
            ret
        },
    )

    private val celsius = JsRegex("""°C|\^oC|\^{o}C""")
    private val fahrenheit = JsRegex("""°F|\^oF|\^{o}F""")

    private fun degrees(s: String): String =
        fahrenheit.replaceAll(celsius.replaceAll(s, "{}^{\\circ}C"), "{}^{\\circ}F")

    private fun thousands(buffer: Buffer, fromEnd: Boolean): List<MhchemItem> {
        val ret = ArrayList<MhchemItem>()
        val text = buffer.text.orEmpty()
        if (text.length > 4) {
            if (fromEnd) {
                var a = text.length % 3
                if (a == 0) a = 3
                var i = text.length - 3
                while (i > 0) {
                    ret.add(Raw(text.substr(i, 3)))
                    ret.add(Plain("1000 separator"))
                    i -= 3
                }
                ret.add(Raw(text.substr(0, a)))
                ret.reverse()
            } else {
                val a = text.length - 3
                var i = 0
                while (i < a) {
                    ret.add(Raw(text.substr(i, 3)))
                    ret.add(Plain("1000 separator"))
                    i += 3
                }
                ret.add(Raw(text.substr(i)))
            }
        } else {
            ret.add(Raw(text))
        }
        buffer.clear()
        return ret
    }

    // endregion

    // region State machines

    private val stateMachines: Map<String, Machine> = mapOf(
        //
        // \ce state machines
        //
        "ce" to Machine( // main parser
            createTransitions(
                "empty" to mapOf("*" to task("output")),
                "else" to mapOf("0|1|2" to task("beginsWithBond=false", revisit = true, toContinue = true)),
                "oxidation$" to mapOf("0" to task("oxidation-output")),
                "CMT" to mapOf(
                    "r" to task("rdt=", next = "rt"),
                    "rd" to task("rqt=", next = "rdt"),
                ),
                "arrowUpDown" to mapOf("0|1|2|as" to task("sb=false", "output", "operator", next = "1")),
                "uprightEntities" to mapOf("0|1|2" to task("o=", "output", next = "1")),
                "orbital" to mapOf("0|1|2|3" to task("o=", next = "o")),
                "->" to mapOf(
                    "0|1|2|3" to task("r=", next = "r"),
                    "a|as" to task("output", "r=", next = "r"),
                    "*" to task("output", "r=", next = "r"),
                ),
                "+" to mapOf(
                    "o" to task("d= kv", next = "d"),
                    "d|D" to task("d=", next = "d"),
                    "q" to task("d=", next = "qd"),
                    "qd|qD" to task("d=", next = "qd"),
                    "dq" to task("output", "d=", next = "d"),
                    "3" to task("sb=false", "output", "operator", next = "0"),
                ),
                "amount" to mapOf("0|2" to task("a=", next = "a")),
                "pm-operator" to mapOf(
                    "0|1|2|a|as" to task("sb=false", "output", act("operator", "\\pm"), next = "0"),
                ),
                "operator" to mapOf("0|1|2|a|as" to task("sb=false", "output", "operator", next = "0")),
                "-$" to mapOf(
                    "o|q" to task("charge or bond", "output", next = "qd"),
                    "d" to task("d=", next = "d"),
                    "D" to task("output", act("bond", "-"), next = "3"),
                    "q" to task("d=", next = "qd"),
                    "qd" to task("d=", next = "qd"),
                    "qD|dq" to task("output", act("bond", "-"), next = "3"),
                ),
                "-9" to mapOf("3|o" to task("output", act("insert", "hyphen"), next = "3")),
                "- orbital overlap" to mapOf(
                    "o" to task("output", act("insert", "hyphen"), next = "2"),
                    "d" to task("output", act("insert", "hyphen"), next = "2"),
                ),
                "-" to mapOf(
                    "0|1|2" to task(act("output", 1), "beginsWithBond=true", act("bond", "-"), next = "3"),
                    "3" to task(act("bond", "-")),
                    "a" to task("output", act("insert", "hyphen"), next = "2"),
                    "as" to task(act("output", 2), act("bond", "-"), next = "3"),
                    "b" to task("b="),
                    "o" to task(act("- after o/d", false), next = "2"),
                    "q" to task(act("- after o/d", false), next = "2"),
                    "d|qd|dq" to task(act("- after o/d", true), next = "2"),
                    "D|qD|p" to task("output", act("bond", "-"), next = "3"),
                ),
                "amount2" to mapOf("1|3" to task("a=", next = "a")),
                "letters" to mapOf(
                    "0|1|2|3|a|as|b|p|bp|o" to task("o=", next = "o"),
                    "q|dq" to task("output", "o=", next = "o"),
                    "d|D|qd|qD" to task("o after d", next = "o"),
                ),
                "digits" to mapOf(
                    "o" to task("q=", next = "q"),
                    "d|D" to task("q=", next = "dq"),
                    "q" to task("output", "o=", next = "o"),
                    "a" to task("o=", next = "o"),
                ),
                "space A" to mapOf("b|p|bp" to task()),
                "space" to mapOf(
                    "a" to task(next = "as"),
                    "0" to task("sb=false"),
                    "1|2" to task("sb=true"),
                    "r|rt|rd|rdt|rdq" to task("output", next = "0"),
                    "*" to task("output", "sb=true", next = "1"),
                ),
                "1st-level escape" to mapOf(
                    "1|2" to task("output", act("insert+p1", "1st-level escape")),
                    "*" to task("output", act("insert+p1", "1st-level escape"), next = "0"),
                ),
                "[(...)]" to mapOf(
                    "r|rt" to task("rd=", next = "rd"),
                    "rd|rdt" to task("rq=", next = "rdq"),
                ),
                "..." to mapOf(
                    "o|d|D|dq|qd|qD" to task("output", act("bond", "..."), next = "3"),
                    "*" to task(act("output", 1), act("insert", "ellipsis"), next = "1"),
                ),
                ". |* " to mapOf("*" to task("output", act("insert", "addition compound"), next = "1")),
                "state of aggregation $" to mapOf("*" to task("output", "state of aggregation", next = "1")),
                "{[(" to mapOf(
                    "a|as|o" to task("o=", "output", "parenthesisLevel++", next = "2"),
                    "0|1|2|3" to task("o=", "output", "parenthesisLevel++", next = "2"),
                    "*" to task("output", "o=", "output", "parenthesisLevel++", next = "2"),
                ),
                ")]}" to mapOf(
                    "0|1|2|3|b|p|bp|o" to task("o=", "parenthesisLevel--", next = "o"),
                    "a|as|d|D|q|qd|qD|dq" to task("output", "o=", "parenthesisLevel--", next = "o"),
                ),
                ", " to mapOf("*" to task("output", "comma", next = "0")),
                "^_" to mapOf("*" to task()), // ^ and _ without a sensible argument
                "^{(...)}|^(\$...\$)" to mapOf(
                    "0|1|2|as" to task("b=", next = "b"),
                    "p" to task("b=", next = "bp"),
                    "3|o" to task("d= kv", next = "D"),
                    "q" to task("d=", next = "qD"),
                    "d|D|qd|qD|dq" to task("output", "d=", next = "D"),
                ),
                "^a|^\\x{}{}|^\\x{}|^\\x|'" to mapOf(
                    "0|1|2|as" to task("b=", next = "b"),
                    "p" to task("b=", next = "bp"),
                    "3|o" to task("d= kv", next = "d"),
                    "q" to task("d=", next = "qd"),
                    "d|qd|D|qD" to task("d="),
                    "dq" to task("output", "d=", next = "d"),
                ),
                "_{(state of aggregation)}$" to mapOf("d|D|q|qd|qD|dq" to task("output", "q=", next = "q")),
                "_{(...)}|_(\$...\$)|_9|_\\x{}{}|_\\x{}|_\\x" to mapOf(
                    "0|1|2|as" to task("p=", next = "p"),
                    "b" to task("p=", next = "bp"),
                    "3|o" to task("q=", next = "q"),
                    "d|D" to task("q=", next = "dq"),
                    "q|qd|qD|dq" to task("output", "q=", next = "q"),
                ),
                "=<>" to mapOf("0|1|2|3|a|as|o|q|d|D|qd|qD|dq" to task(act("output", 2), "bond", next = "3")),
                "#" to mapOf("0|1|2|3|a|as|o" to task(act("output", 2), act("bond", "#"), next = "3")),
                "{}" to mapOf("*" to task(act("output", 1), next = "1")),
                "{...}" to mapOf(
                    "0|1|2|3|a|as|b|p|bp" to task("o=", next = "o"),
                    "o|d|D|q|qd|qD|dq" to task("output", "o=", next = "o"),
                ),
                "\$...\$" to mapOf(
                    "a" to task("a="), // 2$n$
                    "0|1|2|3|as|b|p|bp|o" to task("o=", next = "o"), // not 'amount'
                    "as|o" to task("o="),
                    "q|d|D|qd|qD|dq" to task("output", "o=", next = "o"),
                ),
                "\\bond{(...)}" to mapOf("*" to task(act("output", 2), "bond", next = "3")),
                "\\frac{(...)}" to mapOf("*" to task(act("output", 1), "frac-output", next = "3")),
                "\\overset{(...)}" to mapOf("*" to task(act("output", 2), "overset-output", next = "3")),
                "\\underset{(...)}" to mapOf("*" to task(act("output", 2), "underset-output", next = "3")),
                "\\underbrace{(...)}" to mapOf("*" to task(act("output", 2), "underbrace-output", next = "3")),
                "\\color{(...)}{(...)}1|\\color(...){(...)}2" to mapOf(
                    "*" to task(act("output", 2), "color-output", next = "3"),
                ),
                "\\color{(...)}0" to mapOf("*" to task(act("output", 2), "color0-output")),
                "\\ce{(...)}" to mapOf("*" to task(act("output", 2), "ce", next = "3")),
                "\\," to mapOf("*" to task(act("output", 1), "copy", next = "1")),
                "\\x{}{}|\\x{}|\\x" to mapOf(
                    "0|1|2|3|a|as|b|p|bp|o|c0" to task("o=", "output", next = "3"),
                    "*" to task("output", "o=", "output", next = "3"),
                ),
                "others" to mapOf("*" to task(act("output", 1), "copy", next = "3")),
                "else2" to mapOf(
                    "a" to task("a to o", next = "o", revisit = true),
                    "as" to task("output", "sb=true", next = "1", revisit = true),
                    "r|rt|rd|rdt|rdq" to task("output", next = "0", revisit = true),
                    "*" to task("output", "copy", next = "3"),
                ),
            ),
            ceActions,
        ),
        "a" to Machine(
            createTransitions(
                "empty" to mapOf("*" to task()),
                "1/2$" to mapOf("0" to task("1/2")),
                "else" to mapOf("0" to task(next = "1", revisit = true)),
                "\$(...)\$" to mapOf("*" to task("tex-math tight", next = "1")),
                "," to mapOf("*" to task(act("insert", "commaDecimal"))),
                "else2" to mapOf("*" to task("copy")),
            ),
            emptyMap(),
        ),
        "o" to Machine(
            createTransitions(
                "empty" to mapOf("*" to task()),
                "1/2$" to mapOf("0" to task("1/2")),
                "else" to mapOf("0" to task(next = "1", revisit = true)),
                "letters" to mapOf("*" to task("rm")),
                "\\ca" to mapOf("*" to task(act("insert", "circa"))),
                "\\x{}{}|\\x{}|\\x" to mapOf("*" to task("copy")),
                "\${(...)}\$|\$(...)\$" to mapOf("*" to task("tex-math")),
                "{(...)}" to mapOf("*" to task("{text}")),
                "else2" to mapOf("*" to task("copy")),
            ),
            emptyMap(),
        ),
        "text" to Machine(
            createTransitions(
                "empty" to mapOf("*" to task("output")),
                "{...}" to mapOf("*" to task("text=")),
                "\${(...)}\$|\$(...)\$" to mapOf("*" to task("tex-math")),
                "\\greek" to mapOf("*" to task("output", "rm")),
                "\\,|\\x{}{}|\\x{}|\\x" to mapOf("*" to task("output", "copy")),
                "else" to mapOf("*" to task("text=")),
            ),
            mapOf(
                "output" to Action { b, _, _ ->
                    if (b.text.t) {
                        val ret = Text("text", b.text!!)
                        b.clear()
                        ret
                    } else {
                        null
                    }
                },
            ),
        ),
        "pq" to Machine(
            createTransitions(
                "empty" to mapOf("*" to task()),
                "state of aggregation $" to mapOf("*" to task("state of aggregation")),
                "i$" to mapOf("0" to task(next = "!f", revisit = true)),
                "(KV letters)," to mapOf("0" to task("rm", next = "0")),
                "formula$" to mapOf("0" to task(next = "f", revisit = true)),
                "1/2$" to mapOf("0" to task("1/2")),
                "else" to mapOf("0" to task(next = "!f", revisit = true)),
                "\${(...)}\$|\$(...)\$" to mapOf("*" to task("tex-math")),
                "{(...)}" to mapOf("*" to task("text")),
                "a-z" to mapOf("f" to task("tex-math")),
                "letters" to mapOf("*" to task("rm")),
                "-9.,9" to mapOf("*" to task("9,9")),
                "," to mapOf("*" to task(act("insert+p1", "comma enumeration S"))),
                "\\color{(...)}{(...)}1|\\color(...){(...)}2" to mapOf("*" to task("color-output")),
                "\\color{(...)}0" to mapOf("*" to task("color0-output")),
                "\\ce{(...)}" to mapOf("*" to task("ce")),
                "\\,|\\x{}{}|\\x{}|\\x" to mapOf("*" to task("copy")),
                "else2" to mapOf("*" to task("copy")),
            ),
            mapOf(
                "state of aggregation" to Action { _, m, _ ->
                    Nested("state of aggregation subscript", go(m.str, "o"))
                },
                "color-output" to Action { _, m, _ -> Color(m.list[0], go(m.list[1], "pq")) },
            ),
        ),
        "bd" to Machine(
            createTransitions(
                "empty" to mapOf("*" to task()),
                "x$" to mapOf("0" to task(next = "!f", revisit = true)),
                "formula$" to mapOf("0" to task(next = "f", revisit = true)),
                "else" to mapOf("0" to task(next = "!f", revisit = true)),
                "-9.,9 no missing 0" to mapOf("*" to task("9,9")),
                "." to mapOf("*" to task(act("insert", "electron dot"))),
                "a-z" to mapOf("f" to task("tex-math")),
                "x" to mapOf("*" to task(act("insert", "KV x"))),
                "letters" to mapOf("*" to task("rm")),
                "'" to mapOf("*" to task(act("insert", "prime"))),
                "\${(...)}\$|\$(...)\$" to mapOf("*" to task("tex-math")),
                "{(...)}" to mapOf("*" to task("text")),
                "\\color{(...)}{(...)}1|\\color(...){(...)}2" to mapOf("*" to task("color-output")),
                "\\color{(...)}0" to mapOf("*" to task("color0-output")),
                "\\ce{(...)}" to mapOf("*" to task("ce")),
                "\\,|\\x{}{}|\\x{}|\\x" to mapOf("*" to task("copy")),
                "else2" to mapOf("*" to task("copy")),
            ),
            mapOf("color-output" to Action { _, m, _ -> Color(m.list[0], go(m.list[1], "bd")) }),
        ),
        "oxidation" to Machine(
            createTransitions(
                "empty" to mapOf("*" to task()),
                "roman numeral" to mapOf("*" to task("roman-numeral")),
                "\${(...)}\$|\$(...)\$" to mapOf("*" to task("tex-math")),
                "else" to mapOf("*" to task("copy")),
            ),
            mapOf("roman-numeral" to Action { _, m, _ -> Text("roman numeral", m.str.orEmpty()) }),
        ),
        "tex-math" to Machine(
            createTransitions(
                "empty" to mapOf("*" to task("output")),
                "\\ce{(...)}" to mapOf("*" to task("output", "ce")),
                "{...}|\\,|\\x{}{}|\\x{}|\\x" to mapOf("*" to task("o=")),
                "else" to mapOf("*" to task("o=")),
            ),
            mapOf("output" to texMathOutput),
        ),
        "tex-math tight" to Machine(
            createTransitions(
                "empty" to mapOf("*" to task("output")),
                "\\ce{(...)}" to mapOf("*" to task("output", "ce")),
                "{...}|\\,|\\x{}{}|\\x{}|\\x" to mapOf("*" to task("o=")),
                "-|+" to mapOf("*" to task("tight operator")),
                "else" to mapOf("*" to task("o=")),
            ),
            mapOf(
                "tight operator" to Action { b, m, _ -> b.o = b.o.orEmpty() + "{" + m.str + "}"; null },
                "output" to texMathOutput,
            ),
        ),
        "9,9" to Machine(
            createTransitions(
                "empty" to mapOf("*" to task()),
                "," to mapOf("*" to task("comma")),
                "else" to mapOf("*" to task("copy")),
            ),
            mapOf("comma" to Action { _, _, _ -> Plain("commaDecimal") }),
        ),
        //
        // \pu state machines
        //
        "pu" to Machine(
            createTransitions(
                "empty" to mapOf("*" to task("output")),
                "space$" to mapOf("*" to task("output", "space")),
                "{[(|)]}" to mapOf("0|a" to task("copy")),
                "(-)(9)^(-9)" to mapOf("0" to task("number^", next = "a")),
                "(-)(9.,9)(e)(99)" to mapOf("0" to task("enumber", next = "a")),
                "space" to mapOf("0|a" to task()),
                "pm-operator" to mapOf("0|a" to task(act("operator", "\\pm"), next = "0")),
                "operator" to mapOf("0|a" to task("copy", next = "0")),
                "//" to mapOf("d" to task("o=", next = "/")),
                "/" to mapOf("d" to task("o=", next = "/")),
                "{...}|else" to mapOf(
                    "0|d" to task("d=", next = "d"),
                    "a" to task("space", "d=", next = "d"),
                    "/|q" to task("q=", next = "q"),
                ),
            ),
            puActions,
        ),
        "pu-2" to Machine(
            createTransitions(
                "empty" to mapOf("*" to task("output")),
                "*" to mapOf("*" to task("output", "cdot", next = "0")),
                "\\x" to mapOf("*" to task("rm=")),
                "space" to mapOf("*" to task("output", "space", next = "0")),
                "^{(...)}|^(-1)" to mapOf("1" to task("^(-1)")),
                "-9.,9" to mapOf(
                    "0" to task("rm=", next = "0"),
                    "1" to task("^(-1)", next = "0"),
                ),
                "{...}|else" to mapOf("*" to task("rm=", next = "1")),
            ),
            mapOf(
                "cdot" to Action { _, _, _ -> Plain("tight cdot") },
                "^(-1)" to Action { b, m, _ -> b.rm = jsString(b.rm) + "^{" + m.str + "}"; null },
                "space" to Action { _, _, _ -> Plain("pu-space-2") },
                "output" to Action { b, _, _ ->
                    var ret: Any = ArrayList<MhchemItem>()
                    if (b.rm.t) {
                        val mrm = match("{(...)}", b.rm!!)
                        ret = if (mrm != null && mrm.remainder == "") go(mrm.match.str, "pu") else Text("rm", b.rm!!)
                    }
                    b.clear()
                    ret
                },
            ),
        ),
        "pu-9,9" to Machine(
            createTransitions(
                "empty" to mapOf(
                    "0" to task("output-0"),
                    "o" to task("output-o"),
                ),
                "," to mapOf("0" to task("output-0", "comma", next = "o")),
                "." to mapOf("0" to task("output-0", "copy", next = "o")),
                "else" to mapOf("*" to task("text=")),
            ),
            mapOf(
                "comma" to Action { _, _, _ -> Plain("commaDecimal") },
                "output-0" to Action { b, _, _ -> thousands(b, fromEnd = true) },
                "output-o" to Action { b, _, _ -> thousands(b, fromEnd = false) },
            ),
        ),
    )

    // endregion
}
