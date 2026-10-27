package me.rerere.bettertext.gallery.highlight

/** Code for the highlight demo, with the fence language a reply would use. */
internal class CodeSample(val label: String, val language: String, val code: String)

internal val codeSamples = listOf(
    CodeSample(
        "Kotlin", "kotlin",
        $$"""
        /** Solves ax² + bx + c = 0. */
        fun solve(a: Double, b: Double, c: Double): List<Double> {
            val d = b * b - 4 * a * c
            if (d < 0) return emptyList()
            val s = sqrt(d)
            return listOf((-b + s) / (2 * a), (-b - s) / (2 * a))
        }

        data class Reply(val text: String, val streaming: Boolean = false)

        fun main() = println("roots: ${solve(1.0, -3.0, 2.0)}")
        """.trimIndent(),
    ),
    CodeSample(
        "Python", "python",
        """
        import math
        from dataclasses import dataclass

        @dataclass
        class Quadratic:
            a: float
            b: float
            c: float

            def roots(self) -> list[float]:
                d = self.b ** 2 - 4 * self.a * self.c
                if d < 0:
                    return []  # no real roots
                s = math.sqrt(d)
                return [(-self.b + s) / (2 * self.a), (-self.b - s) / (2 * self.a)]

        print(f"roots: {Quadratic(1, -3, 2).roots()}")
        """.trimIndent(),
    ),
    CodeSample(
        "TypeScript", "typescript",
        $$"""
        interface Message {
          role: "user" | "assistant";
          content: string;
        }

        async function* stream(url: string): AsyncGenerator<string> {
          const response = await fetch(url, { method: "POST" });
          const reader = response.body!.getReader();
          const decoder = new TextDecoder();
          while (true) {
            const { done, value } = await reader.read();
            if (done) return;
            yield decoder.decode(value);
          }
        }

        const greet = (m: Message) => `${m.role}: ${m.content}`;
        """.trimIndent(),
    ),
    CodeSample(
        "Rust", "rust",
        """
        use std::collections::HashMap;

        #[derive(Debug, Clone)]
        struct Token<'a> {
            text: &'a str,
            id: u32,
        }

        fn count_words(text: &str) -> HashMap<&str, usize> {
            let mut counts = HashMap::new();
            for word in text.split_whitespace() {
                *counts.entry(word).or_insert(0) += 1;
            }
            counts
        }

        fn main() {
            let counts = count_words("a b a");
            println!("{:?}", counts.get("a"));
        }
        """.trimIndent(),
    ),
    CodeSample(
        "Go", "go",
        """
        package main

        import (
            "fmt"
            "strings"
        )

        type Chunk struct {
            Start, End int
        }

        func split(text string) []string {
            return strings.Fields(text)
        }

        func main() {
            ch := make(chan string, 4)
            go func() {
                defer close(ch)
                for _, w := range split("streamed reply") {
                    ch <- w
                }
            }()
            for w := range ch {
                fmt.Println(w, len(w))
            }
        }
        """.trimIndent(),
    ),
    CodeSample(
        "HTML", "html",
        """
        <!DOCTYPE html>
        <html lang="zh">
        <head>
          <meta charset="utf-8">
          <title>BetterText</title>
          <style>
            body { font-family: sans-serif; margin: 0 auto; max-width: 40em; }
            .formula { color: #6750a4; }
          </style>
        </head>
        <body>
          <!-- An inline formula -->
          <p>The roots are <span class="formula">x₁, x₂</span>.</p>
          <script>
            document.querySelector("p").addEventListener("click", () => alert("hi"));
          </script>
        </body>
        </html>
        """.trimIndent(),
    ),
    CodeSample(
        "SQL", "sql",
        """
        -- Conversations with the most messages this week
        SELECT c.id, c.title, COUNT(m.id) AS messages
        FROM conversations AS c
        JOIN messages AS m ON m.conversation_id = c.id
        WHERE m.created_at >= DATE('now', '-7 days')
          AND c.archived = FALSE
        GROUP BY c.id, c.title
        HAVING COUNT(m.id) > 10
        ORDER BY messages DESC
        LIMIT 20;
        """.trimIndent(),
    ),
    CodeSample(
        "Bash", "bash",
        $$"""
        #!/usr/bin/env bash
        set -euo pipefail

        # Build and install the demo on every connected device
        for serial in $(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }'); do
          echo "Installing on ${serial}..."
          ANDROID_SERIAL="$serial" ./gradlew :app:installDebug --quiet
        done

        if [[ -z "${CI:-}" ]]; then
          adb shell am start -n me.rerere.bettertext/.MainActivity
        fi
        """.trimIndent(),
    ),
    CodeSample(
        "JSON", "json",
        """
        {
          "model": "claude-opus-5-5",
          "stream": true,
          "max_tokens": 1024,
          "temperature": 0.7,
          "messages": [
            { "role": "user", "content": "解一元二次方程" }
          ],
          "metadata": null
        }
        """.trimIndent(),
    ),
)
