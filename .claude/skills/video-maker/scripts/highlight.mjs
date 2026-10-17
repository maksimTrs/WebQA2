// Подсветка кода токенами shiki (как в VS Code) → JSON, который страница рисует на canvas построчно.
//   node highlight.mjs <file> [--lang ts] [--theme github-dark] [--out tokens.json]
// Формат: { bg, fg, lines: [[{ t: "текст", c: "#цвет", s: fontStyle }]] }  (s: 1 italic, 2 bold, 4 underline)
import fs from 'node:fs';
import path from 'node:path';
import { tkImport, parseArgs } from './_toolkit.mjs';

const args = parseArgs(process.argv.slice(2));
const file = args._[0];
if (!file) { console.error('usage: node highlight.mjs <file> [--lang ts] [--theme github-dark] [--out tokens.json]'); process.exit(2); }
const { codeToTokens } = await tkImport('shiki');
const lang = args.lang ?? path.extname(file).slice(1) ?? 'text';
const r = await codeToTokens(fs.readFileSync(file, 'utf8').replace(/\s+$/, ''), { lang, theme: args.theme ?? 'github-dark' });
const json = JSON.stringify({ bg: r.bg, fg: r.fg, lines: r.tokens.map(l => l.map(t => ({ t: t.content, c: t.color, s: t.fontStyle ?? 0 }))) });
const out = args.out ?? file + '.tokens.json';
fs.writeFileSync(out, json);
console.log(`${r.tokens.length} строк → ${out}`);
