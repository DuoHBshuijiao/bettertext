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

<span style="color: #e53935">HTML 里的颜色</span>和 <u>下划线</u> 也能正常显示。行内代码里的颜色 `#e53935`、`#1E88E5` 和 `rgb(67, 160, 71)` 前面有色块，正文里的珊瑚红（#FF7F50）也有，但 issue #123 没有。
""".trimIndent()

/** A message of the long conversation demo; user messages are plain text, replies are Markdown. */
internal class ChatMessage(val fromUser: Boolean, val text: String)

private val chatRounds = listOf(
    "一元二次方程怎么解？" to MARKDOWN_SAMPLE,
    "用 Python 写一个快速排序" to $$"""
        选一个基准值，把小于它的放左边、大于它的放右边，再分别递归：

        ```python
        def quick_sort(items: list[int]) -> list[int]:
            if len(items) <= 1:
                return items
            pivot, *rest = items
            left = [x for x in rest if x < pivot]
            right = [x for x in rest if x >= pivot]
            return quick_sort(left) + [pivot] + quick_sort(right)
        ```

        平均时间复杂度是 $O(n \log n)$，最坏情况（每次都选到最值）退化为 $O(n^2)$。
    """.trimIndent(),
    "常见排序算法的复杂度对比一下" to $$"""
        | 算法 | 平均 | 最坏 | 稳定 |
        |:--|:-:|:-:|:-:|
        | 快速排序 | $O(n \log n)$ | $O(n^2)$ | 否 |
        | 归并排序 | $O(n \log n)$ | $O(n \log n)$ | 是 |
        | 堆排序 | $O(n \log n)$ | $O(n \log n)$ | 否 |
        | 插入排序 | $O(n^2)$ | $O(n^2)$ | 是 |

        数据量小或者基本有序时，**插入排序**反而更快，很多库的排序在小区间里会切换到它。
    """.trimIndent(),
    "解释一下欧拉公式" to $$"""
        欧拉公式把指数函数和三角函数联系起来：

        $$
        e^{i\theta} = \cos\theta + i\sin\theta
        $$

        取 $\theta = \pi$ 就得到欧拉恒等式 $e^{i\pi} + 1 = 0$，它把 $e$、$i$、$\pi$、$1$ 和 $0$ 放进了同一个式子。
    """.trimIndent(),
)

/** A few rounds of questions and replies, repeated so the list needs scrolling. */
internal val chatMessages: List<ChatMessage> = List(3) { chatRounds }.flatten().flatMap { (question, reply) ->
    listOf(ChatMessage(fromUser = true, text = question), ChatMessage(fromUser = false, text = reply))
}
