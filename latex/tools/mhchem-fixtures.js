// Generates src/test/resources/mhchem/fixtures.tsv: the TeX that KaTeX's contrib/mhchem produces
// for each case in src/test/resources/mhchem/inputs.txt, or the error it throws. Each line is
// "<state machine><TAB><input><TAB><TeX>", or "!<error name>" in place of the TeX.
//
//   node tools/mhchem-fixtures.js <path to the katex npm package>
const fs = require("fs");
const path = require("path");

const katexDir = process.argv[2];
if (!katexDir) throw new Error("usage: node tools/mhchem-fixtures.js <katex package dir>");
const version = require(path.resolve(katexDir, "package.json")).version;

// mhchem.js registers its macros on the katex import; run it against a stub and keep the parser.
const source = fs.readFileSync(path.join(katexDir, "contrib/mhchem/mhchem.js"), "utf8")
  .replace(/^﻿/, "")
  .replace(/^import katex from "katex";$/m, "");
const load = new Function("katex", source + "\nreturn { mhchemParser, texify };");
const { mhchemParser, texify } = load({ __defineMacro() {} });

const resources = path.join(__dirname, "../src/test/resources/mhchem");
const cases = fs.readFileSync(path.join(resources, "inputs.txt"), "utf8")
  .split("\n")
  .filter(line => line && !line.startsWith("#"))
  .map(line => {
    const tab = line.indexOf("\t");
    const machine = line.slice(0, tab), input = line.slice(tab + 1);
    try {
      return { machine, input, tex: texify.go(mhchemParser.go(input, machine)) };
    } catch (e) {
      if (!Array.isArray(e)) throw e;
      return { machine, input, error: e[0] };
    }
  });
const lines = cases.map(c => [c.machine, c.input, c.error ? "!" + c.error : c.tex].join("\t"));
for (const line of lines) {
  if (/[\n\r]/.test(line) || line.split("\t").length !== 3) throw new Error("can't store " + JSON.stringify(line));
}
fs.writeFileSync(path.join(resources, "fixtures.tsv"),
  `# GENERATED from KaTeX ${version} contrib/mhchem by tools/mhchem-fixtures.js. DO NOT EDIT.\n` + lines.join("\n") + "\n");
console.log(`${cases.length} cases from KaTeX ${version}`);
