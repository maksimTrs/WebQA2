// Рендер страницы-ролика в mp4 (или контрольные кадры).
//   node render.mjs <page.html> [--out video.mp4] [--sub 4] [--crf 16] [--poster 12.5] [--lufs -16|off] [--workers 4] [--gl cpu]
//   node render.mjs <page.html> --stills 0.5,3,7.2      → work/stills/sNN.png + work/stills/sheet.png
//   node render.mjs <page.html> --stills 2,21.6-22.5/0.15  → диапазон «от-до/шаг» — плотная проверка пролёта или взрыва
// Страница отдаётся локальным HTTP-сервером: /toolkit/… — общий набор инструментов, /skill/… — файлы скилла,
// остальное — папка страницы. По file:// ES-модули и шрифты не грузятся, поэтому нужен сервер.
import fs from 'node:fs';
import http from 'node:http';
import os from 'node:os';
import path from 'node:path';
import { spawn, spawnSync } from 'node:child_process';
import { once } from 'node:events';
import { toolkit, tkImport, parseArgs, TK, SKILL_DIR } from './_toolkit.mjs';

const args = parseArgs(process.argv.slice(2));
if (!args._[0]) { console.error('usage: node render.mjs <page.html> [--out x.mp4] [--sub 4] [--stills t1,t2] [--poster t]'); process.exit(2); }
const T = toolkit();
const { chromium } = await tkImport('playwright-core');
const PAGE = path.resolve(args._[0]), ROOT = path.dirname(PAGE), WORK = path.join(ROOT, 'work');
const SLUG = path.basename(PAGE, '.html');
// video-output/ игнорирует сам себя: в git status проекта не попадает, .gitignore проекта не трогаем
const OUTDIR = path.dirname(ROOT);
if (path.basename(OUTDIR) === 'video-output' && !fs.existsSync(path.join(OUTDIR, '.gitignore'))) fs.writeFileSync(path.join(OUTDIR, '.gitignore'), '*\n');
if (!args.stills) { fs.mkdirSync(WORK, { recursive: true }); lock(path.join(WORK, `${SLUG}.render.lock`)); }   // до запуска браузера: второй запуск падает сразу

const MIME = { '.html': 'text/html', '.js': 'text/javascript', '.mjs': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.png': 'image/png', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.svg': 'image/svg+xml', '.webp': 'image/webp', '.gif': 'image/gif', '.woff2': 'font/woff2', '.woff': 'font/woff', '.ttf': 'font/ttf', '.ogg': 'audio/ogg', '.mp3': 'audio/mpeg', '.wav': 'audio/wav', '.mp4': 'video/mp4', '.wasm': 'application/wasm' };
const server = http.createServer((req, res) => {
  const u = decodeURIComponent(new URL(req.url, 'http://x').pathname);
  const [base, rel] = u.startsWith('/toolkit/') ? [TK, u.slice(9)] : u.startsWith('/skill/') ? [path.join(SKILL_DIR, 'scripts'), u.slice(7)] : [ROOT, u.slice(1)];
  const f = path.resolve(base, rel);
  if (!f.startsWith(path.resolve(base)) || !fs.existsSync(f) || fs.statSync(f).isDirectory()) { res.writeHead(404); return res.end(); }
  res.writeHead(200, { 'Content-Type': MIME[path.extname(f).toLowerCase()] ?? 'application/octet-stream' });
  fs.createReadStream(f).pipe(res);
});
server.listen(0, '127.0.0.1'); await once(server, 'listening');

// GPU через ANGLE; если его нет — программный SwiftShader (нужен для WebGL/three.js без видеокарты).
// Без явного --use-angle headless на Windows берёт SwiftShader даже при живой видеокарте; d3d11 включает её
// (замер: Canvas 2D −12…20 % времени кадра). --gl cpu — прежний режим без флага (SwiftShader через Vulkan);
// не --use-angle=swiftshader: тот в ~7 раз медленнее.
const angle = process.platform === 'win32' && args.gl !== 'cpu' ? ['--use-angle=d3d11'] : [];
const browser = await chromium.launch({ args: ['--ignore-gpu-blocklist', '--enable-gpu-rasterization', '--enable-unsafe-swiftshader', ...angle] });
async function openPage(p) {
  p.on('pageerror', e => { console.error('PAGE ERROR:', e.message); process.exit(1); });
  p.on('console', m => m.type() === 'error' && console.error('[page]', m.text()));
  await p.goto(`http://127.0.0.1:${server.address().port}/${encodeURIComponent(path.basename(PAGE))}?render`);
  await p.waitForFunction(() => window.READY === true || window.READY_ERROR, null, { timeout: 120_000 });
  const err = await p.evaluate(() => window.READY_ERROR);
  if (err) { console.error('setup() упал:', err); process.exit(1); }
  return p;
}
const page = await openPage(await browser.newPage());
const V = await page.evaluate(() => window.VIDEO);
await page.setViewportSize({ width: V.width, height: V.height });
// чем рисует браузер: видеокарта (ANGLE …) или программный SwiftShader — от этого зависит скорость рендера
const gpu = await page.evaluate(() => { const g = document.createElement('canvas').getContext('webgl'); if (!g) return 'WebGL недоступен'; const x = g.getExtension('WEBGL_debug_renderer_info'); return g.getParameter(x ? x.UNMASKED_RENDERER_WEBGL : g.RENDERER); });
console.log(`графика: ${gpu}`);
const dom = V.mode === 'dom';
const sub = dom ? 1 : Number(args.sub ?? 4);
const png = d => Buffer.from(d.slice(d.indexOf(',') + 1), 'base64');
async function grab(t, s, p = page) {
  if (!dom) return png(await p.evaluate(([t, s]) => window.frameAt(t, s), [t, s]));
  await p.evaluate(t => window.seek(t), t);
  return p.screenshot({ type: 'png', clip: { x: 0, y: 0, width: V.width, height: V.height } });
}
const FF = T.ffmpeg;
const ff = (a, opts) => spawnSync(FF, ['-y', '-hide_banner', '-loglevel', 'error', ...a], opts);

if (args.stills) {
  const dir = path.join(WORK, 'stills');
  fs.rmSync(dir, { recursive: true, force: true }); fs.mkdirSync(dir, { recursive: true });
  const times = String(args.stills).split(',').flatMap(tok => {
    const r = tok.match(/^([\d.]+)-([\d.]+)\/([\d.]+)$/);
    if (!r) return [Number(tok)];
    const [a, b, s] = r.slice(1).map(Number), n = Math.floor((b - a) / s + 1e-9);
    return Array.from({ length: n + 1 }, (_, k) => Math.round((a + k * s) * 1000) / 1000);
  }).filter(t => Number.isFinite(t) && t >= 0 && t < V.duration);
  if (!times.length) { console.error(`--stills: нет моментов внутри 0…${V.duration} c`); process.exit(2); }
  if (times.length > 100) { console.error(`--stills: ${times.length} кадров — больше 100, сузь диапазон или увеличь шаг`); process.exit(2); }
  for (const [i, t] of times.entries()) fs.writeFileSync(path.join(dir, `s${String(i).padStart(2, '0')}.png`), await grab(t, 1));
  const cols = Math.min(4, times.length), rows = Math.ceil(times.length / cols);
  ff(['-i', path.join(dir, 's%02d.png'), '-vf', `scale=480:-1,tile=${cols}x${rows}`, '-frames:v', '1', path.join(dir, 'sheet.png')]);
  console.log(`кадры: ${dir}\nлист:  ${path.join(dir, 'sheet.png')}\n(порядок: ${times.join(', ')})`);
} else {
  fs.mkdirSync(WORK, { recursive: true });
  const out = path.resolve(args.out ?? path.join(ROOT, SLUG + '.mp4'));
  // ffmpeg пишет во временные файлы; готовый mp4 появляется только после успешного конца
  const partial = path.join(WORK, `${SLUG}.partial.mp4`), video = path.join(WORK, `${SLUG}.video.mp4`);
  const newPage = async () => openPage(await (await browser.newContext({ viewport: { width: V.width, height: V.height } })).newPage());
  const hasAudio = await page.evaluate(() => typeof window.audioWav === 'function');
  const wav = path.join(WORK, 'audio.wav'), t0 = Date.now();
  // Звук синтезируется в своей вкладке одновременно с кадрами (на насыщенной партитуре это десятки секунд),
  // видео кодируется без звука, дорожка подмешивается в конце без перекодирования видео.
  const audioDone = hasAudio && (async () => {
    const ap = await newPage(), raw = path.join(WORK, 'audio.raw.wav'), ta = Date.now();
    fs.writeFileSync(raw, Buffer.from(await ap.evaluate(() => window.audioWav()), 'base64'));
    normalize(raw, wav, args.lufs ?? -16); await ap.context().close();
    return (Date.now() - ta) / 1000;
  })();
  // постер: лучший «устоявшийся» кадр, он же кадр №0 — платформы берут превью с начала ролика
  let poster = null;
  if (args.poster !== undefined) {
    poster = await grab(Number(args.poster), sub);
    fs.writeFileSync(path.join(WORK, 'poster.png'), poster);
    ff(['-i', path.join(WORK, 'poster.png'), '-q:v', '2', out.replace(/\.mp4$/i, '.jpg')]);
  }
  const enc = spawn(FF, ['-y', '-loglevel', 'error', '-f', 'image2pipe', '-framerate', String(V.fps), '-i', '-',
    '-c:v', 'libx264', '-preset', 'slow', '-crf', String(args.crf ?? 16), '-pix_fmt', 'yuv420p', '-f', 'mp4', video], { stdio: ['pipe', 'inherit', 'inherit'] });
  // Параллельный рендер: кадр — чистая функция t, поэтому кадры раздаются N вкладкам в отдельных контекстах
  // (у каждого свой процесс отрисовки), а в ffmpeg уходят строго по порядку. Замер (GAME HAND, 32 потока CPU):
  // 4 вкладки — ×2,1–2,5 к отрисовке, 6 — не быстрее 4. Порядок кадров точный; на видеокарте отдельный кадр
  // изредка отличается от однопоточного на неразличимую величину (PSNR 49–54 dB). Нужна побитовая повторяемость — --workers 1.
  const NW = Math.max(1, Math.floor(Number(args.workers ?? Math.min(4, Math.max(1, Math.floor(os.cpus().length / 4))))));
  const pages = [page];
  for (let i = 1; i < NW; i++) pages.push(await newPage());
  const total = Math.round(V.duration * V.fps), tF = Date.now();
  const ready = new Map(), AHEAD = 4 * NW;   // не больше AHEAD готовых кадров в памяти сверх записанного
  let next = 0, written = 0, flushing = false, tDraw = 0;
  async function flush() {
    if (flushing) return; flushing = true;
    while (ready.has(written)) {
      const buf = ready.get(written); ready.delete(written); written++;
      if (!enc.stdin.write(buf)) await once(enc.stdin, 'drain');
      if (written % V.fps === 0 && written < total) { const el = (Date.now() - t0) / 1000; console.log(`${written / V.fps}/${V.duration} c видео · ${el.toFixed(0)} c · осталось ~${(el / written * (total - written)).toFixed(0)} c`); }
    }
    flushing = false;
  }
  await Promise.all(pages.map(async p => {
    for (let f = next++; f < total; f = next++) {
      while (f - written > AHEAD) await new Promise(r => setTimeout(r, 5));
      const tg = Date.now(); ready.set(f, f === 0 && poster ? poster : await grab(f / V.fps, sub, p)); tDraw += Date.now() - tg;
      await flush();
    }
  }));
  while (written < total) { await flush(); await new Promise(r => setTimeout(r, 5)); }
  enc.stdin.end(); await once(enc, 'close');
  if (enc.exitCode !== 0) { console.error('ffmpeg завершился с ошибкой'); process.exit(1); }
  const frames = (Date.now() - tF) / 1000, tAudio = hasAudio ? await audioDone : 0;
  const mux = ff(['-i', video, ...(hasAudio ? ['-i', wav, '-map', '0:v', '-map', '1:a', '-c:a', 'aac', '-b:a', '256k', '-shortest'] : []), '-c:v', 'copy', '-movflags', '+faststart', partial]);
  if (mux.status !== 0) { console.error('ffmpeg: сведение звука не удалось'); process.exit(1); }
  fs.renameSync(partial, out); fs.rmSync(video, { force: true });
  const all = (Date.now() - t0) / 1000;
  console.log(`время: кадры ${frames.toFixed(0)} c (${(frames * 1000 / total).toFixed(0)} мс/кадр; одна вкладка рисует кадр за ${(tDraw / total).toFixed(0)} мс, вкладок ${NW}, sub=${sub}) · звук параллельно ${tAudio.toFixed(0)} c`);
  console.log(`готово: ${out} (${all.toFixed(0)} c, sub=${sub}${dom ? ', DOM-режим без motion blur' : ''})`);
}
await browser.close(); server.close();

// Один рендер ролика за раз: второй запуск на тот же ролик падает сразу, а не пишет в тот же файл.
function lock(file) {
  try { fs.writeFileSync(file, String(process.pid), { flag: 'wx' }); }
  catch (e) {
    if (e.code !== 'EEXIST') throw e;
    const pid = Number(fs.readFileSync(file, 'utf8'));
    let alive = true; try { process.kill(pid, 0); } catch { alive = false; }
    if (alive) { console.error(`уже идёт рендер этого ролика (PID ${pid}). Дождись его или останови процесс; lock: ${file}`); process.exit(3); }
    fs.writeFileSync(file, String(process.pid));          // lock от упавшего процесса
  }
  process.on('exit', () => { try { if (fs.readFileSync(file, 'utf8') === String(process.pid)) fs.unlinkSync(file); } catch {} });
}

// Громкость к цели (EBU R128, два прохода loudnorm). linear=true — сдвиг уровня без компрессии;
// если иначе пик вылезет за −1,5 dBTP, loudnorm сам переходит в динамический режим (сжатие).
function normalize(src, dst, target) {
  if (target === 'off') return fs.copyFileSync(src, dst);
  const I = Number(target), base = `I=${I}:TP=-1.5:LRA=20`;   // TP с запасом: AAC добавляет ~0,5 dB к пику; LRA=20 — чтобы не сжимать динамику микса
  const ff = (a, o) => spawnSync(FF, ['-y', '-hide_banner', '-nostats', '-loglevel', 'info', ...a], o);   // отчёт loudnorm печатается на уровне info
  const m = JSON.parse(ff(['-i', src, '-af', `loudnorm=${base}:print_format=json`, '-f', 'null', '-'], { encoding: 'utf8' }).stderr.match(/\{[\s\S]*\}/)[0]);
  const r = ff(['-i', src, '-af', `loudnorm=${base}:linear=true:measured_I=${m.input_i}:measured_TP=${m.input_tp}:measured_LRA=${m.input_lra}:measured_thresh=${m.input_thresh}:offset=${m.target_offset}:print_format=json`,
    '-ar', '48000', '-c:a', 'pcm_s16le', dst], { encoding: 'utf8' });
  const o = JSON.parse(r.stderr.match(/\{[\s\S]*\}/)[0]);
  console.log(`звук: ${m.input_i} → ${o.output_i} LUFS, пик ${o.output_tp} dBTP (${o.normalization_type})`);
}
