package me.rerere.bettertext.latex

import androidx.test.ext.junit.runners.AndroidJUnit4
import me.rerere.bettertext.latex.contrib.Mhchem
import me.rerere.bettertext.latex.contrib.MhchemException
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/** `MhchemTest`'s fixtures on a device, where regexes run on ICU instead of java.util.regex. */
@RunWith(AndroidJUnit4::class)
class MhchemDeviceTest {
    @Test
    fun outputMatchesKatexMhchem() {
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream("mhchem/fixtures.tsv")) {
            "mhchem/fixtures.tsv not found"
        }
        val lines = stream.bufferedReader().readLines().filter { it.isNotEmpty() && !it.startsWith("#") }
        val failures = lines.mapNotNull { line ->
            val (machine, input, expected) = line.split('\t')
            val actual = try {
                Mhchem.toTex(input, machine)
            } catch (e: MhchemException) {
                "!" + e.name
            }
            if (actual == expected) null else "$machine{$input}\n  expected $expected\n  actual   $actual"
        }
        if (failures.isNotEmpty()) fail("${failures.size}/${lines.size} cases differ:\n" + failures.joinToString("\n"))
    }
}
