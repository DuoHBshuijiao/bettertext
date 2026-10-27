// Generates Kotlin data sources from KaTeX's data tables.
const fs = require("fs");
const d = require("../katex-data.json");
const d2 = require("../katex-data2.json");
const outDir = process.argv[2];
fs.mkdirSync(outDir, {recursive: true});

const SAFE = /[A-Za-z0-9 .,:;_\-+*\/()=<>!?@|~^'\[\]{}&%#`]/;
function esc(s) {
  let out = "";
  for (const ch of s) {
    const cp = ch.codePointAt(0);
    if (cp < 0x80 && SAFE.test(ch)) out += ch;
    else if (cp > 0xFFFF) {
      const hi = Math.floor((cp - 0x10000) / 0x400) + 0xD800, lo = (cp - 0x10000) % 0x400 + 0xDC00;
      out += "\\u" + hi.toString(16).padStart(4, "0") + "\\u" + lo.toString(16).padStart(4, "0");
    } else out += "\\u" + cp.toString(16).padStart(4, "0");
  }
  return out;
}
// Splits an encoded payload into string literals small enough for the constant pool.
function chunks(s, size = 16000) {
  const parts = [];
  for (let i = 0; i < s.length; i += size) parts.push(s.slice(i, i + size));
  return parts.map(p => `        "${esc(p)}",`).join("\n");
}
function writeKt(name, objName, doc, payload) {
  fs.writeFileSync(`${outDir}/${name}.kt`, `// GENERATED from KaTeX ${require("../katex-dl/package/package.json").version} by tools/gen.js. DO NOT EDIT.
package me.rerere.bettertext.latex.data

/** ${doc} */
internal object ${objName} {
    val raw: String = arrayOf(
${chunks(payload)}
    ).joinToString("")
}
`);
}
const FS = "\u0001", RS = "\u0002";
const num = x => (x === 0 ? "0" : String(+x.toFixed(5)));

// font metrics: font RS cp:d,h,i,s,w ...
{
  const recs = [];
  for (const [font, map] of Object.entries(d.metricMap)) {
    const entries = Object.entries(map).map(([cp, m]) => cp + ":" + m.map(num).join(","));
    recs.push(font + FS + entries.join(";"));
  }
  writeKt("FontMetricsData", "FontMetricsData", "Per-font character metrics: depth, height, italic, skew, width (in em).", recs.join(RS));
}
// symbols: mode FS name FS font FS group FS replace
{
  const recs = [];
  for (const mode of ["math", "text"]) for (const [name, s] of Object.entries(d.symbols[mode]))
    recs.push([mode, name, s.font, s.group, s.replace ?? ""].join(FS));
  writeKt("SymbolsData", "SymbolsData", "KaTeX symbol table: mode, name, font, group, replacement.", recs.join(RS));
}
// string macros: name FS expansion
{
  const recs = Object.entries(d.stringMacros).map(([k, v]) => k + FS + v);
  writeKt("MacrosData", "MacrosData", "KaTeX built-in macros whose expansion is a plain string.", recs.join(RS));
}
// static svg paths
{
  const recs = Object.entries(d.svgPaths).map(([k, v]) => k + FS + v.replace(/\s+/g, " ").trim());
  writeKt("SvgPathData", "SvgPathData", "Static SVG paths from svgGeometry.ts (viewBox units, 1000 per em).", recs.join(RS));
}
// unicode tables
{
  const sym = Object.entries(d2.unicodeSymbols).map(([k, v]) => k + FS + v).join(RS);
  const ss = Object.entries(d2.uSubsAndSups).map(([k, v]) => k + FS + v).join(RS);
  fs.writeFileSync(`${outDir}/UnicodeData.kt`, `// GENERATED from KaTeX by tools/gen.js. DO NOT EDIT.
package me.rerere.bettertext.latex.data

internal object UnicodeData {
    /** Precomposed accented characters mapped to base + combining marks. */
    val symbols: String = "${esc(sym)}"

    /** Unicode sub/superscript characters mapped to their plain equivalents. */
    val subsAndSups: String = "${esc(ss)}"

    /** Subscript characters (the rest of [subsAndSups] are superscripts). */
    const val SUB_CHARS: String = "${esc(d2.unicodeSubRegEx.replace(/^\^\[/, "").replace(/\]$/, ""))}"

    /** Codepoint ranges (inclusive) of scripts allowed in text mode. */
    val supportedRanges: IntArray = intArrayOf(${d2.ranges.flat().join(", ")})
}
`);
}
console.log("ok");
