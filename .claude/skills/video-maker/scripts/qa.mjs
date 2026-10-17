// Проверка готового ролика: параметры потоков, громкость, лист кадров и картинка звуковой волны.
// Звук модель не слышит — громкость и волна заменяют прослушивание.
//   node qa.mjs <video.mp4> [--frames 16]
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { toolkit, parseArgs } from './_toolkit.mjs';

const args = parseArgs(process.argv.slice(2));
const file = path.resolve(args._[0] ?? '');
const FF = toolkit().ffmpeg;
const run = a => spawnSync(FF, ['-hide_banner', ...a], { encoding: 'utf8' });

const info = run(['-i', file]).stderr;
const dur = info.match(/Duration: (\d+):(\d+):([\d.]+)/);
if (!dur) { console.error('не удалось прочитать файл:', file); process.exit(1); }
const seconds = +dur[1] * 3600 + +dur[2] * 60 + +dur[3];
const fps = Number(info.match(/([\d.]+) fps/)?.[1] ?? 30);
const hasAudio = /Audio:/.test(info);
console.log(info.split('\n').filter(l => /Duration|Stream/.test(l)).map(l => l.trim()).join('\n'));

const n = Number(args.frames ?? 16), cols = 4, rows = Math.ceil(n / cols);
const step = Math.max(1, Math.floor(seconds * fps / n));
const base = file.replace(/\.mp4$/i, '');
run(['-y', '-loglevel', 'error', '-i', file, '-vf', `select='not(mod(n\\,${step}))',scale=480:-1,tile=${cols}x${rows}`, '-frames:v', '1', `${base}.sheet.png`]);
console.log(`лист кадров: ${base}.sheet.png (шаг ${(step / fps).toFixed(2)} c)`);

if (hasAudio) {
  run(['-y', '-loglevel', 'error', '-i', file, '-filter_complex', 'showwavespic=s=1920x320:split_channels=1:colors=0x4FA3FF|0xFF8A3D', '-frames:v', '1', `${base}.wave.png`]);
  const lo = run(['-i', file, '-af', 'ebur128=peak=true', '-f', 'null', '-']).stderr;
  const I = Number(lo.match(/I:\s+(-?[\d.]+) LUFS/g)?.pop()?.match(/-?[\d.]+/)[0]);
  const peak = Number(lo.match(/Peak:\s+(-?[\d.]+) dBFS/g)?.pop()?.match(/-?[\d.]+/)[0]);
  console.log(`волна: ${base}.wave.png\nгромкость: ${I} LUFS, пик ${peak} dBFS`);
  if (I > -12 || I < -22) console.log('  ⚠ громкость вне −22…−12 LUFS: проверь баланс микса');
  if (peak > -0.5) console.log('  ⚠ пик выше −0.5 dBFS: возможен клиппинг после перекодирования');
} else console.log('звука нет');
