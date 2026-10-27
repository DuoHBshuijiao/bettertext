/*************************************************************
 *
 *  Kotlin port of KaTeX 0.19.1 contrib/mhchem (mhchem 3.3.0):
 *  the API, the reconstruction of the argument string and texify.
 *  The parser is in MhchemParser.kt.
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

import me.rerere.bettertext.latex.parse.Token

/** What mhchem.js throws as a `[name, message]` array, e.g. `MhchemBugT` or `ExtraCloseMissingOpen`. */
internal class MhchemException(val name: String, message: String) : Exception(message)

/** Converts the argument of `\ce` or `\pu` to TeX. */
internal object Mhchem {
    /** [stateMachine] is `ce` or `pu`. Throws [MhchemException] for invalid input. */
    fun toTex(input: String, stateMachine: String): String =
        Texify.go(MhchemParser.go(input, stateMachine))
}

/**
 * Recreates the argument string from [tokens], as returned by `MacroExpander.consumeArgs`
 * (in reverse order), and converts it to TeX. Spaces that the lexer swallowed after a control
 * word are restored from the token locations.
 */
internal fun chemParse(tokens: List<Token>, stateMachine: String): String {
    val str = StringBuilder()
    var expectedLoc = tokens.lastOrNull()?.loc?.start ?: 0
    for (i in tokens.indices.reversed()) {
        val token = tokens[i]
        val start = token.loc?.start
        if (start != null && start > expectedLoc) {
            // consumeArgs has eaten a space.
            str.append(' ')
            expectedLoc = start
        }
        str.append(token.text)
        expectedLoc += token.text.length
    }
    return Mhchem.toTex(str.toString(), stateMachine)
}

/** Parser output (mhchem's `ParserOutput`): a raw string or a typed item for [Texify]. */
internal sealed interface MhchemItem {
    /** A string copied to the output as is. */
    class Raw(val text: String) : MhchemItem

    /** An item with only a type, e.g. `hyphen` or `commaDecimal`. */
    class Plain(val type: String) : MhchemItem

    /** An item with a string `p1`, e.g. `rm` or `tex-math`. */
    class Text(val type: String, val p1: String) : MhchemItem

    /** `state of aggregation` and `state of aggregation subscript`. */
    class Nested(val type: String, val p1: List<MhchemItem>) : MhchemItem

    /** `frac-ce`, `pu-frac`, `overset`, `underset` and `underbrace`. */
    class Binary(val type: String, val p1: List<MhchemItem>, val p2: List<MhchemItem>) : MhchemItem

    class Frac(val p1: String, val p2: String) : MhchemItem
    class Bond(val kind: String?) : MhchemItem
    class Operator(val kind: String?) : MhchemItem
    class Color(val color1: String?, val color2: List<MhchemItem>) : MhchemItem
    class Color0(val color: String?) : MhchemItem
    class Arrow(val r: String, val rd: List<MhchemItem>, val rq: List<MhchemItem>) : MhchemItem

    class ChemFive(
        val a: List<MhchemItem>,
        val b: List<MhchemItem>,
        val p: List<MhchemItem>,
        val o: List<MhchemItem>,
        val q: List<MhchemItem>,
        val d: List<MhchemItem>,
        val dType: String?,
    ) : MhchemItem
}

/** Takes the parser output and converts it to TeX. */
internal object Texify {
    fun go(input: List<MhchemItem>?, isInner: Boolean = false): String {
        if (input == null) return ""
        var res = ""
        var cee = false
        for (item in input) {
            if (item is MhchemItem.Raw) {
                res += item.text
            } else {
                res += go2(item)
                if (item is MhchemItem.Text && item.type == "1st-level escape") cee = true
            }
        }
        if (!isInner && !cee && res.isNotEmpty()) res = "{$res}"
        return res
    }

    private fun goInner(input: List<MhchemItem>): String = go(input, true)

    private fun bugT(): Nothing = throw MhchemException("MhchemBugT", "mhchem bug T. Please report.")

    private fun go2(buf: MhchemItem): String = when (buf) {
        is MhchemItem.ChemFive -> chemFive(buf)
        is MhchemItem.Text -> when (buf.type) {
            "rm", "roman numeral" -> "\\mathrm{" + buf.p1 + "}"
            "text" -> if (buf.p1.contains('^') || buf.p1.contains('_')) {
                "\\mathrm{" + buf.p1.replaceFirst(" ", "~").replaceFirst("-", "\\text{-}") + "}"
            } else {
                "\\text{" + buf.p1 + "}"
            }

            "tex-math" -> buf.p1 + " "
            "1st-level escape" -> buf.p1 + " " // &, \\\\, \\hline
            "comma enumeration L" -> "{" + buf.p1 + "}\\mkern6mu "
            "comma enumeration M" -> "{" + buf.p1 + "}\\mkern3mu "
            "comma enumeration S" -> "{" + buf.p1 + "}\\mkern1mu "
            else -> bugT()
        }

        is MhchemItem.Nested -> when (buf.type) {
            "state of aggregation" -> "\\mskip2mu " + goInner(buf.p1)
            "state of aggregation subscript" -> "\\mskip1mu " + goInner(buf.p1)
            else -> bugT()
        }

        is MhchemItem.Bond -> getBond(buf.kind).ifEmpty {
            throw MhchemException("MhchemErrorBond", "mhchem Error. Unknown bond type (" + buf.kind + ")")
        }

        is MhchemItem.Frac -> {
            val c = "\\frac{" + buf.p1 + "}{" + buf.p2 + "}"
            "\\mathchoice{\\textstyle$c}{$c}{$c}{$c}"
        }

        is MhchemItem.Binary -> when (buf.type) {
            "pu-frac" -> {
                val d = "\\frac{" + goInner(buf.p1) + "}{" + goInner(buf.p2) + "}"
                "\\mathchoice{\\textstyle$d}{$d}{$d}{$d}"
            }

            "frac-ce" -> "\\frac{" + goInner(buf.p1) + "}{" + goInner(buf.p2) + "}"
            "overset" -> "\\overset{" + goInner(buf.p1) + "}{" + goInner(buf.p2) + "}"
            "underset" -> "\\underset{" + goInner(buf.p1) + "}{" + goInner(buf.p2) + "}"
            "underbrace" -> "\\underbrace{" + goInner(buf.p1) + "}_{" + goInner(buf.p2) + "}"
            else -> bugT()
        }

        is MhchemItem.Color -> "{\\color{" + jsString(buf.color1) + "}{" + goInner(buf.color2) + "}}"
        is MhchemItem.Color0 -> "\\color{" + jsString(buf.color) + "}"
        is MhchemItem.Arrow -> {
            val rd = goInner(buf.rd)
            val rq = goInner(buf.rq)
            var arrow = "\\x" + getArrow(buf.r)
            if (rq.isNotEmpty()) arrow += "[{$rq}]"
            arrow += if (rd.isNotEmpty()) "{$rd}" else "{}"
            arrow
        }

        is MhchemItem.Operator -> getOperator(buf.kind)
        is MhchemItem.Plain -> when (buf.type) {
            "space" -> " "
            "entitySkip" -> "~"
            "pu-space-1" -> "~"
            "pu-space-2" -> "\\mkern3mu "
            "1000 separator" -> "\\mkern2mu "
            "commaDecimal" -> "{,}"
            "hyphen" -> "\\text{-}"
            "addition compound" -> "\\,{\\cdot}\\,"
            "electron dot" -> "\\mkern1mu \\bullet\\mkern1mu "
            "KV x" -> "{\\times}"
            "prime" -> "\\prime "
            "cdot" -> "\\cdot "
            "tight cdot" -> "\\mkern1mu{\\cdot}\\mkern1mu "
            "times" -> "\\times "
            "circa" -> "{\\sim}"
            "^" -> "uparrow"
            "v" -> "downarrow"
            "ellipsis" -> "\\ldots "
            "/" -> "/"
            " / " -> "\\,/\\,"
            else -> bugT()
        }

        is MhchemItem.Raw -> buf.text
    }

    private fun chemFive(buf: MhchemItem.ChemFive): String {
        var res = ""
        var a = goInner(buf.a)
        val b = goInner(buf.b)
        val p = goInner(buf.p)
        var o = goInner(buf.o)
        val q = goInner(buf.q)
        val d = goInner(buf.d)
        if (a.isNotEmpty()) {
            if (a.startsWithSign()) a = "{$a}"
            res += a + "\\,"
        }
        if (b.isNotEmpty() || p.isNotEmpty()) {
            res += "{\\vphantom{X}}"
            res += "^{\\hphantom{$b}}_{\\hphantom{$p}}"
            res += "{\\vphantom{X}}"
            res += "^{\\smash[t]{\\vphantom{2}}\\mathllap{$b}}"
            res += "_{\\vphantom{2}\\mathllap{\\smash[t]{$p}}}"
        }
        if (o.isNotEmpty()) {
            if (o.startsWithSign()) o = "{$o}"
            res += o
        }
        when (buf.dType) {
            "kv" -> {
                if (d.isNotEmpty() || q.isNotEmpty()) res += "{\\vphantom{X}}"
                if (d.isNotEmpty()) res += "^{$d}"
                if (q.isNotEmpty()) res += "_{\\smash[t]{$q}}"
            }

            "oxidation" -> {
                if (d.isNotEmpty()) res += "{\\vphantom{X}}^{$d}"
                if (q.isNotEmpty()) res += "{\\vphantom{X}}_{\\smash[t]{$q}}"
            }

            else -> {
                if (q.isNotEmpty()) res += "{\\vphantom{X}}_{\\smash[t]{$q}}"
                if (d.isNotEmpty()) res += "{\\vphantom{X}}^{$d}"
            }
        }
        return res
    }

    private fun String.startsWithSign() = startsWith("+") || startsWith("-")

    private fun getArrow(a: String): String = when (a) {
        "->", "→", "⟶" -> "rightarrow"
        "<-" -> "leftarrow"
        "<->" -> "leftrightarrow"
        "<-->" -> "rightleftarrows"
        "<=>", "⇌" -> "rightleftharpoons"
        "<=>>" -> "rightequilibrium"
        "<<=>" -> "leftequilibrium"
        else -> bugT()
    }

    private fun getBond(a: String?): String = when (a) {
        "-", "1" -> "{-}"
        "=", "2" -> "{=}"
        "#", "3" -> "{\\equiv}"
        "~" -> "{\\tripledash}"
        "~-" -> "{\\mathrlap{\\raisebox{-.1em}{\$-\$}}\\raisebox{.1em}{\$\\tripledash\$}}"
        "~=", "~--" -> "{\\mathrlap{\\raisebox{-.2em}{\$-\$}}\\mathrlap{\\raisebox{.2em}{\$\\tripledash\$}}-}"
        "-~-" -> "{\\mathrlap{\\raisebox{-.2em}{\$-\$}}\\mathrlap{\\raisebox{.2em}{\$-\$}}\\tripledash}"
        "..." -> "{{\\cdot}{\\cdot}{\\cdot}}"
        "...." -> "{{\\cdot}{\\cdot}{\\cdot}{\\cdot}}"
        "->" -> "{\\rightarrow}"
        "<-" -> "{\\leftarrow}"
        "<" -> "{<}"
        ">" -> "{>}"
        else -> bugT()
    }

    private fun getOperator(a: String?): String = when (a) {
        "+" -> " {}+{} "
        "-" -> " {}-{} "
        "=" -> " {}={} "
        "<" -> " {}<{} "
        ">" -> " {}>{} "
        "<<" -> " {}\\ll{} "
        ">>" -> " {}\\gg{} "
        "\\pm" -> " {}\\pm{} "
        "\\approx", "\$\\approx\$" -> " {}\\approx{} "
        "v", "(v)" -> " \\downarrow{} "
        "^", "(^)" -> " \\uparrow{} "
        else -> bugT()
    }
}

/** JavaScript's string conversion of a possibly undefined value. */
internal fun jsString(s: String?): String = s ?: "undefined"
