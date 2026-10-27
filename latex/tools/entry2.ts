import unicodeSymbols from "../katex-dl/package/src/unicodeSymbols";
import {uSubsAndSups, unicodeSubRegEx} from "../katex-dl/package/src/unicodeSupOrSub";
import {supportedCodepoint} from "../katex-dl/package/src/unicodeScripts";
const ranges: number[][] = [];
let start = -1;
for (let cp = 0; cp <= 0x10FFFF + 1; cp++) {
  const ok = cp <= 0x10FFFF && supportedCodepoint(cp);
  if (ok && start < 0) start = cp;
  if (!ok && start >= 0) { ranges.push([start, cp - 1]); start = -1; }
}
require("fs").writeFileSync(process.argv[2], JSON.stringify({unicodeSymbols, uSubsAndSups, unicodeSubRegEx: unicodeSubRegEx.source, ranges}));
