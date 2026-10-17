# Скилл `video-maker` — полная спецификация для переноса

> Документ для Claude Code (облачная сессия). Задача: развернуть **точную копию** скилла `video-maker`
> и его набора инструментов, проверить, что он работает, и дальше делать видео строго по нему.
> Все 15 файлов скилла приложены ниже дословно (раздел «Файлы скилла»), с SHA-256 каждого.
> Ничего не переписывать «по памяти» и не «улучшать»: файлы извлекаются скриптом из этого документа.

Снято с рабочей машины 2026-10-17: Windows 11, Node v24.4.1, скилл в `~/.claude/skills/video-maker/`,
набор инструментов в `~/.video-toolkit/`.

---

## 1. Что нужно сделать (порядок для облачного Claude Code)

1. Сохранить этот документ в репозиторий (например, `docs/video-maker-skill.md`).
2. Создать скрипт извлечения из раздела 8 (`extract-video-maker.mjs`, вне папки скилла) и запустить:
   ```bash
   node extract-video-maker.mjs docs/video-maker-skill.md .claude/skills/video-maker
   ```
   Ожидаемо: 15 строк `OK`, `с несовпавшим sha256: 0`, код возврата 0. Любой `BAD` — файл
   испорчен при передаче документа, не «чинить руками», а сообщить пользователю.
3. Поставить набор инструментов: `node .claude/skills/video-maker/scripts/setup.mjs`
   (≈440 МБ скачивается в `~/.video-toolkit`). Проверка: `setup.mjs --check` → код 0, все строки `OK`
   (строка `sfx` будет `НЕТ`, пока не распакован приложенный `sfx.zip` — см. раздел 6).
4. Дымовой тест (раздел 7): контрольные кадры и рендер `examples/voxel.html`, затем `qa.mjs`.
5. Дальше любой запрос на видео выполнять по `SKILL.md` (он загрузится как скилл автоматически,
   если лежит в `.claude/skills/video-maker/SKILL.md` репозитория).

> ОБРАТИ ВНИМАНИЕ: в облаке домашняя папка не переживает сессию. Скилл кладём **в репозиторий**
> (`.claude/skills/video-maker/` — проектный скилл), а `~/.video-toolkit` ставится заново в каждой
> новой сессии командой из п. 3. Сам набор в репозиторий не коммитить — это ~440 МБ бинарников.

---

## 2. Архитектура

Два слоя, разделённые намеренно:

| Слой | Где | Что | Размер |
|---|---|---|---|
| **Скилл** | `<skill>/` (любая папка) | инструкции (`SKILL.md`, `references/`), скрипты Node (`scripts/`), образцы (`examples/`) | ~190 КБ, текст |
| **Набор инструментов** | `~/.video-toolkit/` (или `$VIDEO_TOOLKIT`) | npm-пакеты, свой Chromium headless shell, ffmpeg, звуки CC0, `toolkit.json` | ~440 МБ |

Скрипты скилла находят набор через `_toolkit.mjs`: `TK = process.env.VIDEO_TOOLKIT ?? ~/.video-toolkit`,
пути к Chromium/ffmpeg читаются из `TK/toolkit.json` (его пишет `setup.mjs` только когда всё установлено).
npm-пакеты импортируются из `TK/node_modules` через `createRequire(TK/package.json)` — у скилла
своего `node_modules` нет.

### Главная идея

Ролик — HTML-страница, где кадр рисуется **чистой функцией времени** `draw(ctx, t)` на Canvas 2D.
Следствия: рендер покадрово детерминирован; motion blur — честное усреднение подкадров; любой кадр
можно отрендерить отдельно (контрольные кадры); кадры можно раздать нескольким вкладкам параллельно;
звук синтезируется офлайн (`OfflineAudioContext`) на тех же отметках времени — синхрон до сэмпла.

### Поток данных рендера (`scripts/render.mjs`)

```
<slug>.html ──► локальный HTTP-сервер 127.0.0.1:<random>
                 /toolkit/…  → ~/.video-toolkit/…          (d3, three, шрифты, sfx)
                 /skill/…    → <skill>/scripts/…           (engine.js, voxel.js)
                 /…          → папка страницы              (data.json, code.json, картинки)
            ──► playwright-core → свой Chromium headless shell
                 (Windows: --use-angle=d3d11 → GPU; иначе / --gl cpu → SwiftShader)
            ──► страница: ?render, ждём window.READY (или READY_ERROR → выход 1)
            ──► window.VIDEO = {width, height, fps, duration[, mode:'dom']}

 КАДРЫ: N вкладок (по умолчанию min(4, cpus/4), --workers N), каждая в своём контексте
        window.frameAt(t, sub) → PNG dataURL   (DOM-режим: window.seek(t) + page.screenshot)
        → упорядочивание (Map, не больше 4·N кадров впереди) → stdin ffmpeg
        ffmpeg -f image2pipe -framerate fps -i - -c:v libx264 -preset slow -crf 16 -pix_fmt yuv420p
        → work/<slug>.video.mp4
 ЗВУК (параллельно, отдельная вкладка): window.audioWav() → renderAudio(OfflineAudioContext 48 кГц стерео)
        → WAV base64 → work/audio.raw.wav → loudnorm (EBU R128, 2 прохода, I=-16, TP=-1.5, LRA=20, linear)
        → work/audio.wav
 СВЕДЕНИЕ: -c:v copy + AAC 256k, -shortest, -movflags +faststart → work/<slug>.partial.mp4
        → rename → <slug>.mp4   (готовый файл появляется только при успехе)
 ПОСТЕР (--poster t): кадр t с motion blur → work/poster.png → <slug>.jpg, и он же кадр №0
 LOCK: work/<slug>.render.lock с PID; второй рендер того же ролика → выход 3
```

### Контракт страницы (между `engine.js` и `render.mjs`)

| Глобал | Кто ставит | Смысл |
|---|---|---|
| `window.VIDEO` | `createVideo` / `createDomVideo` | `{width, height, fps, duration, mode?}` |
| `window.READY` / `window.READY_ERROR` | после `await setup()` | готовность / текст ошибки `setup()` |
| `window.frameAt(t, sub)` | canvas-режим | рисует кадр (sub подкадров в выдержке `shutter=0.5`), возвращает PNG dataURL |
| `window.seek(t)` | DOM-режим | ставит на паузу все `document.getAnimations()`, `currentTime = t·1000`, вызывает пользовательский `seek(t)` |
| `window.audioWav()` | если передан `audio(A)` | WAV (base64) всей звуковой дорожки |

Без `?render` в URL страница показывает превью: кнопка ▶ играет со звуком, `?t=4.5` — один кадр.

### API `engine.js` (ES-модуль, `/skill/engine.js`)

- **Математика:** `TAU, clamp, lerp, P(t,a,b)` (прогресс в окне → 0..1), `E.*` (easing: outCubic, inCubic,
  inOutCubic, outQuart, outExpo, inExpo, inOutExpo, outBack), `rng(seed)` (mulberry32), `hash, hex, mix`.
- **Ритм:** `grid(bpm)` → `{bpm, beat, bar, at(bar, beat), pulse(t, k)}` — все склейки и звуки на долях.
- **Canvas:** `canvas(w,h)`, `text(ctx, s, x, y, font, color, align, spacing)`, `letters()` (раскладка по буквам),
  `wrap()` (перенос по ширине).
- **Пиксельный шрифт 5×7:** `PIXFONT` (латиница, кириллица, цифры, знаки), `pixGlyph, pixWidth, pixText`.
- **Эффекты:** `bloom(ctx, {alpha, contrast, blur, scale})` — в `post()`.
- **Шрифты:** `fonts('inter:400,800,900', 'jetbrains-mono:400')` — из `@fontsource` набора.
- **Звук:** `renderAudio(duration, compose)` — синтезатор с компрессором, ревербом, сайдчейном:
  `A.kick, clap, hat, bass, stab, pad, whoosh, riser, boom, crash, blip, bell, tick, tone, noise, groove, out,
  load, sample, mf`; `wavBase64(buf)`.
- **Режимы:** `createVideo({width, height, fps, duration, shutter, background, setup, draw, post, audio})`,
  `createDomVideo({width, height, fps, duration, setup, seek, audio})`.

### API `voxel.js` (`/skill/voxel.js`, импортирует `engine.js`)

3D-математика (`add, sub, dot, cross, norm, scale, lerp3, mv, mm, rotX/Y/Z, axAng, around`), камера
(`orbit, keys, shake, proj, toCam, horizonY, NEAR`), модели (`box, model, textModel`), хореография
(`seedFlight, seedBurst, assemble, explode, emit, cull`), отрисовка (`palette, drawCubes, floorGrid,
floorGlow, quadAt, segment`). Подробно — `references/story.md`, каркас — `examples/voxel.html`.

---

## 3. Библиотеки и инструменты (точные версии)

| Компонент | Версия | Откуда ставится | Зачем |
|---|---|---|---|
| Node.js | на источнике v24.4.1; нужен ≥ 18 (глобальный `fetch`, top-level await, ESM) | система | все скрипты скилла |
| `playwright-core` | **1.64.1** | npm → `TK/node_modules` | управление Chromium из `render.mjs` |
| Chromium headless shell | ревизия **1248** (определяется `playwright-core/browsers.json`) | `playwright-core/cli.js install chromium-headless-shell` с `PLAYWRIGHT_BROWSERS_PATH=TK/browsers` | отрисовка кадров, WebGL, WebAudio офлайн |
| `d3` | **7.9.0** | npm | шкалы и форматы для графиков (`/toolkit/node_modules/d3/dist/d3.min.js`) |
| `three` | **0.186.1** | npm | 3D с мешами (`/toolkit/node_modules/three/build/three.module.js`) |
| `shiki` | **4.5.0** | npm | подсветка кода токенами (`highlight.mjs`) |
| `@fontsource/inter` | **5.3.0** | npm | шрифт Inter (с кириллицей) |
| `@fontsource/jetbrains-mono` | **5.3.0** | npm | моноширинный JetBrains Mono |
| ffmpeg (Windows) | **9.0.2** essentials (gyan.dev), SHA-256 архива зашит в `setup.mjs` | GitHub-релиз `GyanD/codexffmpeg` | кодирование libx264, loudnorm, листы кадров, волна |
| ffmpeg (Linux/macOS) | версия не зафиксирована — статическая сборка из `imageio-ffmpeg` (pip) | временный venv `TK/.py-tmp`, удаляется | то же; проверяется только наличие `libx264` |
| Звуки CC0 Kenney | 262 файла, ~3 МБ (`impact/ ui/ interface/ keyboard/ casino/` + `sfx-analysis.md/json`) | копируются из плагина `brag` (`~/.claude/plugins/cache/brag/brag/<ver>/skills/brag/assets/sfx`), если он есть | `A.load('ui/click1.ogg')` в партитуре |

Фактический размер набора на источнике: `node_modules` 57 МБ, `browsers` 282 МБ, `ffmpeg` 101 МБ, `sfx` 3 МБ.

Общий кэш Playwright (`~/.cache/ms-playwright` / `%LOCALAPPDATA%\ms-playwright`) **намеренно не используется**
для хранения: его чистят установки Playwright в других проектах. Если нужная ревизия там уже есть —
`setup.mjs` её копирует, а не качает заново.

Структура набора после установки:

```
~/.video-toolkit/
  package.json          # {name: video-toolkit, private, dependencies: точные версии выше}
  node_modules/
  browsers/chromium_headless_shell-1248/   (+ служебные папки Playwright)
  ffmpeg/ffmpeg(.exe)
  sfx/<категории>/*.ogg|wav, sfx-analysis.md, sfx-analysis.json
  toolkit.json          # {root, browsers, chromium, ffmpeg, sfx|null, versions, ffmpegVersion}
```

---

## 4. Скрипты: команды, флаги, коды возврата

| Команда | Что делает | Коды |
|---|---|---|
| `node <skill>/scripts/setup.mjs [--check]` | ставит/проверяет набор (идемпотентно): npm → Chromium → ffmpeg → sfx → `toolkit.json` | 0 — npm+browser+ffmpeg OK; 1 — чего-то нет |
| `node <skill>/scripts/render.mjs <page.html> --stills 0.5,2.1,21.6-22.5/0.15` | контрольные кадры (≤100, диапазон «от-до/шаг») → `work/stills/sNN.png` + `work/stills/sheet.png` (сетка 4 колонки, 480 px) | 0; 1 — ошибка страницы; 2 — нет набора / нет моментов / >100 кадров |
| `node <skill>/scripts/render.mjs <page.html> [--out x.mp4] [--sub 4] [--crf 16] [--poster t] [--lufs -16\|off] [--workers N] [--gl cpu]` | полный рендер mp4 | 0; 1 — ошибка страницы/ffmpeg; 2 — нет набора; **3 — рендер этого ролика уже идёт** |
| `node <skill>/scripts/qa.mjs <video.mp4> [--frames 16]` | потоки, лист кадров `.sheet.png`, волна `.wave.png`, LUFS и пик (предупреждения вне −22…−12 LUFS, пик > −0,5 dBFS) | 0; 1 — файл не читается; 2 — нет набора |
| `node <skill>/scripts/highlight.mjs <file> [--lang ts] [--theme github-dark] [--out tokens.json]` | токены shiki → `{bg, fg, lines:[[{t, c, s}]]}` | 0; 2 — нет аргумента |

Флаги рендера: `--sub` — подкадры motion blur (в DOM-режиме всегда 1); `--crf 20` — файл меньше для
мессенджера; `--workers 1` — побитово повторяемый файл; `--gl cpu` — принудительно SwiftShader (влияет
только на Windows); `--lufs off` — без нормализации.

Вывод `render.mjs` не обрезать (`| tail`, `| head`): строки `графика:`, `звук:`, `время:` — диагностика.

---

## 5. Как используется (рабочий процесс)

Полные правила — в `SKILL.md` ниже; кратко:

1. `setup.mjs --check`; если не 0 — спросить пользователя и запустить без `--check`.
2. Длительность: не указана → 30 с; ≤ 60 с — без вопросов; > 60 с — предупредить, делать сериями по 30–60 с,
   склейка `ffmpeg -f concat -safe 0 -i list.txt -c copy all.mp4`.
3. Результат — `./video-output/<slug>/` текущей папки, промежуточное — `work/`. `render.mjs` сам кладёт
   `video-output/.gitignore` со строкой `*`; `.gitignore` проекта не трогать.
4. По типу ролика прочитать справочник: проект → `references/project.md`, данные → `references/data.md`,
   мультик/3D/воксели → `references/story.md`, звук — всегда `references/sound.md`; шоурил — `examples/reel.html`.
5. `plan.md`: тон, BPM, раскадровка «сцена → секунды → что движется → что звучит».
6. `<slug>.html` на `engine.js` → контрольные кадры (каждая сцена + середина каждого перехода, быстрые
   события — плотным диапазоном) → смотреть `sheet.png` → исправлять → повторять.
7. Рендер с `--poster`; дольше ~8 минут — в фоне; один рендер ролика за раз (код 3 = ждать).
8. `qa.mjs` → смотреть `.sheet.png` и `.wave.png` (звук модель не слышит, волна — единственная проверка).
9. Выдача: пути к mp4 и постеру, замысел одной фразой, список самостоятельно принятых решений; для промо — `share-copy.txt`.

---

## 6. Отличия облачной среды (Linux) — что проверить

> ОБРАТИ ВНИМАНИЕ: скилл писался и проверялся на Windows. Пункты ниже — ожидаемые места отказа на Linux;
> они выведены из кода `setup.mjs`/`render.mjs`, на Linux **не проверены**.

- **ffmpeg.** На Linux `setup.mjs` вызывает `python3 -m venv` и `pip install imageio-ffmpeg`. Нужны `python3`,
  модуль `venv` (на Debian/Ubuntu — пакет `python3-venv`) и доступ к PyPI. Обходной путь без Python: положить
  любой ffmpeg с `libx264` в `~/.video-toolkit/ffmpeg/ffmpeg` (например, скопировать системный после
  `apt-get install ffmpeg`) — на не-Windows `setup.mjs` проверяет только наличие энкодера `libx264`, версию не сверяет.
- **Chromium headless shell** на Linux требует системные библиотеки (libnss3 и т. п.). Если запуск браузера
  падает с ошибкой о недостающих `.so` — `node ~/.video-toolkit/node_modules/playwright-core/cli.js install-deps chromium-headless-shell`
  (нужен root/apt).
- **Сеть.** Нужен доступ к registry npm, к CDN браузеров Playwright и (для ffmpeg) к PyPI. Если среда
  ограничивает исходящие соединения — установка упадёт на соответствующем шаге; сообщить пользователю, какой домен заблокирован.
- **GPU нет** → рендер через SwiftShader, медленнее. `--use-angle=d3d11` добавляется только на `win32`, на Linux флагов менять не нужно.
- **Звуки CC0 (sfx).** Плагина `brag` в облаке, скорее всего, нет → `setup.mjs` выведет предупреждение,
  `toolkit.json` получит `"sfx": null`. Это не ошибка, но тогда **`A.load(...)` в партитуре использовать нельзя**
  (fetch `/toolkit/sfx/...` вернёт 404 и упадёт генерация звука) — только синтезированные звуки `A.*`.
  **Пользователь прикладывает `sfx.zip`** (262 файла, 1,9 МБ; в корне архива папка `sfx/` с `impact/`, `ui/`,
  `interface/`, `keyboard/`, `casino/`, `sfx-analysis.md`, `sfx-analysis.json`). Сам архив закоммитить в репозиторий
  рядом с этим документом (например, `docs/sfx.zip`), а в каждой новой сессии **после** `setup.mjs` распаковать и перезапустить установку:
  ```bash
  mkdir -p ~/.video-toolkit
  unzip -oq docs/sfx.zip -d ~/.video-toolkit/            # нет unzip → python3 -m zipfile -e docs/sfx.zip ~/.video-toolkit/
  ls ~/.video-toolkit/sfx/impact | head -3                # должны быть .ogg
  node .claude/skills/video-maker/scripts/setup.mjs       # обновит toolkit.json: "sfx" перестанет быть null
  node .claude/skills/video-maker/scripts/setup.mjs --check   # строка sfx → OK
  ```
  Пока `--check` не показывает `OK  sfx`, `A.load(...)` не использовать.
- **Время рендера.** Лимит одной команды — 10 минут; длинные рендеры запускать в фоне.
- **Каждая новая сессия** — снова `setup.mjs` (домашняя папка не сохраняется).

---

## 7. Дымовой тест после установки

```bash
S=.claude/skills/video-maker
node $S/scripts/setup.mjs --check                                   # код 0
mkdir -p video-output/smoke && cp $S/examples/voxel.html video-output/smoke/smoke.html
node $S/scripts/render.mjs video-output/smoke/smoke.html --stills 1,3,5     # → work/stills/sheet.png
node $S/scripts/render.mjs video-output/smoke/smoke.html --workers 2       # → smoke.mp4, строки графика/звук/время
node $S/scripts/qa.mjs video-output/smoke/smoke.mp4                         # → .sheet.png, .wave.png, ≈ −16 LUFS
```

Открыть `sheet.png` и `.wave.png` через Read и убедиться, что кадры не пустые и звук есть.
`examples/voxel.html` работает на `engine.js` + `voxel.js` и не использует sfx — подходит для проверки без звуков CC0.
Длительность примера и допустимые моменты для `--stills` — смотреть в `createVideo({... duration ...})` файла.

---

## 8. Скрипт извлечения файлов

Сохранить как `extract-video-maker.mjs` (вне папки скилла) и запустить, как в разделе 1.
Он находит в этом документе блоки, помеченные `<!-- FILE: путь sha256=… -->`, пишет их в целевую папку
и сверяет SHA-256 (концы строк в документе приводятся к LF; каждый файл записывается с одним завершающим `\n`).

```js
// node extract-video-maker.mjs <video-maker-skill.md> <папка скилла>
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';

const [md, dest] = process.argv.slice(2);
if (!md || !dest) { console.error('usage: node extract-video-maker.mjs <file.md> <skill-dir>'); process.exit(2); }
const src = fs.readFileSync(md, 'utf8').replace(/\r\n/g, '\n');
const re = /<!-- FILE: (\S+) sha256=([0-9a-f]{64}) -->\n`{5}[^\n]*\n([\s\S]*?)`{5}\n/g;
let n = 0, bad = 0;
for (const [, rel, sha, body] of src.matchAll(re)) {
  const got = crypto.createHash('sha256').update(body, 'utf8').digest('hex');
  const f = path.join(dest, rel);
  fs.mkdirSync(path.dirname(f), { recursive: true });
  fs.writeFileSync(f, body);
  console.log(`${got === sha ? 'OK ' : 'BAD'} ${rel}`);
  n++; if (got !== sha) bad++;
}
console.log(`файлов: ${n}, с несовпавшим sha256: ${bad}`);
process.exit(n === 15 && bad === 0 ? 0 : 1);
```

---

## 9. Известные расхождения в исходниках (копировать как есть)

- `SKILL.md` (раздел 0) пишет «ffmpeg 7.1» и «~450 МБ», а `setup.mjs` фиксирует ffmpeg **9.0.2** на Windows;
  фактический размер набора на источнике ≈ 440 МБ. Источник истины — `setup.mjs`.
- `SKILL.md` ссылается на плагин `brag` как источник звуков; в облаке его, скорее всего, нет (раздел 6).

---

## 10. Файлы скилла

Дерево:

```
video-maker/
  SKILL.md
  references/  project.md  data.md  story.md  sound.md
  scripts/     _toolkit.mjs  setup.mjs  render.mjs  qa.mjs  highlight.mjs  engine.js  voxel.js
  examples/    voxel.html  reel.html  dino.html
```

| Файл | Роль |
|---|---|
| `SKILL.md` | точка входа скилла: frontmatter (`name`, `description` — триггеры), процесс, контракт страницы, правила качества, тоны, подводные камни |
| `references/project.md` | видео о проекте/продукте: материал, DOM-режим/скриншоты, код на экране |
| `references/data.md` | графики и показатели: данные только из источника, d3-шкалы, рисование на canvas |
| `references/story.md` | мультики, персонажи, 3D, воксели: API `voxel.js`, порядок слоёв |
| `references/sound.md` | партитура `audio(A)`, драматургия звука, каталог sfx, проверка громкости |
| `scripts/_toolkit.mjs` | пути (`TK`, `SKILL_DIR`), чтение `toolkit.json`, импорт пакетов из набора, разбор аргументов |
| `scripts/setup.mjs` | установка/проверка набора с точными версиями |
| `scripts/render.mjs` | HTTP-сервер + Chromium + параллельные кадры + звук + ffmpeg; контрольные кадры |
| `scripts/qa.mjs` | проверка готового mp4: лист кадров, волна, LUFS |
| `scripts/highlight.mjs` | подсветка кода shiki → JSON |
| `scripts/engine.js` | движок страницы-ролика (браузерный ES-модуль) |
| `scripts/voxel.js` | воксельный 3D на Canvas 2D |
| `examples/voxel.html` | каркас воксельного ролика на `engine.js` + `voxel.js` |
| `examples/reel.html` | шоурил — образец уровня (самостоятельный, написан до `engine.js`) |
| `examples/dino.html` | воксельный мультик (самостоятельный, до `engine.js`) |


### `SKILL.md`

<!-- FILE: SKILL.md sha256=12f667c8f455f8c6a7d299550fa996ad6da5c64d04da2f73e35974c945970fc0 -->
`````markdown
---
name: video-maker
description: Создание видео любого типа кодом и рендер в mp4 со своей музыкой — моушн-графика и шоурилы, видео о проекте или продукте из ИТ (промо, демо, launch video, обзор фичи), анимированные графики и показатели (data viz, дашборд в движении), мультики и истории для детей, 3D, пиксель-арт и стиль Minecraft. Работает в любой папке через общий набор инструментов ~/.video-toolkit (Chromium, ffmpeg, d3, three.js, shiki, шрифты, звуки CC0). Используй ВСЕГДА, когда просят сделать, смонтировать или анимировать видео, ролик, клип, мультик, анимацию, промо, демо-ролик, «make a video», «launch video», «turn this into a video», «video about this project». Это главный скилл для видео — используй его вместо brag и brag-slim, даже если фраза похожа на их триггеры.
---

# Video Maker

Ролик — это HTML-страница, где каждый кадр рисуется **чистой функцией времени** `draw(ctx, t)`.
Отсюда всё остальное: рендер покадрово точный и повторяемый, motion blur честный
(усреднение подкадров), любой кадр можно отрендерить отдельно для проверки, звук
синтезируется офлайн на тех же отметках времени — синхрон до сэмпла.

`<skill>` ниже — папка этого скилла (Claude Code печатает её как «Base directory for this skill»).

## 0. Набор инструментов

```bash
node "<skill>/scripts/setup.mjs" --check
```

Код 0 — всё на месте. Иначе спроси пользователя и запусти без `--check`: один раз
скачается ~450 МБ в `~/.video-toolkit` (свой Chromium, ffmpeg 7.1, d3, three.js, shiki,
шрифты Inter и JetBrains Mono; звуки Kenney CC0 копируются из плагина brag, если он есть).
Версии зафиксированы в `setup.mjs`. Общий кэш Playwright не используется намеренно:
его чистят установки Playwright в других проектах.

## 1. Длительность

| Запрос | Что делать |
|---|---|
| не указана | **30 с** |
| до 60 с | делать без вопросов |
| больше 60 с | **предупредить** до начала работы: каждая секунда — ручная хореография, время рендера растёт линейно, огрехи легче пропустить. Предложить серии по 30–60 с (каждая — своя страница, проверка, рендер), потом склейка. Если пользователь настаивает — делать сериями всё равно |

Время рендера (замер в один поток): простые сцены 30 fps — секунды на секунду видео; тяжёлые
(тысячи объектов, 60 fps, motion blur ×5) — до 12 мин на минуту. Теперь `render.mjs` рисует кадры
в нескольких вкладках (по умолчанию до 4, `--workers N`) и синтезирует звук параллельно с кадрами:
общее время в 1,7–2 раза меньше (GAME HAND, 20 с видео: 88–109 → 54–59 с). Рендер дольше ~8 минут
запускай в фоне (`run_in_background`), лимит обычной команды — 10 минут.
На ноутбуке время одного и того же рендера плавает в 2–3 раза (замер: 134–370 мс/кадр на одном
коде; простой счётный цикл в Node скачет так же) — это управление питанием системы, не код
ролика. Не оптимизируй ролик по одному медленному прогону; при сомнении сравни строку `время:`.

Склейка серий: `ffmpeg -f concat -safe 0 -i list.txt -c copy all.mp4` (ffmpeg — путь из `~/.video-toolkit/toolkit.json`).

## 2. Процесс

Результат кладётся в `./video-output/<slug>/` текущей папки; промежуточное — в `work/` внутри.
`render.mjs` сам кладёт в `video-output/` файл `.gitignore` со строкой `*`, поэтому папка не видна
в `git status` проекта. `.gitignore` проекта не трогай.

1. **Тип ролика → прочитай справочник.** Видео о проекте/продукте — `references/project.md`.
   Графики и показатели — `references/data.md`. Мультик, персонажи, 3D, воксели —
   `references/story.md` (воксели — на `scripts/voxel.js`, каркас — `examples/voxel.html`).
   Звук — всегда `references/sound.md`. Шоурил/моушн — `examples/reel.html` как образец уровня.
2. **Материал.** Ответь себе: что это (одно предложение), для кого, чем отличается, какой
   крючок в первые 2 секунды, какая финальная мысль. Для проекта — читай код, для данных —
   бери цифры только у пользователя или из источника, не придумывай.
3. **План** — `plan.md`: тон, темп (BPM), раскадровка «сцена → секунды → что движется →
   что звучит», сумма = длительность. Решения, которые пользователь не задал (сюжет,
   финал, тексты на экране), принимай сам и перечисли их в итоговом сообщении.
4. **Сборка** — `<slug>.html` на движке (см. «Контракт страницы»).
5. **Контрольные кадры** — `node "<skill>/scripts/render.mjs" <slug>.html --stills 0.5,2.1,…`:
   кадр из каждой сцены **и из середины каждого перехода**. Открой `work/stills/sheet.png`
   через Read, исправь наложения, вылезший текст, низкий контраст. Повтори.
   **Быстрые события — плотно:** пролёт камеры сквозь объект, портал, взрыв, склейки короче
   доли — диапазоном `--stills 21.6-22.5/0.15` (от-до/шаг), особенно последние 0,5 с перед
   проходом сквозь объект: там всплывают ошибки порядка слоёв. Лист QA после рендера берёт
   16 равномерных кадров и такие места пропускает, а найденная после рендера ошибка стоит
   ещё одного полного рендера.
6. **Рендер** — `node "<skill>/scripts/render.mjs" <slug>.html --poster <t>` (`--poster` —
   лучший устоявшийся кадр: он сохраняется в `.jpg` и становится кадром №0, чтобы превью
   на любой платформе было красивым). Звук автоматически приводится к −16 LUFS, пик −1,5 dBTP
   (`--lufs -14` — другая цель, `--lufs off` — без нормализации). ffmpeg пишет в
   `work/<slug>.partial.mp4`, готовый mp4 появляется только после успешного конца.
   **Один рендер ролика за раз:** второй запуск падает с кодом 3 («уже идёт рендер»). Фоновый
   рендер завершён, только когда пришло системное уведомление о задаче — не текст из твоего
   же сообщения. Код 3 — жди уведомления, а не запускай заново.
   Вывод рендера не обрезай (`| tail`, `| head`): он короткий, а в нём строки `графика:` (GPU или
   SwiftShader), `звук:` (итог нормализации) и `время:` (звук · кадры · ожидание ffmpeg) — по ним
   разбирают медленный рендер и громкость. На Windows рендер идёт на видеокарте (ANGLE d3d11);
   `--gl cpu` — принудительно SwiftShader, если GPU даёт артефакты. `--workers 1` — один поток:
   побитово повторяемый файл (в параллельном режиме на GPU отдельный кадр изредка отличается
   на неразличимую величину; порядок кадров всегда точный).
7. **QA** — `node "<skill>/scripts/qa.mjs" <slug>.mp4`: открой `.sheet.png` и `.wave.png`,
   громкость уже выставлена рендером (≈ −16 LUFS; пик ≈ −1 dBFS: нормализация ставит −1,5 dBTP,
   AAC добавляет ~0,5 dB; тревога — выше −0,5); смотри по волне баланс во
   времени: провалы, слишком тихое вступление, бочка, съевшая всё. Звук ты не слышишь — волна
   единственная проверка, отнесись к ней серьёзно.
8. **Выдача:** путь к mp4 и постеру, одна фраза о замысле, список принятых тобой решений,
   предложение переделать сцену. Для промо проекта — ещё `share-copy.txt` (1–3 предложения
   для поста, конкретно, без «рад поделиться»).

## 3. Контракт страницы

Страница открывается через локальный сервер `render.mjs`: `/toolkit/…` — общий набор,
`/skill/…` — `scripts/` скилла, остальное — папка страницы. Поэтому работают ES-модули,
шрифты и `fetch` локальных файлов (по `file://` — нет).

```html
<script src="/toolkit/node_modules/d3/dist/d3.min.js"></script>   <!-- если нужны графики -->
<script type="module">
import { createVideo, fonts, grid, text, letters, pixText, bloom, P, E, lerp, rng } from '/skill/engine.js';
import { orbit, keys, textModel, seedFlight, assemble, emit, drawCubes, palette } from '/skill/voxel.js'; // воксели
import * as THREE from '/toolkit/node_modules/three/build/three.module.js'; // если нужно 3D с мешами
const g = grid(128);                       // доли: g.beat, g.bar, g.at(bar, beat), g.pulse(t)
createVideo({
  width: 1920, height: 1080, fps: 30, duration: 30, background: '#0b0d12',
  async setup() { await fonts('inter:400,800,900', 'jetbrains-mono:400'); /* загрузка данных, картинок */ },
  draw(ctx, t) { /* весь кадр из t */ },
  post(ctx, t) { bloom(ctx, { alpha: 0.4 }); /* вспышки, виньетка, HUD — поверх, без размытия */ },
  async audio(A) { A.groove(g, [1, 2, 3]); A.riser(g.at(3), g.at(4)); /* см. sound.md */ },
});
</script>
```

- **Canvas-режим** (`createVideo`) — по умолчанию: motion blur `--sub 4`, полный контроль.
- **DOM-режим** (`createDomVideo({…, seek(t)})`) — когда нужен настоящий HTML/CSS проекта:
  CSS-анимации и Web Animations ставятся на паузу и перематываются на `t` автоматически,
  `seek(t)` выставляет остальное. Без motion blur.
- **Воксели** (кубики, Minecraft-стиль, сборка/разлёт): `voxel.js` — камера в перспективе,
  модели и объёмные надписи, сборка и взрыв, отрисовка со скрытием внутренних граней.
  Справочник — `references/story.md`, рабочий каркас — `examples/voxel.html`.
- **Пиксельные надписи:** `pixText(ctx, s, x, y, px, color, {align, shadow})` — шрифт 5×7,
  латиница, кириллица, цифры, знаки (`PIXFONT`). Для HUD, счёта, «GAME OVER», ретро-титров.
- **Свечение:** `bloom(ctx, {alpha, contrast, blur})` в `post` — неон и CRT; на ярких
  полноэкранных сценах `alpha` ≈ 0,2, иначе пересвет.
- **three.js:** рендерь в свой WebGL-canvas и копируй в кадр `ctx.drawImage(renderer.domElement, …)`
  сразу после `renderer.render` — на видеокарте, без неё — через SwiftShader (медленнее в разы).
- **Код на экране:** `node "<skill>/scripts/highlight.mjs" file.ts --out code.json` → токены
  с цветами темы VS Code; рисуй построчно (`examples` в `references/project.md`).
- **Примеры** `examples/reel.html` (шоурил) и `examples/dino.html` (воксельный мультик) —
  самостоятельные страницы со своим синтезатором, написаны до `engine.js`. Бери из них
  приёмы и уровень; новый ролик собирай на `engine.js`. `examples/voxel.html` — на
  `engine.js` + `voxel.js`, с него начинай воксельный ролик.
- **Просмотр:** страницу можно открыть в браузере через тот же сервер — кнопка ▶ играет
  со звуком, `?t=4.5` показывает один кадр.

## 4. Правила качества

- **Чистая функция времени.** Никаких `Date.now()`, `Math.random()`, накопленного состояния
  между кадрами — только `t` и `rng(seed)`. Иначе кадры при рендере и при проверке разойдутся.
- **Всё на сетке долей.** Склейки, удары, появление текста — на долях `grid(bpm)`; звук ставится
  на те же времена. Так картинка и музыка совпадают без подгонки.
- **Крючок в первые 2 секунды.** Планируй его первым.
- **Читаемость.** Текст, который нужно прочитать, стоит неподвижно ≈0,3 с на слово, считая
  с момента, когда строка появилась целиком. Текст-фактура — не обязан.
- **Показывай настоящее.** Для проекта — его UI, код, тексты, цвета, а не общий «SaaS».
  Придуманных цифр, отзывов и заявлений не бывает.
- **Живое.** Появление по одному, нажатия, печать, сборка из частей лучше статичных слайдов.
  Переход двух насыщенных сцен через прямой наплыв даёт кашу — сначала уводи старое, потом вводи новое,
  или проходи через фон / шторку / вспышку.
- **Любой стоп-кадр годится для поста.**
- **Не трогай чужое.** Видео — в `video-output/`; исходники проекта не меняются.

## 5. Тоны

| Тон | Ощущение | Темп и переходы |
|---|---|---|
| `default` | бодро, чисто, с улыбкой | 4–6 сцен на 30 с, мягкие переходы |
| `polished` | серьёзно, сдержанно | 3–4 сцены, длинные паузы, мягкие затемнения |
| `chaotic` | БЫСТРО, ГРОМКО | 8+ сцен, есть короче 1 с, вспышки и зумы |
| `cinematic` | трейлер, крупная типографика | 4–5 сцен, драматичные шторки |
| `showreel` | «смотри, что умею» | смена стиля каждый такт, эффекты (`examples/reel.html`) |
| `kids` | ярко, смешно, понятно без слов | персонаж, звукоподражания, сквош-и-стретч (`examples/dino.html`) |
| `data` | ясно и честно | одна мысль на сцену, цифры успевают прочитаться |

Свободное описание пользователя («как заставка новостей 90-х») важнее пресета.

## 6. Подводные камни

- `render.mjs` ждёт `window.READY`; ошибка в `setup()` выводится и прерывает рендер.
- Шрифт не догрузился → в кадре системный. Всегда `await fonts(…)` в `setup`.
- `ctx.letterSpacing`, `ctx.filter`, `roundRect` есть в этом Chromium — пользуйся.
- Огромный `ctx.filter: blur()` на полном кадре × подкадры = медленно. Размывай уменьшенную копию.
- 60 fps удваивает время рендера; нужен для коротких резких роликов, для остального хватит 30.
- `--crf 16` по умолчанию; зерно и шум раздувают файл — для отправки в мессенджер `--crf 20`.
- **Слои в 3D-сцене.** Кубики сортируются по глубине внутри одного `drawCubes`. Всё, что
  рисуется «между» (экран в отверстии, свечение пола, портал), требует отдельных проходов:
  дальние кубики → вставка → ближние. Если вставка рисуется до всех кубиков, дальние кубики лягут поверх неё.
- **Экран внутри экрана** (игра в отверстии, монитор в сцене) рисуй на маленьком canvas
  (480×270) с `imageSmoothingEnabled = false` и растягивай — честные пиксели и дёшево.
`````

### `references/project.md`

<!-- FILE: references/project.md sha256=3f6d01293abc2d80e8dcada2a3b6cc36a91c7c471e94575bda43f036f4db3fff -->
`````markdown
# Видео о проекте или продукте (ИТ)

Промо, демо фичи, обзор архитектуры, «что мы сделали за спринт». Задача — чтобы человек,
который о проекте не слышал, после одного просмотра понял: что это, для кого, что делает.

## Материал

- **Код и README:** что проект делает, ключевой сценарий «вход → главное действие → результат».
- **Айдентика:** точные цвета и шрифты из CSS/темы проекта. Нет своих — Inter + JetBrains Mono.
- **Настоящий интерфейс.** Если проект — веб-приложение или сайт:
  - DOM-режим: подключи его реальные стили/компоненты в страницу-ролик и анимируй их;
  - или сними скриншоты Playwright из набора (Chromium `~/.video-toolkit`) в `work/` и
    анимируй их в canvas (панорама, зум на нужный элемент, курсор, клики).
- **Нет UI** (CLI, библиотека, бэкенд, CI): показывай то, что видит пользователь — терминал
  с печатающейся командой и выводом, код, диаграмму потока данных, цифры до/после.
- **Код на экране** — через shiki:
  ```bash
  node "<skill>/scripts/highlight.mjs" src/checker.ts --out video-output/x/code.json --theme github-dark
  ```
  ```js
  // в setup: CODE = await (await fetch('code.json')).json();
  CODE.lines.forEach((line, i) => { let x = 220;
    for (const tok of line) { ctx.font = '400 34px "JetBrains Mono"'; ctx.fillStyle = tok.c; ctx.textAlign = 'left';
      ctx.fillText(tok.t, x, 340 + i * 56); x += ctx.measureText(tok.t).width; } });
  ```
  Показывай 5–12 строк, не файл целиком. Печать по символам — `tok.t.slice(0, n)` от `t`.

## Структура (30 с)

Крючок (2–3 с) → в чём проблема или что это (3–5 с) → 2–3 ключевые возможности на
настоящем интерфейсе или коде (по 5–7 с) → итог/призыв (3–4 с: название, ссылка/репозиторий).

## Правила

- Только настоящие тексты, функции и цифры проекта. Маленькие иллюстративные детали
  в UI допустимы (имя файла, тост «Сохранено»), придуманные метрики и отзывы — нет.
- Никаких общих фраз вроде «оптимизируйте рабочий процесс».
- Курсор и клики — заметные: курсор крупнее системного, клик = вспышка-кольцо + `A.sample` из `ui/`.
- Терминал: моноширинный шрифт, приглашение, печать со звуком `keyboard/`, вывод появляется блоком.
- В конце — `share-copy.txt`.
`````

### `references/data.md`

<!-- FILE: references/data.md sha256=b529a1fa3f2d8891af46fbcd081ce2c908be63fef819d73c56b41c0ae2571e1f -->
`````markdown
# Графики и показатели

Метрики, отчёт, рост, сравнение, дашборд в движении. Задача — одна ясная мысль на сцену,
и каждая цифра успевает прочитаться.

## Данные

- Цифры — только от пользователя или из указанного источника (CSV, JSON, отчёт). Не
  округляй «для красоты» и не достраивай пропуски. Нет данных — спроси.
- Положи данные в `video-output/<slug>/data.json` и грузи в `setup()` через `fetch`.

## d3 — шкалы и форматы (рисование — своё, на canvas)

```html
<script src="/toolkit/node_modules/d3/dist/d3.min.js"></script>
```
```js
const x = d3.scaleBand().domain(rows.map(r => r.month)).range([200, 1720]).padding(0.25);
const y = d3.scaleLinear().domain([0, d3.max(rows, r => r.value)]).nice().range([900, 260]);
const ru = d3.formatLocale({ decimal: ',', thousands: ' ', grouping: [3], currency: ['', ' ₽'] });
const money = ru.format('$,.0f'), pct = ru.format('+.0%');
const line = d3.line().x(r => x(r.month) + x.bandwidth() / 2).y(r => y(r.value)).curve(d3.curveMonotoneX).context(ctx);
ctx.beginPath(); line(rows); ctx.stroke();       // d3-shape рисует прямо в canvas
```
`d3.interpolate`, `d3.scaleTime`, `d3.pie`, `d3.arc().context(ctx)`, `d3.stack` — всё работает так же.

## Приёмы

- **Рост столбиков** — `E.outBack(P(t, t0 + i*0.06, t0 + i*0.06 + 0.5))`, столбики по очереди.
- **Линия рисуется** — считай длину пути, рисуй до доли `P(t, a, b)`; точка-«голова» с кольцом.
- **Счётчик** — значение = `target * E.outCubic(P(t, a, b))`, формат тот же, что в итоге;
  моноширинные цифры (`font-variant-numeric` недоступен в canvas → JetBrains Mono или
  выравнивание по правому краю), чтобы число не «дрожало» по ширине.
- **Акцент** — один цвет на главную величину, остальное нейтральное.
- **Сравнение до/после** — одно и то же построение, меняются только данные (`d3.interpolate`).
- **Подписи осей** — минимум; лучше подпись прямо у значения.

## Читаемость

- Итоговое число стоит неподвижно минимум 1,5 с.
- Не больше одной анимированной величины в фокусе одновременно.
- Контраст текста к фону ≥ 4.5:1; на светлом фоне — тёмные цифры, а не цветные.
- Шкала от нуля для столбиков; если нет — явно пометить.
`````

### `references/story.md`

<!-- FILE: references/story.md sha256=4e58cdb93fc5db5f3735e0231aac35d1b1cacabb9516f454410c2bf1cd012c32 -->
`````markdown
# Мультики, персонажи, 3D, воксели

Детские ролики, истории с героем, пиксель-арт, Minecraft-стиль, 3D-сцены.
Новый воксельный ролик — на `scripts/voxel.js`, каркас — `examples/voxel.html` (слово из
кубиков, портал в букве, разлёт, ~120 строк). `examples/dino.html` (динозавр из 843 кубиков,
текстуры 16×16, ортографическая камера) написан до движка — бери из него хореографию и юмор.

## voxel.js — что есть и как собирать кадр

| Шаг | Функции |
|---|---|
| Модель | `box(map, x0,x1, y0,y1, z0,z1, mat)` → `model(map, shift)` — список кубиков с соседями. `textModel('ИГРА', {x0, y0, px: 2, depth: 3, gap: 3, mat})` — модель на букву из `PIXFONT`, у каждой `.ch`, `.center`, `.x0` |
| Хореография | `seedFlight(L, {start, end, order, curve, from})` — расписание сборки (посадка ≤ `end`). `seedBurst(L, {center, speed, up, spin})` — параметры разлёта |
| Кадр модели | `emit(L, out, v => …)`: верни `assemble(v, t, home, G)` или `[explode(v, tau, home), false]`. `home` — дом с групповым преобразованием (`around(v.h, pivot, M, move)`), `G` — матрица группы |
| Камера | `orbit({yaw, pitch, dist, tg, f, shake})`, ключи `keys([[t, yaw°, pitch°, dist, tg], …], t)`, тряска `shake(t, [[время, px]])` |
| Отрисовка | `palette({мат: '#hex'})` один раз; `drawCubes(ctx, items, cam, PAL)`; фон — `horizonY`, `floorGrid`, `floorGlow`; `quadAt` — экранный четырёхугольник отверстия; `segment` — линия с отсечением |

- **Предмет кадра** — `{c, M, m, fl, j, cull}`: центр, матрица 3×3 (поворот × масштаб), материал,
  вспышка (0..1, при посадке 0,85 → 0), сдвиг яркости, маска скрытых граней. Свои предметы
  (декоративные кубики) — с `cull: 0`.
- **Скрытые грани.** `emit`/`cull` прячут грань между соседями, только когда оба «на месте»
  (`rigid`). Вся группа должна двигаться одной матрицей (поворот руки, поп буквы) — тогда соседи
  остаются соседями и скрытие работает; кубик в полёте рисует все грани.
- **Порядок слоёв:** фон → дальний `drawCubes` → вставки (экран в отверстии, свечение) →
  ближний `drawCubes` → 2D-оверлеи (кольца, искры, надписи). Сортировка — только внутри одного вызова.
- **Пролёт сквозь объект** (нырок в букву, в окно): камера с `tg` в центре отверстия и
  `dist` от 60 до −6 (`dist < 0` — камера прошла насквозь, смотрит туда же). Ускорение `60 − 66u³`
  даёт проход ровно перед долей. Содержимое «внутри» рисуй в `quadAt(...)` передней
  плоскости, пока камера перед ней, затем задней, а когда камера за задней — на весь кадр без
  сцены. Кубики ближе `NEAR` пропускаются сами. Выход — та же кривая назад; yaw/pitch меняй,
  только когда камера уже отлетела на ~10 единиц, иначе она пройдёт сквозь стенку.
- **Не пускай кубики в камеру:** смещения старта (`from`) — с боков и сзади (z ≤ +10), иначе
  у объектива пролетают огромные грани.
- **Перспектива:** `f = 1800` на 1920 px ≈ 56° по горизонтали. Видимая полуширина на
  расстоянии d — `d · 960 / f`; от неё подбирай `dist`, чтобы сцена влезла.
- **Скорость** (замер, «GAME HAND»): ~1600 кубиков + мини-игры, 30 fps, sub 4, 60 с —
  ~110–120 мс на кадр в один поток, 3,5 мин рендера; видеокарта на Canvas 2D дала ~12 %,
  4 вкладки (по умолчанию) — ещё ×2. Сцене тяжелее в разы — `--sub 2` или three.js.

## Как в dino.html устроен воксельный рендер (без three.js)

- Модель — `Map("x,y,z" → материал)`, строится коробками `boxInto(...)`.
- Ортографическая камера: грань куба проецируется в параллелограмм, поэтому текстура 16×16
  ложится точно через `ctx.setTransform(U/16, V/16, origin)` + `drawImage` с
  `imageSmoothingEnabled = false` — настоящий пиксельный вид.
- Оттенки граней предрассчитаны (12 уровней) — одна отрисовка на грань.
- Порядок: сначала земля, потом все кубы по глубине центра (painter's algorithm).
- Скрытые грани между соседями не рисуются, но только когда оба куба «на месте» —
  во время полёта/взрыва рисуется всё.
- Сегменты (хвост, голова) вращаются вокруг своих осей — грани между сегментами не прячутся.
- ~3000 кубов × 5 подкадров укладываются в ~2,5 мин рендера на 15 с при 60 fps.

## Когда нужен three.js

Перспектива, свет и тени, сложные меши, модели. Рендери в свой WebGL-canvas и копируй
в кадр через `ctx.drawImage(renderer.domElement, …)`. Время `t` → позиции/повороты
напрямую (без `clock`, без накопления). На Windows рендер идёт на видеокарте (строка `графика:`
в логе); без неё — SwiftShader, медленнее в разы: держи разрешение WebGL-canvas не выше нужного.
Тысячи одинаковых кубов в three.js — `InstancedMesh` (одна команда на все).

## Анимация персонажей

- **Сборка из частей:** каждой части — время старта по порядку (снизу вверх, от тела к
  глазам), стартовая точка со смещением, поворот-кувырок, который гасится к 0; финальная
  деталь (глаза) — отдельным акцентом со звуком.
- **Сквош-и-стретч:** при ударе `sy = 1 − 0.3·e^{−7τ}·cos(26τ)`, `sx = sz = 1 + 0.5·(1 − sy)`
  — пружинит и сохраняет объём.
- **Жизнь в покое:** дыхание, покачивание хвостом `sin(t·5)`, кивок головой на бочку `g.pulse(t)`.
- **Подготовка перед действием:** замах назад перед ударом (0,1 с), отдача после.
- **Реакция:** зажмуриться, звёздочки над головой, осколки-частицы, вспышка, тряска камеры.
- **Звукоподражания:** пиксельные надписи («БАМ!», «БУМ!») — `pixText` с `shadow`
  (выдавливание), вылет с `outBack`, наклон через `ctx.rotate`.

## Для детей

- Понятно без слов: действие → реакция → смешной итог.
- Яркие, но не кислотные цвета; персонаж контрастирует с фоном.
- «Разрушение» — мультяшное: развалился на кубики и собрался обратно.
- Крупные надписи, коротко, на русском, если пользователь пишет по-русски.
- Финальный кадр с названием/героем держится ≥ 0,8 с.
`````

### `references/sound.md`

<!-- FILE: references/sound.md sha256=06dcfdd1ef5b2b229d4a03cd3ad3603b089cf7d983e0721c169030078419cecf -->
`````markdown
# Звук

Музыка и эффекты — одна вещь: эффекты в той же тональности и в том же «пространстве»
(общий ревербератор), тише музыки, без резких пиков. Повторяющиеся мелкие звуки — фоном.
Без озвучки: голоса диктора в наборе нет намеренно.

## Партитура `audio(A)`

`audio` вызывается внутри `OfflineAudioContext`; всё ставится на абсолютное время в секундах.
Бери времена из той же сетки `grid(bpm)`, что и анимацию.

| Вызов | Что это |
|---|---|
| `A.groove(g, [1,2,3], {chords, roots, stabs, shift})` | готовый грув 4/4 на указанных тактах: бочка, хлопки на 2 и 4, хэты, бас, аккорды. По умолчанию Am–F–C–G (MIDI). Каждый вызов начинает прогрессию с первого аккорда; секции, продолжающей прогрессию, дай `shift` |
| `A.tone(t, f, d, v, {type, f1, lp, vib, pan, send, duck})` | нота любой формы волны с ползущей высотой `f → f1` и вибрато: чиптюн-лид, арпеджио, «прыжок» (330→880), «монетка» (988 → 1319), «лазер» (1600→260), «пауэр-ап» (быстрое арпеджио вверх) |
| `A.noise(t, d, v, {type, f, f1, q, pan, send})` | шумовой удар с ползущим фильтром: взрыв (lowpass 2500→150), помехи (bandpass 3000) |
| `A.out(node, {pan, send, duck})` | подключить свой узел к миксу; `duck: true` — «приседает» под бочку |
| `A.kick(t, v)` | бочка; заодно «приседает» бас и пэды (sidechain) |
| `A.clap / A.hat(t, open, v, pan)` | хлопок / хэт |
| `A.bass(t, freq, dur, v)` | бас (пила + саб) |
| `A.stab(t, midiNotes, v)` / `A.pad(t0, t1, midiNotes, v)` | аккорд-удар / подложка |
| `A.whoosh(t0, t1, f0, f1, v, pan0, pan1)` | свист перехода (полосовой шум со сдвигом частоты) |
| `A.riser(t0, t1, v)` | нарастание в «дроп» |
| `A.boom(t, v, d)` / `A.crash(t, v, d)` | низкий удар / тарелка |
| `A.blip(t, freq, v, type, d, pan)` / `A.bell(t, freq, v)` / `A.tick(t, v)` | писк интерфейса / колокольчик / щелчок |
| `A.mf(midi)` | номер ноты MIDI → частота |
| `await A.load('ui/click1.ogg')` + `A.sample(buf, t, {v, pan, rate})` | сэмпл из `~/.video-toolkit/sfx` |
| `A.ac`, `A.bus`, `A.verb`, `A.rand` | для своих синтезаторов: `ac` — контекст, `bus` — общий выход, `verb` — посыл в ревербератор |

Свой звук (рык, «бонк», чириканье) — собирай из осцилляторов и шума так же, как функции
в `scripts/engine.js`; примеры — `roar`, `bonk`, `tweet`, `place` в `examples/dino.html`.
Выход — через `A.out`, а не напрямую в `A.bus`: иначе звук минует сайдчейн.
Перед `exponentialRampToValueAtTime` ставь `setValueAtTime(значение, t)` — без начальной точки
рампа считается от нуля секунд и частота «ползёт» с начала ролика (в `A.tone`/`A.noise` уже учтено).

**Игровой / чиптюн-стиль:** квадратная волна (`square`) для лида и арпеджио на 16-х
поверх `A.groove`; аккорд арпеджио бери тем же индексом, что и грув (см. `shift`); на каждую
склейку — свой «игровой» звук через `A.tone`, громкость 0,02–0,05.

## Драматургия

- Вступление (первый такт): пэд + низкий удар + нарастание в начало грува.
- Склейки на сильных долях, перед каждой — короткий `whoosh`.
- Кульминация: дробь хлопков 8-е → 16-е → 32-е и `riser` за такт до удара, на ударе
  `kick` + `boom` + `crash` + вспышка в кадре.
- Финал: пэд на последний аккорд, колокольчики на появление заголовка, затухание
  в последние 0,35 с (уже встроено в мастер).

## Звуки CC0 (Kenney) — `~/.video-toolkit/sfx/`

| Папка | Что там |
|---|---|
| `ui/` | клики, переключатели, «вжух» интерфейса — для демонстрации продукта |
| `interface/` | подтверждения, ошибки, открытие/закрытие |
| `impact/` | удары по металлу, дереву, стеклу, шаги — для акцентов |
| `keyboard/` | отдельные нажатия клавиш — для печати кода и текста |
| `casino/` | карты, фишки — для раздачи, подсчёта, «выпадения» |

Описания звуков — `sfx/sfx-analysis.md`. Сэмпл звучит естественнее синтезированного
щелчка, но не перегружай: 5–15 сэмплов на 30 секунд обычно достаточно.

## Проверка

Общую громкость выставляет `render.mjs` (EBU R128: −16 LUFS, пик −1,5 dBTP) — подгонять `v`
ради LUFS не нужно. Если микс заметно тише цели и у него уже высокие пики, нормализация
работает как лимитер (в логе `dynamic`) — это нормально. Твоя задача — баланс во времени:
`qa.mjs` рисует волну, на ней видно тишину, провалы между сценами, слишком тихое вступление
и не съела ли всё бочка. Это правится аранжировкой, а не общим уровнем.
`````

### `scripts/_toolkit.mjs`

<!-- FILE: scripts/_toolkit.mjs sha256=328069e60942513d53427d807025ba82024e5629a83781eed9b10d0b31e4c6fd -->
`````js
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
`````

### `scripts/setup.mjs`

<!-- FILE: scripts/setup.mjs sha256=b03cc316a74951efe753353e891280c15524a5a7f1d86124507e01f7176a74f0 -->
`````js
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
`````

### `scripts/render.mjs`

<!-- FILE: scripts/render.mjs sha256=7692d693470bd99d55dec2e88a2656871a0ff0267cf75c330b222a686f14ec8e -->
`````js
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
`````

### `scripts/qa.mjs`

<!-- FILE: scripts/qa.mjs sha256=abb1832a77e344e50b071ecc753acfd532b9df4cda0a85ede3458fb92f89ae8c -->
`````js
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
`````

### `scripts/highlight.mjs`

<!-- FILE: scripts/highlight.mjs sha256=3e39810342869aa635e876f3b4438630574f0646b9fd0fe25af77449c0c3d785 -->
`````js
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
`````

### `scripts/engine.js`

<!-- FILE: scripts/engine.js sha256=93aa4fe005bdc124231dfe276dbaeed90cc9b70e7f423aa994d42ad063af29f2 -->
`````js
// Движок для страницы-ролика (ES-модуль, подключается как /skill/engine.js).
// Кадр — чистая функция времени: draw(ctx, t) не хранит состояние между кадрами,
// поэтому рендер детерминирован, motion blur честный, а любой кадр можно отрендерить отдельно.

/* ---------------------------------------------------------------- math */
export const TAU = Math.PI * 2;
export const clamp = (x, a = 0, b = 1) => x < a ? a : x > b ? b : x;
export const lerp = (a, b, t) => a + (b - a) * t;
export const P = (t, a, b) => clamp((t - a) / (b - a));             // прогресс t в окне [a, b] → 0..1
export const E = {
  outCubic: t => 1 - (1 - t) ** 3, inCubic: t => t * t * t, inOutCubic: t => t < .5 ? 4 * t * t * t : 1 - (-2 * t + 2) ** 3 / 2,
  outQuart: t => 1 - (1 - t) ** 4,
  outExpo: t => t >= 1 ? 1 : 1 - 2 ** (-10 * t), inExpo: t => t <= 0 ? 0 : 2 ** (10 * t - 10),
  inOutExpo: t => t <= 0 ? 0 : t >= 1 ? 1 : t < .5 ? 2 ** (20 * t - 10) / 2 : (2 - 2 ** (-20 * t + 10)) / 2,
  outBack: t => { const c1 = 1.70158, c3 = c1 + 1; return 1 + c3 * (t - 1) ** 3 + c1 * (t - 1) ** 2; },
};
export function rng(a) { return () => { a |= 0; a = a + 0x6D2B79F5 | 0; let t = Math.imul(a ^ a >>> 15, 1 | a); t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t; return ((t ^ t >>> 14) >>> 0) / 4294967296; }; }
export const hash = (x, y = 0, s = 0) => { const v = Math.sin(x * 127.1 + y * 311.7 + s * 74.7) * 43758.5453; return v - Math.floor(v); };
export const hex = h => [1, 3, 5].map(i => parseInt(h.slice(i, i + 2), 16));
export const mix = (a, b, t) => { const A = hex(a), B = hex(b); return `rgb(${A.map((v, i) => Math.round(lerp(v, B[i], t))).join(',')})`; };
// Сетка долей: всё анимируемое и все звуки ставятся на доли, поэтому монтаж и музыка совпадают.
export function grid(bpm) { const beat = 60 / bpm; return { bpm, beat, bar: beat * 4, at: (bar, b = 0) => bar * beat * 4 + b * beat, pulse: (t, k = 12) => Math.exp(-(((t % beat) + beat) % beat) * k) }; }

/* ---------------------------------------------------------------- canvas helpers */
export function canvas(w, h) { const c = document.createElement('canvas'); c.width = w; c.height = h; return c; }
export function text(ctx, s, x, y, font, color, align = 'center', spacing = 0) {
  ctx.font = font; ctx.fillStyle = color; ctx.textAlign = align; ctx.textBaseline = 'alphabetic'; ctx.letterSpacing = spacing + 'px';
  ctx.fillText(s, x, y); ctx.letterSpacing = '0px';
}
// Раскладка по буквам для побуквенной анимации: [{ch, x, w}], x — от центра строки.
const LAYOUT = new Map();
export function letters(ctx, s, font, spacing = 0) {
  const k = s + '|' + font + '|' + spacing; if (LAYOUT.has(k)) return LAYOUT.get(k);
  ctx.save(); ctx.font = font; const ws = [...s].map(c => ctx.measureText(c).width); ctx.restore();
  const total = ws.reduce((a, b) => a + b, 0) + spacing * (ws.length - 1); let x = -total / 2;
  const L = [...s].map((ch, i) => { const o = { ch, x, w: ws[i] }; x += ws[i] + spacing; return o; }); L.width = total;
  LAYOUT.set(k, L); return L;
}
// Перенос текста по ширине — для подписей, которые нужно прочитать.
export function wrap(ctx, s, font, maxW) {
  ctx.save(); ctx.font = font; const out = []; let line = '';
  for (const w of s.split(/\s+/)) { const t = line ? line + ' ' + w : w; if (ctx.measureText(t).width > maxW && line) { out.push(line); line = w; } else line = t; }
  if (line) out.push(line); ctx.restore(); return out;
}

/* ---------------------------------------------------------------- pixel font 5x7 (латиница, кириллица, цифры) */
// Пиксельные надписи для ретро/игрового стиля и для воксельных букв (voxel.js → textModel).
// Глиф — 7 строк по 5 символов, '#' — пиксель. Строчные буквы рисуются прописными.
const G7 = s => s.split('|');
export const PIXFONT = {
  A: G7('.###.|#...#|#...#|#####|#...#|#...#|#...#'), B: G7('####.|#...#|#...#|####.|#...#|#...#|####.'), C: G7('.###.|#...#|#....|#....|#....|#...#|.###.'),
  D: G7('####.|#...#|#...#|#...#|#...#|#...#|####.'), E: G7('#####|#....|#....|####.|#....|#....|#####'), F: G7('#####|#....|#....|####.|#....|#....|#....'),
  G: G7('.###.|#...#|#....|#.###|#...#|#...#|.####'), H: G7('#...#|#...#|#...#|#####|#...#|#...#|#...#'), I: G7('.###.|..#..|..#..|..#..|..#..|..#..|.###.'),
  J: G7('..###|...#.|...#.|...#.|...#.|#..#.|.##..'), K: G7('#...#|#..#.|#.#..|##...|#.#..|#..#.|#...#'), L: G7('#....|#....|#....|#....|#....|#....|#####'),
  M: G7('#...#|##.##|#.#.#|#.#.#|#...#|#...#|#...#'), N: G7('#...#|#...#|##..#|#.#.#|#..##|#...#|#...#'), O: G7('.###.|#...#|#...#|#...#|#...#|#...#|.###.'),
  P: G7('####.|#...#|#...#|####.|#....|#....|#....'), Q: G7('.###.|#...#|#...#|#...#|#.#.#|#..#.|.##.#'), R: G7('####.|#...#|#...#|####.|#.#..|#..#.|#...#'),
  S: G7('.####|#....|#....|.###.|....#|....#|####.'), T: G7('#####|..#..|..#..|..#..|..#..|..#..|..#..'), U: G7('#...#|#...#|#...#|#...#|#...#|#...#|.###.'),
  V: G7('#...#|#...#|#...#|#...#|#...#|.#.#.|..#..'), W: G7('#...#|#...#|#...#|#.#.#|#.#.#|#.#.#|.#.#.'), X: G7('#...#|#...#|.#.#.|..#..|.#.#.|#...#|#...#'),
  Y: G7('#...#|#...#|.#.#.|..#..|..#..|..#..|..#..'), Z: G7('#####|....#|...#.|..#..|.#...|#....|#####'),
  Б: G7('#####|#....|#....|####.|#...#|#...#|####.'), Г: G7('#####|#....|#....|#....|#....|#....|#....'), Д: G7('.###.|.#.#.|.#.#.|.#.#.|.#.#.|#####|#...#'),
  Ё: G7('.#.#.|#####|#....|####.|#....|#....|#####'), Ж: G7('#.#.#|#.#.#|.###.|..#..|.###.|#.#.#|#.#.#'), З: G7('.###.|#...#|....#|..##.|....#|#...#|.###.'),
  И: G7('#...#|#...#|#..##|#.#.#|##..#|#...#|#...#'), Й: G7('.#.#.|#...#|#..##|#.#.#|##..#|#...#|#...#'), Л: G7('..###|.#..#|.#..#|.#..#|.#..#|.#..#|#...#'),
  П: G7('#####|#...#|#...#|#...#|#...#|#...#|#...#'), У: G7('#...#|#...#|#...#|.####|....#|#...#|.###.'), Ф: G7('..#..|.###.|#.#.#|#.#.#|#.#.#|.###.|..#..'),
  Ц: G7('#..#.|#..#.|#..#.|#..#.|#..#.|#####|....#'), Ч: G7('#...#|#...#|#...#|.####|....#|....#|....#'), Ш: G7('#.#.#|#.#.#|#.#.#|#.#.#|#.#.#|#.#.#|#####'),
  Щ: G7('#.#.#|#.#.#|#.#.#|#.#.#|#.#.#|#####|....#'), Ъ: G7('##...|.#...|.#...|.###.|.#..#|.#..#|.###.'), Ы: G7('#...#|#...#|#...#|###.#|#.#.#|#.#.#|###.#'),
  Ь: G7('#....|#....|#....|####.|#...#|#...#|####.'), Э: G7('.###.|#...#|....#|..###|....#|#...#|.###.'), Ю: G7('#..#.|#.#.#|#.#.#|###.#|#.#.#|#.#.#|#..#.'),
  Я: G7('.####|#...#|#...#|.####|..#.#|.#..#|#...#'),
  0: G7('.###.|#...#|#..##|#.#.#|##..#|#...#|.###.'), 1: G7('..#..|.##..|..#..|..#..|..#..|..#..|.###.'), 2: G7('.###.|#...#|....#|...#.|..#..|.#...|#####'),
  3: G7('####.|....#|....#|.###.|....#|....#|####.'), 4: G7('...#.|..##.|.#.#.|#..#.|#####|...#.|...#.'), 5: G7('#####|#....|####.|....#|....#|#...#|.###.'),
  6: G7('.###.|#....|#....|####.|#...#|#...#|.###.'), 7: G7('#####|....#|...#.|..#..|.#...|.#...|.#...'), 8: G7('.###.|#...#|#...#|.###.|#...#|#...#|.###.'),
  9: G7('.###.|#...#|#...#|.####|....#|....#|.###.'),
  '.': G7('.....|.....|.....|.....|.....|.....|..#..'), ',': G7('.....|.....|.....|.....|.....|..#..|.#...'), '!': G7('..#..|..#..|..#..|..#..|..#..|.....|..#..'),
  '?': G7('.###.|#...#|....#|...#.|..#..|.....|..#..'), ':': G7('.....|..#..|..#..|.....|..#..|..#..|.....'), '-': G7('.....|.....|.....|#####|.....|.....|.....'),
  '+': G7('.....|..#..|..#..|#####|..#..|..#..|.....'), '=': G7('.....|.....|#####|.....|#####|.....|.....'), '/': G7('....#|....#|...#.|..#..|.#...|#....|#....'),
  '(': G7('...#.|..#..|.#...|.#...|.#...|..#..|...#.'), ')': G7('.#...|..#..|...#.|...#.|...#.|..#..|.#...'), '%': G7('##..#|##..#|...#.|..#..|.#...|#..##|#..##'),
  "'": G7('..#..|..#..|.....|.....|.....|.....|.....'), '"': G7('.#.#.|.#.#.|.....|.....|.....|.....|.....'), '*': G7('.....|#.#.#|.###.|#####|.###.|#.#.#|.....'),
  '<': G7('...#.|..#..|.#...|#....|.#...|..#..|...#.'), '>': G7('.#...|..#..|...#.|....#|...#.|..#..|.#...'), '_': G7('.....|.....|.....|.....|.....|.....|#####'),
};
// Кириллица, совпадающая по начертанию с латиницей (А, В, Е, К, М, Н, О, Р, С, Т, Х).
for (const [cy, la] of Object.entries({ А: 'A', В: 'B', Е: 'E', К: 'K', М: 'M', Н: 'H', О: 'O', Р: 'P', С: 'C', Т: 'T', Х: 'X' })) PIXFONT[cy] = PIXFONT[la];
export const pixGlyph = ch => PIXFONT[ch.toUpperCase()];
export const pixWidth = (s, px) => [...s].length * 6 * px - px;
// Надпись пикселями размера px; shadow — цвет тени со сдвигом ~0,6 px вниз-вправо.
export function pixText(ctx, s, x, y, px, color, { align = 'left', shadow = null } = {}) {
  const w = pixWidth(s, px), x0 = Math.round(align === 'center' ? x - w / 2 : align === 'right' ? x - w : x), y0 = Math.round(y);
  for (const [col, o] of shadow ? [[shadow, Math.max(1, Math.round(px * 0.6))], [color, 0]] : [[color, 0]]) {
    ctx.fillStyle = col;
    [...s].forEach((ch, i) => pixGlyph(ch)?.forEach((row, r) => { for (let k = 0; k < 5; k++) if (row[k] === '#') ctx.fillRect(x0 + i * 6 * px + k * px + o, y0 + r * px + o, px, px); }));
  }
}

/* ---------------------------------------------------------------- bloom */
// Неоновое свечение для post(): уменьшенная копия кадра → contrast (гасит тёмное) → blur → сложение 'lighter'.
// Размывается копия в scale раз меньше — на полном кадре blur медленный. Для ярких полноэкранных сцен alpha ниже (~0.2).
let BLOOM = null;
export function bloom(ctx, { alpha = 0.4, contrast = 1.7, blur = 4, scale = 4 } = {}) {
  if (alpha <= 0) return;
  const W = ctx.canvas.width, H = ctx.canvas.height, w = Math.ceil(W / scale), h = Math.ceil(H / scale);
  if (!BLOOM || BLOOM.canvas.width !== w || BLOOM.canvas.height !== h) BLOOM = canvas(w, h).getContext('2d');
  BLOOM.filter = `contrast(${contrast}) blur(${blur}px)`; BLOOM.clearRect(0, 0, w, h); BLOOM.drawImage(ctx.canvas, 0, 0, w, h); BLOOM.filter = 'none';
  ctx.save(); ctx.setTransform(1, 0, 0, 1, 0, 0); ctx.globalCompositeOperation = 'lighter'; ctx.globalAlpha = alpha; ctx.drawImage(BLOOM.canvas, 0, 0, W, H); ctx.restore();
}

/* ---------------------------------------------------------------- fonts */
// Шрифты из набора инструментов (Inter, JetBrains Mono — с кириллицей). Пример: await fonts('inter:400,700,900', 'jetbrains-mono:400')
const FONT_PKG = { inter: ['@fontsource/inter', 'Inter'], 'jetbrains-mono': ['@fontsource/jetbrains-mono', 'JetBrains Mono'] };
export async function fonts(...specs) {
  for (const spec of specs) {
    const [id, ws] = spec.split(':'), [pkg, family] = FONT_PKG[id];
    for (const w of ws.split(',')) {
      const l = document.createElement('link'); l.rel = 'stylesheet'; l.href = `/toolkit/node_modules/${pkg}/${w}.css`;
      document.head.append(l); await new Promise((ok, no) => { l.onload = ok; l.onerror = () => no(new Error('font css ' + l.href)); });
      await document.fonts.load(`${w} 40px "${family}"`, 'AaЯя09');
    }
  }
}

/* ---------------------------------------------------------------- audio: синтезатор + сэмплы, всё рендерится офлайн */
export async function renderAudio(duration, compose, { sampleRate = 48000, seed = 7 } = {}) {
  const sr = sampleRate, ac = new OfflineAudioContext(2, Math.ceil(sr * duration), sr), R = rng(seed);
  const noise = ac.createBuffer(1, sr * 3, sr); { const d = noise.getChannelData(0); for (let i = 0; i < d.length; i++) d[i] = R() * 2 - 1; }
  const comp = ac.createDynamicsCompressor(); comp.threshold.value = -15; comp.knee.value = 8; comp.ratio.value = 5; comp.attack.value = 0.004; comp.release.value = 0.18;
  const master = ac.createGain(); master.gain.setValueAtTime(0.95, 0); master.gain.setValueAtTime(0.95, Math.max(0, duration - 0.35)); master.gain.linearRampToValueAtTime(0, duration);
  comp.connect(master); master.connect(ac.destination);
  const bus = ac.createGain(); bus.gain.value = 0.75; bus.connect(comp);
  const duck = ac.createGain(); duck.connect(bus);                       // басы/пэды «приседают» под бочку
  const ir = ac.createBuffer(2, sr * 2.4, sr); for (let c = 0; c < 2; c++) { const d = ir.getChannelData(c); for (let i = 0; i < d.length; i++) d[i] = (R() * 2 - 1) * (1 - i / d.length) ** 3.4; }
  const verb = ac.createConvolver(); verb.buffer = ir; const vr = ac.createGain(); vr.gain.value = 0.32; verb.connect(vr); vr.connect(comp);
  const mf = m => 440 * 2 ** ((m - 69) / 12);
  const out = (node, pan = 0, send = 0, dest = bus) => { const p = ac.createStereoPanner(); p.pan.value = pan; node.connect(p); p.connect(dest); if (send) { const s = ac.createGain(); s.gain.value = send; p.connect(s); s.connect(verb); } return p; };
  const env = (g, t, a, peak, d) => { g.gain.setValueAtTime(0, t); g.gain.linearRampToValueAtTime(peak, t + a); g.gain.exponentialRampToValueAtTime(0.0001, t + a + d); };
  const nz = (t, dur) => { const s = ac.createBufferSource(); s.buffer = noise; s.loop = true; s.start(t, R() * 2); s.stop(t + dur); return s; };
  const filt = (type, f, q = 0.7) => { const b = ac.createBiquadFilter(); b.type = type; b.frequency.value = f; b.Q.value = q; return b; };
  const osc = (type, f, t, dur) => { const o = ac.createOscillator(); o.type = type; o.frequency.value = f; o.start(t); o.stop(t + dur); return o; };
  const A = { ac, mf, rand: R, verb, bus };
  A.kick = (t, v = 1) => { const o = osc('sine', 170, t, 0.6); o.frequency.setValueAtTime(170, t); o.frequency.exponentialRampToValueAtTime(48, t + 0.11); o.frequency.exponentialRampToValueAtTime(38, t + 0.45);
    const g = ac.createGain(); env(g, t, 0.002, v, 0.45); o.connect(g); out(g);
    const n = nz(t, 0.03), g2 = ac.createGain(); env(g2, t, 0.001, 0.3 * v, 0.018); n.connect(filt('highpass', 2500)).connect(g2); out(g2);
    duck.gain.setValueAtTime(0.35, t); duck.gain.linearRampToValueAtTime(1, t + 0.22); };
  A.clap = (t, v = 0.5) => { const n = nz(t, 0.3), b = filt('bandpass', 1300, 0.9), g = ac.createGain(); g.gain.setValueAtTime(0, t);
    for (const o of [0, 0.011, 0.022]) { g.gain.setValueAtTime(v, t + o); g.gain.exponentialRampToValueAtTime(0.05, t + o + 0.009); }
    g.gain.setValueAtTime(v * 0.8, t + 0.032); g.gain.exponentialRampToValueAtTime(0.0001, t + 0.25); n.connect(b).connect(g); out(g, 0.05, 0.35); };
  A.hat = (t, open = false, v = 0.06, pan = 0.2) => { const n = nz(t, 0.25), g = ac.createGain(); env(g, t, 0.001, v, open ? 0.16 : 0.035); n.connect(filt('highpass', 7500)).connect(g); out(g, pan, 0.1); };
  A.bass = (t, f, d, v = 0.3) => { const o = osc('sawtooth', f, t, d + 0.1), lp = filt('lowpass', 300, 4), g = ac.createGain();
    lp.frequency.setValueAtTime(2200, t); lp.frequency.exponentialRampToValueAtTime(260, t + d); env(g, t, 0.004, v, d); o.connect(lp).connect(g); out(g, 0, 0, duck);
    const s = osc('sine', f / 2, t, d + 0.1), gs = ac.createGain(); env(gs, t, 0.004, v * 1.1, d); s.connect(gs); out(gs, 0, 0, duck); };
  A.stab = (t, notes, v = 0.06) => notes.forEach((n, i) => [-9, 9].forEach(dt => { const o = osc('sawtooth', mf(n), t, 0.5); o.detune.value = dt;
    const lp = filt('lowpass', 3000, 1.2), g = ac.createGain(); lp.frequency.setValueAtTime(3200, t); lp.frequency.exponentialRampToValueAtTime(700, t + 0.3); env(g, t, 0.003, v, 0.3); o.connect(lp).connect(g); out(g, (i - 1) * 0.45 + dt / 40, 0.4, duck); }));
  A.pad = (t0, t1, notes, v = 0.035) => notes.forEach((n, i) => [-12, 12].forEach(dt => { const o = osc('sawtooth', mf(n), t0, t1 - t0 + 0.6); o.detune.value = dt;
    const lp = filt('lowpass', 1000, 0.6), g = ac.createGain(); g.gain.setValueAtTime(0, t0); g.gain.linearRampToValueAtTime(v, t0 + 0.5); g.gain.setValueAtTime(v, t1); g.gain.linearRampToValueAtTime(0, t1 + 0.5);
    o.connect(lp).connect(g); out(g, (i % 2 ? 0.5 : -0.5) * (dt > 0 ? 1 : -1), 0.6, duck); }));
  A.whoosh = (t0, t1, f0, f1, v = 0.3, p0 = -0.7, p1 = 0.7) => { const n = nz(t0, t1 - t0 + 0.05), b = filt('bandpass', f0, 1.6), g = ac.createGain(), p = ac.createStereoPanner();
    b.frequency.setValueAtTime(f0, t0); b.frequency.exponentialRampToValueAtTime(f1, t1); g.gain.setValueAtTime(0, t0); g.gain.linearRampToValueAtTime(v, t0 + (t1 - t0) * 0.75); g.gain.exponentialRampToValueAtTime(0.0001, t1);
    p.pan.setValueAtTime(p0, t0); p.pan.linearRampToValueAtTime(p1, t1); n.connect(b).connect(g).connect(p); p.connect(bus); const s = ac.createGain(); s.gain.value = 0.3; p.connect(s); s.connect(verb); };
  A.riser = (t0, t1, v = 0.25) => { const n = nz(t0, t1 - t0), hp = filt('highpass', 300, 2), g = ac.createGain(); hp.frequency.setValueAtTime(300, t0); hp.frequency.exponentialRampToValueAtTime(9000, t1);
    g.gain.setValueAtTime(0.0001, t0); g.gain.exponentialRampToValueAtTime(v, t1 - 0.01); g.gain.linearRampToValueAtTime(0, t1); n.connect(hp).connect(g); out(g, 0, 0.3);
    const o = osc('sawtooth', 110, t0, t1 - t0), go = ac.createGain(); o.frequency.setValueAtTime(110, t0); o.frequency.exponentialRampToValueAtTime(880, t1);
    go.gain.setValueAtTime(0.0001, t0); go.gain.exponentialRampToValueAtTime(v * 0.25, t1 - 0.01); go.gain.linearRampToValueAtTime(0, t1); o.connect(filt('lowpass', 2500)).connect(go); out(go, 0, 0.3); };
  A.boom = (t, v = 0.8, d = 1.6) => { const o = osc('sine', 70, t, d + 0.2), g = ac.createGain(); o.frequency.setValueAtTime(70, t); o.frequency.exponentialRampToValueAtTime(28, t + d); env(g, t, 0.005, v, d); o.connect(g); out(g); };
  A.crash = (t, v = 0.3, d = 1.4) => { const n = nz(t, d + 0.1), g = ac.createGain(); env(g, t, 0.002, v, d); n.connect(filt('highpass', 2800)).connect(g); out(g, 0, 0.6); };
  A.blip = (t, f, v = 0.08, type = 'triangle', d = 0.09, pan = 0) => { const o = osc(type, f, t, d + 0.05), g = ac.createGain(); env(g, t, 0.002, v, d); o.connect(g); out(g, pan, 0.3); };
  A.bell = (t, f, v = 0.12) => [[1, 1, 1.8], [2.76, .45, 1], [5.4, .22, .6], [8.93, .1, .3]].forEach(([r, a, d]) => { const o = osc('sine', f * r, t, d + 0.1), g = ac.createGain(); env(g, t, 0.002, v * a, d); o.connect(g); out(g, 0, 0.6); });
  A.tick = (t, v = 0.12) => { const n = nz(t, 0.02), g = ac.createGain(); env(g, t, 0.001, v, 0.012); n.connect(filt('bandpass', 4200, 2)).connect(g); out(g, (R() - .5) * 0.6); };
  // Свои синтезаторы: A.out подключает узел к миксу (duck: true — «приседает» под бочку, как бас и пэды).
  A.out = (node, { pan = 0, send = 0, duck: d = false } = {}) => out(node, pan, send, d ? duck : bus);
  // Нота с ползущей высотой (f → f1) и вибрато: чиптюн-лиды, арпеджио, «прыжок», «монетка», «лазер».
  A.tone = (t, f, d, v = 0.05, { type = 'square', f1, lp = 5000, pan = 0, send = 0.15, vib = 0, duck: dk = false } = {}) => {
    const o = osc(type, f, t, d + 0.05); o.frequency.setValueAtTime(f, t); if (f1) o.frequency.exponentialRampToValueAtTime(f1, t + d);
    if (vib) { const l = osc('sine', 5.5, t, d + 0.05), lg = ac.createGain(); lg.gain.setValueAtTime(0, t); lg.gain.linearRampToValueAtTime(f * vib, t + Math.min(d, 0.25)); l.connect(lg).connect(o.frequency); }
    const g = ac.createGain(); g.gain.setValueAtTime(0, t); g.gain.linearRampToValueAtTime(v, t + 0.004); g.gain.setValueAtTime(v, t + Math.max(0.005, d * 0.7)); g.gain.exponentialRampToValueAtTime(0.0001, t + d);
    o.connect(filt('lowpass', lp)).connect(g); out(g, pan, send, dk ? duck : bus); };
  // Шумовой удар с ползущим фильтром (f → f1): взрыв, помехи, короткий «вжух».
  A.noise = (t, d, v = 0.2, { type = 'lowpass', f = 800, f1, q = 0.8, pan = 0, send = 0.2 } = {}) => {
    const n = nz(t, d + 0.05), b = filt(type, f, q), g = ac.createGain(); b.frequency.setValueAtTime(f, t); if (f1) b.frequency.exponentialRampToValueAtTime(f1, t + d);
    g.gain.setValueAtTime(v, t); g.gain.exponentialRampToValueAtTime(0.0001, t + d); n.connect(b).connect(g); out(g, pan, send); };
  // Типовой грув на 4/4: бочка, хлопки на 2 и 4, хэты, бас на слабые доли, аккорды. bars — номера тактов.
  // Аккорд такта = chords[(i + shift) % len], i — индекс в bars: каждый вызов начинает прогрессию заново,
  // поэтому секцию, которая продолжает прогрессию с середины, вызывай с shift (иначе бас разойдётся с арпеджио).
  A.groove = (g, bars, { chords = [[57, 60, 64], [53, 57, 60], [55, 60, 64], [55, 59, 62]], roots = [45, 41, 48, 43], stabs = true, shift = 0 } = {}) => bars.forEach((b, i) => {
    const t0 = b * g.bar, ch = chords[(i + shift) % chords.length], rt = roots[(i + shift) % roots.length];
    for (let k = 0; k < 4; k++) { A.kick(t0 + k * g.beat); if (k % 2) A.clap(t0 + k * g.beat); A.hat(t0 + (k + 0.5) * g.beat, true, 0.09, 0.25);
      for (const s of [0.25, 0.75]) A.hat(t0 + (k + s) * g.beat, false, 0.045, -0.3);
      A.bass(t0 + (k + 0.5) * g.beat, mf(rt), 0.19); if (k % 2) A.bass(t0 + (k + 0.75) * g.beat, mf(rt + 12), 0.1, 0.2); }
    if (stabs) { const n = ch.map(x => x + 12); A.stab(t0, n); A.stab(t0 + 1.5 * g.beat, n, 0.045); A.stab(t0 + 2.75 * g.beat, n, 0.045); } });
  // Сэмплы из набора (звуки Kenney CC0): await A.load('impact/impactPunch_heavy_001.ogg'); A.sample(buf, t, {v, pan, rate})
  A.load = async name => ac.decodeAudioData(await (await fetch('/toolkit/sfx/' + name)).arrayBuffer());
  A.sample = (buf, t, { v = 0.5, pan = 0, rate = 1, send = 0.2 } = {}) => { const s = ac.createBufferSource(); s.buffer = buf; s.playbackRate.value = rate; const g = ac.createGain(); g.gain.value = v; s.connect(g); out(g, pan, send); s.start(t); };
  await compose(A);
  return ac.startRendering();
}
export function wavBase64(buf) {
  const n = buf.length, sr = buf.sampleRate, ab = new ArrayBuffer(44 + n * 4), dv = new DataView(ab), ws = (o, s) => [...s].forEach((c, i) => dv.setUint8(o + i, c.charCodeAt(0)));
  ws(0, 'RIFF'); dv.setUint32(4, 36 + n * 4, true); ws(8, 'WAVE'); ws(12, 'fmt '); dv.setUint32(16, 16, true); dv.setUint16(20, 1, true); dv.setUint16(22, 2, true);
  dv.setUint32(24, sr, true); dv.setUint32(28, sr * 4, true); dv.setUint16(32, 4, true); dv.setUint16(34, 16, true); ws(36, 'data'); dv.setUint32(40, n * 4, true);
  const L = buf.getChannelData(0), R = buf.getChannelData(1);
  for (let i = 0; i < n; i++) { dv.setInt16(44 + i * 4, clamp(L[i], -1, 1) * 32767, true); dv.setInt16(46 + i * 4, clamp(R[i], -1, 1) * 32767, true); }
  const u8 = new Uint8Array(ab); let s = ''; for (let i = 0; i < u8.length; i += 0x8000) s += String.fromCharCode.apply(null, u8.subarray(i, i + 0x8000)); return btoa(s);
}

/* ---------------------------------------------------------------- video contract */
// Canvas-режим: draw(ctx, t) рисует кадр. render.mjs вызывает window.frameAt(t, sub) и сам собирает mp4.
// audio(A) — необязательная партитура для renderAudio. post(ctx, t) — эффекты поверх кадра после motion blur.
export function createVideo({ width = 1920, height = 1080, fps = 30, duration = 30, shutter = 0.5, setup, draw, post, audio, background = '#000' }) {
  const view = document.querySelector('canvas#video') ?? document.body.appendChild(Object.assign(canvas(width, height), { id: 'video' }));
  view.width = width; view.height = height;
  const out = view.getContext('2d'), acc = canvas(width, height).getContext('2d'), work = canvas(width, height).getContext('2d');
  const renderTo = (ctx, t) => { ctx.setTransform(1, 0, 0, 1, 0, 0); ctx.globalAlpha = 1; ctx.globalCompositeOperation = 'source-over'; ctx.filter = 'none'; ctx.fillStyle = background; ctx.fillRect(0, 0, width, height); ctx.save(); draw(ctx, t); ctx.restore(); };
  function frame(t, sub = 1) {
    if (sub <= 1) renderTo(acc, t);
    else for (let k = 0; k < sub; k++) {
      // подкадры внутри «выдержки» вокруг t, усреднение — честный motion blur
      renderTo(work, clamp(t + ((k + 0.5) / sub - 0.5) * shutter / fps, 0, duration - 1e-4));
      acc.setTransform(1, 0, 0, 1, 0, 0); acc.globalAlpha = 1 / (k + 1); acc.drawImage(work.canvas, 0, 0); acc.globalAlpha = 1;
    }
    out.setTransform(1, 0, 0, 1, 0, 0); out.globalAlpha = 1; out.globalCompositeOperation = 'source-over'; out.drawImage(acc.canvas, 0, 0);
    if (post) { out.save(); post(out, t); out.restore(); }
  }
  window.VIDEO = { width, height, fps, duration };
  window.frameAt = (t, sub) => { frame(t, sub); return view.toDataURL('image/png'); };
  if (audio) window.audioWav = async () => wavBase64(await renderAudio(duration, audio));
  (async () => {
    if (setup) await setup();
    window.READY = true;
    if (location.search.includes('render')) return;
    preview(view, { duration, frame, audio });
  })().catch(e => { console.error(e); window.READY_ERROR = String(e); });
}

// DOM-режим: ролик собран из HTML/CSS (настоящий интерфейс проекта). seek(t) выставляет состояние на момент t;
// CSS-анимации и Web Animations ставятся на паузу и перематываются автоматически. Motion blur в этом режиме нет.
export function createDomVideo({ width = 1920, height = 1080, fps = 30, duration = 30, setup, seek, audio }) {
  window.VIDEO = { width, height, fps, duration, mode: 'dom' };
  window.seek = async t => { for (const a of document.getAnimations()) { a.pause(); a.currentTime = t * 1000; } if (seek) await seek(t); };
  if (audio) window.audioWav = async () => wavBase64(await renderAudio(duration, audio));
  (async () => { if (setup) await setup(); window.READY = true; })().catch(e => { console.error(e); window.READY_ERROR = String(e); });
}

// Просмотр в браузере: кнопка запускает ролик со звуком; ?t=4.5 показывает один кадр.
function preview(view, { duration, frame, audio }) {
  const t0 = Number(new URLSearchParams(location.search).get('t') ?? duration - 0.01);
  frame(Math.min(t0, duration - 0.01));
  const b = document.createElement('button');
  b.textContent = '▶'; b.style.cssText = 'position:fixed;inset:auto 16px 16px auto;font:20px system-ui;padding:8px 18px;cursor:pointer';
  document.body.append(b);
  b.onclick = async () => {
    b.disabled = true; const ac = new AudioContext(); let start;
    if (audio) { const src = ac.createBufferSource(); src.buffer = await renderAudio(duration, audio); src.connect(ac.destination); start = ac.currentTime + 0.05; src.start(start); }
    else start = ac.currentTime;
    const loop = () => { const t = ac.currentTime - start; if (t < duration) { frame(Math.max(0, t)); requestAnimationFrame(loop); } else b.disabled = false; };
    loop();
  };
}
`````

### `scripts/voxel.js`

<!-- FILE: scripts/voxel.js sha256=af20c017e49fd24c202c6c229ded6334bb37322ea2a242facac17663b81936cc -->
`````js
// Воксели в перспективе на Canvas 2D (ES-модуль, /skill/voxel.js). Справочник — references/story.md.
// Модель — список кубиков с соседями; кадр — список «предметов» {c, M, m, fl, j, cull}, собранный из t:
// центр, матрица 3×3 (поворот × масштаб), материал, вспышка 0..1, сдвиг яркости, маска скрытых граней.
import { E, P, lerp, clamp, rng, PIXFONT } from './engine.js';
const { sin, cos, abs, hypot, exp, round, sqrt, PI } = Math;

/* ---------------------------------------------------------------- 3D math */
export const add = (a, b) => [a[0] + b[0], a[1] + b[1], a[2] + b[2]];
export const sub = (a, b) => [a[0] - b[0], a[1] - b[1], a[2] - b[2]];
export const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
export const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
export const norm = a => { const l = hypot(a[0], a[1], a[2]) || 1; return [a[0] / l, a[1] / l, a[2] / l]; };
export const scale = (a, s) => [a[0] * s, a[1] * s, a[2] * s];
export const lerp3 = (a, b, t) => [lerp(a[0], b[0], t), lerp(a[1], b[1], t), lerp(a[2], b[2], t)];
export const I3 = [1, 0, 0, 0, 1, 0, 0, 0, 1];
export const mv = (M, v) => [M[0] * v[0] + M[1] * v[1] + M[2] * v[2], M[3] * v[0] + M[4] * v[1] + M[5] * v[2], M[6] * v[0] + M[7] * v[1] + M[8] * v[2]];
export const mm = (A, B) => { const R = new Array(9); for (let i = 0; i < 3; i++) for (let j = 0; j < 3; j++) R[i * 3 + j] = A[i * 3] * B[j] + A[i * 3 + 1] * B[3 + j] + A[i * 3 + 2] * B[6 + j]; return R; };
export const sc3 = (M, s) => M.map(v => v * s);
export const rotX = a => { const c = cos(a), s = sin(a); return [1, 0, 0, 0, c, -s, 0, s, c]; };
export const rotY = a => { const c = cos(a), s = sin(a); return [c, 0, s, 0, 1, 0, -s, 0, c]; };
export const rotZ = a => { const c = cos(a), s = sin(a); return [c, -s, 0, s, c, 0, 0, 0, 1]; };
export const axAng = (k, a) => { const c = cos(a), s = sin(a), t = 1 - c, [x, y, z] = k; return [t * x * x + c, t * x * y - s * z, t * x * z + s * y, t * x * y + s * z, t * y * y + c, t * y * z - s * x, t * x * z - s * y, t * y * z + s * x, t * z * z + c]; };
export const randAxis = R => norm([R() - .5, R() - .5, R() - .5]);
// Групповое преобразование вокруг опорной точки: home' = pivot + M·(home − pivot) + move
export const around = (p, pivot, M, move = [0, 0, 0]) => add(add(pivot, mv(M, sub(p, pivot))), move);

/* ---------------------------------------------------------------- camera */
export const NEAR = 0.25;   // ближе этого к камере кубик не рисуется: иначе при пролёте сквозь объект проекция выворачивается
// Камера на орбите вокруг tg: yaw/pitch в градусах, dist — расстояние. dist < 0 — камера проходит сквозь tg
// и летит дальше в ту же сторону (так делается нырок в отверстие). f — фокус в пикселях (1800 ≈ 56° по горизонтали на 1920).
export function orbit({ yaw = 0, pitch = 0, dist = 80, tg = [0, 0, 0], f = 1800, width = 1920, height = 1080, shake = [0, 0] }) {
  const y = yaw * PI / 180, p = pitch * PI / 180, dir = [sin(y) * cos(p), sin(p), cos(y) * cos(p)];
  const eye = add(tg, scale(dir, dist)), fwd = scale(dir, -1), right = norm(cross(fwd, [0, 1, 0])), up = cross(right, fwd);
  return { eye, fwd, right, up, f, yaw: y, pitch: p, ox: width / 2 + shake[0], oy: height / 2 + shake[1] };
}
// Ключи [t, yaw, pitch, dist, tg] → {yaw, pitch, dist, tg} на момент t (между ключами — ease).
export function keys(K, t, ease = E.inOutCubic) {
  let i = 0; while (i < K.length - 2 && t >= K[i + 1][0]) i++;
  const a = K[i], b = K[i + 1], u = ease(P(t, a[0], b[0]));
  return { yaw: lerp(a[1], b[1], u), pitch: lerp(a[2], b[2], u), dist: lerp(a[3], b[3], u), tg: lerp3(a[4], b[4], u) };
}
// Тряска от событий [[время, амплитуда px], …], затухание e^(−k·τ). Детерминированная: зерно — номер 1/60 с.
export function shake(t, events, k = 12) {
  let amp = 0; for (const [x, a] of events) if (t >= x) amp += a * exp(-(t - x) * k);
  if (amp < 0.05) return [0, 0]; const R = rng(Math.floor(t * 60) + 3); return [(R() - .5) * 2 * amp, (R() - .5) * 2 * amp];
}
export const toCam = (cam, p) => { const d = sub(p, cam.eye); return [dot(d, cam.right), dot(d, cam.up), dot(d, cam.fwd)]; };
export const projC = (cam, q) => [cam.ox + cam.f * q[0] / q[2], cam.oy - cam.f * q[1] / q[2], q[2]];
// Мир → экран [x, y, глубина] или null, если точка ближе NEAR.
export const proj = (cam, p) => { const q = toCam(cam, p); return q[2] < NEAR ? null : projC(cam, q); };
// Отрезок в текущий path с отсечением по ближней плоскости — для сеток пола, лучей, рамок.
export function segment(ctx, cam, a, b) {
  let A = toCam(cam, a), B = toCam(cam, b);
  if (A[2] < NEAR && B[2] < NEAR) return;
  if (A[2] < NEAR) [A, B] = [B, A];
  if (B[2] < NEAR) { const s = (A[2] - NEAR) / (A[2] - B[2]); B = [lerp(A[0], B[0], s), lerp(A[1], B[1], s), NEAR]; }
  const p = projC(cam, A), q = projC(cam, B); ctx.moveTo(p[0], p[1]); ctx.lineTo(q[0], q[1]);
}
// Экранная высота горизонта для пола на высоте y.
export function horizonY(cam, y) { const ff = norm([cam.fwd[0], 0, cam.fwd[2]]), p = proj(cam, [cam.eye[0] + ff[0] * 1e4, y, cam.eye[2] + ff[2] * 1e4]); return p ? p[1] : cam.oy; }
// Сетка пола (synthwave и т.п.): линии вдоль z и поперёк, поперечные едут к камере со скоростью scroll ед/с.
export function floorGrid(ctx, cam, t, { y = 0, step = 6, x = [-156, 156], z = [-280, 60], scroll = 7, color = 'rgba(255,70,200,0.6)', width = 2 } = {}) {
  ctx.strokeStyle = color; ctx.lineWidth = width; ctx.beginPath();
  for (let xx = x[0]; xx <= x[1]; xx += step) segment(ctx, cam, [xx, y, z[1]], [xx, y, z[0]]);
  const off = ((t * scroll) % step + step) % step; for (let zz = z[0] + off; zz <= z[1]; zz += step) segment(ctx, cam, [x[0], y, zz], [x[1], y, zz]);
  ctx.stroke();
}

/* ---------------------------------------------------------------- models */
const NBD = [[1, 0, 0], [-1, 0, 0], [0, 1, 0], [0, -1, 0], [0, 0, 1], [0, 0, -1]];   // порядок граней: +x −x +y −y +z −z
export const box = (map, x0, x1, y0, y1, z0, z1, mat) => { for (let x = x0; x <= x1; x++) for (let y = y0; y <= y1; y++) for (let z = z0; z <= z1; z++) map.set(`${x},${y},${z}`, typeof mat === 'function' ? mat(x, y, z) : mat); };
// Map "x,y,z" → материал  →  [{g: сетка, h: дом в мире (g + shift), m, nb: индексы соседей}], .rigid — служебный массив.
export function model(map, shift = [0, 0, 0]) {
  const list = [...map].map(([k, m]) => { const g = k.split(',').map(Number); return { g, h: add(g, shift), m }; });
  const idx = new Map(list.map((v, i) => [v.g.join(','), i]));
  list.forEach(v => { v.nb = NBD.map(d => idx.get(`${v.g[0] + d[0]},${v.g[1] + d[1]},${v.g[2] + d[2]}`) ?? -1); });
  list.rigid = new Array(list.length).fill(false);
  return list;
}
// Объёмная надпись из PIXFONT: пиксель = px×px кубиков, глубина depth (по z от −⌊depth/2⌋). Возвращает модель на букву,
// у каждой .ch, .center, .x0 (левый край). mat(ch, k) — материал k-й буквы. Пробел пропускается, но занимает место.
export function textModel(str, { x0 = 0, y0 = 0, px = 2, depth = 3, gap = 3, mat = ch => ch } = {}) {
  const out = []; let x = x0, k = 0;
  for (const ch of str) {
    const gl = PIXFONT[ch.toUpperCase()];
    if (gl) {
      const m = new Map(), zz = -Math.floor(depth / 2), mt = mat(ch, k);
      gl.forEach((row, r) => [...row].forEach((v, c) => { if (v === '#') box(m, x + c * px, x + c * px + px - 1, y0 + (6 - r) * px, y0 + (6 - r) * px + px - 1, zz, zz + depth - 1, mt); }));
      const L = model(m); L.ch = ch; L.x0 = x; L.center = [x + 2.5 * px - 0.5, y0 + 3.5 * px - 0.5, 0]; out.push(L); k++;
    }
    x += 5 * px + gap;
  }
  return out;
}

/* ---------------------------------------------------------------- choreography */
// Расписание сборки: кубики слетаются по порядку order (меньше — раньше) за окно [start, end] (время посадки ≤ end).
// curve > 1 — плотнее в начале. from(R, v) — смещение старта от дома (не направляй к камере: огромные кубики у объектива).
export function seedFlight(L, { seed = 1, start = 0, end = 4, dur = 0.5, order = (v, R) => v.g[1] + R() * 3, curve = 1, from = R => { const a = R() * PI * 2, r = 30 + R() * 30; return [cos(a) * r, (R() - 0.3) * 40, -abs(sin(a)) * r * 0.8 - 5]; }, arc = R => (R() - 0.3) * 8 } = {}) {
  const R = rng(seed), keyd = L.map(v => [v, order(v, R)]).sort((a, b) => a[1] - b[1]).map(a => a[0]);
  keyd.forEach((v, i) => { const q = keyd.length > 1 ? i / (keyd.length - 1) : 0; v.d = dur; v.s = start + (end - dur - start) * q ** curve; });
  L.forEach(v => { v.off = from(R, v); v.arc = arc(R); v.ax = randAxis(R); v.ang = (1 + R()) * PI; v.j = (R() - 0.5) * 0.08; });
  return L;
}
// Параметры разлёта от центра: скорость [min, max], подброс вверх, вращение.
export function seedBurst(L, { seed = 2, center, speed = [12, 28], up = 9, spin = 16, jitter = 3 } = {}) {
  const R = rng(seed), C = center ?? L.center ?? [0, 0, 0];
  L.forEach(v => { const d = norm(add(sub(v.h, C), [(R() - .5) * jitter, (R() - .5) * jitter, (R() - .5) * jitter + 1])), s = lerp(speed[0], speed[1], R()); v.vel = [d[0] * s, d[1] * s + up, d[2] * s]; v.spin = (R() - .5) * spin; v.bax = randAxis(R); });
  return L;
}
// Кубик на момент t при сборке. home — дом с групповым преобразованием, G — матрица группы.
// Возвращает [предмет | null, rigid]: rigid = стоит на месте (между такими соседями грани скрываются).
export function assemble(v, t, home, G = I3) {
  if (t < v.s) return [null, false];
  const e0 = P(t, v.s, v.s + v.d);
  if (e0 < 1) { const e = E.outCubic(e0), c = lerp3(add(v.h, v.off), home, e); c[1] += sin(PI * e) * v.arc; return [{ c, M: mm(G, sc3(axAng(v.ax, v.ang * (1 - e)), lerp(0.25, 1, E.outBack(e0)))), m: v.m, fl: 0, j: v.j }, false]; }
  return [{ c: home, M: G, m: v.m, fl: 0.85 * exp(-(t - v.s - v.d) * 10), j: v.j }, true];
}
// Разлёт через tau секунд после взрыва: баллистика с гравитацией g, кувырок, исчезание в окне fade.
export function explode(v, tau, home, { g = 14, fade = [0.6, 1.7] } = {}) {
  if (tau > fade[1]) return null;
  return { c: [home[0] + v.vel[0] * tau, home[1] + v.vel[1] * tau - g * tau * tau, home[2] + v.vel[2] * tau], M: sc3(axAng(v.bax ?? v.ax, v.spin * tau), 1 - E.inCubic(P(tau, fade[0], fade[1]))), m: v.m, fl: 0.9 * exp(-tau * 7), j: v.j };
}
// st[i] — предмет кубика i или null, rigid[i] — стоит ли он на месте. Ставит маску скрытых граней и складывает в out.
export function cull(L, st, out) {
  L.forEach((v, i) => { const q = st[i]; if (!q) return; let cm = 0; if (L.rigid[i]) for (let f = 0; f < 6; f++) { const n = v.nb[f]; if (n >= 0 && L.rigid[n]) cm |= 1 << f; } q.cull = cm; out.push(q); });
}
// Типовой кадр модели: state(v, i) → [предмет | null, rigid]. Сам ведёт L.rigid и cull.
export function emit(L, out, state) { const st = L.map((v, i) => { const [q, r] = state(v, i); L.rigid[i] = r; return q; }); cull(L, st, out); }

/* ---------------------------------------------------------------- rendering */
// Палитра: материал → levels строк цвета по яркости from + k·step (выше 1 — к белому). Считается один раз.
export function palette(hexByMat, { levels = 26, from = 0.3, step = 0.05 } = {}) {
  const pal = { levels, from, step };
  for (const [k, h] of Object.entries(hexByMat)) {
    const c = [1, 3, 5].map(i => parseInt(h.slice(i, i + 2), 16));
    pal[k] = Array.from({ length: levels }, (_, l) => { const b = from + l * step, o = b <= 1 ? c.map(v => v * b) : c.map(v => v + (255 - v) * Math.min(1, (b - 1) * 1.1)); return `rgb(${o.map(v => round(clamp(v, 0, 255))).join(',')})`; });
  }
  return pal;
}
const CORN = [[-.5, -.5, -.5], [-.5, -.5, .5], [-.5, .5, -.5], [-.5, .5, .5], [.5, -.5, -.5], [.5, -.5, .5], [.5, .5, -.5], [.5, .5, .5]];
const FACEI = [[4, 6, 7, 5], [0, 2, 3, 1], [2, 6, 7, 3], [0, 4, 5, 1], [1, 5, 7, 3], [0, 4, 6, 2]];
// Кубики по глубине (дальние первыми), видимые грани залиты по свету + подсветке со стороны камеры, с обводкой.
// Порядок «по расстоянию до центра» верен для непересекающихся кубов. Всё, что должно лечь между слоями
// (портал в отверстии, свечение пола), рисуй между отдельными вызовами drawCubes: дальний слой → вставка → ближний.
export function drawCubes(ctx, items, cam, PAL, { light = [-0.4, 0.75, 0.55], ambient = 0.32, diffuse = 0.55, rim = 0.22, stroke = 'rgba(8,4,26,.55)', edge = 0.045 } = {}) {
  const L = norm(light), ey = cam.eye, F = cam.fwd, pc = new Array(8);
  for (const q of items) { const d = sub(q.c, ey); q.d = dot(d, d); }
  items.sort((a, b) => b.d - a.d);
  ctx.strokeStyle = stroke; ctx.lineJoin = 'round';
  for (const q of items) {
    const M = q.M, c = q.c; let ok = true;
    for (let i = 0; i < 8; i++) { const k = CORN[i], s = proj(cam, [c[0] + M[0] * k[0] + M[1] * k[1] + M[2] * k[2], c[1] + M[3] * k[0] + M[4] * k[1] + M[5] * k[2], c[2] + M[6] * k[0] + M[7] * k[1] + M[8] * k[2]]); if (!s) { ok = false; break; } pc[i] = s; }
    if (!ok) continue;
    ctx.lineWidth = clamp(cam.f / sqrt(q.d) * edge, 0.4, 3);
    const pal = PAL[q.m];
    for (let f = 0; f < 6; f++) {
      if (q.cull & (1 << f)) continue;
      const a = f >> 1, sg = f & 1 ? -1 : 1; let nx = M[a] * sg, ny = M[3 + a] * sg, nz = M[6 + a] * sg;
      if (nx * (c[0] + nx * 0.5 - ey[0]) + ny * (c[1] + ny * 0.5 - ey[1]) + nz * (c[2] + nz * 0.5 - ey[2]) >= 0) continue;
      const l = hypot(nx, ny, nz) || 1; nx /= l; ny /= l; nz /= l;
      const b = ambient + diffuse * Math.max(0, nx * L[0] + ny * L[1] + nz * L[2]) + rim * Math.max(0, -(nx * F[0] + ny * F[1] + nz * F[2])) + (q.fl || 0) * 0.8 + (q.j || 0);
      const p4 = FACEI[f];
      ctx.beginPath(); ctx.moveTo(pc[p4[0]][0], pc[p4[0]][1]); for (let k = 1; k < 4; k++) ctx.lineTo(pc[p4[k]][0], pc[p4[k]][1]); ctx.closePath();
      ctx.fillStyle = pal[clamp(round((b - PAL.from) / PAL.step), 0, PAL.levels - 1)]; ctx.fill(); ctx.stroke();
    }
  }
}
// Экранный четырёхугольник прямоугольника [x0..x1]×[y0..y1] на плоскости z (отверстие буквы, экран, окно) или null.
export function quadAt(cam, x0, x1, y0, y1, z) { const ps = [[x0, y0, z], [x1, y0, z], [x1, y1, z], [x0, y1, z]].map(p => proj(cam, p)); return ps.every(Boolean) ? ps : null; }
// Свечение на полу под объектом (эллипс по перспективе), режим 'lighter'.
export function floorGlow(ctx, cam, p, rad, color, alpha) {
  if (alpha <= 0.01) return; const s = proj(cam, p); if (!s) return; const r = cam.f * rad / s[2];
  ctx.save(); ctx.translate(s[0], s[1]); ctx.scale(1, 0.28);
  const gr = ctx.createRadialGradient(0, 0, 0, 0, 0, r); gr.addColorStop(0, color); gr.addColorStop(1, 'rgba(0,0,0,0)');
  ctx.globalAlpha = alpha; ctx.globalCompositeOperation = 'lighter'; ctx.fillStyle = gr; ctx.fillRect(-r, -r, 2 * r, 2 * r); ctx.restore();
}
`````

### `examples/voxel.html`

<!-- FILE: examples/voxel.html sha256=181ab9482f2d98f5aece5186c47a1ec77cb52eaa367de10546cead0a4a97d855 -->
`````html
<!doctype html>
<html><head><meta charset="utf-8"><title>voxel</title>
<style>html,body{margin:0;background:#000}canvas{display:block;max-width:100vw;height:auto}</style></head>
<body>
<script type="module">
// Шаблон воксельного ролика на engine.js + voxel.js: слово из кубиков собирается по долям, в отверстии «А»
// загорается экран (порядок слоёв: фон → дальние кубы → портал → слово), слово разлетается, финальная надпись.
import { createVideo, grid, P, E, rng, canvas, pixText, bloom } from '/skill/engine.js';
import { orbit, keys, shake, floorGrid, horizonY, textModel, seedFlight, seedBurst, assemble, explode, emit, drawCubes, palette, quadAt, floorGlow, axAng, sc3, I3 } from '/skill/voxel.js';
const W = 1920, H = 1080, DUR = 8, FY = -8.5;
const g = grid(120), BEAT = g.beat, at = g.at;
const LOCK = [0, 1, 2, 3].map(k => 1 + k * 0.5), BOOM = at(3);   // буквы встают на доли 2–5, взрыв на такт 3

const COLORS = { И: '#FF3B5C', Г: '#FFB000', Р: '#2EE66B', А: '#2FA8FF', amb: '#B05CFF' };
const PAL = palette(COLORS);
const WORD = textModel('ИГРА', { x0: -25, y0: -1, mat: ch => ch });
WORD.forEach((L, k) => { seedFlight(L, { seed: 10 + k, start: LOCK[k] - 1.5, end: LOCK[k], order: (v, R) => R() }); seedBurst(L, { seed: 20 + k }); });
const A = WORD[3], HOLE = { x0: A.x0 + 1.5, x1: A.x0 + 7.5, y0: -1 + 7.5, y1: -1 + 11.5 };
const AMB = Array.from({ length: 30 }, (_, i) => { const R = rng(300 + i); return { p: [(R() * 2 - 1) * 90, -3 + R() * 40, -35 - R() * 80], s: 0.8 + R() * 2, ax: [R() - .5, R() - .5, R() - .5], sp: (R() - .5) * 1.2 }; });
AMB.forEach(a => { const l = Math.hypot(...a.ax); a.ax = a.ax.map(v => v / l); });

const KEYS = [[0, -25, 8, 60, [-2, 5, 0]], [at(3), 8, 6, 72, [0, 5, 0]], [DUR, 14, 9, 70, [0, 5, 0]]];
const camAt = t => orbit({ ...keys(KEYS, t), f: 1800 * (1 + 0.012 * g.pulse(t, 10) * (t >= at(1) && t < at(3))), shake: shake(t, [...LOCK.map(x => [x, 7]), [BOOM, 16]]) });

const SCREEN = canvas(160, 90).getContext('2d');
function screen(t) {   // мини-игра в отверстии: бегущие полосы и надпись
  const c = SCREEN; c.fillStyle = '#0B1030'; c.fillRect(0, 0, 160, 90);
  for (let i = 0; i < 12; i++) { c.fillStyle = ['#FF3B5C', '#FFB000', '#2EE66B', '#2FA8FF'][i % 4]; c.fillRect(((i * 23 - t * 90) % 276 + 276) % 276 - 20, 60 + (i % 3) * 8, 14, 5); }
  pixText(c, 'GO!', 80, 20, 4, '#FFE14D', { align: 'center', shadow: '#B0153E' });
  return c.canvas;
}

function draw(ctx, t) {
  const cam = camAt(t);
  // 1. фон: небо, горизонт, сетка пола
  const hy = horizonY(cam, FY), gr = ctx.createLinearGradient(0, 0, 0, hy); gr.addColorStop(0, '#05021A'); gr.addColorStop(1, '#3A0B52');
  ctx.fillStyle = gr; ctx.fillRect(0, 0, W, hy); ctx.fillStyle = '#0B0322'; ctx.fillRect(0, hy, W, H - hy);
  floorGrid(ctx, cam, t, { y: FY });
  WORD.forEach((L, k) => floorGlow(ctx, cam, [L.center[0], FY, 0], 9, COLORS[L.ch], 0.45 * P(t, LOCK[k] - 0.5, LOCK[k]) * (1 - P(t, BOOM, BOOM + 0.5))));
  // 2. дальний слой: декоративные кубики (все за словом)
  const far = AMB.map(a => ({ c: [a.p[0], a.p[1] + Math.sin(t * 0.7 + a.p[0]) * 1.5, a.p[2]], M: sc3(axAng(a.ax, t * a.sp + a.p[1]), a.s * E.outBack(P(t, 0, 0.6))), m: 'amb', j: -0.1, cull: 0 }));
  drawCubes(ctx, far, cam, PAL);
  // 3. портал в отверстии «А» — после дальнего слоя, до слова (стенки отверстия лягут поверх краёв)
  const pa = P(t, at(1, 3), at(2)) * (1 - P(t, BOOM - 0.1, BOOM));
  const q = pa > 0 && quadAt(cam, HOLE.x0, HOLE.x1, HOLE.y0, HOLE.y1, 1.5);
  if (q) {
    ctx.save(); ctx.beginPath(); q.forEach((p, i) => i ? ctx.lineTo(p[0], p[1]) : ctx.moveTo(p[0], p[1])); ctx.closePath(); ctx.clip();
    const xs = q.map(p => p[0]), ys = q.map(p => p[1]), x0 = Math.min(...xs), y0 = Math.min(...ys);
    ctx.globalAlpha = pa; ctx.imageSmoothingEnabled = false; ctx.drawImage(screen(t), x0, y0, Math.max(...xs) - x0, Math.max(...ys) - y0); ctx.restore();
  }
  // 4. ближний слой: слово — сборка, поп на фиксации, прыжок волной, разлёт
  const near = [];
  WORD.forEach((L, k) => {
    const lt = t - LOCK[k], s = 1 + (lt >= 0 ? 0.16 * Math.exp(-lt * 7) * Math.cos(lt * 16) : 0);
    const jt = t - (at(2) + k * BEAT / 2), dy = jt >= 0 && jt < 0.4 ? 2.2 * Math.sin(Math.PI * jt / 0.4) : 0, C = L.center;
    emit(L, near, v => {
      const home = [C[0] + (v.h[0] - C[0]) * s, C[1] + (v.h[1] - C[1]) * s + dy, C[2] + (v.h[2] - C[2]) * s];
      return t >= BOOM ? [explode(v, t - BOOM, home), false] : assemble(v, t, home, sc3(I3, s));
    });
  });
  drawCubes(ctx, near, cam, PAL);
  // 5. финальная надпись
  if (t > BOOM + 0.5) { ctx.globalAlpha = P(t, BOOM + 0.5, BOOM + 0.65); pixText(ctx, 'ГОТОВО!', W / 2, H / 2 - 70, 20, '#FFE14D', { align: 'center', shadow: '#FF2E88' }); ctx.globalAlpha = 1; }
}
function post(ctx, t) {
  bloom(ctx, { alpha: 0.4 });
  let fl = 0; for (const [x, a] of [...LOCK.map(x => [x, 0.3]), [BOOM, 0.7]]) if (t >= x) fl = Math.max(fl, a * Math.exp(-(t - x) / 0.08));
  if (fl > 0.004) { ctx.fillStyle = `rgba(255,245,255,${fl})`; ctx.fillRect(0, 0, W, H); }
}
async function audio(A) {
  A.boom(0.02, 0.6, 1.6); A.pad(0, at(1), [45, 57, 60, 64], 0.03); A.riser(0.4, at(1), 0.2);
  A.groove(g, [1, 2]); A.groove(g, [3], { stabs: false, shift: 2 });
  for (let b = 1; b < 4; b++) for (let i = 0; i < 16; i++) { const ch = [[57, 60, 64], [53, 57, 60], [55, 60, 64]][b - 1]; A.tone(at(b) + i * BEAT / 4, A.mf(ch[i % 3] + 24), 0.09, 0.04, { lp: 3500, pan: i % 2 ? 0.3 : -0.3 }); }
  LOCK.forEach((L, k) => { A.crash(L, 0.15, 1); [0, 4, 7, 12].forEach((d, i) => A.tone(L + i * 0.035, A.mf(69 + k * 3 + d), 0.09, 0.035)); });
  A.tone(at(1, 3), 300, 0.2, 0.03, { f1: 1200, vib: 0.01 });
  A.boom(BOOM, 0.8, 1.4); A.noise(BOOM, 0.6, 0.3, { f: 2500, f1: 150 }); A.tone(BOOM, 900, 0.4, 0.04, { f1: 60 }); A.crash(BOOM, 0.25, 1.4);
  A.pad(BOOM + 0.4, DUR - 0.3, [45, 52, 57, 64], 0.035); [0, 1, 2].forEach(i => A.bell(BOOM + 0.5 + i * 0.07, A.mf([81, 84, 88][i]), 0.06));
}
createVideo({ width: W, height: H, fps: 30, duration: DUR, background: '#05021A', draw, post, audio });
</script>
</body></html>
`````

### `examples/reel.html`

<!-- FILE: examples/reel.html sha256=1d607ecd323f01f363e5d46aed52af7d4b21b334bd70d36937a1f209d96c71b8 -->
`````html
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Claude Motion Reel</title>
<style>
:root{--bg:#050507;--fg:#F3EFE6}
html,body{margin:0;height:100%;background:var(--bg);color:var(--fg);font-family:Consolas,monospace}
body{display:grid;place-items:center}
#wrap{position:relative;width:min(calc(100vw - 32px),calc((100vh - 32px)*16/9));aspect-ratio:16/9}
canvas{width:100%;height:100%;display:block}
#play{position:absolute;inset:0;display:grid;place-items:center;background:rgba(5,5,7,.55);cursor:pointer;font:inherit;font-size:clamp(14px,2.4vw,28px);letter-spacing:.3em;border:0;color:inherit}
</style>
</head>
<body>
<div id="wrap"><canvas id="c" width="1920" height="1080"></canvas><button id="play">&#9654; PLAY REEL</button></div>
<script>
'use strict';
/* ============================================================ core */
const W=1920,H=1080,FPS=60,DUR=15,BPM=128,BEAT=60/BPM,BAR=BEAT*4,TAU=Math.PI*2,HB=BEAT/2;
const {sin,cos,abs,floor,min,max,PI,sqrt,pow,exp,round,hypot}=Math;
const C={ink:'#0A0A0F',paper:'#F3EFE6',orange:'#FF5A1F',blue:'#2F55FF',lime:'#C8FF3D',violet:'#8B5CFF',pink:'#FF3D8B',white:'#FFFFFF'};
const clamp=(x,a=0,b=1)=>x<a?a:x>b?b:x;
const lerp=(a,b,t)=>a+(b-a)*t;
const P=(t,a,b)=>clamp((t-a)/(b-a));
const E={
  outCubic:t=>1-pow(1-t,3), inCubic:t=>t*t*t, inOutCubic:t=>t<.5?4*t*t*t:1-pow(-2*t+2,3)/2,
  outQuart:t=>1-pow(1-t,4),
  outExpo:t=>t>=1?1:1-pow(2,-10*t), inExpo:t=>t<=0?0:pow(2,10*t-10),
  inOutExpo:t=>t<=0?0:t>=1?1:t<.5?pow(2,20*t-10)/2:(2-pow(2,-20*t+10))/2,
  outBack:t=>{const c1=1.70158,c3=c1+1;return 1+c3*pow(t-1,3)+c1*pow(t-1,2)},
};
function rng(a){return()=>{a|=0;a=a+0x6D2B79F5|0;let t=Math.imul(a^a>>>15,1|a);t=t+Math.imul(t^t>>>7,61|t)^t;return((t^t>>>14)>>>0)/4294967296}}
const hash=(x,y=0,s=0)=>{const v=sin(x*127.1+y*311.7+s*74.7)*43758.5453;return v-floor(v)};
const hex=h=>[1,3,5].map(i=>parseInt(h.slice(i,i+2),16));
const mix=(a,b,t)=>{const A=hex(a),B=hex(b);return `rgb(${A.map((v,i)=>round(lerp(v,B[i],t))).join(',')})`};
const F={disp:(s,w=900)=>`${w} ${s}px "Segoe UI","Arial Black",sans-serif`,mono:(s,w=400)=>`${w} ${s}px Consolas,"Courier New",monospace`};
function mk(w=W,h=H){const c=document.createElement('canvas');c.width=w;c.height=h;return c}
// four-on-the-floor envelope, active from bar 2 to the drop
const kick=t=>(t<BAR||t>=7*BAR)?0:exp(-(t%BEAT)*12);

function fill(ctx,c){ctx.fillStyle=c;ctx.fillRect(-80,-80,W+160,H+160)}
function disc(ctx,x,y,r){ctx.beginPath();ctx.arc(x,y,max(0,r),0,TAU);ctx.fill()}
function ring(ctx,x,y,r){ctx.beginPath();ctx.arc(x,y,max(0,r),0,TAU);ctx.stroke()}
const LC=new Map();
function lay(ctx,str,font,tr=0){
  const k=str+'|'+font+'|'+tr;let L=LC.get(k);if(L)return L;
  ctx.save();ctx.font=font;ctx.letterSpacing='0px';
  const ws=[...str].map(c=>ctx.measureText(c).width);ctx.restore();
  const tot=ws.reduce((a,b)=>a+b,0)+tr*(ws.length-1);let x=-tot/2;
  L=[...str].map((ch,i)=>{const o={ch,x,w:ws[i]};x+=ws[i]+tr;return o});L.width=tot;LC.set(k,L);return L;
}
function txt(ctx,s,x,y,font,col,align='center',ls=0){ctx.font=font;ctx.fillStyle=col;ctx.textAlign=align;ctx.textBaseline='alphabetic';ctx.letterSpacing=ls+'px';ctx.fillText(s,x,y);ctx.letterSpacing='0px'}
function ticker(ctx,str,y,off,font,stroke){
  ctx.font=font;ctx.textAlign='left';ctx.textBaseline='alphabetic';
  const w=ctx.measureText(str).width;let x=-(((off%w)+w)%w);
  for(;x<W;x+=w) stroke?ctx.strokeText(str,x,y):ctx.fillText(str,x,y);
}

/* ============================================================ S1 intro */
function S1(ctx,t){
  fill(ctx,C.ink);
  const cx=W/2, by=H/2+110;
  const la=E.outQuart(P(t,0.55,1.7));
  if(la>0){
    ctx.save();ctx.translate(cx,H/2);ctx.rotate(t*0.18);ctx.lineWidth=2;
    for(let i=0;i<64;i++){
      const a=i/64*TAU, r0=420+(i%2)*50, r1=r0+la*(800+(i%3)*260);
      ctx.strokeStyle=i%8==0?'rgba(255,90,31,.45)':'rgba(255,255,255,.07)';
      ctx.beginPath();ctx.moveTo(cos(a)*r0,sin(a)*r0);ctx.lineTo(cos(a)*r1,sin(a)*r1);ctx.stroke();
    }
    ctx.restore();
  }
  const rp=P(t,0.04,0.8);
  if(rp>0&&rp<1){ctx.strokeStyle=`rgba(255,255,255,${(1-rp)*.55})`;ctx.lineWidth=1+3*(1-rp);ring(ctx,cx,H/2,E.outExpo(rp)*700)}
  // dot -> line -> retract
  const pop=E.outBack(P(t,0,0.24));
  const st=E.inOutExpo(P(t,0.26,0.62));
  const rt=E.inOutExpo(P(t,1.0,1.34));
  const th=lerp(30,8,E.outCubic(P(t,0.26,0.45)))*pop;
  const dy=lerp(H/2,by+16,st);
  const half=lerp(th/2,820,st);
  const x0=lerp(cx-half,cx+820,rt), x1=cx+half;
  if(x1-x0>0.5&&th>0.5){
    ctx.fillStyle=st>0.5?C.orange:C.white;
    ctx.beginPath();ctx.roundRect(x0,dy-th/2,x1-x0,th,th/2);ctx.fill();
  }
  // letters rise from behind the line, with colour echoes trailing
  const font=F.disp(300), L=lay(ctx,'MOTION',font,8);
  const spread=E.inOutCubic(P(t,1.15,1.9));
  ctx.save();ctx.beginPath();ctx.rect(0,0,W,by+10);ctx.clip();
  ctx.font=font;ctx.textAlign='left';ctx.textBaseline='alphabetic';
  const cols=[C.orange,C.blue,C.violet];
  L.forEach((l,i)=>{
    const d=0.58+i*0.045;
    for(let k=3;k>=0;k--){
      const p=E.outExpo(P(t,d+k*0.04,d+k*0.04+0.55));if(p<=0)continue;
      ctx.fillStyle=k==0?C.white:cols[k-1];
      ctx.fillText(l.ch,cx+l.x*(1+spread*0.05),by+(1-p)*330);
    }
  });
  ctx.restore();
  // subtitle types on
  const s='a showreel by claude', n=floor(P(t,1.12,1.5)*s.length);
  if(n>0)txt(ctx,s.slice(0,n)+(n<s.length||((t*6)|0)%2?'_':' '),cx,by+96,F.mono(30),'rgba(243,239,230,.75)','center',8);
}

/* ============================================================ S2 kinetic type */
function S2(ctx,t,gt){
  const b=min(3,floor(t/BEAT)), u=t-b*BEAT;
  ctx.textBaseline='alphabetic';
  if(b==0){
    fill(ctx,C.orange);
    ctx.fillStyle='rgba(10,10,15,.55)';ticker(ctx,'DESIGN THAT MOVES  —  ',150,gt*420,F.mono(30));ticker(ctx,'DESIGN THAT MOVES  —  ',H-120,-gt*420,F.mono(30));
    const font=F.disp(330),L=lay(ctx,'DESIGN',font,0);ctx.font=font;ctx.textAlign='left';ctx.fillStyle=C.ink;
    L.forEach((l,i)=>{const p=E.outBack(P(u,i*0.03,i*0.03+0.3));ctx.fillText(l.ch,W/2+l.x,H/2+120-(1-p)*800)});
  }else if(b==1){
    fill(ctx,C.ink);
    ctx.strokeStyle='rgba(255,255,255,.13)';ctx.lineWidth=2;
    for(let r=0;r<8;r++)ticker(ctx,'THAT  ',r*150+40,(r%2?1:-1)*gt*900+r*300,F.disp(150),true);
    const p=E.outExpo(P(u,0,0.16)),s=lerp(2.8,1,p);
    ctx.save();ctx.translate(W/2,H/2);ctx.rotate(lerp(-0.2,0,p));ctx.scale(s,s);
    txt(ctx,'THAT',0,135,F.disp(380),C.white);ctx.restore();
    ctx.fillStyle=C.lime;ctx.fillRect(W/2-300,H/2+200,600*E.outExpo(P(u,0.08,0.35)),14);
  }else if(b==2){
    fill(ctx,C.blue);
    ctx.strokeStyle='rgba(200,255,61,.35)';ctx.lineWidth=3;
    for(let k=0;k<14;k++){const y=80+k*70,o=((gt*2600+hash(k)*W)%(W+800))-400;ctx.beginPath();ctx.moveTo(o,y);ctx.lineTo(o+200+hash(k,2)*300,y);ctx.stroke()}
    const font=F.disp(340),top=H/2-140,hh=280,n=7;
    for(let k=0;k<n;k++){
      const p=E.outExpo(P(u,k*0.022,k*0.022+0.3)),dx=(k%2?1:-1)*(1-p)*W;
      ctx.save();ctx.beginPath();ctx.rect(0,top+k*hh/n,W,hh/n+1);ctx.clip();
      txt(ctx,'MOVES',W/2+dx,H/2+122,font,C.lime);ctx.restore();
    }
  }else{
    fill(ctx,C.ink);
    const words=[['DESIGN',C.orange],['THAT',C.white],['MOVES',C.lime]];
    const s=1+u*0.08;ctx.save();ctx.translate(W/2,H/2);ctx.scale(s,s);
    words.forEach(([w,c],i)=>{
      const y=(i-1)*175+62,p=E.outExpo(P(u,i*0.05,i*0.05+0.3));
      ctx.save();ctx.beginPath();ctx.rect(-W,y-140,2*W,160);ctx.clip();
      txt(ctx,w,0,y+(1-p)*170,F.disp(180),c);ctx.restore();
    });
    ctx.restore();
  }
}

/* ============================================================ S3 geometry */
const TS=120,COLS=16,ROWS=9;
const TCOL=[C.ink,C.orange,C.blue,C.violet,C.pink,C.ink];
function tileShape(ctx,type,s){
  const h=s/2;ctx.beginPath();
  switch(type){
    case 0:ctx.arc(0,0,h,0,TAU);break;
    case 1:ctx.rect(-h,-h,s,s);break;
    case 2:ctx.moveTo(-h,-h);ctx.arc(-h,-h,s,0,PI/2);ctx.closePath();break;
    case 3:ctx.moveTo(-h,-h);ctx.lineTo(h,-h);ctx.lineTo(-h,h);ctx.closePath();break;
    case 4:ctx.moveTo(-h,h/2);ctx.arc(0,h/2,h,PI,TAU);ctx.closePath();break;
    default:ctx.arc(0,0,h,0,TAU);ctx.moveTo(h*0.45,0);ctx.arc(0,0,h*0.45,0,TAU,true);
  }
  ctx.fill();
}
function shapeR(k,th){
  switch(k){
    case 0:return 1;
    case 1:return 0.9/max(abs(cos(th)),abs(sin(th)));
    case 2:{const a=TAU/3,q=(((th+PI/2)%a)+a)%a;return 1.25*cos(PI/3)/cos(q-a/2)}
    default:{const f=((th*5/TAU+0.25)%1+1)%1;return lerp(0.52,1.22,1-abs(2*f-1))}
  }
}
function S3(ctx,t){
  fill(ctx,C.paper);
  const cxT=(COLS-1)/2,cyT=(ROWS-1)/2;
  for(let j=0;j<ROWS;j++)for(let i=0;i<COLS;i++){
    const d=hypot(i-cxT,j-cyT);
    let s=E.outBack(P(t,d*0.03,d*0.03+0.38));
    const sh=1.4+(8.6-d)*0.018;s*=1-E.inCubic(P(t,sh,sh+0.22));
    if(s<=0.001)continue;
    let rot=floor(hash(i,j,3)*4)*PI/2;
    for(let b=1;b<4;b++){const st=b*BEAT+(i+j)*0.011;rot+=PI/2*E.inOutCubic(P(t,st,st+0.26))}
    ctx.save();ctx.translate(i*TS+TS/2,j*TS+TS/2);ctx.rotate(rot);ctx.scale(s,s);
    ctx.fillStyle=TCOL[floor(hash(i,j,2)*6)];tileShape(ctx,floor(hash(i,j,1)*6),TS-20);ctx.restore();
  }
  // centre morph: circle > square > triangle > star > circle, then swallows the frame
  const keys=[[0,0],[BEAT,1],[2*BEAT,2],[3*BEAT,3],[1.6,0]];
  let A=0,B=0,m=0;
  for(let q=1;q<keys.length;q++)if(t>=keys[q][0]){A=keys[q-1][1];B=keys[q][1];m=E.inOutCubic(P(t,keys[q][0],keys[q][0]+0.24))}
  const pop=E.outBack(P(t,0.05,0.45)),grow=E.inExpo(P(t,1.6,1.875));
  const R=240*pop+grow*1500,rot=t*0.9;
  const wr=330*pop*(1-E.inCubic(P(t,1.45,1.6)));
  if(wr>1){ctx.fillStyle=C.paper;disc(ctx,W/2,H/2,wr);ctx.strokeStyle=C.ink;ctx.lineWidth=3;ring(ctx,W/2,H/2,wr)}
  if(R>1){
    const path=new Path2D();
    for(let i=0;i<=240;i++){const th=i/240*TAU,r=lerp(shapeR(A,th-rot),shapeR(B,th-rot),m)*R;i?path.lineTo(W/2+cos(th)*r,H/2+sin(th)*r):path.moveTo(W/2+cos(th)*r,H/2+sin(th)*r)}
    ctx.save();ctx.translate(18*(1-grow),18*(1-grow));ctx.fillStyle=C.orange;ctx.fill(path);ctx.restore();
    ctx.fillStyle=C.ink;ctx.fill(path);
  }
}

/* ============================================================ S4 particles */
let PARTS=null;const PCOLS=[C.orange,'#FF4466',C.pink,C.violet,'#5B5BFF',C.blue];
function initParticles(){
  const c=mk(),x=c.getContext('2d');x.fillStyle='#fff';x.font=F.disp(500);x.textAlign='center';x.letterSpacing='16px';x.fillText('FLOW',W/2,H/2+178);
  const d=x.getImageData(0,0,W,H).data,pts=[];
  for(let y=0;y<H;y+=5)for(let xx=0;xx<W;xx+=5)if(d[(y*W+xx)*4+3]>128)pts.push([xx,y]);
  const R=rng(42);for(let i=pts.length-1;i>0;i--){const j=floor(R()*(i+1));[pts[i],pts[j]]=[pts[j],pts[i]]}
  PARTS=[];
  for(let i=0;i<4200;i++){const p=pts[i%pts.length];PARTS.push({tx:p[0]+(R()-.5)*4,ty:p[1]+(R()-.5)*4,a0:R()*TAU,R:pow(R(),0.6)*900+60,w:1.2+R()*2.2,d:R()*0.28,bulge:(R()-.5)*520,j:R()*TAU,col:min(5,floor(p[0]/W*6))})}
}
function ppos(p,t){
  const ex=E.outExpo(P(t,0,0.7));
  const ang=p.a0+t*p.w*(1.2-0.5*ex)+(1-ex)*1.5,r=p.R*ex;
  let x=W/2+cos(ang)*r*1.3,y=H/2+sin(ang)*r*0.75;
  const m=E.inOutCubic(P(t,0.45+p.d,0.95+p.d));
  if(m>0){const b=sin(m*PI)*p.bulge;x=lerp(x,p.tx,m)+cos(p.a0)*b;y=lerp(y,p.ty,m)+sin(p.a0)*b}
  const hold=m*(1-P(t,1.35,1.5));x+=sin(t*14+p.j)*2.4*hold;y+=cos(t*11+p.j*1.3)*2.4*hold;
  const z=E.inExpo(P(t,1.3+p.d*0.35,1.875)),s=1/(1-z*0.95);
  return [W/2+(x-W/2)*s,H/2+(y-H/2)*s,s];
}
function S4(ctx,t){
  fill(ctx,C.ink);
  const g=ctx.createRadialGradient(W/2,H/2,0,W/2,H/2,950);g.addColorStop(0,'rgba(139,92,255,.24)');g.addColorStop(1,'rgba(139,92,255,0)');ctx.fillStyle=g;ctx.fillRect(0,0,W,H);
  const paths=[];for(let i=0;i<24;i++)paths.push(new Path2D());
  for(const p of PARTS){
    const a=ppos(p,t-0.035),b=ppos(p,t),sb=b[2]<1.5?0:b[2]<3?1:b[2]<7?2:3,pa=paths[p.col*4+sb];
    pa.moveTo(a[0],a[1]);pa.lineTo(b[0],b[1]);
  }
  ctx.globalCompositeOperation='lighter';ctx.lineCap='round';
  paths.forEach((pa,i)=>{ctx.strokeStyle=PCOLS[floor(i/4)];ctx.lineWidth=[3,5,9,16][i%4];ctx.stroke(pa)});
  ctx.globalCompositeOperation='source-over';
  const ca=P(t,0.95,1.15)*(1-P(t,1.3,1.4));
  if(ca>0)txt(ctx,'4 200 PARTICLES  /  ONE WORD',W/2,H/2+300,F.mono(24),`rgba(243,239,230,${ca*.7})`,'center',6);
}

/* ============================================================ S5 3D */
const DEPTH=[];for(let i=0;i<32;i++){const t=i/31;DEPTH.push(t<.4?mix(C.blue,C.violet,t/.4):t<.8?mix(C.violet,C.orange,(t-.4)/.4):mix(C.orange,'#FFE2D0',(t-.8)/.2))}
function S5(ctx,t){
  fill(ctx,C.ink);
  if(t<2*BEAT){
    const camZ=t*4600,f=720;
    ctx.save();ctx.translate(W/2,H/2);ctx.rotate(t*0.8);ctx.globalCompositeOperation='lighter';
    const cols=[C.orange,C.violet,C.blue,C.pink];
    for(let k=0;k<90;k++){
      const z=k*200-camZ+60;if(z<40||z>6000)continue;
      const s=f/z;ctx.globalAlpha=clamp(1-z/6000)*clamp((z-40)/150);ctx.fillStyle=cols[k%4];
      const sz=max(1,26*s),dir=k%2?1:-1;
      for(let q=0;q<24;q++){const a=q/24*TAU+k*0.21+t*1.6*dir;ctx.fillRect(cos(a)*600*s-sz/2,sin(a)*600*s-sz/2,sz,sz)}
    }
    ctx.strokeStyle='#fff';ctx.lineWidth=2;
    for(let i=0;i<48;i++){
      const a=hash(i,7)*TAU,zz=((hash(i,8)*6000-camZ*1.3)%6000+6000)%6000+50,s1=f/zz,s2=f/(zz+500),R=520+hash(i,9)*320;
      ctx.globalAlpha=clamp(1-zz/6000)*0.6;ctx.beginPath();ctx.moveTo(cos(a)*R*s2,sin(a)*R*s2);ctx.lineTo(cos(a)*R*s1,sin(a)*R*s1);ctx.stroke();
    }
    ctx.restore();
    return;
  }
  const lt=t-2*BEAT;
  const sp=E.outBack(P(lt,0,0.35))*(1-E.inExpo(P(lt,0.72,0.9375)));
  const m=E.inOutCubic(P(lt,0.18,0.55));
  const g=ctx.createRadialGradient(W/2,H/2,0,W/2,H/2,700);g.addColorStop(0,'rgba(47,85,255,.25)');g.addColorStop(1,'rgba(47,85,255,0)');ctx.fillStyle=g;ctx.fillRect(0,0,W,H);
  if(sp<=0.001)return;
  const U=44,V=24,ry=t*1.5,rx=0.5+0.3*sin(t*2.2),cy=cos(ry),sy=sin(ry),cxr=cos(rx),sxr=sin(rx);
  const pts=[];
  for(let v=0;v<V;v++)for(let u=0;u<U;u++){
    const th=u/U*TAU,ph=(v+0.5)/V*PI,ps=v/V*TAU;
    const sx=sin(ph)*cos(th)*330,syy=cos(ph)*330,sz=sin(ph)*sin(th)*330;
    const rr=290+130*cos(ps),tx=rr*cos(th),ty=130*sin(ps),tz=rr*sin(th);
    let x=lerp(sx,tx,m),y=lerp(syy,ty,m),z=lerp(sz,tz,m);
    let x2=x*cy+z*sy,z2=-x*sy+z*cy;let y3=y*cxr-z2*sxr,z3=y*sxr+z2*cxr;
    const s=1000/(1000+z3)*sp;pts.push([W/2+x2*s,H/2+y3*s,z3,s]);
  }
  ctx.strokeStyle='rgba(255,255,255,.16)';ctx.lineWidth=1.3;ctx.beginPath();
  for(let v=0;v<V;v++)for(let u=0;u<=U;u++){const p=pts[v*U+u%U];u?ctx.lineTo(p[0],p[1]):ctx.moveTo(p[0],p[1])}
  for(let u=0;u<U;u++)for(let v=0;v<=V;v++){if(v==V&&m<0.5)break;const p=pts[(v%V)*U+u];v?ctx.lineTo(p[0],p[1]):ctx.moveTo(p[0],p[1])}
  ctx.stroke();
  const order=pts.map((p,i)=>i).sort((a,b)=>pts[b][2]-pts[a][2]);
  for(const i of order){const p=pts[i],dn=clamp((420-p[2])/840);ctx.fillStyle=DEPTH[floor(dn*31)];const r=(2+dn*6)*p[3];ctx.fillRect(p[0]-r,p[1]-r,2*r,2*r)}
  ctx.save();ctx.translate(W/2,H/2);ctx.strokeStyle=C.lime;ctx.lineWidth=2;ctx.setLineDash([14,12]);ctx.lineDashOffset=-t*220;
  for(let k=0;k<2;k++){ctx.beginPath();ctx.ellipse(0,0,(500+k*90)*sp,(120+k*40)*sp,t*0.5*(k?-1:1)+k,0,TAU);ctx.stroke()}
  ctx.restore();
  // technical callout
  const ca=P(lt,0.25,0.4)*(1-P(lt,0.7,0.8));
  if(ca>0){
    const p=pts[8*U+6],lx=W/2+560,ly=H/2-270;
    ctx.strokeStyle=`rgba(255,255,255,${ca*.8})`;ctx.lineWidth=1.5;ctx.beginPath();ctx.moveTo(p[0],p[1]);ctx.lineTo(lx-40,ly);ctx.lineTo(lx+260,ly);ctx.stroke();
    ctx.fillStyle=C.lime;ctx.fillRect(p[0]-6,p[1]-6,12,12);
    txt(ctx,'VERTEX 0358',lx-30,ly-14,F.mono(22,700),`rgba(255,255,255,${ca})`,'left',3);
    txt(ctx,`MORPH ${m.toFixed(3)}  ROT ${(ry%TAU).toFixed(3)}`,lx-30,ly+32,F.mono(18),`rgba(255,255,255,${ca*.7})`,'left',2);
  }
}

/* ============================================================ S6 fluid + data */
const G1=mk(960,540),G2=mk(960,540),g1=G1.getContext('2d'),g2=G2.getContext('2d');
const DATA=[.32,.45,.38,.6,.52,.7,.58,.82,.74,.9,.86,1];
function S6(ctx,t,gt){
  if(t<0.94){
    fill(ctx,C.ink);
    g1.fillStyle='#000';g1.fillRect(0,0,960,540);g1.fillStyle='#fff';
    const e=E.outBack(P(t,0,0.35)),k=kick(gt);
    disc(g1,480,270,(95+k*14)*e);
    for(let i=0;i<11;i++){
      const a=i/11*TAU+t*(0.9+(i%3)*0.5)*(i%2?1:-1),r=(120+(i%4)*55+sin(t*4+i)*45)*e;
      disc(g1,480+cos(a)*r*1.5,270+sin(a)*r,(28+hash(i,4)*40)*min(1,e*1.2));
    }
    g2.clearRect(0,0,960,540);g2.filter='blur(14px) contrast(30)';g2.drawImage(G1,0,0);g2.filter='none';
    g2.globalCompositeOperation='multiply';
    const gr=g2.createLinearGradient(0,0,960,540);gr.addColorStop(0,C.orange);gr.addColorStop(.5,C.pink);gr.addColorStop(1,C.violet);
    g2.fillStyle=gr;g2.fillRect(0,0,960,540);g2.globalCompositeOperation='source-over';
    ctx.globalCompositeOperation='lighter';ctx.drawImage(G2,0,0,W,H);ctx.globalCompositeOperation='difference';
    ctx.strokeStyle='#fff';ctx.lineWidth=3;ctx.font=F.disp(400);ctx.textAlign='center';ctx.letterSpacing='12px';
    ctx.strokeText('FLUID',W/2,H/2+145);ctx.letterSpacing='0px';ctx.globalCompositeOperation='source-over';
    // liquid fill rising
    for(const [lag,col] of [[0,C.orange],[0.06,C.paper]]){
      const lv=lerp(H+120,-160,E.inOutCubic(P(t,0.62+lag,0.94)));if(lv>H+100)continue;
      ctx.fillStyle=col;ctx.beginPath();ctx.moveTo(0,H+10);
      for(let x=0;x<=W;x+=20)ctx.lineTo(x,lv+sin(x*0.005+t*9)*42+sin(x*0.012-t*6)*20);
      ctx.lineTo(W,H+10);ctx.fill();
    }
    return;
  }
  const d=t-0.94;
  fill(ctx,C.paper);
  ctx.fillStyle='rgba(10,10,15,.14)';
  for(let i=0;i<6;i++){const p=E.outExpo(P(d,i*0.03,i*0.03+0.4));ctx.fillRect(140,300+i*116,(W-280)*p,2)}
  ctx.save();ctx.beginPath();ctx.rect(0,110,W,120);ctx.clip();
  txt(ctx,'DATA, ANIMATED.',140,210+(1-E.outExpo(P(d,0,0.3)))*120,F.disp(86),C.ink,'left');ctx.restore();
  txt(ctx,'Q1—Q12 / INDEXED',W-140,200,F.mono(22),'rgba(10,10,15,.6)','right',4);
  const base=880,k=kick(gt),tops=[];
  DATA.forEach((v,i)=>{
    const p=E.outBack(P(d,0.05+i*0.025,0.4+i*0.025)),h=v*470*p*(1+0.035*k*(i%2?1:-1)),x=150+i*74;
    ctx.fillStyle=i==11?C.orange:C.ink;ctx.fillRect(x,base-h,52,h);tops.push([x+26,base-h-46]);
  });
  const lp=E.inOutCubic(P(d,0.25,0.75))*(tops.length-1);
  ctx.strokeStyle=C.orange;ctx.lineWidth=5;ctx.lineJoin='round';ctx.beginPath();
  let hx=tops[0][0],hy=tops[0][1];ctx.moveTo(hx,hy);
  for(let i=1;i<tops.length&&i-1<lp;i++){const f=min(1,lp-(i-1));hx=lerp(tops[i-1][0],tops[i][0],f);hy=lerp(tops[i-1][1],tops[i][1],f);ctx.lineTo(hx,hy)}
  ctx.stroke();if(lp>0){ctx.fillStyle=C.paper;disc(ctx,hx,hy,11);ctx.lineWidth=5;ring(ctx,hx,hy,11)}
  const sw=E.inOutCubic(P(d,0.1,0.62))*TAU,segs=[[.42,C.orange],[.26,C.blue],[.18,C.violet],[.14,C.ink]];
  let a0=-PI/2+d*0.6;ctx.lineWidth=66;ctx.lineCap='butt';
  for(const [f,c] of segs){const a1=a0+f*TAU,e=min(a1,-PI/2+d*0.6+sw);if(e>a0){ctx.strokeStyle=c;ctx.beginPath();ctx.arc(1480,560,215,a0,e);ctx.stroke()}a0=a1}
  txt(ctx,'+'+round(248*E.outCubic(P(d,0.1,0.7)))+'%',1480,592,F.disp(96),C.ink);
  txt(ctx,'GROWTH',1480,640,F.mono(22),'rgba(10,10,15,.6)','center',6);
}

/* ============================================================ S7 style frames */
function S7(ctx,t){
  const c=min(7,floor(t/HB)),u=clamp((t-c*HB)/HB),eo=E.outExpo(u);
  ctx.save();const pz=lerp(1.07,1,eo);ctx.translate(W/2,H/2);ctx.scale(pz,pz);ctx.translate(-W/2,-H/2);
  switch(c){
    case 0:{
      fill(ctx,C.paper);ctx.fillStyle=C.orange;disc(ctx,lerp(W+320,1420,eo),H/2+40,300);
      ctx.save();ctx.beginPath();ctx.rect(0,200,W,H-200);ctx.clip();txt(ctx,'01',110,H/2+250+(1-eo)*420,F.disp(600),C.ink,'left');ctx.restore();
      ctx.fillStyle=C.ink;ctx.fillRect(120,170,(W-240)*eo,6);txt(ctx,'SWISS  /  GRID  /  ONE RED CIRCLE',120,140,F.mono(24),C.ink,'left',4);break;
    }
    case 1:{
      fill(ctx,C.ink);const sx=lerp(560,1360,u),sy=H/2,R=430,g=32;ctx.fillStyle=C.lime;ctx.beginPath();
      for(let y=g/2;y<H;y+=g)for(let x=g/2;x<W;x+=g){
        const dx=(x-sx)/R,dy=(y-sy)/R,d2=dx*dx+dy*dy;let r;
        if(d2<1){const nz=sqrt(1-d2);r=lerp(1.5,15,clamp(-0.55*dx-0.6*dy+0.6*nz))}else r=lerp(1.6,0,clamp((d2-1)*0.7));
        if(r>0.3){ctx.moveTo(x+r,y);ctx.arc(x,y,r,0,TAU)}
      }
      ctx.fill();break;
    }
    case 2:{
      fill(ctx,C.ink);ctx.globalCompositeOperation='lighter';const dx=10+18*(1-u);
      txt(ctx,'GLITCH',W/2-dx,H/2+105,F.disp(300),'#FF2040');txt(ctx,'GLITCH',W/2+dx,H/2+105,F.disp(300),'#20E0FF');txt(ctx,'GLITCH',W/2,H/2+105,F.disp(300),'#6060A0');
      ctx.globalCompositeOperation='source-over';
      ctx.save();ctx.setTransform(1,0,0,1,0,0);const R=rng(floor(t*30)+3);
      for(let i=0;i<9;i++){const y=R()*H,h=8+R()*70,o=(R()-.5)*240;ctx.drawImage(ctx.canvas,0,y,W,h,o,y,W,h)}
      ctx.fillStyle='rgba(0,0,0,.28)';for(let y=0;y<H;y+=4)ctx.fillRect(0,y,W,1);ctx.restore();break;
    }
    case 3:{
      fill(ctx,C.blue);ctx.translate(W/2,H/2);ctx.lineWidth=3;
      for(let k=0;k<40;k++){const s=30+k*34;ctx.save();ctx.rotate(k*0.07*sin(u*PI+0.6)+u*0.9);ctx.strokeStyle=`rgba(255,255,255,${1-k/44})`;ctx.strokeRect(-s/2,-s/2,s,s);ctx.restore()}
      break;
    }
    case 4:{
      fill(ctx,C.paper);ctx.strokeStyle=C.ink;ctx.lineWidth=7;
      for(const [x,y] of [[W/2-260+u*220,H/2],[W/2+260-u*220,H/2+40]]){ctx.beginPath();for(let r=10;r<1500;r+=22){ctx.moveTo(x+r,y);ctx.arc(x,y,r,0,TAU)}ctx.stroke()}
      break;
    }
    case 5:{
      fill(ctx,C.orange);const cw=62,ch=36,N=9,ox=W/2,oy=H/2-260;
      for(let s=0;s<2*N-1;s++)for(let i=0;i<N;i++){const j=s-i;if(j<0||j>=N)continue;
        const h=max(24,130+70*(sin(i*0.8+u*7)+cos(j*0.7-u*6))),x=ox+(i-j)*cw,b=oy+(i+j)*ch+120,y=b-h;
        ctx.fillStyle=C.ink;ctx.beginPath();ctx.moveTo(x-cw,y);ctx.lineTo(x,y+ch);ctx.lineTo(x,b+ch);ctx.lineTo(x-cw,b);ctx.fill();
        ctx.fillStyle='#2A2A35';ctx.beginPath();ctx.moveTo(x+cw,y);ctx.lineTo(x,y+ch);ctx.lineTo(x,b+ch);ctx.lineTo(x+cw,b);ctx.fill();
        ctx.fillStyle=C.paper;ctx.beginPath();ctx.moveTo(x,y-ch);ctx.lineTo(x+cw,y);ctx.lineTo(x,y+ch);ctx.lineTo(x-cw,y);ctx.fill();
      }
      break;
    }
    case 6:{
      fill(ctx,C.ink);ctx.translate(W/2,H/2);ctx.textAlign='center';ctx.textBaseline='middle';ctx.font=F.disp(220);
      for(let k=11;k>=0;k--){const s=0.1*pow(1.55,k+u*2);if(s>14)continue;ctx.save();ctx.scale(s,s);ctx.lineWidth=3/s;
        if(k==0){ctx.fillStyle=C.lime;ctx.fillText('FAST',0,0)}else{ctx.strokeStyle=k%2?C.white:C.lime;ctx.strokeText('FAST',0,0)}ctx.restore()}
      break;
    }
    default:{
      const inv=u>0.5;fill(ctx,inv?C.ink:C.lime);const s=lerp(1.35,1,eo);
      ctx.translate(W/2,H/2);ctx.scale(s,s);txt(ctx,'CUT',0,180,F.disp(520),inv?C.lime:C.ink);
    }
  }
  ctx.restore();
  ctx.save();ctx.globalCompositeOperation='difference';txt(ctx,`${String(c+1).padStart(2,'0')} / 08`,W/2,H-118,F.mono(24,700),'#fff','center',6);ctx.restore();
}

/* ============================================================ S8 resolve */
const DUST=(()=>{const R=rng(5);return Array.from({length:150},()=>[R()*W,R()*H,0.5+R()*2.5,R()*TAU,10+R()*40])})();
function S8(ctx,t){
  fill(ctx,C.ink);
  for(const [x,y,r,a,v] of DUST){ctx.fillStyle=`rgba(243,239,230,${0.12+r*0.06})`;disc(ctx,(x+cos(a)*v*t*3)%W,(y-v*t)%H,r)}
  const font=F.disp(200),L=lay(ctx,'CLAUDE',font,4),markW=234,gap=70,total=markW+gap+L.width,x0=(W-total)/2,mcx=x0+markW/2,mcy=H/2-40,tx0=x0+markW+gap,base=mcy+72;
  const glow=ctx.createRadialGradient(mcx,mcy,0,mcx,mcy,700);glow.addColorStop(0,`rgba(255,90,31,${0.2+0.06*sin(t*6)})`);glow.addColorStop(1,'rgba(255,90,31,0)');ctx.fillStyle=glow;ctx.fillRect(0,0,W,H);
  const marks=[[-1,-1,0,C.orange,0],[1,-1,1,C.blue,0],[-1,1,2,C.lime,PI/2*3],[1,1,3,C.violet,PI]];
  marks.forEach(([sx,sy,type,col,r0],i)=>{
    const st=0.1+i*0.07,p=E.outExpo(P(t,st,st+0.5)),x=mcx+sx*62+(1-p)*sx*1100,y=mcy+sy*62+(1-p)*sy*700;
    ctx.save();ctx.translate(x,y);ctx.rotate(r0+(1-p)*sx*PI*1.5);ctx.fillStyle=col;tileShape(ctx,type,110);ctx.restore();
    const rp=P(t,st+0.16,st+0.6);if(rp>0&&rp<1){ctx.strokeStyle=`rgba(255,255,255,${(1-rp)*.5})`;ctx.lineWidth=2;ring(ctx,mcx+sx*62,mcy+sy*62,40+E.outExpo(rp)*120)}
  });
  ctx.save();ctx.beginPath();ctx.rect(0,base-170,W,190);ctx.clip();ctx.font=font;ctx.textAlign='left';ctx.fillStyle=C.paper;
  L.forEach((l,i)=>{const p=E.outExpo(P(t,0.32+i*0.04,0.8+i*0.04));ctx.fillText(l.ch,tx0+l.x+L.width/2,base+(1-p)*200)});ctx.restore();
  ctx.fillStyle=C.orange;ctx.fillRect(tx0,base+34,L.width*E.inOutExpo(P(t,0.62,1.0)),8);
  const s='MOTION DESIGN  ·  SHOWREEL 2026',n=floor(P(t,0.78,1.18)*s.length);
  if(n>0)txt(ctx,s.slice(0,n)+(((t*5)|0)%2?'█':' '),tx0,base+96,F.mono(28),'rgba(243,239,230,.8)','left',6);
  const fa=P(t,1.18,1.4);
  if(fa>0)txt(ctx,'AVAILABLE FOR NEW PROJECTS  →',W/2+sin(t*8)*4*fa,H-110,F.mono(22),`rgba(243,239,230,${fa*.6})`,'center',8);
}

/* ============================================================ timeline */
const SC=[[0,'INTRO',S1],[BAR,'KINETIC TYPE',S2],[2*BAR,'GEOMETRY',S3],[3*BAR,'PARTICLES',S4],[4*BAR,'3D / DEPTH',S5],[5*BAR,'FLUID + DATA',S6],[6*BAR,'STYLE FRAMES',S7],[7*BAR,'RESOLVE',S8]];
const sceneAt=t=>{let i=0;SC.forEach((s,k)=>{if(t>=s[0])i=k});return i};
const SHK=[0,6,0,4,6,3,10,0];
function drawScene(ctx,i,t){
  ctx.save();
  const a=kick(t)*SHK[i]+(t>=7*BAR?exp(-(t-7*BAR)*7)*22:0);
  if(a>0.05){const R=rng(floor(t*FPS*2)+7);ctx.translate((R()-.5)*2*a,(R()-.5)*2*a)}
  if(i==4||i==6){const z=1+kick(t)*0.015;ctx.translate(W/2,H/2);ctx.scale(z,z);ctx.translate(-W/2,-H/2)}
  SC[i][2](ctx,max(0,t-SC[i][0]),t);
  ctx.restore();
}
function renderAt(ctx,t){
  ctx.setTransform(1,0,0,1,0,0);ctx.globalAlpha=1;ctx.globalCompositeOperation='source-over';ctx.filter='none';ctx.setLineDash([]);
  drawScene(ctx,sceneAt(t),t);
  // band wipe 1 -> 2
  if(t>1.6&&t<2.16){
    const cols=[C.orange,C.lime,C.blue,C.violet,C.pink,C.paper];
    for(let i=0;i<6;i++){const d=i*0.015,r=W*E.inOutExpo(P(t,1.62+d,1.8+d)),l=W*E.inOutExpo(P(t,1.875+d,2.06+d));if(r>l){ctx.fillStyle=cols[i];ctx.fillRect(l,i*H/6,r-l,H/6+1)}}
  }
  // iris 2 -> 3
  if(t>3.58&&t<3.8){
    const r=E.inOutExpo(P(t,3.58,3.8))*1250;
    if(t<3.75){ctx.save();ctx.beginPath();ctx.arc(W/2,H/2,r,0,TAU);ctx.clip();drawScene(ctx,2,2*BAR);ctx.restore()}
    ctx.strokeStyle=C.lime;ctx.lineWidth=34;ring(ctx,W/2,H/2,r+17);
  }
}

/* ============================================================ post */
const out=document.getElementById('c').getContext('2d');
const ACC=mk(),acc=ACC.getContext('2d'),WORK=mk(),work=WORK.getContext('2d'),TMP=mk(),tmp=TMP.getContext('2d');
const GR=[0,1,2,3].map(s=>{const c=mk(256,256),x=c.getContext('2d'),id=x.createImageData(256,256),R=rng(s+9);for(let i=0;i<id.data.length;i+=4){const v=R()*255;id.data[i]=id.data[i+1]=id.data[i+2]=v;id.data[i+3]=255}x.putImageData(id,0,0);return out.createPattern(c,'repeat')});
const VIG=(()=>{const c=mk(),x=c.getContext('2d'),g=x.createRadialGradient(W/2,H/2,H*0.35,W/2,H/2,H*1.05);g.addColorStop(0,'rgba(0,0,0,0)');g.addColorStop(1,'rgba(0,0,0,.42)');x.fillStyle=g;x.fillRect(0,0,W,H);return c})();
const HITS=[...[1,2,3,4,5,6,7].map(b=>[b*BAR,16]),...[1,2,3,4,5,6,7].map(k=>[6*BAR+k*HB,9]),[4*BAR+2*BEAT,14]];
const FLASH=[[4*BAR,0.65,0.06],[4*BAR+2*BEAT,0.8,0.06],[7*BAR,1,0.1]];
const env=(t,list,dec)=>list.reduce((a,[h,amp,d])=>t>=h?max(a,amp*exp(-(t-h)/(d||dec))):a,0);
function post(t){
  const ab=env(t,HITS,0.07);
  out.globalCompositeOperation='source-over';out.globalAlpha=1;out.setTransform(1,0,0,1,0,0);
  if(ab>0.6){
    out.fillStyle='#000';out.fillRect(0,0,W,H);
    [['#f00',-ab],['#0f0',0],['#00f',ab]].forEach(([c,o])=>{tmp.globalCompositeOperation='source-over';tmp.drawImage(ACC,0,0);tmp.globalCompositeOperation='multiply';tmp.fillStyle=c;tmp.fillRect(0,0,W,H);out.globalCompositeOperation='lighter';out.drawImage(TMP,o,0)});
    out.globalCompositeOperation='source-over';
  }else out.drawImage(ACC,0,0);
  const fl=env(t,FLASH,0.06);if(fl>0.005){out.fillStyle=`rgba(255,255,255,${fl})`;out.fillRect(0,0,W,H)}
  out.drawImage(VIG,0,0);
  const f=round(t*FPS),R=rng(f+1);out.save();out.globalCompositeOperation='overlay';out.globalAlpha=0.08;out.translate(-R()*256,-R()*256);out.fillStyle=GR[f%4];out.fillRect(0,0,W+256,H+256);out.restore();
  hud(out,t);
}
function hud(ctx,t){
  const a=P(t,0.3,0.6)*(1-P(t,7*BAR+0.1,7*BAR+0.4));if(a<=0)return;
  ctx.save();ctx.globalCompositeOperation='difference';ctx.globalAlpha=a;ctx.strokeStyle='#fff';ctx.fillStyle='#fff';ctx.lineWidth=3;
  const m=48,L=44*E.outExpo(P(t,0.3,0.9));ctx.beginPath();
  for(const [x,y,sx,sy] of [[m,m,1,1],[W-m,m,-1,1],[m,H-m,1,-1],[W-m,H-m,-1,-1]]){ctx.moveTo(x,y+sy*L);ctx.lineTo(x,y);ctx.lineTo(x+sx*L,y)}
  ctx.stroke();
  const f=round(t*FPS),tc=`00:00:${String(floor(f/60)).padStart(2,'0')}:${String(f%60).padStart(2,'0')}`;
  txt(ctx,'CLAUDE — MOTION REEL',m+22,m+34,F.mono(20,700),'#fff','left',3);
  txt(ctx,(floor(t/BEAT*2)%2?'●':'○')+' REC   1920×1080 · 60P',W-m-22,m+34,F.mono(20),'#fff','right',3);
  txt(ctx,tc,m+22,H-m-22,F.mono(20),'#fff','left',3);
  const i=sceneAt(t),lt=t-SC[i][0],dy=(1-E.outExpo(P(lt,0,0.25)))*30;
  ctx.save();ctx.beginPath();ctx.rect(W-700,H-m-50,700-m,40);ctx.clip();txt(ctx,`0${i+1} / ${SC[i][1]}`,W-m-22,H-m-22+dy,F.mono(20,700),'#fff','right',3);ctx.restore();
  const bx=m+240,bw=W-2*m-560;ctx.globalAlpha=a*0.35;ctx.fillRect(bx,H-m-30,bw,2);ctx.globalAlpha=a;ctx.fillRect(bx,H-m-31,bw*t/DUR,4);
  for(let k=1;k<8;k++)ctx.fillRect(bx+bw*k*BAR/DUR-1,H-m-38,2,18);
  ctx.restore();
}
const SHUTTER=0.5;
function frame(t,sub=1){
  if(sub<=1)renderAt(acc,t);
  else for(let k=0;k<sub;k++){
    const ts=clamp(t+((k+0.5)/sub-0.5)*SHUTTER/FPS,0,DUR-1e-4);
    renderAt(work,ts);acc.globalAlpha=1/(k+1);acc.globalCompositeOperation='source-over';acc.drawImage(WORK,0,0);acc.globalAlpha=1;
  }
  post(t);
}

/* ============================================================ audio (offline synth, beat-locked) */
async function buildAudio(){
  const sr=48000,ac=new OfflineAudioContext(2,sr*DUR,sr),R=rng(7);
  const noise=ac.createBuffer(1,sr*3,sr);{const d=noise.getChannelData(0);for(let i=0;i<d.length;i++)d[i]=R()*2-1}
  const comp=ac.createDynamicsCompressor();comp.threshold.value=-15;comp.knee.value=8;comp.ratio.value=5;comp.attack.value=0.004;comp.release.value=0.18;
  const master=ac.createGain();master.gain.setValueAtTime(0.95,0);master.gain.setValueAtTime(0.95,DUR-0.35);master.gain.linearRampToValueAtTime(0,DUR);
  comp.connect(master);master.connect(ac.destination);
  const bus=ac.createGain();bus.gain.value=0.75;bus.connect(comp);
  const duck=ac.createGain();duck.connect(bus);
  const ir=ac.createBuffer(2,sr*2.4,sr);for(let c=0;c<2;c++){const d=ir.getChannelData(c);for(let i=0;i<d.length;i++)d[i]=(R()*2-1)*pow(1-i/d.length,3.4)}
  const verb=ac.createConvolver();verb.buffer=ir;const vr=ac.createGain();vr.gain.value=0.32;verb.connect(vr);vr.connect(comp);
  const mf=m=>440*pow(2,(m-69)/12);
  function out(node,pan=0,send=0,dest=bus){const p=ac.createStereoPanner();p.pan.value=pan;node.connect(p);p.connect(dest);if(send){const s=ac.createGain();s.gain.value=send;p.connect(s);s.connect(verb)}return p}
  function env(g,t,a,peak,d){g.gain.setValueAtTime(0,t);g.gain.linearRampToValueAtTime(peak,t+a);g.gain.exponentialRampToValueAtTime(0.0001,t+a+d)}
  function nz(t,dur){const s=ac.createBufferSource();s.buffer=noise;s.loop=true;s.start(t,R()*2);s.stop(t+dur);return s}
  function filt(type,f,q=0.7){const b=ac.createBiquadFilter();b.type=type;b.frequency.value=f;b.Q.value=q;return b}
  function osc(type,f,t,dur){const o=ac.createOscillator();o.type=type;o.frequency.value=f;o.start(t);o.stop(t+dur);return o}
  const kick=(t,v=1)=>{const o=osc('sine',170,t,0.6);o.frequency.setValueAtTime(170,t);o.frequency.exponentialRampToValueAtTime(48,t+0.11);o.frequency.exponentialRampToValueAtTime(38,t+0.45);
    const g=ac.createGain();env(g,t,0.002,v,0.45);o.connect(g);out(g);
    const n=nz(t,0.03),g2=ac.createGain();env(g2,t,0.001,0.3*v,0.018);n.connect(filt('highpass',2500)).connect(g2);out(g2);
    duck.gain.setValueAtTime(0.35,t);duck.gain.linearRampToValueAtTime(1,t+0.22)};
  const clap=(t,v=0.5)=>{const n=nz(t,0.3),b=filt('bandpass',1300,0.9),g=ac.createGain();g.gain.setValueAtTime(0,t);
    for(const o of [0,0.011,0.022]){g.gain.setValueAtTime(v,t+o);g.gain.exponentialRampToValueAtTime(0.05,t+o+0.009)}
    g.gain.setValueAtTime(v*0.8,t+0.032);g.gain.exponentialRampToValueAtTime(0.0001,t+0.25);n.connect(b).connect(g);out(g,0.05,0.35)};
  const hat=(t,open,v,pan=0.2)=>{const n=nz(t,0.25),g=ac.createGain();env(g,t,0.001,v,open?0.16:0.035);n.connect(filt('highpass',7500)).connect(g);out(g,pan,0.1)};
  const bass=(t,f,d,v=0.3)=>{const o=osc('sawtooth',f,t,d+0.1),lp=filt('lowpass',300,4),g=ac.createGain();
    lp.frequency.setValueAtTime(2200,t);lp.frequency.exponentialRampToValueAtTime(260,t+d);env(g,t,0.004,v,d);o.connect(lp).connect(g);out(g,0,0,duck);
    const s=osc('sine',f/2,t,d+0.1),gs=ac.createGain();env(gs,t,0.004,v*1.1,d);s.connect(gs);out(gs,0,0,duck)};
  const stab=(t,notes,v=0.06)=>notes.forEach((n,i)=>[-9,9].forEach(dt=>{const o=osc('sawtooth',mf(n),t,0.5);o.detune.value=dt;
    const lp=filt('lowpass',3000,1.2),g=ac.createGain();lp.frequency.setValueAtTime(3200,t);lp.frequency.exponentialRampToValueAtTime(700,t+0.3);env(g,t,0.003,v,0.3);o.connect(lp).connect(g);out(g,(i-1)*0.45+dt/40,0.4,duck)}));
  const pad=(t0,t1,notes,v=0.035)=>notes.forEach((n,i)=>[-12,12].forEach(dt=>{const o=osc('sawtooth',mf(n),t0,t1-t0+0.6);o.detune.value=dt;
    const lp=filt('lowpass',1000,0.6),g=ac.createGain();g.gain.setValueAtTime(0,t0);g.gain.linearRampToValueAtTime(v,t0+0.5);g.gain.setValueAtTime(v,t1);g.gain.linearRampToValueAtTime(0,t1+0.5);
    o.connect(lp).connect(g);out(g,(i%2?0.5:-0.5)*(dt>0?1:-1),0.6,duck)}));
  const whoosh=(t0,t1,f0,f1,v=0.3,p0=-0.7,p1=0.7)=>{const n=nz(t0,t1-t0+0.05),b=filt('bandpass',f0,1.6),g=ac.createGain(),p=ac.createStereoPanner();
    b.frequency.setValueAtTime(f0,t0);b.frequency.exponentialRampToValueAtTime(f1,t1);g.gain.setValueAtTime(0,t0);g.gain.linearRampToValueAtTime(v,t0+(t1-t0)*0.75);g.gain.exponentialRampToValueAtTime(0.0001,t1);
    p.pan.setValueAtTime(p0,t0);p.pan.linearRampToValueAtTime(p1,t1);n.connect(b).connect(g).connect(p);p.connect(bus);const s=ac.createGain();s.gain.value=0.3;p.connect(s);s.connect(verb)};
  const riser=(t0,t1,v=0.25)=>{const n=nz(t0,t1-t0),hp=filt('highpass',300,2),g=ac.createGain();hp.frequency.setValueAtTime(300,t0);hp.frequency.exponentialRampToValueAtTime(9000,t1);
    g.gain.setValueAtTime(0.0001,t0);g.gain.exponentialRampToValueAtTime(v,t1-0.01);g.gain.linearRampToValueAtTime(0,t1);n.connect(hp).connect(g);out(g,0,0.3);
    const o=osc('sawtooth',110,t0,t1-t0),go=ac.createGain();o.frequency.setValueAtTime(110,t0);o.frequency.exponentialRampToValueAtTime(880,t1);
    go.gain.setValueAtTime(0.0001,t0);go.gain.exponentialRampToValueAtTime(v*0.25,t1-0.01);go.gain.linearRampToValueAtTime(0,t1);o.connect(filt('lowpass',2500)).connect(go);out(go,0,0.3)};
  const boom=(t,v=0.8,d=1.6)=>{const o=osc('sine',70,t,d+0.2),g=ac.createGain();o.frequency.setValueAtTime(70,t);o.frequency.exponentialRampToValueAtTime(28,t+d);env(g,t,0.005,v,d);o.connect(g);out(g)};
  const crash=(t,v=0.3,d=1.4)=>{const n=nz(t,d+0.1),g=ac.createGain();env(g,t,0.002,v,d);n.connect(filt('highpass',2800)).connect(g);out(g,0,0.6)};
  const blip=(t,f,v=0.08,type='triangle',d=0.09,pan=0)=>{const o=osc(type,f,t,d+0.05),g=ac.createGain();env(g,t,0.002,v,d);o.connect(g);out(g,pan,0.3)};
  const bell=(t,f,v=0.12)=>[[1,1,1.8],[2.76,.45,1],[5.4,.22,.6],[8.93,.1,.3]].forEach(([r,a,d])=>{const o=osc('sine',f*r,t,d+0.1),g=ac.createGain();env(g,t,0.002,v*a,d);o.connect(g);out(g,0,0.6)});
  const tick=(t,v=0.12)=>{const n=nz(t,0.02),g=ac.createGain();env(g,t,0.001,v,0.012);n.connect(filt('bandpass',4200,2)).connect(g);out(g,(R()-.5)*0.6)};

  // bar 1 — intro
  boom(0.01,0.5,1.4);bell(0.04,mf(81),0.07);pad(0,1.9,[45,57,60,64,67,71],0.03);
  blip(0.02,mf(93),0.07,'sine',0.12);whoosh(0.24,0.64,300,3200,0.22);
  [69,72,76,79,81,84].forEach((n,i)=>blip(0.6+i*0.045,mf(n),0.06,'triangle',0.12,(i-2.5)*0.25));
  for(let i=0;i<20;i+=2)tick(1.12+i*0.019,0.06);
  riser(1.0,1.875,0.22);whoosh(1.6,1.9,500,6000,0.32,-0.9,0.9);
  // bars 2–7 — groove
  const chords=[[57,60,64],[53,57,60],[55,60,64],[55,59,62],[57,60,64],[53,57,60]],roots=[45,41,48,43,45,41];
  for(let b=1;b<=6;b++){
    const t0=b*BAR,ch=chords[b-1],rt=roots[b-1];
    for(let k=0;k<4;k++){
      kick(t0+k*BEAT,b==1&&k==0?1.15:1);
      if(k%2)clap(t0+k*BEAT);
      hat(t0+(k+0.5)*BEAT,true,0.09,0.25);
      for(const s of [0.25,0.75])hat(t0+(k+s)*BEAT,false,0.045,-0.3);
      bass(t0+(k+0.5)*BEAT,mf(rt),0.19);if(k%2)bass(t0+(k+0.75)*BEAT,mf(rt+12),0.1,0.2);
    }
    if(b<6){stab(t0,ch.map(n=>n+12));stab(t0+1.5*BEAT,ch.map(n=>n+12),0.045);stab(t0+2.75*BEAT,ch.map(n=>n+12),0.045)}
  }
  whoosh(3.5,3.8,300,4500,0.3);whoosh(5.4,5.7,4000,250,0.28,0.8,-0.8);
  whoosh(6.8,7.5,180,7000,0.38,-0.3,0.3);boom(7.5,0.45,0.8);crash(7.5,0.12,0.6);
  whoosh(8.25,8.44,800,5000,0.22);boom(8.4375,0.5,0.7);bell(8.45,mf(88),0.05);
  for(let i=0;i<8;i++)blip(6.6+i*0.045,mf([81,84,88,91,93,96,100,103][i]),0.035,'sine',0.15,(R()-.5)*1.4);
  whoosh(9.1,9.4,3500,200,0.25,0.6,-0.6);whoosh(9.95,10.33,200,1600,0.3,-0.5,0.5);
  DATA.forEach((_,i)=>blip(10.36+i*0.025,mf(72+i*2),0.03,'sine',0.06,(i-6)*0.1));
  whoosh(11.05,11.25,600,5000,0.25);
  // bar 7 — style-frame montage: cut blips, snare roll, riser
  for(let k=0;k<8;k++){const t=6*BAR+k*HB;blip(t,mf(84+k*2),0.06,'square',0.04,(k%2?0.5:-0.5));tick(t,0.18)}
  for(let k=0;k<16;k++){const t=6*BAR+(k<4?k*HB:k<8?2*BEAT+(k-4)*BEAT/4:3*BEAT+(k-8)*BEAT/8);clap(t,0.12+0.3*k/16)}
  riser(6*BAR,7*BAR,0.32);
  // bar 8 — the drop and the title
  kick(7*BAR,1.4);boom(7*BAR,0.95,2.0);crash(7*BAR,0.34,1.6);
  pad(7*BAR+0.02,DUR-0.3,[45,52,57,60,64,71],0.04);bass(7*BAR,mf(45),1.6,0.25);
  [0,1,2,3].forEach(i=>bell(7*BAR+0.27+i*0.07,mf([81,84,88,93][i]),0.06));
  for(let i=0;i<31;i+=2)tick(7*BAR+0.78+i*0.0129,0.07);
  bell(7*BAR+1.18,mf(76),0.05);
  return ac.startRendering();
}
function wavB64(buf){
  const n=buf.length,sr=buf.sampleRate,ab=new ArrayBuffer(44+n*4),dv=new DataView(ab),ws=(o,s)=>[...s].forEach((c,i)=>dv.setUint8(o+i,c.charCodeAt(0)));
  ws(0,'RIFF');dv.setUint32(4,36+n*4,true);ws(8,'WAVE');ws(12,'fmt ');dv.setUint32(16,16,true);dv.setUint16(20,1,true);dv.setUint16(22,2,true);
  dv.setUint32(24,sr,true);dv.setUint32(28,sr*4,true);dv.setUint16(32,4,true);dv.setUint16(34,16,true);ws(36,'data');dv.setUint32(40,n*4,true);
  const L=buf.getChannelData(0),Rr=buf.getChannelData(1);
  for(let i=0;i<n;i++){dv.setInt16(44+i*4,clamp(L[i],-1,1)*32767,true);dv.setInt16(46+i*4,clamp(Rr[i],-1,1)*32767,true)}
  const u8=new Uint8Array(ab);let s='';for(let i=0;i<u8.length;i+=0x8000)s+=String.fromCharCode.apply(null,u8.subarray(i,i+0x8000));return btoa(s);
}

/* ============================================================ entry points */
initParticles();
window.frameAt=(t,sub)=>{frame(t,sub);return out.canvas.toDataURL('image/png')};
window.audioWav=async()=>wavB64(await buildAudio());
window.VIDEO={width:W,height:H,fps:FPS,duration:DUR};
window.READY=true;
const btn=document.getElementById('play');
if(location.search.includes('render'))btn.remove();
else{
  frame(DUR-0.01);
  btn.onclick=async()=>{
    btn.textContent='RENDERING AUDIO…';
    const buf=await buildAudio(),ac=new AudioContext(),src=ac.createBufferSource();src.buffer=buf;src.connect(ac.destination);
    btn.style.display='none';const t0=ac.currentTime+0.05;src.start(t0);
    const loop=()=>{const t=ac.currentTime-t0;if(t<DUR){frame(max(0,t));requestAnimationFrame(loop)}else{btn.style.display='';btn.textContent='↻ REPLAY'}};loop();
  };
}
</script>
</body>
</html>
`````

### `examples/dino.html`

<!-- FILE: examples/dino.html sha256=e34b10ad9246209856c30d85e5eebb7122451d2e884b3a0f15207b821df85bad -->
`````html
<!doctype html>
<html lang="ru">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Dino Blocks</title>
<style>
:root{--bg:#0d1830;--fg:#fff}
html,body{margin:0;height:100%;background:var(--bg);color:var(--fg);font-family:Consolas,monospace}
body{display:grid;place-items:center}
#wrap{position:relative;width:min(calc(100vw - 32px),calc((100vh - 32px)*16/9));aspect-ratio:16/9}
canvas{width:100%;height:100%;display:block}
#play{position:absolute;inset:0;display:grid;place-items:center;background:rgba(13,24,48,.5);cursor:pointer;font:inherit;font-size:clamp(14px,2.4vw,28px);letter-spacing:.3em;border:0;color:inherit}
</style>
</head>
<body>
<div id="wrap"><canvas id="c" width="1920" height="1080"></canvas><button id="play">&#9654; ИГРАТЬ</button></div>
<script>
'use strict';
/* ============================================================ core */
const W=1920,H=1080,FPS=60,DUR=15,BPM=128,BEAT=60/BPM,BAR=BEAT*4,TAU=Math.PI*2;
const {sin,cos,abs,floor,min,max,PI,sqrt,pow,exp,round,hypot}=Math;
const clamp=(x,a=0,b=1)=>x<a?a:x>b?b:x;
const lerp=(a,b,t)=>a+(b-a)*t;
const P=(t,a,b)=>clamp((t-a)/(b-a));
const E={
  outCubic:t=>1-pow(1-t,3), inCubic:t=>t*t*t, inOutCubic:t=>t<.5?4*t*t*t:1-pow(-2*t+2,3)/2,
  outBack:t=>{const c1=1.70158,c3=c1+1;return 1+c3*pow(t-1,3)+c1*pow(t-1,2)},
};
function rng(a){return()=>{a|=0;a=a+0x6D2B79F5|0;let t=Math.imul(a^a>>>15,1|a);t=t+Math.imul(t^t>>>7,61|t)^t;return((t^t>>>14)>>>0)/4294967296}}
const hash=(x,y=0,s=0)=>{const v=sin(x*127.1+y*311.7+s*74.7)*43758.5453;return v-floor(v)};
const hex=h=>[1,3,5].map(i=>parseInt(h.slice(i,i+2),16));
function mk(w=W,h=H){const c=document.createElement('canvas');c.width=w;c.height=h;return c}
const kickEnv=t=>(t<BAR||t>=7*BAR)?0:exp(-(t%BEAT)*12);
const HITS=[5*BAR,5*BAR+2*BEAT,6*BAR,6*BAR+BEAT,6*BAR+2*BEAT,6*BAR+3*BEAT,7*BAR]; // last one = big finale
const FINAL=7*BAR;

/* ============================================================ 3x3 math */
const mv=(M,v)=>[M[0]*v[0]+M[1]*v[1]+M[2]*v[2],M[3]*v[0]+M[4]*v[1]+M[5]*v[2],M[6]*v[0]+M[7]*v[1]+M[8]*v[2]];
const mm=(A,B)=>{const R=new Array(9);for(let i=0;i<3;i++)for(let j=0;j<3;j++)R[i*3+j]=A[i*3]*B[j]+A[i*3+1]*B[3+j]+A[i*3+2]*B[6+j];return R};
const I3=[1,0,0,0,1,0,0,0,1];
const rotY=a=>{const c=cos(a),s=sin(a);return[c,0,s,0,1,0,-s,0,c]};
const rotZ=a=>{const c=cos(a),s=sin(a);return[c,-s,0,s,c,0,0,0,1]};
const axAng=(k,a)=>{const c=cos(a),s=sin(a),t=1-c,[x,y,z]=k;return[t*x*x+c,t*x*y-s*z,t*x*z+s*y,t*x*y+s*z,t*y*y+c,t*y*z-s*x,t*x*z-s*y,t*y*z+s*x,t*z*z+c]};
const sc=(M,s)=>M.map(v=>v*s);
const add=(a,b)=>[a[0]+b[0],a[1]+b[1],a[2]+b[2]];
const sub=(a,b)=>[a[0]-b[0],a[1]-b[1],a[2]-b[2]];
const lerp3=(a,b,t)=>[lerp(a[0],b[0],t),lerp(a[1],b[1],t),lerp(a[2],b[2],t)];
const randAxis=R=>{const a=[R()-.5,R()-.5,R()-.5],l=hypot(...a)||1;return a.map(v=>v/l)};

/* ============================================================ pixel textures (16x16, 12 shade levels) */
const LV=12;
function shades(fn,seed){
  const R=rng(seed),base=[];for(let y=0;y<16;y++)for(let x=0;x<16;x++)base.push(fn(x,y,R));
  return Array.from({length:LV},(_,k)=>{const b=0.45+k*0.05,c=mk(16,16),g=c.getContext('2d'),id=g.createImageData(16,16);
    base.forEach(([r,gg,bb],i)=>{id.data[i*4]=r*b;id.data[i*4+1]=gg*b;id.data[i*4+2]=bb*b;id.data[i*4+3]=255});g.putImageData(id,0,0);return c});
}
const nz=(h,amp)=>{const c=hex(h);return(x,y,R)=>{const f=1+(R()-.5)*amp;return c.map(v=>v*f)}};
const grassTop=nz('#69B23E',.3),dirt=nz('#8B5B34',.34);
const grassSide=(x,y,R)=>(y<3||(y==3&&hash(x,1)>.45)||(y==4&&hash(x,2)>.8))?grassTop(x,y,R):dirt(x,y,R);
const planks=(x,y,R)=>{const row=floor(y/4);return(y%4==3||x==(row*5+3)%16)?hex('#7A5630').map(v=>v*(0.9+R()*.15)):nz('#A97E4A',.14)(x,y,R)};
const iron=(x,y,R)=>(x==0||y==0)?hex('#F4F6F8'):(x==15||y==15)?hex('#8F959E'):nz('#D3D8DE',.07)(x,y,R);
const leaves=(x,y,R)=>R()<.16?hex('#25611A'):nz('#3E8F2C',.35)(x,y,R);
const bark=(x,y,R)=>x%4==0?hex('#4E3820'):nz('#6B4E2E',.25)(x,y,R);
const grip=(x,y,R)=>(x+y)%5==0?hex('#3A2412'):nz('#5C3B20',.2)(x,y,R);
function material(seed,top,side=top,bottom=side){return{top:shades(top,seed),side:shades(side,seed+1),bottom:shades(bottom,seed+2)}}
const M={
  grass:material(1,grassTop,grassSide,dirt), dirt:material(4,dirt), stone:material(7,nz('#8E8E8E',.32)),
  blue:material(10,nz('#2F7DF2',.18)), belly:material(13,nz('#8FD0FF',.1)), spike:material(16,nz('#1A4FB5',.16)),
  white:material(19,nz('#F3F6FA',.06)), black:material(22,nz('#15171C',.12)), mouth:material(25,nz('#10296A',.12)),
  wood:material(28,planks), grip:material(31,grip), iron:material(34,iron), ironDark:material(37,nz('#8F959E',.1)),
  leaves:material(40,leaves), log:material(43,nz('#B08850',.2),bark),
};
const BLUE_PIX=[0,1,2].map(i=>{const c=hex(['#2F7DF2','#5FA2FF','#1A4FB5'][i]);return `rgb(${c})`});

/* ============================================================ models */
function boxInto(map,x0,x1,y0,y1,z0,z1,m){for(let x=x0;x<=x1;x++)for(let y=y0;y<=y1;y++)for(let z=z0;z<=z1;z++)map.set(`${x},${y},${z}`,m)}
const NB=[[1,0,0],[-1,0,0],[0,0,1],[0,0,-1],[0,1,0],[0,-1,0]];
function finalize(map,segOf){
  const list=[...map].map(([k,m])=>{const g=k.split(',').map(Number);return{g,m,seg:segOf?segOf(g):0}});
  const idx=new Map(list.map((v,i)=>[v.g.join(','),i]));
  list.forEach(v=>{v.nb=NB.map(d=>{const j=idx.get(`${v.g[0]+d[0]},${v.g[1]+d[1]},${v.g[2]+d[2]}`);return j!==undefined&&list[j].seg===v.seg?j:-1})});
  return list;
}
// --- island + tree
const GROUND=(()=>{const m=new Map();boxInto(m,-18,25,-1,-1,-8,8,'grass');boxInto(m,-18,25,-3,-2,-8,8,'dirt');boxInto(m,-17,24,-4,-4,-7,7,'stone');
  const L=finalize(m);L.forEach(v=>{v.d=hypot(v.g[0]-3,v.g[2]*1.4)});return L})();
const TREE=(()=>{const m=new Map();boxInto(m,-17,-13,4,5,-9,-5,'leaves');boxInto(m,-16,-14,6,6,-8,-6,'leaves');m.set('-15,7,-7','leaves');boxInto(m,-15,-15,0,4,-7,-7,'log');return finalize(m)})();
// --- the dinosaur (faces +x)
const DINO=(()=>{
  const m=new Map(),b=(...a)=>boxInto(m,...a);
  for(const [z0,z1] of [[-3,-2],[2,3]]){b(-2,2,0,0,z0,z1,'blue');b(-2,0,1,3,z0,z1,'blue');b(-3,1,4,6,z0,z1,'blue')}
  b(-5,3,4,10,-2,2,'blue');                                // body
  b(-8,-6,5,9,-2,2,'blue');b(-11,-9,5,7,-1,1,'blue');b(-14,-12,4,5,-1,1,'blue');b(-16,-15,4,4,0,0,'blue'); // tail
  b(2,5,10,13,-2,2,'blue');b(3,10,13,18,-2,2,'blue');      // neck + head
  for(const [k,v] of m)if(v==='blue'){const [x,y,z]=k.split(',').map(Number);if(x>=0&&x<=5&&y<=8&&abs(z)<=1)m.set(k,'belly')}
  b(7,10,14,14,-2,2,'mouth');for(const z of [-2,2]){m.set(`8,14,${z}`,'white');m.set(`10,14,${z}`,'white')}m.set('10,14,-1','white');m.set('10,14,1','white');
  for(const z of [-2,2]){m.set(`6,17,${z}`,'white');m.set(`7,17,${z}`,'white');m.set(`6,16,${z}`,'white');m.set(`7,16,${z}`,'black')}
  for(const z of [-1,1])m.set(`10,17,${z}`,'black');
  for(const z of [-3,3]){m.set(`3,7,${z}`,'blue');m.set(`4,7,${z}`,'blue');m.set(`5,7,${z}`,'blue');m.set(`5,6,${z}`,'white')}
  for(let x=-15;x<=5;x+=2){let top=-1;for(let y=0;y<30;y++)if(m.has(`${x},${y},0`))top=y;if(top<0)continue;m.set(`${x},${top+1},0`,'spike');if(x>=-8&&x<=3)m.set(`${x},${top+2},0`,'spike')}
  const L=finalize(m,([x,y])=>x<=-6?1:(x>=2&&y>=11?2:0)),R=rng(99);
  L.forEach(v=>{
    const [x,y,z]=v.g;v.T=[x,y+0.5,z];
    v.s=1.95+clamp(y/21)*4.25+R()*0.18;
    if(v.m==='white'&&y>=16||v.m==='black'&&y==16){v.s=6.72+R()*0.08;v.eye=true}
    v.off=[(R()-.5)*22,16+R()*14,(R()-.5)*22];v.ax=randAxis(R);v.ang=(1+R())*PI;
    const d=sub(v.T,[6,17,0]),l=hypot(...d)||1,sp=9+R()*11;v.vel=[d[0]/l*sp,d[1]/l*sp+7+R()*7,d[2]/l*sp+(R()-.5)*6];v.spin=(R()-.5)*18;
    v.r=13.62+clamp(y/21)*0.55+R()*0.12;
  });
  return L;
})();
// --- the hammer (local: pivot at origin, handle along +Y, head along X)
const HAMMER=(()=>{
  const m=new Map();boxInto(m,0,1,0,10,0,1,'wood');boxInto(m,0,1,0,2,0,1,'grip');boxInto(m,-3,4,11,14,-1,2,'iron');
  for(const x of [-3,4])boxInto(m,x,x,11,14,-1,2,'ironDark');
  const L=finalize(m),R=rng(7);
  L.forEach(v=>{const [x,y,z]=v.g;v.L=[x-0.5,y+0.5,z-0.5];v.s=7.62+y/14*0.95+R()*0.1;v.off=[18+R()*16,10+R()*16,(R()-.5)*24];v.ax=randAxis(R);v.ang=(1+R())*PI;
    v.vel=[12+R()*16,20+R()*12,(R()-.5)*18];v.spin=(R()-.5)*16});
  return L;
})();
const PIVOT=[20,23,0],RAISE=-0.35,DOWN=PI/2;

/* ============================================================ animation state */
function hammerTheta(t){
  let th=RAISE;
  for(const h of HITS){
    if(t>=h-0.22&&t<h-0.11)th=lerp(RAISE,RAISE-0.4,E.outCubic(P(t,h-0.22,h-0.11)));
    else if(t>=h-0.11&&t<h)th=lerp(RAISE-0.4,DOWN,E.inCubic(P(t,h-0.11,h)));
    else if(t>=h&&t<h+0.24&&h<FINAL)th=lerp(DOWN,RAISE,E.inOutCubic(P(t,h+0.03,h+0.24)))-0.22*sin(P(t,h,h+0.09)*PI);
  }
  return t>=FINAL?DOWN:th;
}
const lastHit=t=>{let h=-9;for(const x of HITS)if(t>=x)h=x;return h};
function squash(t){let s=0;for(const h of HITS)if(h<FINAL&&t>=h&&t<h+1.2)s+=exp(-(t-h)*7)*cos((t-h)*26)*0.3;return s}
const bump=(t,a,b)=>sin(P(t,a,b)*PI);
function nod(t){let n=0.05*kickEnv(t)*P(t,6.6,7.0)+0.3*bump(t,6.95,7.5);for(const h of HITS)if(h<FINAL&&t>=h)n-=0.28*exp(-(t-h)*8);return n}
function eyesClosed(t){if(abs(t-7.75)<0.06||abs(t-8.85)<0.06)return true;const h=lastHit(t);return h>0&&h<FINAL&&t-h<0.35}

/* ============================================================ camera */
const CK=[[0,70,36,13,[2,0,0]],[1.875,48,28,23,[-2,5,0]],[6.6,26,24,29,[-2,9,0]],[7.5,22,24,28,[0,11,0]],[9.15,30,22,22.5,[5,16,0]],[13.1,40,22,22.5,[5,15.5,0]],[13.75,46,27,21,[-1,9,0]],[15,32,24,27,[-1,12.5,0]]];
let CAM;
function setCam(t){
  let i=0;while(i<CK.length-2&&t>=CK[i+1][0])i++;
  const a=CK[i],b=CK[i+1],u=E.inOutCubic(P(t,a[0],b[0]));
  const yaw=lerp(a[1],b[1],u)*PI/180,pit=lerp(a[2],b[2],u)*PI/180;
  let k=lerp(a[3],b[3],u);const tg=lerp3(a[4],b[4],u);
  for(const h of HITS)if(t>=h)k*=1+(h==FINAL?0.1:0.05)*exp(-(t-h)*10);
  let sx=0,sy=0;const h=lastHit(t),amp=(h>0?(h==FINAL?34:14)*exp(-(t-h)*14):0)+3*kickEnv(t);
  if(amp>0.05){const R=rng(floor(t*FPS*2)+3);sx=(R()-.5)*2*amp;sy=(R()-.5)*2*amp}
  const cp=cos(pit),sp=sin(pit),cy=cos(yaw),sy2=sin(yaw);
  CAM={yaw,k,tg,ox:W/2+sx,oy:H/2+sy,c:[cp*sy2,sp,cp*cy],r:[cy,0,-sy2],u:[-sp*sy2,cp,-sp*cy]};
}
const proj=p=>{const d=sub(p,CAM.tg),r=CAM.r,u=CAM.u;return[CAM.ox+CAM.k*(r[0]*d[0]+r[2]*d[2]),CAM.oy-CAM.k*(u[0]*d[0]+u[1]*d[1]+u[2]*d[2])]};
const projv=v=>{const r=CAM.r,u=CAM.u;return[CAM.k*(r[0]*v[0]+r[2]*v[2]),-CAM.k*(u[0]*v[0]+u[1]*v[1]+u[2]*v[2])]};

/* ============================================================ cube renderer (orthographic, painter's order, textured via affine) */
// per face: [edge index, sign, U edge (sign), V edge (sign), texture kind]
const FACES=[[0,1,[2,-1],[1,-1],'side'],[0,-1,[2,1],[1,-1],'side'],[2,1,[0,1],[1,-1],'side'],[2,-1,[0,-1],[1,-1],'side'],[1,1,[0,1],[2,1],'top'],[1,-1,[0,1],[2,-1],'bottom']];
function drawCube(ctx,q){
  const Mx=q.M,ed=[[Mx[0],Mx[3],Mx[6]],[Mx[1],Mx[4],Mx[7]],[Mx[2],Mx[5],Mx[8]]],pe=ed.map(projv),[px,py]=proj(q.c),cc=CAM.c,mat=M[q.m];
  for(let f=0;f<6;f++){
    if(q.cull&(1<<f))continue;
    const [a,s,[ua,us],[va,vs],kind]=FACES[f],e=ed[a],l=hypot(e[0],e[1],e[2])||1,nx=e[0]*s/l,ny=e[1]*s/l,nzz=e[2]*s/l;
    if(nx*cc[0]+ny*cc[1]+nzz*cc[2]<=1e-4)continue;
    const b=0.75+0.25*ny+0.08*nzz-0.05*nx,tex=mat[kind][clamp(round((b-0.45)/0.05),0,LV-1)];
    const Ux=pe[ua][0]*us,Uy=pe[ua][1]*us,Vx=pe[va][0]*vs,Vy=pe[va][1]*vs;
    const fx=px+pe[a][0]*s/2-Ux/2-Vx/2,fy=py+pe[a][1]*s/2-Uy/2-Vy/2;
    ctx.setTransform(Ux/16,Uy/16,Vx/16,Vy/16,fx,fy);ctx.drawImage(tex,-0.16,-0.16,16.32,16.32);
  }
}
function cullMask(v,free,list){let c=0;if(free[v.i])return 0;for(let f=0;f<6;f++){const j=v.nb[f];if(j>=0&&!free[j])c|=1<<f}return c}

function groundCubes(t){
  const out=[],free=GROUND.map(v=>t<v.d*0.03+0.62);
  GROUND.forEach((v,i)=>{v.i=i;const u=P(t,0.1+v.d*0.03,0.6+v.d*0.03);if(u<=0)return;
    const dy=-14*(1-E.outBack(u));out.push({c:[v.g[0],v.g[1]+0.5+dy,v.g[2]],M:I3,m:v.m,cull:cullMask(v,free)})});
  TREE.forEach((v,i)=>{const u=P(t,1.05+v.g[1]*0.05,1.45+v.g[1]*0.05);if(u<=0)return;const s=E.outBack(u);
    out.push({c:[v.g[0],v.g[1]+0.5,v.g[2]],M:sc(I3,s),m:v.m,cull:u>=1?(v.nb.reduce((c,j,f)=>j>=0?c|1<<f:c,0)):0})});
  return out;
}
function dinoCubes(t,out){
  const sq=squash(t),sy=1-sq,sxz=1+sq*0.5,S=[sxz,0,0,0,sy,0,0,0,sxz],base=[-2,0,0];
  const wagA=sin(t*5.2)*0.16*P(t,6.4,6.8),nd=nod(t),Rt=rotY(wagA),Rh=rotZ(nd),closed=eyesClosed(t),ex=t-FINAL;
  const free=new Array(DINO.length);let settled=0;
  const st=DINO.map((v,i)=>{
    v.i=i;if(t<v.s){free[i]=true;return null}
    const e=E.outCubic(P(t,v.s,v.s+0.42));
    let R=I3,c=v.T;
    if(v.seg==1){R=Rt;c=add([-5,0,0],mv(Rt,sub(c,[-5,0,0])))}
    else if(v.seg==2){R=Rh;c=add([3,11,0],mv(Rh,sub(c,[3,11,0])))}
    c=add(base,mv(S,sub(c,base)));let Mx=mm(S,R);
    if(e<1){c=lerp3(add(v.T,v.off),c,e);Mx=sc(mm(Mx,axAng(v.ax,v.ang*(1-e))),lerp(0.3,1,E.outBack(P(t,v.s,v.s+0.3))))}
    let fr=e<1;
    if(ex>=0){
      const tau=min(ex,v.r-FINAL),re=E.inOutCubic(P(t,v.r,v.r+0.42));
      const pe=[v.T[0]+v.vel[0]*tau,max(0.5,v.T[1]+v.vel[1]*tau-16*tau*tau),v.T[2]+v.vel[2]*tau];
      c=lerp3(pe,c,re);Mx=mm(Mx,axAng(v.ax,v.spin*tau*(1-re)));fr=re<1;
    }
    free[i]=fr;if(!fr||ex<0&&e>=1)settled++;
    return{c,M:Mx,m:v.eye&&closed?'blue':v.m};
  });
  DINO.forEach((v,i)=>{if(st[i]){st[i].cull=cullMask(v,free);out.push(st[i])}});
  return settled;
}
function hammerCubes(t,out){
  if(t<HAMMER[0].s-0.01)return 0;
  const th=hammerTheta(t),R=rotZ(th),pv=[PIVOT[0],PIVOT[1]+sin(t*3)*0.3*(t<FINAL),PIVOT[2]],ex=t-FINAL;
  const free=new Array(HAMMER.length);let n=0;const st=[];
  HAMMER.forEach((v,i)=>{
    v.i=i;if(t<v.s){free[i]=true;return}
    const e=E.outCubic(P(t,v.s,v.s+0.4));let c=add(pv,mv(R,v.L)),Mx=R;
    if(e<1){c=lerp3(add(c,v.off),c,e);Mx=sc(mm(R,axAng(v.ax,v.ang*(1-e))),lerp(0.3,1,E.outBack(P(t,v.s,v.s+0.3))))}else n++;
    if(ex>=0){c=[c[0]+v.vel[0]*ex,c[1]+v.vel[1]*ex-16*ex*ex,c[2]+v.vel[2]*ex];Mx=sc(mm(R,axAng(v.ax,v.spin*ex)),1-E.inCubic(P(ex,0,0.8)))}
    free[i]=e<1||ex>=0;st[i]={c,M:Mx,m:v.m};
  });
  HAMMER.forEach((v,i)=>{if(st[i]){st[i].cull=cullMask(v,free);out.push(st[i])}});
  return ex>=0?0:n;
}

/* ============================================================ 2D overlays */
const GL={
  'Д':['.###.','.#.#.','.#.#.','.#.#.','.#.#.','#####','#...#'],'И':['#...#','#...#','#..##','#.#.#','##..#','#...#','#...#'],
  'Н':['#...#','#...#','#...#','#####','#...#','#...#','#...#'],'О':['.###.','#...#','#...#','#...#','#...#','#...#','.###.'],
  'Б':['#####','#....','#....','####.','#...#','#...#','####.'],'А':['.###.','#...#','#...#','#####','#...#','#...#','#...#'],
  'М':['#...#','##.##','#.#.#','#.#.#','#...#','#...#','#...#'],'У':['#...#','#...#','#...#','.####','....#','#...#','.###.'],
  'Х':['#...#','#...#','.#.#.','..#..','.#.#.','#...#','#...#'],'!':['..#..','..#..','..#..','..#..','..#..','.....','..#..'],
};
function pixText(ctx,str,cx,cy,px,front,side,outline,anim){
  const ch=[...str],cw=6.6*px,total=ch.length*cw-1.6*px,ex=px*0.3;
  ch.forEach((c,i)=>{
    const g=GL[c],A=anim(i);if(!g||A.a<=0)return;
    ctx.save();ctx.globalAlpha=A.a;ctx.translate(cx-total/2+i*cw+2.5*px,cy+A.dy);ctx.rotate(A.r||0);ctx.scale(A.s,A.s);ctx.translate(-2.5*px,-3.5*px);
    const cells=[];g.forEach((row,y)=>[...row].forEach((v,x)=>{if(v==='#')cells.push([x*px,y*px])}));
    ctx.fillStyle=outline;cells.forEach(([x,y])=>ctx.fillRect(x-3,y-3,px+6+ex,px+6+ex));
    ctx.fillStyle=side;cells.forEach(([x,y])=>ctx.fillRect(x+ex,y+ex,px+0.6,px+0.6));
    ctx.fillStyle=front;cells.forEach(([x,y])=>ctx.fillRect(x,y,px+0.6,px+0.6));
    ctx.fillStyle='rgba(255,255,255,.35)';cells.forEach(([x,y])=>ctx.fillRect(x,y,px+0.6,px*0.2));
    ctx.restore();
  });
}
function star(ctx,x,y,p,col){ctx.fillStyle='#5A3E00';[[0,-2],[0,-1],[-2,0],[-1,0],[0,0],[1,0],[2,0],[0,1],[0,2]].forEach(([a,b])=>ctx.fillRect(x+a*p-2,y+b*p-2,p+4,p+4));ctx.fillStyle=col;[[0,-2],[0,-1],[-2,0],[-1,0],[0,0],[1,0],[2,0],[0,1],[0,2]].forEach(([a,b])=>ctx.fillRect(x+a*p,y+b*p,p,p))}
const CLOUDS=(()=>{const R=rng(11);return Array.from({length:7},(_,i)=>({x:R()*W*1.6,y:70+R()*260,w:180+R()*260,h:34+R()*26,v:14+R()*18,z:0.5+R()*0.8}))})();
function sky(ctx,t){
  const g=ctx.createLinearGradient(0,0,0,H);g.addColorStop(0,'#6E9DFF');g.addColorStop(0.65,'#A9D2FF');g.addColorStop(1,'#D4ECFF');ctx.fillStyle=g;ctx.fillRect(0,0,W,H);
  const sx=1640-CAM.yaw*120,sy=170;ctx.fillStyle='rgba(255,250,200,.25)';ctx.fillRect(sx-110,sy-110,220,220);ctx.fillStyle='#FFF4A8';ctx.fillRect(sx-68,sy-68,136,136);
  for(const c of CLOUDS){const x=((c.x+t*c.v-CAM.yaw*300*c.z)%(W+800)+W+800)%(W+800)-400;
    ctx.fillStyle='rgba(255,255,255,.92)';ctx.fillRect(x,c.y,c.w,c.h);ctx.fillRect(x+c.w*0.2,c.y-c.h*0.6,c.w*0.45,c.h*0.6);ctx.fillStyle='rgba(214,226,242,.9)';ctx.fillRect(x,c.y+c.h,c.w,8)}
}
function shadow(ctx,center,rx,rz,a){
  if(a<=0.003)return;const o=proj(center),U=projv([rx,0,0]),V=projv([0,0,rz]);
  ctx.save();ctx.setTransform(U[0],U[1],V[0],V[1],o[0],o[1]);ctx.fillStyle=`rgba(20,40,10,${a})`;ctx.beginPath();ctx.arc(0,0,1,0,TAU);ctx.fill();ctx.restore();
}

/* ============================================================ frame */
function renderAt(ctx,t){
  ctx.setTransform(1,0,0,1,0,0);ctx.globalAlpha=1;ctx.globalCompositeOperation='source-over';ctx.imageSmoothingEnabled=false;
  setCam(t);sky(ctx,t);
  const g=groundCubes(t).map(q=>(q.d=q.c[0]*CAM.c[0]+q.c[1]*CAM.c[1]+q.c[2]*CAM.c[2],q)).sort((a,b)=>a.d-b.d);
  for(const q of g)drawCube(ctx,q);
  ctx.setTransform(1,0,0,1,0,0);
  const objs=[];const nd=dinoCubes(t,objs),nh=hammerCubes(t,objs);
  shadow(ctx,[-3,0.02,0],12.5,4.6,0.22*P(t,2.5,6.5)*(1-P(t,FINAL,FINAL+0.2)+P(t,14.2,14.6)));
  if(t>=8.4&&t<FINAL+0.2){const th=hammerTheta(t),hx=PIVOT[0]-sin(th)*13;shadow(ctx,[hx,0.02,0],4.5,3.5,0.16*P(t,8.4,9)*(1-P(t,FINAL,FINAL+0.2)))}
  objs.forEach(q=>q.d=q.c[0]*CAM.c[0]+q.c[1]*CAM.c[1]+q.c[2]*CAM.c[2]);objs.sort((a,b)=>a.d-b.d);
  for(const q of objs)drawCube(ctx,q);
  ctx.setTransform(1,0,0,1,0,0);
  // block-break particles + dizzy stars + БАМ!
  for(const h of HITS){
    const tau=t-h;if(tau<0||tau>(h==FINAL?1:0.7))continue;const R=rng(round(h*100));
    for(let i=0;i<(h==FINAL?40:16);i++){
      const v=[(R()-.5)*14,5+R()*8,(R()-.5)*14],p=proj([7+v[0]*tau,19.2+v[1]*tau-14*tau*tau,v[2]*tau]),s=(0.35+R()*0.25)*CAM.k;
      ctx.fillStyle=BLUE_PIX[i%3];ctx.globalAlpha=1-P(tau,0.4,0.7);ctx.fillRect(p[0]-s/2,p[1]-s/2,s,s);
    }
    ctx.globalAlpha=1;
  }
  const h=lastHit(t),tau=t-h;
  if(h>0&&h<FINAL&&tau<0.9)for(let i=0;i<3;i++){const a=t*7+i*TAU/3,p=proj([7+cos(a)*2.8,21.6+sin(t*9+i)*0.3,sin(a)*2.8]);star(ctx,p[0],p[1],7,'#FFE14D')}
  if(h>0&&tau<(h==FINAL?0.7:0.42)){
    const i=HITS.indexOf(h),word=h==FINAL?'БАБАХ!':i%2?'БУМ!':'БАМ!',hp=proj([7,19,0]),big=h==FINAL;
    const sc=E.outBack(P(tau,0,0.12))*(big?1.25:1),a=1-P(tau,big?0.55:0.32,big?0.7:0.42);
    pixText(ctx,word,big?W/2:hp[0]-180,big?H/2-230:hp[1]-170,big?24:15,'#FFD23F','#E0701A','#1B1030',k=>({a,s:sc,dy:-tau*60,r:(i%2?0.1:-0.1)+(k%2?0.04:-0.04)}));
  }
  // block counter, Minecraft-UI style
  const ca=P(t,1.9,2.2)*(1-P(t,12.9,13.1));
  if(ca>0){ctx.globalAlpha=ca;ctx.font='700 40px Consolas,monospace';ctx.textAlign='left';ctx.textBaseline='alphabetic';const s=`КУБИКОВ: ${nd+nh}`;
    ctx.fillStyle='#3F3F3F';ctx.fillText(s,64,104);ctx.fillStyle='#FFFFFF';ctx.fillText(s,60,100);ctx.globalAlpha=1}
  // title
  if(t>=14.05){
    pixText(ctx,'ДИНО',W/2,150,30,'#5AAEFF','#1C4FB0','#0B1A3A',k=>{const p=P(t,14.05+k*0.07,14.4+k*0.07);return{a:p>0?1:0,s:1,dy:-(1-E.outBack(p))*360,r:0}});
    const sa=P(t,14.45,14.7);if(sa>0){ctx.globalAlpha=sa;ctx.font='700 34px Consolas,monospace';ctx.textAlign='center';const s=`СОБРАН ИЗ ${DINO.length} КУБИКОВ`;
      ctx.fillStyle='#2B3A55';ctx.fillText(s,W/2+3,333);ctx.fillStyle='#FFFFFF';ctx.fillText(s,W/2,330);ctx.globalAlpha=1}
  }
}

/* ============================================================ post */
const out=document.getElementById('c').getContext('2d');
const ACC=mk(),acc=ACC.getContext('2d'),WORK=mk(),work=WORK.getContext('2d');
const VIG=(()=>{const c=mk(),x=c.getContext('2d'),g=x.createRadialGradient(W/2,H/2,H*0.45,W/2,H/2,H*1.1);g.addColorStop(0,'rgba(0,0,30,0)');g.addColorStop(1,'rgba(0,0,30,.28)');x.fillStyle=g;x.fillRect(0,0,W,H);return c})();
function post(t){
  out.setTransform(1,0,0,1,0,0);out.globalAlpha=1;out.globalCompositeOperation='source-over';out.drawImage(ACC,0,0);
  let fl=0;for(const h of HITS)if(t>=h)fl=max(fl,(h==FINAL?0.85:0.3)*exp(-(t-h)/(h==FINAL?0.09:0.04)));
  if(fl>0.005){out.fillStyle=`rgba(255,255,255,${fl})`;out.fillRect(0,0,W,H)}
  out.drawImage(VIG,0,0);
}
const SHUTTER=0.5;
function frame(t,sub=1){
  if(sub<=1)renderAt(acc,t);
  else for(let k=0;k<sub;k++){const ts=clamp(t+((k+0.5)/sub-0.5)*SHUTTER/FPS,0,DUR-1e-4);renderAt(work,ts);acc.setTransform(1,0,0,1,0,0);acc.globalAlpha=1/(k+1);acc.drawImage(WORK,0,0);acc.globalAlpha=1}
  post(t);
}

/* ============================================================ audio — same synth and groove as the reel, new SFX */
async function buildAudio(){
  const sr=48000,ac=new OfflineAudioContext(2,sr*DUR,sr),R=rng(7);
  const noise=ac.createBuffer(1,sr*3,sr);{const d=noise.getChannelData(0);for(let i=0;i<d.length;i++)d[i]=R()*2-1}
  const comp=ac.createDynamicsCompressor();comp.threshold.value=-15;comp.knee.value=8;comp.ratio.value=5;comp.attack.value=0.004;comp.release.value=0.18;
  const master=ac.createGain();master.gain.setValueAtTime(0.95,0);master.gain.setValueAtTime(0.95,DUR-0.35);master.gain.linearRampToValueAtTime(0,DUR);
  comp.connect(master);master.connect(ac.destination);
  const bus=ac.createGain();bus.gain.value=0.75;bus.connect(comp);
  const duck=ac.createGain();duck.connect(bus);
  const ir=ac.createBuffer(2,sr*2.4,sr);for(let c=0;c<2;c++){const d=ir.getChannelData(c);for(let i=0;i<d.length;i++)d[i]=(R()*2-1)*pow(1-i/d.length,3.4)}
  const verb=ac.createConvolver();verb.buffer=ir;const vr=ac.createGain();vr.gain.value=0.32;verb.connect(vr);vr.connect(comp);
  const mf=m=>440*pow(2,(m-69)/12);
  function out(node,pan=0,send=0,dest=bus){const p=ac.createStereoPanner();p.pan.value=pan;node.connect(p);p.connect(dest);if(send){const s=ac.createGain();s.gain.value=send;p.connect(s);s.connect(verb)}return p}
  function env(g,t,a,peak,d){g.gain.setValueAtTime(0,t);g.gain.linearRampToValueAtTime(peak,t+a);g.gain.exponentialRampToValueAtTime(0.0001,t+a+d)}
  function nz(t,dur){const s=ac.createBufferSource();s.buffer=noise;s.loop=true;s.start(t,R()*2);s.stop(t+dur);return s}
  function filt(type,f,q=0.7){const b=ac.createBiquadFilter();b.type=type;b.frequency.value=f;b.Q.value=q;return b}
  function osc(type,f,t,dur){const o=ac.createOscillator();o.type=type;o.frequency.value=f;o.start(t);o.stop(t+dur);return o}
  const kick=(t,v=1)=>{const o=osc('sine',170,t,0.6);o.frequency.setValueAtTime(170,t);o.frequency.exponentialRampToValueAtTime(48,t+0.11);o.frequency.exponentialRampToValueAtTime(38,t+0.45);
    const g=ac.createGain();env(g,t,0.002,v,0.45);o.connect(g);out(g);
    const n=nz(t,0.03),g2=ac.createGain();env(g2,t,0.001,0.3*v,0.018);n.connect(filt('highpass',2500)).connect(g2);out(g2);
    duck.gain.setValueAtTime(0.35,t);duck.gain.linearRampToValueAtTime(1,t+0.22)};
  const clap=(t,v=0.5)=>{const n=nz(t,0.3),b=filt('bandpass',1300,0.9),g=ac.createGain();g.gain.setValueAtTime(0,t);
    for(const o of [0,0.011,0.022]){g.gain.setValueAtTime(v,t+o);g.gain.exponentialRampToValueAtTime(0.05,t+o+0.009)}
    g.gain.setValueAtTime(v*0.8,t+0.032);g.gain.exponentialRampToValueAtTime(0.0001,t+0.25);n.connect(b).connect(g);out(g,0.05,0.35)};
  const hat=(t,open,v,pan=0.2)=>{const n=nz(t,0.25),g=ac.createGain();env(g,t,0.001,v,open?0.16:0.035);n.connect(filt('highpass',7500)).connect(g);out(g,pan,0.1)};
  const bass=(t,f,d,v=0.3)=>{const o=osc('sawtooth',f,t,d+0.1),lp=filt('lowpass',300,4),g=ac.createGain();
    lp.frequency.setValueAtTime(2200,t);lp.frequency.exponentialRampToValueAtTime(260,t+d);env(g,t,0.004,v,d);o.connect(lp).connect(g);out(g,0,0,duck);
    const s=osc('sine',f/2,t,d+0.1),gs=ac.createGain();env(gs,t,0.004,v*1.1,d);s.connect(gs);out(gs,0,0,duck)};
  const stab=(t,notes,v=0.06)=>notes.forEach((n,i)=>[-9,9].forEach(dt=>{const o=osc('sawtooth',mf(n),t,0.5);o.detune.value=dt;
    const lp=filt('lowpass',3000,1.2),g=ac.createGain();lp.frequency.setValueAtTime(3200,t);lp.frequency.exponentialRampToValueAtTime(700,t+0.3);env(g,t,0.003,v,0.3);o.connect(lp).connect(g);out(g,(i-1)*0.45+dt/40,0.4,duck)}));
  const pad=(t0,t1,notes,v=0.035)=>notes.forEach((n,i)=>[-12,12].forEach(dt=>{const o=osc('sawtooth',mf(n),t0,t1-t0+0.6);o.detune.value=dt;
    const lp=filt('lowpass',1000,0.6),g=ac.createGain();g.gain.setValueAtTime(0,t0);g.gain.linearRampToValueAtTime(v,t0+0.5);g.gain.setValueAtTime(v,t1);g.gain.linearRampToValueAtTime(0,t1+0.5);
    o.connect(lp).connect(g);out(g,(i%2?0.5:-0.5)*(dt>0?1:-1),0.6,duck)}));
  const whoosh=(t0,t1,f0,f1,v=0.3,p0=-0.7,p1=0.7)=>{const n=nz(t0,t1-t0+0.05),b=filt('bandpass',f0,1.6),g=ac.createGain(),p=ac.createStereoPanner();
    b.frequency.setValueAtTime(f0,t0);b.frequency.exponentialRampToValueAtTime(f1,t1);g.gain.setValueAtTime(0,t0);g.gain.linearRampToValueAtTime(v,t0+(t1-t0)*0.75);g.gain.exponentialRampToValueAtTime(0.0001,t1);
    p.pan.setValueAtTime(p0,t0);p.pan.linearRampToValueAtTime(p1,t1);n.connect(b).connect(g).connect(p);p.connect(bus);const s=ac.createGain();s.gain.value=0.3;p.connect(s);s.connect(verb)};
  const riser=(t0,t1,v=0.25)=>{const n=nz(t0,t1-t0),hp=filt('highpass',300,2),g=ac.createGain();hp.frequency.setValueAtTime(300,t0);hp.frequency.exponentialRampToValueAtTime(9000,t1);
    g.gain.setValueAtTime(0.0001,t0);g.gain.exponentialRampToValueAtTime(v,t1-0.01);g.gain.linearRampToValueAtTime(0,t1);n.connect(hp).connect(g);out(g,0,0.3);
    const o=osc('sawtooth',110,t0,t1-t0),go=ac.createGain();o.frequency.setValueAtTime(110,t0);o.frequency.exponentialRampToValueAtTime(880,t1);
    go.gain.setValueAtTime(0.0001,t0);go.gain.exponentialRampToValueAtTime(v*0.25,t1-0.01);go.gain.linearRampToValueAtTime(0,t1);o.connect(filt('lowpass',2500)).connect(go);out(go,0,0.3)};
  const boom=(t,v=0.8,d=1.6)=>{const o=osc('sine',70,t,d+0.2),g=ac.createGain();o.frequency.setValueAtTime(70,t);o.frequency.exponentialRampToValueAtTime(28,t+d);env(g,t,0.005,v,d);o.connect(g);out(g)};
  const crash=(t,v=0.3,d=1.4)=>{const n=nz(t,d+0.1),g=ac.createGain();env(g,t,0.002,v,d);n.connect(filt('highpass',2800)).connect(g);out(g,0,0.6)};
  const blip=(t,f,v=0.08,type='triangle',d=0.09,pan=0)=>{const o=osc(type,f,t,d+0.05),g=ac.createGain();env(g,t,0.002,v,d);o.connect(g);out(g,pan,0.3)};
  const bell=(t,f,v=0.12)=>[[1,1,1.8],[2.76,.45,1],[5.4,.22,.6],[8.93,.1,.3]].forEach(([r,a,d])=>{const o=osc('sine',f*r,t,d+0.1),g=ac.createGain();env(g,t,0.002,v*a,d);o.connect(g);out(g,0,0.6)});
  const tick=(t,v=0.12)=>{const n=nz(t,0.02),g=ac.createGain();env(g,t,0.001,v,0.012);n.connect(filt('bandpass',4200,2)).connect(g);out(g,(R()-.5)*0.6)};
  // new: block placement, roar, bonk, dizzy tweets
  const place=(t,n,v=0.14)=>{const s=nz(t,0.08),g=ac.createGain();env(g,t,0.001,v,0.05);s.connect(filt('bandpass',650,1.4)).connect(g);out(g,(R()-.5)*0.8);blip(t,mf(n),0.035,'triangle',0.06,(R()-.5)*0.6)};
  const roar=t=>{const o=osc('sawtooth',80,t,0.95),l=osc('sine',9,t,0.95),lg=ac.createGain();lg.gain.value=10;l.connect(lg).connect(o.frequency);
    o.frequency.setValueAtTime(70,t);o.frequency.linearRampToValueAtTime(125,t+0.25);o.frequency.linearRampToValueAtTime(78,t+0.85);
    const g=ac.createGain();g.gain.setValueAtTime(0,t);g.gain.linearRampToValueAtTime(0.32,t+0.08);g.gain.setValueAtTime(0.32,t+0.5);g.gain.exponentialRampToValueAtTime(0.0001,t+0.9);
    o.connect(filt('lowpass',1100,2)).connect(g);out(g,0,0.4);
    const n=nz(t,0.9),gn=ac.createGain();gn.gain.setValueAtTime(0,t);gn.gain.linearRampToValueAtTime(0.18,t+0.1);gn.gain.exponentialRampToValueAtTime(0.0001,t+0.85);n.connect(filt('bandpass',520,0.8)).connect(gn);out(gn,0,0.4)};
  const bonk=t=>{kick(t,1.25);const n=nz(t,0.12),g=ac.createGain();env(g,t,0.001,0.55,0.08);n.connect(filt('bandpass',1050,5)).connect(g);out(g,0,0.2);
    const o=osc('sine',1100,t,0.3),go=ac.createGain();o.frequency.setValueAtTime(1100,t);o.frequency.exponentialRampToValueAtTime(160,t+0.2);env(go,t,0.002,0.22,0.22);o.connect(go);out(go,0,0.3);crash(t,0.07,0.4)};
  const tweet=t=>{for(let k=0;k<3;k++){const s=t+k*0.11,o=osc('sine',2400,s,0.1),g=ac.createGain();o.frequency.setValueAtTime(2400,s);o.frequency.exponentialRampToValueAtTime(3500,s+0.06);env(g,s,0.003,0.045,0.06);o.connect(g);out(g,k%2?0.5:-0.5,0.3)}};

  // bar 1 — the island pops up
  boom(0.01,0.45,1.3);pad(0,1.9,[45,57,60,64,67,71],0.03);whoosh(0,0.6,200,2200,0.16);
  for(let i=0;i<12;i++)place(0.12+i*0.075,60+i*2,0.1);
  bell(1.15,mf(84),0.05);riser(1.0,1.875,0.2);whoosh(1.6,1.9,500,6000,0.28,-0.9,0.9);
  // bars 2–7 — the reel's groove, unchanged
  const chords=[[57,60,64],[53,57,60],[55,60,64],[55,59,62],[57,60,64],[53,57,60]],roots=[45,41,48,43,45,41];
  for(let b=1;b<=6;b++){
    const t0=b*BAR,ch=chords[b-1],rt=roots[b-1];
    for(let k=0;k<4;k++){
      kick(t0+k*BEAT,b==1&&k==0?1.15:1);if(k%2)clap(t0+k*BEAT);
      hat(t0+(k+0.5)*BEAT,true,0.09,0.25);for(const s of [0.25,0.75])hat(t0+(k+s)*BEAT,false,0.045,-0.3);
      bass(t0+(k+0.5)*BEAT,mf(rt),0.19);if(k%2)bass(t0+(k+0.75)*BEAT,mf(rt+12),0.1,0.2);
    }
    if(b<6){stab(t0,ch.map(n=>n+12));stab(t0+1.5*BEAT,ch.map(n=>n+12),0.045);stab(t0+2.75*BEAT,ch.map(n=>n+12),0.045)}
  }
  // dino assembles: a placement click on every 16th, climbing in pitch
  for(let t=1.95,i=0;t<6.7;t+=BEAT/4,i++)place(t,52+floor(i/3),0.12);
  [0,1,2].forEach(i=>bell(6.74+i*0.05,mf([88,91,96][i]),0.06));roar(6.95);
  blip(7.75,mf(100),0.04,'sine',0.05);blip(8.85,mf(100),0.04,'sine',0.05);
  // hammer assembles: metallic clicks + whoosh
  whoosh(7.5,8.4,300,2600,0.2,0.9,0);for(let t=7.62,i=0;t<9.0;t+=BEAT/4,i++)place(t,64+i,0.1);
  for(const h of HITS){whoosh(h-0.24,h,250,2600,0.22,0.6,-0.2);if(h<FINAL){bonk(h);tweet(h+0.18)}}
  // bar 7 — snare roll + riser into the finale, exactly like the reel
  for(let k=0;k<16;k++){const t=6*BAR+(k<4?k*BEAT/2:k<8?2*BEAT+(k-4)*BEAT/4:3*BEAT+(k-8)*BEAT/8);clap(t,0.1+0.25*k/16)}
  riser(6*BAR,7*BAR,0.3);
  // bar 8 — БАБАХ, the blocks scatter and come back, title
  kick(FINAL,1.4);boom(FINAL,0.95,2.0);crash(FINAL,0.34,1.6);bonk(FINAL);
  {const n=nz(FINAL,1.2),g=ac.createGain();env(g,FINAL,0.01,0.45,1.0);n.connect(filt('lowpass',320)).connect(g);out(g)}
  for(let i=0;i<18;i++)tick(FINAL+0.08+R()*0.6,0.1);
  whoosh(13.55,14.25,3000,300,0.22,0.7,-0.7);for(let t=13.62,i=0;t<14.5;t+=BEAT/8,i++)place(t,57+i,0.09);
  pad(14.0,DUR-0.3,[45,52,57,60,64,71],0.04);bass(14.05,mf(45),1.0,0.22);
  [0,1,2,3].forEach(i=>bell(14.05+i*0.07,mf([81,84,88,93][i]),0.07));
  return ac.startRendering();
}
function wavB64(buf){
  const n=buf.length,sr=buf.sampleRate,ab=new ArrayBuffer(44+n*4),dv=new DataView(ab),ws=(o,s)=>[...s].forEach((c,i)=>dv.setUint8(o+i,c.charCodeAt(0)));
  ws(0,'RIFF');dv.setUint32(4,36+n*4,true);ws(8,'WAVE');ws(12,'fmt ');dv.setUint32(16,16,true);dv.setUint16(20,1,true);dv.setUint16(22,2,true);
  dv.setUint32(24,sr,true);dv.setUint32(28,sr*4,true);dv.setUint16(32,4,true);dv.setUint16(34,16,true);ws(36,'data');dv.setUint32(40,n*4,true);
  const L=buf.getChannelData(0),Rr=buf.getChannelData(1);
  for(let i=0;i<n;i++){dv.setInt16(44+i*4,clamp(L[i],-1,1)*32767,true);dv.setInt16(46+i*4,clamp(Rr[i],-1,1)*32767,true)}
  const u8=new Uint8Array(ab);let s='';for(let i=0;i<u8.length;i+=0x8000)s+=String.fromCharCode.apply(null,u8.subarray(i,i+0x8000));return btoa(s);
}

/* ============================================================ entry points */
window.frameAt=(t,sub)=>{frame(t,sub);return out.canvas.toDataURL('image/png')};
window.audioWav=async()=>wavB64(await buildAudio());
window.VIDEO={width:W,height:H,fps:FPS,duration:DUR};
window.READY=true;
const btn=document.getElementById('play');
if(location.search.includes('render'))btn.remove();
else{
  frame(DUR-0.01);
  btn.onclick=async()=>{
    btn.textContent='ГОТОВЛЮ ЗВУК…';
    const buf=await buildAudio(),ac=new AudioContext(),src=ac.createBufferSource();src.buffer=buf;src.connect(ac.destination);
    btn.style.display='none';const t0=ac.currentTime+0.05;src.start(t0);
    const loop=()=>{const t=ac.currentTime-t0;if(t<DUR){frame(max(0,t));requestAnimationFrame(loop)}else{btn.style.display='';btn.textContent='↻ ЕЩЁ РАЗ'}};loop();
  };
}
</script>
</body>
</html>
`````
