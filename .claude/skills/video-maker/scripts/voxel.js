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
