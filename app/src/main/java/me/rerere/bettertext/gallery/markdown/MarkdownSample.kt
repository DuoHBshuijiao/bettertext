package me.rerere.bettertext.gallery.markdown

/** A chat-style reply for the Markdown demo. */
internal val MARKDOWN_SAMPLE = $$"""
## 一元二次方程

对于方程 $ax^2 + bx + c = 0$（其中 $a \neq 0$），求根公式为

$$
x = \frac{-b \pm \sqrt{b^2 - 4ac}}{2a}
$$

判别式 $\Delta = b^2 - 4ac$ 决定了根的情况：

1. **$\Delta > 0$**：两个不等实根
2. **$\Delta = 0$**：两个相等实根 $x_1 = x_2 = -\frac{b}{2a}$
3. **$\Delta < 0$**：没有实根，但有一对共轭复根
   - 实部为 $-\frac{b}{2a}$
   - 虚部为 $\pm\frac{\sqrt{-\Delta}}{2a}$

> 韦达定理：$x_1 + x_2 = -\frac{b}{a}$，$x_1 x_2 = \frac{c}{a}$。即使没有实根，这两个关系依然成立。

### 代码实现

```kotlin
fun solve(a: Double, b: Double, c: Double): List<Double> {
    val d = b * b - 4 * a * c
    if (d < 0) return emptyList()
    val s = sqrt(d)
    return listOf((-b + s) / (2 * a), (-b - s) / (2 * a))
}
```

### 几个例子

| 方程 | 判别式 | 根 |
|:--|:-:|--:|
| $x^2 - 3x + 2 = 0$ | $1$ | $1, 2$ |
| $x^2 - 2x + 1 = 0$ | $0$ | $1$ |
| $x^2 + 1 = 0$ | $-4$ | $\pm i$ |

- [x] 推导求根公式
- [ ] 证明韦达定理

<details>
<summary>为什么叫“判别式”？</summary>

因为它**判别**了根的个数和类型，参见 [Wikipedia](https://en.wikipedia.org/wiki/Discriminant)。

</details>

<span style="color: #e53935">HTML 里的颜色</span>和 <u>下划线</u> 也能正常显示。
""".trimIndent()
