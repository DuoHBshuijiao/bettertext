package me.rerere.bettertext.gallery

import me.rerere.bettertext.latex.LatexLayout
import me.rerere.bettertext.latex.LatexSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class GallerySamplesTest {
    @Test
    fun samplesParse() {
        val failures = sections.filterNot { it.expectErrors }.flatMap { section ->
            section.samples.mapNotNull { sample ->
                LatexLayout.create(sample.latex, sample.settings).error?.let { "${section.title}: ${sample.latex} -> ${it.message}" }
            }
        }
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun errorSamplesFail() {
        // The first sample is an undefined command, which KaTeX renders in red without failing.
        for (sample in sections.single { it.expectErrors }.samples.drop(1)) {
            assertNotNull(sample.latex, LatexLayout.create(sample.latex, sample.settings).error)
        }
    }

    @Test
    fun demoFormulasParse() {
        val formulas = wrapSamples.map { it.second } +
            mixedParagraph.filterIndexed { i, _ -> i % 2 == 1 } +
            """\LaTeX\ \text{playground}""" + """a + b = c \allowbreak + d"""
        val failures = formulas.mapNotNull { latex -> LatexLayout.create(latex).error?.let { "$latex -> ${it.message}" } }
        assertEquals(emptyList<String>(), failures)
        assertEquals(null, LatexLayout.create(TAGGED_DISPLAY, LatexSettings(displayMode = true)).error)
    }
}
