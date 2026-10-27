package me.rerere.bettertext.gallery.latex

import me.rerere.bettertext.latex.LatexSettings

internal class Sample(
    val latex: String,
    val display: Boolean = false,
    val note: String? = null,
    val leqno: Boolean = false,
    val fleqn: Boolean = false,
    val macros: Map<String, String> = emptyMap(),
) {
    val settings = LatexSettings(displayMode = display, macros = macros, leqno = leqno, fleqn = fleqn)
}

internal class Section(
    val title: String,
    val description: String,
    val samples: List<Sample>,
    /** The samples are expected to fail to parse. */
    val expectErrors: Boolean = false,
)

internal val sections = listOf(
    Section(
        "基础", "上下标、希腊字母、关系与运算符号",
        listOf(
            Sample("""a^2 + b^2 = c^2"""),
            Sample("""x_i^2,\ x_{i,j}^{(n)},\ e^{-x^2},\ f'(x),\ f''(x)"""),
            Sample("""\alpha\beta\gamma\delta\epsilon\varepsilon\theta\lambda\pi\sigma\phi\omega\ \Gamma\Delta\Theta\Lambda\Sigma\Phi\Omega"""),
            Sample("""x \neq y,\ a \le b,\ a \approx b,\ A \subseteq B,\ x \in \mathbb{N},\ p \Rightarrow q,\ a \iff b"""),
            Sample("""\pm\ \times\ \div\ \cdot\ \circ\ \cup\ \cap\ \oplus\ \otimes\ \wedge\ \vee"""),
            Sample("""\ldots\ \cdots\ \vdots\ \ddots\ \infty\ \partial\ \nabla\ \forall\ \exists\ \emptyset"""),
        ),
    ),
    Section(
        "分式与根式", "\\frac、\\binom、\\sqrt 和连分式",
        listOf(
            Sample("""\frac{a}{b} + \tfrac{1}{2} + \dfrac{x+1}{x-1}""", note = "行内模式下 \\frac 会缩小，\\dfrac 保持块级大小"),
            Sample("""x = \frac{-b \pm \sqrt{b^2 - 4ac}}{2a}""", display = true),
            Sample("""\binom{n}{k} = \frac{n!}{k!(n-k)!}""", display = true),
            Sample("""\sqrt{x} + \sqrt[3]{y} + \sqrt{\frac{a}{b}} + \sqrt{1+\sqrt{1+\sqrt{x}}}""", display = true),
            Sample("""x = \cfrac{1}{1 + \cfrac{1}{1 + \cfrac{1}{1 + x}}}""", display = true),
            Sample("""\sqrt{\begin{matrix} 1 \\ 2 \\ 3 \\ 4 \\ 5 \\ 6 \end{matrix}}""", display = true, note = "超高的根号由可变长度的 SVG 路径绘制"),
        ),
    ),
    Section(
        "大型运算符", "求和、积分、极限，以及上下限的位置",
        listOf(
            Sample("""\sum_{i=1}^n i = \frac{n(n+1)}{2}""", note = "行内：上下限在右侧"),
            Sample("""\sum_{i=1}^n i = \frac{n(n+1)}{2}""", display = true, note = "块级：上下限在上下方"),
            Sample("""\int_0^1 f(x)\,dx \quad \iint_D dA \quad \oint_C \vec F \cdot d\vec r""", display = true),
            Sample("""\prod_{p\ \text{prime}} \frac{1}{1-p^{-s}} = \sum_{n=1}^\infty \frac{1}{n^s}""", display = true),
            Sample("""\lim_{x \to 0} \frac{\sin x}{x} = 1 \quad \max_{1 \le i \le n} a_i""", display = true),
            Sample("""\sum_{\substack{0 \le i \le m \\ 0 < j < n}} P(i, j) \quad \bigcup_{i=1}^n A_i \quad \bigoplus_k V_k""", display = true),
            Sample("""\operatorname{rank}(A) + \operatorname*{argmax}_x f(x)""", display = true),
        ),
    ),
    Section(
        "定界符", "自动伸缩的括号与手动尺寸",
        listOf(
            Sample("""\left( \frac{a}{b} \right)^2 \left[ \frac{1}{2} \right] \left\{ x \middle| x > 0 \right\}""", display = true),
            Sample("""\big( \Big( \bigg( \Bigg( \quad \Bigg] \bigg] \Big] \big]""", display = true),
            Sample("""\left( \begin{matrix} a \\ b \\ c \\ d \end{matrix} \right) \left. \frac{df}{dx} \right|_{x=0}""", display = true, note = "超高的括号由多段字形拼接"),
            Sample("""\langle \psi | \phi \rangle,\ \lfloor x \rfloor,\ \lceil y \rceil,\ \lvert z \rvert,\ \lVert v \rVert"""),
        ),
    ),
    Section(
        "矩阵与环境", "matrix 系列、array、cases、aligned 等",
        listOf(
            Sample("""\begin{pmatrix} a & b \\ c & d \end{pmatrix} \begin{bmatrix} 1 & 0 \\ 0 & 1 \end{bmatrix} \begin{vmatrix} a & b \\ c & d \end{vmatrix} \begin{Vmatrix} x \end{Vmatrix}""", display = true),
            Sample("""A = \begin{pmatrix} a_{11} & \cdots & a_{1n} \\ \vdots & \ddots & \vdots \\ a_{m1} & \cdots & a_{mn} \end{pmatrix}""", display = true),
            Sample("""M = \left(\begin{smallmatrix} a & b \\ c & d \end{smallmatrix}\right)""", note = "smallmatrix 适合行内"),
            Sample("""\begin{array}{c|cc} \times & 1 & 2 \\ \hline 1 & 1 & 2 \\ 2 & 2 & 4 \end{array}""", display = true),
            Sample("""|x| = \begin{cases} x & \text{if } x \ge 0 \\ -x & \text{otherwise} \end{cases}""", display = true),
            Sample("""\begin{aligned} (a+b)^2 &= a^2 + 2ab + b^2 \\ &\ge 4ab \end{aligned}""", display = true),
            Sample("""\begin{gathered} x = 1 \\ y = 22 \\ z = 333 \end{gathered} \qquad \begin{alignedat}{2} 10&x + &3&y = 2 \\ 3&x + &13&y = 4 \end{alignedat}""", display = true),
        ),
    ),
    Section(
        "重音与箭头", "重音符号、可伸缩的上下括号和箭头",
        listOf(
            Sample("""\hat{x}\ \bar{x}\ \tilde{x}\ \vec{v}\ \dot{x}\ \ddot{x}\ \acute{a}\ \breve{a}\ \check{a}"""),
            Sample("""\widehat{abc}\ \widetilde{xyz}\ \overline{x+y}\ \underline{a+b}\ \overrightarrow{AB}\ \overleftrightarrow{CD}""", display = true),
            Sample("""\overbrace{a + \cdots + a}^{n\text{ 个}} \quad \underbrace{b + \cdots + b}_{m}""", display = true),
            Sample("""A \xrightarrow[\text{below}]{\text{above}} B \xleftarrow{f} C \xLeftrightarrow{\quad} D""", display = true),
            Sample("""\to\ \gets\ \mapsto\ \implies\ \longrightarrow\ \hookrightarrow\ \leftrightharpoons\ \uparrow\ \nearrow"""),
            Sample("""\overset{\text{def}}{=} \quad \underset{n \to \infty}{\longrightarrow} \quad \stackrel{?}{=}""", display = true),
        ),
    ),
    Section(
        "字体与文本", "数学字体、文本模式与中文混排",
        listOf(
            Sample("""\mathbb{RQZNC}\ \mathcal{LFH}\ \mathscr{L}\ \mathfrak{gsu}"""),
            Sample("""\mathrm{d}x\ \mathit{diff}\ \mathbf{v}\ \boldsymbol{\alpha\beta}\ \mathsf{Sans}\ \mathtt{mono}"""),
            Sample("""\text{中文与 English 混排}\ \textbf{bold}\ \textit{italic}\ \texttt{code}"""),
            Sample("""\text{圆的面积 } S = \pi r^2"""),
        ),
    ),
    Section(
        "颜色与盒子", "\\color、\\colorbox、\\boxed、删除线",
        listOf(
            Sample("""\color{red}{x} + \textcolor{blue}{y} + \textcolor{#228B22}{z}"""),
            Sample("""\colorbox{#ffe08a}{$\,a+b\,$}\ \fcolorbox{red}{#cce5ff}{$\,c\,$}\ \boxed{E = mc^2}\ \fbox{text}"""),
            Sample("""\cancel{5}\ \bcancel{5}\ \xcancel{AB}\ \sout{abc}"""),
        ),
    ),
    Section(
        "间距与尺寸", "间距命令、字号、样式、\\phantom 与 \\rule",
        listOf(
            Sample("""a\!b \quad ab \quad a\,b \quad a\:b \quad a\;b \quad a\enspace b \quad a\qquad b"""),
            Sample("""\tiny a\ \scriptsize a\ \small a\ \normalsize a\ \large a\ \Large a\ \LARGE a\ \huge a\ \Huge a"""),
            Sample("""\displaystyle\sum_i x_i \quad \textstyle\sum_i x_i \quad \scriptstyle\sum_i x_i""", display = true),
            Sample("""a\raisebox{0.5em}{b}c \quad \rule{1em}{0.5em} \quad x\phantom{yyy}z \quad \smash{\frac{a}{b}}"""),
        ),
    ),
    Section(
        "公式编号", "\\tag、编号环境、leqno 与 fleqn",
        listOf(
            Sample("""E = mc^2 \tag{1}""", display = true),
            Sample("""e^{i\pi} + 1 = 0 \tag*{(Euler)}""", display = true),
            Sample("""\begin{equation} \int_0^\infty e^{-x^2}\,dx = \frac{\sqrt\pi}{2} \end{equation}""", display = true),
            Sample("""\begin{align} a &= b + c \\ d &= e + f + g \end{align}""", display = true),
            Sample("""a = b \tag{1}""", display = true, leqno = true, note = "leqno：编号在左侧"),
            Sample("""\sum_{k=0}^n k^2 = \frac{n(n+1)(2n+1)}{6}""", display = true, fleqn = true, note = "fleqn：公式左对齐"),
            Sample("""x \tag{$\dfrac{1}{2}$}""", display = true, note = "很高的编号会撑高所在行"),
        ),
    ),
    Section(
        "宏", "\\def、\\newcommand 和 LatexSettings.macros",
        listOf(
            Sample("""\def\pair#1#2{\left\langle #1, #2 \right\rangle} \pair{a}{\frac{1}{2}}"""),
            Sample("""\newcommand{\norm}[1]{\left\lVert#1\right\rVert} \norm{x} + \norm{\frac{y}{2}}"""),
            Sample(
                """f: \RR^n \to \RR,\ \NN \subset \RR""",
                note = "\\RR、\\NN 由 LatexSettings.macros 提供",
                macros = mapOf("\\RR" to "\\mathbb{R}", "\\NN" to "\\mathbb{N}"),
            ),
        ),
    ),
    Section(
        "错误处理", "未定义的命令单独标红；语法错误时整段源码标红。都不会抛异常",
        listOf(
            Sample("""\undefinedcommand{x} + y""", note = "只有未定义的命令标红，其余部分正常渲染"),
            Sample("""x^"""),
            Sample("""\frac{a}"""),
            Sample("""\begin{matrix} a & b \end{pmatrix}"""),
        ),
        expectErrors = true,
    ),
)

/** Long inline formulas for the wrapping demo, with a short label each. */
internal val wrapSamples = listOf(
    "多项式" to """f(x) = a_0 + a_1 x + a_2 x^2 + a_3 x^3 + a_4 x^4 + \cdots + a_n x^n = \sum_{k=0}^{n} a_k x^k""",
    "等式链" to """(a+b)^2 = (a+b)(a+b) = a^2 + ab + ba + b^2 = a^2 + 2ab + b^2""",
    "\\nobreak" to """a =\nobreak b =\nobreak c = d + e + f + g + h""",
    "\\allowbreak" to """\text{aaaa}\allowbreak\text{bbbb}\allowbreak\text{cccc}\allowbreak\text{dddd}""",
    "强制换行" to """a = b + c \\ d = e + f""",
)

/** A display formula with a tag, for showing where the tag goes when space runs out. */
internal const val TAGGED_DISPLAY = """\int_{-\infty}^{\infty} e^{-x^2}\,dx = \sqrt{\pi} \tag{1}"""
