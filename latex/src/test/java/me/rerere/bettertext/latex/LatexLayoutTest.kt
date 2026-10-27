package me.rerere.bettertext.latex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LatexLayoutTest {
    private fun layout(latex: String, display: Boolean = false) =
        LatexLayout.create(latex, LatexSettings(displayMode = display))

    @Test
    fun splitsAfterTopLevelOperators() {
        val l = layout("a^2 + b^2 = c^2")
        assertNull(l.error)
        // "a^2 +", "b^2 =", "c^2"
        assertEquals(3, l.chunkCount)
        assertTrue(l.width > 0f)
    }

    @Test
    fun doesNotSplitInsideGroups() {
        val l = layout("{a + b} = c")
        assertNull(l.error)
        assertEquals(2, l.chunkCount)
    }

    @Test
    fun macrosExpand() {
        val l = layout("x \\neq y")
        assertNull(l.error?.message, l.error)
        assertEquals(2, l.chunkCount)
    }

    @Test
    fun superscriptRaisesHeight() {
        val plain = layout("x")
        val sup = layout("x^2")
        assertTrue(sup.height > plain.height)
    }

    @Test
    fun parseErrorsProduceErrorLayout() {
        val l = layout("x^")
        assertTrue(l.error != null)
        assertEquals(1, l.chunkCount)
    }
}
