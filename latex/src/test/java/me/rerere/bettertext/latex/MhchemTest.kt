package me.rerere.bettertext.latex

import me.rerere.bettertext.latex.contrib.Mhchem
import me.rerere.bettertext.latex.contrib.MhchemException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** `\ce` and `\pu`, checked against the TeX that KaTeX's contrib/mhchem produces (see tools/mhchem-fixtures.js). */
class MhchemTest {
    private class Case(val machine: String, val input: String, val tex: String?, val error: String?)

    private val cases: List<Case> by lazy {
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream("mhchem/fixtures.tsv")) {
            "mhchem/fixtures.tsv not found"
        }
        stream.bufferedReader().readLines()
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { line ->
                val (machine, input, expected) = line.split('\t')
                if (expected.startsWith("!")) {
                    Case(machine, input, null, expected.substring(1))
                } else {
                    Case(machine, input, expected, null)
                }
            }
    }

    @Test
    fun `output matches KaTeX mhchem`() {
        val failures = cases.mapNotNull { case ->
            val actual = try {
                Mhchem.toTex(case.input, case.machine)
            } catch (e: MhchemException) {
                "!" + e.name
            }
            val expected = case.tex ?: ("!" + case.error)
            if (actual == expected) null else "${case.machine}{${case.input}}\n  expected $expected\n  actual   $actual"
        }
        if (failures.isNotEmpty()) fail("${failures.size}/${cases.size} cases differ:\n" + failures.joinToString("\n"))
    }

    @Test
    fun `formulas lay out without errors`() {
        for (case in cases.filter { it.tex != null }) {
            val latex = "\\${case.machine}{${case.input}}"
            val layout = LatexLayout.create(latex)
            assertNull("$latex: ${layout.error?.message}", layout.error)
            assertTrue("$latex has zero width", layout.width > 0f)
        }
    }

    @Test
    fun `ce expands to the same layout as its TeX`() {
        val tex = Mhchem.toTex("2H2 + O2 -> 2H2O", "ce")
        val viaMacro = LatexLayout.create("\\ce{2H2 + O2 -> 2H2O}")
        val direct = LatexLayout.create(tex)
        assertEquals(direct.width, viaMacro.width, 1e-4f)
        assertEquals(direct.height, viaMacro.height, 1e-4f)
        assertEquals(direct.depth, viaMacro.depth, 1e-4f)
    }

    @Test
    fun `spaces in the argument are kept`() {
        // The argument is rebuilt from its tokens, which don't include spaces; mhchem needs them
        // ("A - B" is a minus, "A-B" a bond).
        for (input in listOf("A + B", "A - B", "x Na(NH4)HPO4 ->[\\Delta] (NaPO3)_x")) {
            val viaMacro = LatexLayout.create("\\ce{$input}")
            val direct = LatexLayout.create(Mhchem.toTex(input, "ce"))
            assertEquals(input, direct.width, viaMacro.width, 1e-4f)
        }
    }

    @Test
    fun `invalid input is a parse error`() {
        val layout = LatexLayout.create("\\ce{A\\bond{x}B}")
        assertNotNull(layout.error)
    }

    @Test
    fun `tripledash is defined`() {
        val layout = LatexLayout.create("A\\tripledash B")
        assertNull(layout.error?.message, layout.error)
    }
}
