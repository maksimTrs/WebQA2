// Общие пути для скриптов скилла. Набор инструментов живёт в одном месте на машине,
// скилл и видео-проекты — где угодно.
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { createRequire } from 'node:module';
import { fileURLToPath, pathToFileURL } from 'node:url';

export const TK = process.env.VIDEO_TOOLKIT ?? path.join(os.homedir(), '.video-toolkit');
export const SKILL_DIR = path.dirname(path.dirname(fileURLToPath(import.meta.url)));

export function toolkit() {
  const f = path.join(TK, 'toolkit.json');
  if (!fs.existsSync(f)) {
    console.error(`Набор инструментов не найден в ${TK}. Запусти: node "${path.join(SKILL_DIR, 'scripts', 'setup.mjs')}"`);
    process.exit(2);
  }
  const t = JSON.parse(fs.readFileSync(f, 'utf8'));
  process.env.PLAYWRIGHT_BROWSERS_PATH = t.browsers;
  return t;
}

// Пакеты стоят в TK/node_modules, а скрипты лежат в папке скилла — резолвим от TK.
export async function tkImport(name) {
  const req = createRequire(path.join(TK, 'package.json'));
  const m = await import(pathToFileURL(req.resolve(name)).href);
  return { ...(m.default ?? {}), ...m };   // CJS-пакеты (playwright-core) отдают API через default
}

// --key value / --flag / позиционные аргументы
export function parseArgs(argv) {
  const a = { _: [] };
  for (let i = 0; i < argv.length; i++) {
    if (!argv[i].startsWith('--')) { a._.push(argv[i]); continue; }
    const k = argv[i].slice(2), v = argv[i + 1];
    if (v === undefined || v.startsWith('--')) a[k] = true; else { a[k] = v; i++; }
  }
  return a;
}
