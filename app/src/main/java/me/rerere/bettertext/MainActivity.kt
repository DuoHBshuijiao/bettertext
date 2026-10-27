package me.rerere.bettertext

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.rerere.bettertext.latex.Latex
import me.rerere.bettertext.latex.LatexBlock
import me.rerere.bettertext.ui.theme.BettertextTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BettertextTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    LatexGallery(Modifier.padding(innerPadding))
                }
            }
        }
    }
}

private class Sample(val latex: String, val display: Boolean = false, val narrow: Boolean = false)

private val samples = listOf(
    Sample("a^2 + b^2 = c^2"),
    Sample("x \\neq y,\\ a \\iff b,\\ \\alpha \\le \\beta"),
    Sample("\\frac{a}{b} + \\sqrt{x} + \\sqrt[3]{y}"),
    Sample("\\sum_{i=1}^n i = \\frac{n(n+1)}{2}"),
    Sample("\\sum_{i=1}^n i = \\frac{n(n+1)}{2}", display = true),
    Sample("\\int_0^1 f(x)\\,dx \\quad \\lim_{x \\to 0} \\frac{\\sin x}{x} = 1", display = true),
    Sample("\\left( \\frac{a}{b} \\right)^2 \\left\\{ x \\middle| x > 0 \\right\\}", display = true),
    Sample("\\begin{pmatrix} a & b \\\\ c & d \\end{pmatrix} \\begin{bmatrix} 1 & 0 \\\\ 0 & 1 \\end{bmatrix}", display = true),
    Sample("f(x) = \\begin{cases} x^2 & \\text{if } x \\ge 0 \\\\ -x & \\text{otherwise} \\end{cases}", display = true),
    Sample("\\begin{aligned} a &= b + c \\\\ &= d \\end{aligned}", display = true),
    Sample("\\hat{x}\\ \\vec{v}\\ \\widehat{abc}\\ \\overrightarrow{AB}\\ \\overline{x+y}\\ \\underbrace{a+b}_{n}", display = true),
    Sample("\\mathbb{R}\\ \\mathcal{L}\\ \\mathfrak{g}\\ \\mathbf{v}\\ \\boldsymbol{\\alpha}\\ \\text{中文 text}"),
    Sample("\\color{red}{x} + \\textcolor{blue}{y} = \\boxed{z} \\xrightarrow[below]{above} \\cancel{w}"),
    Sample("a^2 + b^2 = c^2 + 2ab\\cos\\theta + \\frac{x}{y} - \\sqrt{z} + e^{i\\pi} = 0", narrow = true),
    Sample("a = b + c \\tag{1}", display = true),
    Sample("x \\tag{$\\dfrac{1}{2}$}", display = true),
    Sample("\\operatorname{a-b}(x)"),
    Sample("\\undefinedcommand{x} + y"),
    Sample("x^"),
)

@Preview(showBackground = true)
@Composable
private fun LatexGallery(modifier: Modifier = Modifier) {
    val textStyle = MaterialTheme.typography.bodyLarge
    val color = MaterialTheme.colorScheme.onSurface
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(samples) { sample ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(sample.latex, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                if (sample.display) {
                    LatexBlock(sample.latex, fontSize = textStyle.fontSize, color = color)
                } else {
                    val m = if (sample.narrow) {
                        Modifier.width(180.dp).border(1.dp, Color.LightGray)
                    } else {
                        Modifier
                    }
                    Latex(sample.latex, m, fontSize = textStyle.fontSize, color = color)
                }
                HorizontalDivider(Modifier.padding(top = 8.dp))
            }
        }
    }
}
