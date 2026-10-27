package me.rerere.bettertext.latex.functions

import me.rerere.bettertext.latex.ParseError
import me.rerere.bettertext.latex.layout.Box
import me.rerere.bettertext.latex.layout.CssColor
import me.rerere.bettertext.latex.layout.HAlign
import me.rerere.bettertext.latex.layout.Options
import me.rerere.bettertext.latex.layout.RuleBox
import me.rerere.bettertext.latex.layout.Style
import me.rerere.bettertext.latex.layout.VListChild
import me.rerere.bettertext.latex.layout.VListPosition
import me.rerere.bettertext.latex.layout.buildExpression
import me.rerere.bettertext.latex.layout.buildGroup
import me.rerere.bettertext.latex.layout.calculateSizeAbs
import me.rerere.bettertext.latex.layout.makeFragment
import me.rerere.bettertext.latex.layout.makeLineSpan
import me.rerere.bettertext.latex.layout.makeSpan
import me.rerere.bettertext.latex.layout.makeSymbol
import me.rerere.bettertext.latex.layout.makeVList
import me.rerere.bettertext.latex.parse.AlignSpec
import me.rerere.bettertext.latex.parse.ArrayNode
import me.rerere.bettertext.latex.parse.LeftRightNode
import me.rerere.bettertext.latex.parse.MacroDefinition
import me.rerere.bettertext.latex.parse.Measurement
import me.rerere.bettertext.latex.parse.Mode
import me.rerere.bettertext.latex.parse.OrdGroupNode
import me.rerere.bettertext.latex.parse.ParseNode
import me.rerere.bettertext.latex.parse.Parser
import me.rerere.bettertext.latex.parse.SizeNode
import me.rerere.bettertext.latex.parse.StylingNode
import me.rerere.bettertext.latex.parse.SymbolParseNode
import me.rerere.bettertext.latex.parse.Token

/* Ported from KaTeX's environments/array.ts. The {CD} environment is not ported. */

/**
 * Return a list with one entry per hline (`\hline` / `\hdashline`) at the
 * current position; each entry tells if the line is dashed.
 */
private fun getHLines(parser: Parser): List<Boolean> {
    val hlineInfo = ArrayList<Boolean>()
    parser.consumeSpaces()
    var nxt = parser.fetch().text
    if (nxt == "\\relax") { // \relax is an artifact of the \cr macro below
        parser.consume()
        parser.consumeSpaces()
        nxt = parser.fetch().text
    }
    while (nxt == "\\hline" || nxt == "\\hdashline") {
        parser.consume()
        hlineInfo.add(nxt == "\\hdashline")
        parser.consumeSpaces()
        nxt = parser.fetch().text
    }
    return hlineInfo
}

private fun validateAmsEnvironmentContext(context: EnvContext) {
    if (!context.parser.settings.displayMode) {
        throw ParseError("{${context.envName}} can be used only in display mode.")
    }
}

private val gatherEnvironments = setOf("gather", "gather*")

/**
 * autoTag (an argument to parseArray) can be one of three values:
 * * null: Regular (not-top-level) array; no tags on each row
 * * true: Automatic equation numbering, overridable by \tag
 * * false: Tags allowed on each row, but no automatic numbering
 * This function *doesn't* work with the "split" environment name.
 */
private fun getAutoTag(name: String): Boolean? =
    if (!name.contains("ed")) !name.contains("*") else null

private class ArrayParams(
    val hskipBeforeAndAfter: Boolean? = null,
    val addJot: Boolean? = null,
    val cols: List<AlignSpec>? = null,
    val arraystretch: Float? = null,
    val colSeparationType: String? = null,
    val autoTag: Boolean? = null,
    val singleRow: Boolean = false,
    val emptySingleRow: Boolean = false,
    val maxNumCols: Int? = null,
    val leqno: Boolean? = null,
)

/**
 * Parse the body of the environment, with rows delimited by \\ and
 * columns delimited by &, and create a nested list in row-major order
 * with one group per cell. If given an optional argument style
 * ("text", "display", etc.), then each cell is cast into that style.
 */
private fun parseArray(parser: Parser, params: ArrayParams, style: String?): ArrayNode {
    val gullet = parser.gullet
    gullet.beginGroup()
    if (!params.singleRow) {
        // \cr is equivalent to \\ without the optional size argument (see below)
        gullet.macros.set("\\cr", MacroDefinition.Text("\\\\\\relax"))
    }

    // Get current arraystretch if it's not set by the environment
    var arraystretch = params.arraystretch
    if (arraystretch == null || arraystretch == 0f) {
        val stretch = gullet.expandMacroAsText("\\arraystretch")
        if (stretch == null) {
            // Default \arraystretch from lttab.dtx
            arraystretch = 1f
        } else {
            arraystretch = parseFloatPrefix(stretch)
            if (arraystretch == null || arraystretch == 0f || arraystretch < 0f) {
                throw ParseError("Invalid \\arraystretch: $stretch")
            }
        }
    }

    // Start group for first cell
    gullet.beginGroup()

    var row = ArrayList<ParseNode>()
    val body = arrayListOf<MutableList<ParseNode>>(row)
    val rowGaps = ArrayList<Measurement?>()
    val hLinesBeforeRow = ArrayList<List<Boolean>>()
    val autoTag = params.autoTag
    val tags: ArrayList<Any>? = if (autoTag != null) ArrayList() else null

    // amsmath uses \global\@eqnswtrue and \global\@eqnswfalse to represent
    // whether this row should have an equation number. Simulate this with
    // a \@eqnsw macro set to 1 or 0.
    fun beginRow() {
        if (autoTag == true) gullet.macros.set("\\@eqnsw", MacroDefinition.Text("1"), true)
    }

    fun endRow() {
        if (tags != null) {
            if (gullet.macros.get("\\df@tag") != null) {
                tags.add(parser.subparse(listOf(Token("\\df@tag"))))
                gullet.macros.set("\\df@tag", null, true)
            } else {
                val eqnsw = gullet.macros.get("\\@eqnsw")
                tags.add(autoTag == true && eqnsw is MacroDefinition.Text && eqnsw.body == "1")
            }
        }
    }
    beginRow()

    // Test for \hline at the top of the array.
    hLinesBeforeRow.add(getHLines(parser))

    while (true) {
        // Parse each cell in its own group (namespace)
        val cellBody = parser.parseExpression(false, if (params.singleRow) "\\end" else "\\\\")
        gullet.endGroup()
        gullet.beginGroup()
        var cell: ParseNode = OrdGroupNode(parser.mode, cellBody)
        if (style != null) {
            cell = StylingNode(parser.mode, style, listOf(cell), resetFont = true)
        }
        row.add(cell)
        val next = parser.fetch().text
        if (next == "&") {
            val maxNumCols = params.maxNumCols
            if (maxNumCols != null && maxNumCols != 0 && row.size == maxNumCols) {
                if (params.singleRow || params.colSeparationType != null) {
                    // {equation} or {split}
                    throw ParseError("Too many tab characters: &", parser.fetch())
                }
                // {array} environment: KaTeX reports "Too few columns specified in
                // the {array} column argument." via handleStrict (a warning by default).
            }
            parser.consume()
        } else if (next == "\\end") {
            endRow()
            // Arrays terminate newlines with `\crcr` which consumes a `\cr` if
            // the last line is empty. However, AMS environments keep the
            // empty row if it's the only one, has a manual tag, or is an
            // automatically numbered row explicitly ended with `\\`.
            // NOTE: Currently, `cell` is the last item added into `row`.
            val lastTag = tags?.lastOrNull()
            val inner = (cell as? StylingNode)?.body
            if (row.size == 1 && cell is StylingNode &&
                inner!!.size == 1 && inner[0] is OrdGroupNode &&
                (inner[0] as OrdGroupNode).body.isEmpty() &&
                (body.size > 1 || !params.emptySingleRow) &&
                lastTag !is List<*> &&
                !(autoTag == true && lastTag == true)
            ) {
                body.removeAt(body.size - 1)
            }
            if (hLinesBeforeRow.size < body.size + 1) {
                hLinesBeforeRow.add(emptyList())
            }
            break
        } else if (next == "\\\\") {
            parser.consume()
            var size: Measurement? = null
            // \def\Let@{\let\\\math@cr}
            // \def\math@cr{...\math@cr@}
            // \def\math@cr@{\new@ifnextchar[\math@cr@@{\math@cr@@[\z@]}}
            // \def\math@cr@@[#1]{...\math@cr@@@...}
            // \def\math@cr@@@{\cr}
            if (gullet.future().text != " ") {
                // KaTeX: parser.parseSizeGroup(true)
                size = (parser.parseArguments("\\\\", 0, 1, listOf(ArgType.SIZE)).second[0] as? SizeNode)?.value
            }
            rowGaps.add(size)
            endRow()

            // check for \hline(s) following the row separator
            hLinesBeforeRow.add(getHLines(parser))

            row = ArrayList()
            body.add(row)
            beginRow()
        } else {
            throw ParseError("Expected & or \\\\ or \\cr or \\end", parser.fetch())
        }
    }

    // End cell group
    gullet.endGroup()
    // End array group defining \cr
    gullet.endGroup()

    return ArrayNode(
        mode = parser.mode,
        colSeparationType = params.colSeparationType,
        hskipBeforeAndAfter = params.hskipBeforeAndAfter,
        addJot = params.addJot,
        cols = params.cols,
        arraystretch = arraystretch,
        body = body,
        rowGaps = rowGaps,
        hLinesBeforeRow = hLinesBeforeRow,
        tags = tags,
        leqno = params.leqno,
    )
}

/** JavaScript's `parseFloat`: parses the longest numeric prefix, or null. */
private fun parseFloatPrefix(s: String): Float? {
    val m = Regex("^\\s*[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?").find(s) ?: return null
    return m.value.trim().toFloatOrNull()
}

/**
 * Decides on a style for cells in an array according to whether the given
 * environment name starts with the letter 'd'.
 */
private fun dCellStyle(envName: String): String = if (envName.startsWith("d")) "display" else "text"

private class OutRow(val cells: Array<Box?>, var height: Float = 0f, var depth: Float = 0f, var pos: Float = 0f)

private fun colAlign(align: String?): HAlign = when (align) {
    "l" -> HAlign.LEFT
    "r" -> HAlign.RIGHT
    else -> HAlign.CENTER
}

/** An `arraycolsep` span of the given width (absolute em). */
private fun colSepSpan(width: Float): Box = makeSpan(listOf("arraycolsep")).also { it.width = width }

/**
 * The array builder. KaTeX relies on CSS table layout (each column is an
 * inline vlist whose `text-align` comes from `.col-align-*`); here each column
 * is a vlist aligned with [HAlign], so it is as wide as its widest cell.
 */
private fun buildArray(group: ArrayNode, options: Options): Box {
    val nr = group.body.size
    val hLinesBeforeRow = group.hLinesBeforeRow
    var nc = 0
    val body = ArrayList<OutRow>(nr)
    // (pos, isDashed)
    val hlines = ArrayList<Pair<Float, Boolean>>()
    val ruleThickness = maxOf(
        // From LaTeX \showthe\arrayrulewidth. Equals 0.04 em.
        options.fm.arrayRuleWidth,
        options.em(options.minRuleThickness), // User override.
    )

    // Horizontal spacing. All lengths below are absolute em.
    val pt = options.em(1f / options.fontMetrics().ptPerEm)
    var arraycolsep = 5 * pt // default value, i.e. \arraycolsep in article.cls
    if (group.colSeparationType == "small") {
        // We're in a {smallmatrix}. Default column space is \thickspace,
        // i.e. 5/18em = 0.2778em, per amsmath.dtx for {smallmatrix}.
        // But that needs adjustment because LaTeX applies \scriptstyle to the
        // entire array, including the colspace, but this function applies
        // \scriptstyle only inside each element.
        val localMultiplier = options.havingStyle(Style.SCRIPT).sizeMultiplier
        arraycolsep = 0.2778f * localMultiplier
    }

    // Vertical spacing ({CD} would use 3ex instead; it is not ported).
    val baselineskip = 12 * pt // see size10.clo
    // Default \jot from ltmath.dtx
    val jot = 3 * pt
    val arrayskip = group.arraystretch * baselineskip
    val arstrutHeight = 0.7f * arrayskip // \strutbox in ltfsstrc.dtx and
    val arstrutDepth = 0.3f * arrayskip // \@arstrutbox in lttab.dtx

    var totalHeight = 0f

    // Set a position for \hline(s) at the top of the array, if any.
    fun setHLinePos(hlinesInGap: List<Boolean>) {
        for (i in hlinesInGap.indices) {
            if (i > 0) totalHeight += options.em(0.25f)
            hlines.add(totalHeight to hlinesInGap[i])
        }
    }
    setHLinePos(hLinesBeforeRow[0])

    // Equation tags, built up front so each row is tall enough for its tag
    // (KaTeX overwrites the tag's size with the row's, letting tall tags overflow).
    val tags = group.tags
    val hasTags = tags != null && tags.any { it != false }
    val rowTags = arrayOfNulls<Box>(nr)
    // KaTeX numbers rows with a CSS counter (`.eqn-num::before`), reset per
    // display; here the counter is local to this environment.
    var eqnNo = 0

    for (r in 0 until nr) {
        val inrow = group.body[r]
        var height = arstrutHeight // \@array adds an \@arstrut
        var depth = arstrutDepth // to each tow (via the template)

        if (nc < inrow.size) nc = inrow.size

        val outrow = OutRow(arrayOfNulls(inrow.size))
        for (c in inrow.indices) {
            val elt = buildGroup(inrow[c], options)
            if (depth < elt.depth) depth = elt.depth
            if (height < elt.height) height = elt.height
            outrow.cells[c] = elt
        }

        if (hasTags) {
            val tag = tags!!.getOrNull(r) ?: false
            val tagSpan = if (tag == true) { // automatic numbering
                eqnNo++
                val text = "($eqnNo)"
                makeSpan(
                    listOf("eqn-num"),
                    text.map { makeSymbol(it.toString(), "Main-Regular", Mode.TEXT, options) },
                    options,
                )
            } else if (tag == false) {
                // \nonumber/\notag or starred environment
                makeSpan(emptyList(), emptyList(), options)
            } else { // manual \tag
                @Suppress("UNCHECKED_CAST")
                makeSpan(emptyList(), buildExpression(tag as List<ParseNode>, options, true), options)
            }
            if (depth < tagSpan.depth) depth = tagSpan.depth
            if (height < tagSpan.height) height = tagSpan.height
            rowTags[r] = tagSpan
        }

        val rowGap = group.rowGaps.getOrNull(r)
        var gap = 0f
        if (rowGap != null) {
            gap = calculateSizeAbs(rowGap, options)
            if (gap > 0) { // \@argarraycr
                gap += arstrutDepth
                if (depth < gap) depth = gap // \@xargarraycr
                gap = 0f
            }
        }
        // In AMS multiline environments such as aligned and gathered, rows
        // correspond to lines that have additional \jot added between lines
        // via \openup.
        // We simulate this by adding \jot depth to each row except the last.
        if (group.addJot == true && r < nr - 1) depth += jot

        outrow.height = height
        outrow.depth = depth
        totalHeight += height
        outrow.pos = totalHeight
        totalHeight += depth + gap // \@yargarraycr
        body.add(outrow)

        // Set a position for \hline(s), if any.
        setHLinePos(hLinesBeforeRow.getOrElse(r + 1) { emptyList() })
    }

    val offset = totalHeight / 2 + options.fm.axisHeight
    val colDescriptions = group.cols ?: emptyList()
    val cols = ArrayList<Box>()
    val tagSpans = ArrayList<VListChild>()

    if (hasTags) {
        // An environment with manual tags and/or automatic equation numbers.
        // Each tag keeps its real size and sits on its row's baseline.
        for (r in 0 until nr) {
            tagSpans.add(VListChild.Elem(rowTags[r]!!, shift = body[r].pos - offset))
        }
    }

    var c = 0
    var colDescrNum = 0
    // Continue while either there are more columns or more column
    // descriptions, so trailing separators don't get lost.
    while (c < nc || colDescrNum < colDescriptions.size) {
        var colDescr = colDescriptions.getOrNull(colDescrNum)

        var firstSeparator = true
        while (colDescr is AlignSpec.Separator) {
            // If there is more than one separator in a row, add a space
            // between them.
            if (!firstSeparator) cols.add(colSepSpan(options.fm.doubleRuleSep))

            if (colDescr.separator == "|" || colDescr.separator == ":") {
                // A border-right of the full array height, with margin
                // `0 -ruleThickness/2` so it takes no horizontal space.
                val separator = RuleBox(listOf("vertical-separator"), dashed = colDescr.separator == ":")
                options.getColor()?.let { separator.color = CssColor.parse(it) }
                separator.width = ruleThickness
                separator.marginLeft = -ruleThickness / 2
                separator.marginRight = -ruleThickness / 2
                // KaTeX: height totalHeight, vertical-align -(totalHeight - offset).
                separator.height = offset
                separator.depth = totalHeight - offset
                cols.add(separator)
            } else {
                throw ParseError("Invalid separator type: ${colDescr.separator}")
            }

            colDescrNum++
            colDescr = colDescriptions.getOrNull(colDescrNum)
            firstSeparator = false
        }

        if (c >= nc) {
            c++
            colDescrNum++
            continue
        }
        val align = colDescr as? AlignSpec.Align

        if (c > 0 || group.hskipBeforeAndAfter == true) {
            val sepwidth = align?.pregap?.let { options.em(it) } ?: arraycolsep
            if (sepwidth != 0f) cols.add(colSepSpan(sepwidth))
        }

        val colElems = ArrayList<VListChild>()
        for (r in 0 until nr) {
            val row = body[r]
            val elem = row.cells.getOrNull(c) ?: continue
            val shift = row.pos - offset
            elem.depth = row.depth
            elem.height = row.height
            colElems.add(VListChild.Elem(elem, shift = shift))
        }

        val colVList = makeVList(VListPosition.IndividualShift, colElems, colAlign(align?.align))
        cols.add(makeSpan(listOf("col-align-" + (align?.align ?: "c")), listOf(colVList)))

        if (c < nc - 1 || group.hskipBeforeAndAfter == true) {
            val sepwidth = align?.postgap?.let { options.em(it) } ?: arraycolsep
            if (sepwidth != 0f) cols.add(colSepSpan(sepwidth))
        }
        c++
        colDescrNum++
    }

    var tableBody: Box = makeSpan(listOf("mtable"), cols)

    // Add \hline(s), if any.
    if (hlines.isNotEmpty()) {
        val vListElems = arrayListOf<VListChild>(VListChild.Elem(tableBody, shift = 0f))
        while (hlines.isNotEmpty()) {
            val (pos, isDashed) = hlines.removeAt(hlines.size - 1)
            val lineShift = pos - offset
            val line = if (isDashed) {
                RuleBox(listOf("katex-hdashline"), dashed = true).also { dashes ->
                    val proto = makeLineSpan("katex-hdashline", options, ruleThickness)
                    dashes.color = proto.color
                    dashes.height = proto.height
                    dashes.fillWidth = true
                    dashes.maxFontSize = proto.maxFontSize
                }
            } else {
                makeLineSpan("katex-hline", options, ruleThickness)
            }
            vListElems.add(VListChild.Elem(line, shift = lineShift))
        }
        tableBody = makeVList(VListPosition.IndividualShift, vListElems)
    }

    return if (tagSpans.isEmpty()) {
        makeSpan(listOf("mord"), listOf(tableBody), options)
    } else {
        val eqnNumCol = makeVList(VListPosition.IndividualShift, tagSpans)
        val tagCol = makeSpan(listOf("katex-tag"), listOf(eqnNumCol), options)
        makeFragment(listOf(tableBody, tagCol))
    }
}

/** Parses an {array}/{subarray} column specification into symbol texts. */
private fun colAlignSymbols(arg: ParseNode): List<SymbolParseNode> {
    // Since no types are specified, the two possibilities are
    // - The argument is wrapped in {} or [], in which case Parser's
    //   parseGroup() returns an "ordgroup" wrapping some symbol node.
    // - The argument is a bare symbol node.
    val colalign: List<ParseNode> = if (arg is SymbolParseNode) listOf(arg) else arg.assertType<OrdGroupNode>().body
    return colalign.map {
        it as? SymbolParseNode
            ?: throw ParseError("Expected node of symbol group type, but got node of type ${it::class.simpleName}")
    }
}

/** Convenience function for align, align*, aligned, alignat, alignat*, alignedat. */
private fun alignedHandler(context: EnvContext, args: List<ParseNode>): ParseNode {
    if (!context.envName.contains("ed")) validateAmsEnvironmentContext(context)
    val cols = ArrayList<AlignSpec>()
    val isSplit = context.envName == "split"
    val res = parseArray(
        context.parser,
        ArrayParams(
            cols = cols,
            addJot = true,
            autoTag = if (isSplit) null else getAutoTag(context.envName),
            emptySingleRow = true,
            colSeparationType = if (context.envName.contains("at")) "alignat" else "align",
            maxNumCols = if (isSplit) 2 else null,
            leqno = context.parser.settings.leqno,
        ),
        "display",
    )

    // Determining number of columns.
    // 1. If the first argument is given, we use it as a number of columns,
    //    and makes sure that each row doesn't exceed that number.
    // 2. Otherwise, just count number of columns = maximum number
    //    of cells in each row ("aligned" mode -- isAligned will be true).
    //
    // At the same time, prepend empty group {} at beginning of every second
    // cell in each row (starting with second cell) so that operators become
    // binary. This behavior is implemented in amsmath's \start@aligned.
    var numMaths = 0
    var numCols = 0
    val emptyGroup = OrdGroupNode(context.mode, emptyList())
    val arg0 = args.getOrNull(0)
    if (arg0 is OrdGroupNode) {
        val message = "Number of columns should be a positive integer"
        val numColumns = envCharacterGroup(arg0, message)
        if (!Regex("^[0-9]+$").matches(numColumns) || (numColumns.toIntOrNull() ?: 0) < 1) {
            throw ParseError(message)
        }
        numMaths = numColumns.toInt()
        numCols = numMaths * 2
    }
    val isAligned = numCols == 0
    for (row in res.body) {
        row as MutableList<ParseNode>
        var i = 1
        while (i < row.size) {
            // Modify ordgroup node within styling node
            val styling = row[i].assertType<StylingNode>()
            val ordgroup = styling.body[0].assertType<OrdGroupNode>()
            val newOrd = OrdGroupNode(ordgroup.mode, listOf<ParseNode>(emptyGroup) + ordgroup.body, ordgroup.semisimple)
            newOrd.loc = ordgroup.loc
            row[i] = StylingNode(styling.mode, styling.style, listOf(newOrd), styling.resetFont).also {
                it.loc = styling.loc
            }
            i += 2
        }
        if (!isAligned) { // Case 1
            val curMaths = row.size / 2f
            if (numMaths < curMaths) {
                throw ParseError("Too many math in a row: expected $numMaths, but got ${formatCount(curMaths)}")
            }
        } else if (numCols < row.size) { // Case 2
            numCols = row.size
        }
    }

    // Adjusting alignment.
    // In aligned mode, we add one \qquad between columns;
    // otherwise we add nothing.
    for (i in 0 until numCols) {
        var align = "r"
        var pregap = 0f
        if (i % 2 == 1) {
            align = "l"
        } else if (i > 0 && isAligned) { // "aligned" mode.
            pregap = 1f // add one \quad
        }
        cols.add(AlignSpec.Align(align, pregap = pregap, postgap = 0f))
    }
    res.colSeparationType = if (isAligned) "align" else "alignat"
    return res
}

private fun formatCount(x: Float): String = if (x == x.toInt().toFloat()) x.toInt().toString() else x.toString()

internal fun registerArray() {
    // Arrays are part of LaTeX, defined in lttab.dtx so its documentation
    // is part of the source2e.pdf file of LaTeX2e source documentation.
    // {darray} is an {array} environment where cells are set in \displaystyle,
    // as defined in nccmath.sty.
    defineEnvironment(names = listOf("array", "darray"), numArgs = 1) { context, args, _ ->
        val cols: List<AlignSpec> = colAlignSymbols(args[0]).map { node ->
            when (val ca = node.text) {
                "l", "c", "r" -> AlignSpec.Align(ca)
                "|" -> AlignSpec.Separator("|")
                ":" -> AlignSpec.Separator(":")
                else -> throw ParseError("Unknown column alignment: $ca")
            }
        }
        parseArray(
            context.parser,
            ArrayParams(
                cols = cols,
                hskipBeforeAndAfter = true, // \@preamble in lttab.dtx
                maxNumCols = cols.size,
            ),
            dCellStyle(context.envName),
        )
    }

    // The matrix environments of amsmath builds on the array environment
    // of LaTeX, which is discussed above.
    // The mathtools package adds starred versions of the same environments.
    // These have an optional argument to choose left|center|right justification.
    val matrixDelimiters = mapOf<String, Pair<String, String>?>(
        "matrix" to null,
        "pmatrix" to ("(" to ")"),
        "bmatrix" to ("[" to "]"),
        "Bmatrix" to ("\\{" to "\\}"),
        "vmatrix" to ("|" to "|"),
        "Vmatrix" to ("\\Vert" to "\\Vert"),
    )
    defineEnvironment(
        names = listOf(
            "matrix", "pmatrix", "bmatrix", "Bmatrix", "vmatrix", "Vmatrix",
            "matrix*", "pmatrix*", "bmatrix*", "Bmatrix*", "vmatrix*", "Vmatrix*",
        ),
    ) { context, _, _ ->
        val delimiters = matrixDelimiters[context.envName.replace("*", "")]
        // \hskip -\arraycolsep in amsmath
        var colAlign = "c"
        var cols = listOf<AlignSpec>(AlignSpec.Align(colAlign))
        if (context.envName.endsWith("*")) {
            // It's one of the mathtools starred functions.
            // Parse the optional alignment argument.
            val parser = context.parser
            parser.consumeSpaces()
            if (parser.fetch().text == "[") {
                parser.consume()
                parser.consumeSpaces()
                colAlign = parser.fetch().text
                if (colAlign !in setOf("l", "c", "r")) {
                    throw ParseError("Expected l or c or r", parser.fetch())
                }
                parser.consume()
                parser.consumeSpaces()
                parser.expect("]")
                parser.consume()
                cols = listOf(AlignSpec.Align(colAlign))
            }
        }
        val res = parseArray(
            context.parser,
            ArrayParams(hskipBeforeAndAfter = false, cols = cols),
            dCellStyle(context.envName),
        )
        // Populate cols with the correct number of column alignment specs.
        val numCols = maxOf(0, res.body.maxOfOrNull { it.size } ?: 0)
        res.cols = List(numCols) { AlignSpec.Align(colAlign) }
        if (delimiters != null) {
            // \right uninfluenced by \color in array
            LeftRightNode(context.mode, listOf(res), delimiters.first, delimiters.second, rightColor = null)
        } else {
            res
        }
    }

    defineEnvironment(names = listOf("smallmatrix")) { context, _, _ ->
        val res = parseArray(context.parser, ArrayParams(arraystretch = 0.5f), "script")
        res.colSeparationType = "small"
        res
    }

    defineEnvironment(names = listOf("subarray"), numArgs = 1) { context, args, _ ->
        // Parsing of {subarray} is similar to {array}
        val cols: List<AlignSpec> = colAlignSymbols(args[0]).map { node ->
            val ca = node.text
            // {subarray} only recognizes "l" & "c"
            if (ca == "l" || ca == "c") AlignSpec.Align(ca) else throw ParseError("Unknown column alignment: $ca")
        }
        if (cols.size > 1) throw ParseError("{subarray} can contain only one column")
        val res = parseArray(
            context.parser,
            ArrayParams(cols = cols, hskipBeforeAndAfter = false, arraystretch = 0.5f),
            "script",
        )
        if (res.body.isNotEmpty() && res.body[0].size > 1) {
            throw ParseError("{subarray} can contain only one column")
        }
        res
    }

    // A cases environment (in amsmath.sty) is almost equivalent to
    // \def\arraystretch{1.2}%
    // \left\{\begin{array}{@{}l@{\quad}l@{}} … \end{array}\right.
    // {dcases} is a {cases} environment where cells are set in \displaystyle,
    // as defined in mathtools.sty.
    // {rcases} is another mathtools environment. It's brace is on the right side.
    defineEnvironment(names = listOf("cases", "dcases", "rcases", "drcases")) { context, _, _ ->
        val res = parseArray(
            context.parser,
            ArrayParams(
                arraystretch = 1.2f,
                cols = listOf(
                    // TODO(kevinb) get the current style.
                    // For now we use the metrics for TEXT style which is what we were
                    // doing before. Before attempting to get the current style we
                    // should look at TeX's behavior especially for \over and matrices.
                    AlignSpec.Align("l", pregap = 0f, postgap = 1.0f /* 1em quad */),
                    AlignSpec.Align("l", pregap = 0f, postgap = 0f),
                ),
            ),
            dCellStyle(context.envName),
        )
        val r = context.envName.contains("r")
        LeftRightNode(
            context.mode,
            listOf(res),
            left = if (r) "." else "\\{",
            right = if (r) "\\}" else ".",
            rightColor = null,
        )
    }

    // In the align environment, one uses ampersands, &, to specify number of
    // columns in each row, and to locate spacing between each column.
    // align gets automatic numbering. align* and aligned do not.
    // The alignedat environment can be used in math mode.
    // Note that we assume \nomallineskiplimit to be zero,
    // so that \strut@ is the same as \strut.
    defineEnvironment(names = listOf("align", "align*", "aligned", "split")) { context, args, _ ->
        alignedHandler(context, args)
    }

    // A gathered environment is like an array environment with one centered
    // column, but where rows are considered lines so get \jot line spacing
    // and contents are set in \displaystyle.
    defineEnvironment(names = listOf("gathered", "gather", "gather*")) { context, _, _ ->
        if (context.envName in gatherEnvironments) validateAmsEnvironmentContext(context)
        parseArray(
            context.parser,
            ArrayParams(
                cols = listOf(AlignSpec.Align("c")),
                addJot = true,
                colSeparationType = "gather",
                autoTag = getAutoTag(context.envName),
                emptySingleRow = true,
                leqno = context.parser.settings.leqno,
            ),
            "display",
        )
    }

    // alignat environment is like an align environment, but one must explicitly
    // specify maximum number of columns in each row, and can adjust spacing between
    // each columns.
    defineEnvironment(names = listOf("alignat", "alignat*", "alignedat"), numArgs = 1) { context, args, _ ->
        alignedHandler(context, args)
    }

    defineEnvironment(names = listOf("equation", "equation*")) { context, _, _ ->
        validateAmsEnvironmentContext(context)
        parseArray(
            context.parser,
            ArrayParams(
                autoTag = getAutoTag(context.envName),
                emptySingleRow = true,
                singleRow = true,
                maxNumCols = 1,
                leqno = context.parser.settings.leqno,
            ),
            "display",
        )
    }

    defineHtmlBuilder<ArrayNode> { group, options -> buildArray(group, options) }

    // \nonumber and \notag are defined in the generated macro table.

    // Catch \hline outside array environment
    defineFunction(
        names = listOf("\\hline", "\\hdashline"),
        numArgs = 0,
        allowedInText = true,
        allowedInMath = true,
    ) { ctx, _, _ ->
        throw ParseError("${ctx.funcName} valid only within array environment")
    }
}
