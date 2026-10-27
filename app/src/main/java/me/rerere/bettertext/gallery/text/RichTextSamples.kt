package me.rerere.bettertext.gallery.text

/**
 * A paragraph for the rich text demo, in a tiny markup: `$…$` is inline math,
 * `[^n]` a citation, `#RRGGBB` a color, `**…**` bold and `` `…` `` code.
 */
internal class RichSample(val title: String, val description: String, val markup: String)

internal val richTextSamples = listOf(
    RichSample(
        "文字与公式", "长公式在关系符和运算符之后断行，和中文一起换行",
        $$"""设二次函数 $f(x) = ax^2 + bx + c$，其判别式为 $\Delta = b^2 - 4ac$。当 $\Delta > 0$ 时，方程 $f(x) = 0$ 有两个不等实根 $x_{1,2} = \frac{-b \pm \sqrt{\Delta}}{2a}$，并且满足韦达定理 $x_1 + x_2 = -\frac{b}{a},\ x_1 x_2 = \frac{c}{a}$。""",
    ),
    RichSample(
        "高公式", "所在的行按内容增高，不会和上下行重叠；公式基线和文字基线对齐",
        TALL_PARAGRAPH,
    ),
    RichSample(
        "颜色方块", "颜色值前面画一个方块；带一圈淡描边，接近背景色时也能看清",
        """Material 3 的默认主色是 #6750A4，错误色是 #B3261E。接近背景色的 #FFFBFE 和 #1C1B1F 也有描边。""",
    ),
    RichSample(
        "引用标记", "引用是可点击的 composable，在文字中间居中放置",
        """Compose 设置 `lineHeight` 之后每一行都一样高[^1]，选择相关的接口也还没有公开[^2]，所以 BetterText 自己排版。""",
    ),
)

internal const val TALL_PARAGRAPH = $$"""矩阵 $\begin{pmatrix} a & b \\ c & d \end{pmatrix}$ 的行列式是 $ad - bc$。连分数 $\cfrac{1}{1 + \cfrac{1}{1 + x}}$ 和 $\sqrt{\begin{matrix} 1 \\ 2 \\ 3 \end{matrix}}$ 会把所在的行撑高，而 $\displaystyle\int_0^1 x^2\,dx = \frac{1}{3}$ 这样的大型运算符也一样，前后的文字照常排列，不会重叠。"""

/** Pieces of the styled demo paragraph: plain, larger, colored. */
internal const val STYLED_PLAIN = $$"""**粗体**、`代码` 和公式 $a^2 + b^2 = c^2$ 可以放在一起。"""
internal const val STYLED_LARGE = $$"""大号文字里的公式 $\sum_{i=1}^n i$ 会跟着放大，"""
internal const val STYLED_COLORED = $$"""彩色文字里的公式 $e^{i\pi} + 1 = 0$ 也跟着变色。"""

/** The inline math of a markup string. */
internal fun markupFormulas(markup: String): List<String> =
    Regex("\\$([^$]+)\\$").findAll(markup).map { it.groupValues[1] }.toList()
