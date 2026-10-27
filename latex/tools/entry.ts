import symbols, {ligatures} from "../katex-dl/package/src/symbols";
import metricMap from "../katex-dl/package/src/fontMetricsData";
import macros from "../katex-dl/package/src/macros";
import functions from "../katex-dl/package/src/functions";
import environments from "../katex-dl/package/src/environments";
import {_functions} from "../katex-dl/package/src/defineFunction";
import {path as svgPaths} from "../katex-dl/package/src/svgGeometry";
const out: any = {symbols, ligatures, metricMap, svgPaths};
out.stringMacros = {}; out.fnMacros = [];
for (const k of Object.keys(macros)) { const v = (macros as any)[k]; if (typeof v === "string") out.stringMacros[k] = v; else if (typeof v === "function") out.fnMacros.push(k); else out.stringMacros[k] = v; }
out.functions = {};
for (const k of Object.keys(_functions)) { const f = (_functions as any)[k]; out.functions[k] = {type: f.type, numArgs: f.numArgs, numOptionalArgs: f.numOptionalArgs, argTypes: f.argTypes, allowedInText: f.allowedInText, allowedInMath: f.allowedInMath, infix: f.infix, primitive: f.primitive}; }
out.environments = Object.keys(environments);
require("fs").writeFileSync(process.argv[2], JSON.stringify(out));
