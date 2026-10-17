// Ставит или проверяет общий набор инструментов для видео (идемпотентно):
// node-библиотеки, свой Chromium, ffmpeg, шрифты, звуки CC0.
//   node setup.mjs          — доставить недостающее
//   node setup.mjs --check  — только проверить; код возврата 1, если чего-то нет
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { TK } from './_toolkit.mjs';

const DEPS = {
  'playwright-core': '1.64.1', d3: '7.9.0', three: '0.186.1', shiki: '4.5.0',
  '@fontsource/inter': '5.3.0', '@fontsource/jetbrains-mono': '5.3.0',
};
const WIN = process.platform === 'win32';
const CHECK = process.argv.includes('--check');
const BROWSERS = path.join(TK, 'browsers');
const FFMPEG = path.join(TK, 'ffmpeg', WIN ? 'ffmpeg.exe' : 'ffmpeg');
const SFX = path.join(TK, 'sfx');
const status = {};

function run(cmd, args, opts = {}) {
  const r = spawnSync(cmd, args, { stdio: 'inherit', ...opts });
  if (r.status !== 0) throw new Error(`${cmd} ${args.join(' ')} → exit ${r.status}`);
}
const readJson = f => JSON.parse(fs.readFileSync(f, 'utf8'));
const installed = n => { try { return readJson(path.join(TK, 'node_modules', n, 'package.json')).version; } catch { return null; } };

// 1. node-библиотеки с точными версиями
status.npm = Object.entries(DEPS).every(([n, v]) => installed(n) === v);
if (!status.npm && !CHECK) {
  fs.mkdirSync(TK, { recursive: true });
  fs.writeFileSync(path.join(TK, 'package.json'), JSON.stringify({ name: 'video-toolkit', private: true, dependencies: DEPS }, null, 2));
  // npm на Windows — это .cmd, без shell его не запустить
  run('npm', ['install', '--no-audit', '--no-fund', '--loglevel=error'], { cwd: TK, shell: WIN });
  status.npm = true;
}

// 2. Свой headless Chromium нужной ревизии. Общий кэш Playwright не используем:
//    его чистят установки Playwright в других проектах.
let shellDir = null;
if (status.npm) {
  const shell = readJson(path.join(TK, 'node_modules/playwright-core/browsers.json')).browsers.find(b => b.name === 'chromium-headless-shell');
  const name = `chromium_headless_shell-${shell.revision}`;
  shellDir = path.join(BROWSERS, name);
  status.browser = fs.existsSync(path.join(shellDir, 'INSTALLATION_COMPLETE'));
  if (!status.browser && !CHECK) {
    const shared = path.join(process.env.LOCALAPPDATA ?? path.join(os.homedir(), '.cache'), 'ms-playwright', name);
    if (fs.existsSync(path.join(shared, 'INSTALLATION_COMPLETE'))) {
      console.log(`Chromium: копирую уже скачанный ${name} из общего кэша`);
      fs.cpSync(shared, shellDir, { recursive: true });
    } else {
      run(process.execPath, [path.join(TK, 'node_modules/playwright-core/cli.js'), 'install', 'chromium-headless-shell'],
        { env: { ...process.env, PLAYWRIGHT_BROWSERS_PATH: BROWSERS } });
    }
    status.browser = fs.existsSync(path.join(shellDir, 'INSTALLATION_COMPLETE'));
  }
}

// 3. ffmpeg с libx264. Windows — релизная сборка gyan.dev точной версии (архив сверяется по SHA-256 из релиза
//    GyanD/codexffmpeg); остальные ОС — статическая сборка из imageio-ffmpeg (venv удаляем после копирования).
const FF_WIN = { version: '9.0.2', sha256: '60f467265b1e312373dbcd92200c2618a74850f98d3d078e94296bb3fa2047ba' };
const hasX264 = () => fs.existsSync(FFMPEG) && spawnSync(FFMPEG, ['-hide_banner', '-encoders']).stdout.toString().includes('libx264');
const ffVersion = () => fs.existsSync(FFMPEG) ? spawnSync(FFMPEG, ['-version']).stdout.toString().match(/ffmpeg version (\S+)/)?.[1] ?? '' : '';
status.ffmpeg = hasX264() && (!WIN || ffVersion().startsWith(FF_WIN.version + '-'));
if (!status.ffmpeg && !CHECK && WIN) {
  const name = `ffmpeg-${FF_WIN.version}-essentials_build`, tmp = path.join(TK, '.ff-tmp'), zip = path.join(tmp, name + '.zip');
  fs.rmSync(tmp, { recursive: true, force: true }); fs.mkdirSync(tmp, { recursive: true });
  const res = await fetch(`https://github.com/GyanD/codexffmpeg/releases/download/${FF_WIN.version}/${name}.zip`);
  if (!res.ok) throw new Error(`ffmpeg: HTTP ${res.status}`);
  const buf = Buffer.from(await res.arrayBuffer()), got = (await import('node:crypto')).createHash('sha256').update(buf).digest('hex');
  if (got !== FF_WIN.sha256) throw new Error(`ffmpeg: SHA-256 не совпал (${got})`);
  fs.writeFileSync(zip, buf);
  // системный bsdtar (Windows 10+) распаковывает zip; просто 'tar' из PATH может оказаться GNU tar из Git Bash — он zip не умеет
  run(path.join(process.env.SystemRoot ?? 'C:\\Windows', 'System32', 'tar.exe'), ['-xf', zip, '-C', tmp]);
  fs.mkdirSync(path.dirname(FFMPEG), { recursive: true });
  fs.copyFileSync(path.join(tmp, name, 'bin', 'ffmpeg.exe'), FFMPEG);
  fs.rmSync(tmp, { recursive: true, force: true });
  status.ffmpeg = hasX264() && ffVersion().startsWith(FF_WIN.version + '-');
}
if (!status.ffmpeg && !CHECK && !WIN) {
  const venv = path.join(TK, '.py-tmp');
  const py = path.join(venv, WIN ? 'Scripts/python.exe' : 'bin/python');
  run(WIN ? 'python' : 'python3', ['-m', 'venv', venv]);
  run(py, ['-m', 'pip', 'install', '-q', '--disable-pip-version-check', 'imageio-ffmpeg']);
  const exe = spawnSync(py, ['-c', 'import imageio_ffmpeg as f;print(f.get_ffmpeg_exe())']).stdout.toString().trim();
  fs.mkdirSync(path.dirname(FFMPEG), { recursive: true });
  fs.copyFileSync(exe, FFMPEG);
  fs.rmSync(venv, { recursive: true, force: true });
  status.ffmpeg = hasX264();
}

// 4. Звуки CC0 (Kenney) — берём из плагина brag, если он установлен. Необязательно.
status.sfx = fs.existsSync(path.join(SFX, 'impact'));
if (!status.sfx && !CHECK) {
  const base = path.join(os.homedir(), '.claude/plugins/cache/brag/brag');
  const ver = fs.existsSync(base) ? fs.readdirSync(base).sort().pop() : null;
  const src = ver && path.join(base, ver, 'skills/brag/assets/sfx');
  if (src && fs.existsSync(src)) { fs.cpSync(src, SFX, { recursive: true }); status.sfx = true; }
  else console.warn('Звуки CC0 не найдены (нужен плагин brag) — видео будут только с синтезированным звуком.');
}

if (status.npm && status.browser && status.ffmpeg) {
  const versions = Object.fromEntries(Object.keys(DEPS).map(n => [n, installed(n)]));
  const ffv = spawnSync(FFMPEG, ['-version']).stdout.toString().split('\n')[0];
  fs.writeFileSync(path.join(TK, 'toolkit.json'), JSON.stringify({ root: TK, browsers: BROWSERS, chromium: shellDir, ffmpeg: FFMPEG, sfx: status.sfx ? SFX : null, versions, ffmpegVersion: ffv }, null, 2));
}
console.log(`Набор инструментов: ${TK}`);
for (const [k, v] of Object.entries(status)) console.log(`  ${v ? 'OK  ' : 'НЕТ '} ${k}`);
process.exit(status.npm && status.browser && status.ffmpeg ? 0 : 1);
