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
