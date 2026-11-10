// Web版(docs/web-reference.html)を jsdom + three.js r128 で動かし、操作の列と、
// 各操作のあとの全関節の状態を書き出す。Kotlin 側の WebGoldenTest が同じ操作を Skeleton に
// 加えて結果を照合する(E01-10)。
//
// 使い方: tools/golden で `npm install` のあと `npm run generate`
// 出力: core/src/test/resources/golden/web-golden.txt

import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { JSDOM } from 'jsdom';

const require = createRequire(import.meta.url);
const THREE_REAL = require('three');
const here = dirname(fileURLToPath(import.meta.url));
const root = resolve(here, '../..');
const htmlPath = resolve(root, 'docs/web-reference.html');
const outPath = resolve(root, 'core/src/test/resources/golden/web-golden.txt');

/* ---------- Web版を読み込む ---------- */

const html = readFileSync(htmlPath, 'utf8');
const scripts = [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)].map((m) => m[1]);
if (scripts.length !== 1) throw new Error(`インラインのscriptが1つではない: ${scripts.length}`);
let src = scripts[0];

/** 参照実装に数行だけ書き足す。置き換え先が見つからなければ止める */
function inject(from, to) {
  if (!src.includes(from)) throw new Error(`書き足す位置が見つからない: ${from}`);
  src = src.replace(from, to);
}
// レイを外から与えられるようにする(dragPart は画面座標からレイを作るため)
inject('function setRay(cx, cy){',
  'function setRay(cx, cy){ if (window.__ray) { raycaster.ray.origin.copy(window.__ray.o); raycaster.ray.direction.copy(window.__ray.d); return; }');
// 内部の関数と部位を外から呼べるようにする
inject('  buildChips();\n  configUI();',
  '  window.__dessin = { parts: parts, select: select, removePart: removePart, resetPart: resetPart, applyState: applyState,\n' +
  '    aimPart: aimPart, aimAtPoint: aimAtPoint, dragPart: dragPart, sphereRotate: sphereRotate,\n' +
  '    setShoulderWidth: setShoulderWidth, setPelvisWidth: setPelvisWidth, setHipInset: setHipInset };\n' +
  '  buildChips();\n  configUI();');

const dom = new JSDOM(html.replace(/<script[\s\S]*?<\/script>/g, ''), { runScripts: 'outside-only', pretendToBeVisual: true });
const w = dom.window;

// 描画は使わないので、WebGL・2Dキャンバス・画面サイズの監視は何もしない部品に差し替える
class FakeRenderer {
  constructor() { this.domElement = w.document.createElement('canvas'); this.shadowMap = {}; }
  setPixelRatio() {}
  setSize() {}
  render() {}
}
const noop = new Proxy(function () {}, { get: (t, k) => (k === 'createRadialGradient' ? () => ({ addColorStop() {} }) : noop), apply: () => undefined, set: () => true });
w.HTMLCanvasElement.prototype.getContext = () => noop;
w.ResizeObserver = class { observe() {} };
w.requestAnimationFrame = () => 0;
w.THREE = Object.assign({}, THREE_REAL, { WebGLRenderer: FakeRenderer });
w.eval(src);
const web = w.__dessin;
if (!web) throw new Error('Web版の初期化に失敗した');
const T = THREE_REAL;

/* ---------- 操作 ---------- */

const ids = Object.keys(web.parts);
const placedIds = () => ids.filter((n) => web.parts[n].placed);
const available = (n) => { const p = web.parts[n]; return !p.parent || p.parent.placed; };

const ops = {
  place(id) { web.select(id); },
  remove(id) { const p = web.parts[id]; if (p.root) return; const parent = p.parent; web.removePart(p); web.select(parent.name); },
  reset(id) { web.resetPart(web.parts[id]); },
  setState(id, phi, psi, roll) { const p = web.parts[id]; p.state.phi = phi; p.state.psi = psi; p.state.roll = roll; web.applyState(p); },
  // Web版の aimPart は単位ベクトルを前提にしている(呼び出し元の dragPart・aimAtPoint が正規化して渡す)
  aim(id, x, y, z) { web.aimPart(web.parts[id], new T.Vector3(x, y, z).normalize()); },
  aimAt(id, x, y, z) { web.aimAtPoint(web.parts[id], new T.Vector3(x, y, z)); },
  drag(id, ox, oy, oz, dx, dy, dz) {
    w.__ray = { o: new T.Vector3(ox, oy, oz), d: new T.Vector3(dx, dy, dz) };
    web.dragPart(web.parts[id], 0, 0);
    w.__ray = null;
  },
  orb(id, x, y, z, qw) { web.select(id); web.sphereRotate(new T.Quaternion(x, y, z, qw)); },
  root(px, py, pz, ex, ey, ez, s) {
    const J = web.parts.rib.J;
    J.position.set(px, py, pz); J.rotation.set(ex, ey, ez); J.scale.setScalar(s);
  },
  body(head, shoulder, pelvis, inset) {
    const h = web.parts.head; h.size = head; h.J.scale.setScalar(head);
    web.setShoulderWidth(shoulder); web.setPelvisWidth(pelvis); web.setHipInset(inset);
  },
};

/* ---------- 書き出し ---------- */

const lines = [
  '# 自動生成。手で編集しない。tools/golden で `npm run generate` を実行して作り直す',
  '# 元: docs/web-reference.html(three.js r128)',
];
const fmt = (v) => (Object.is(v, -0) ? '0' : String(v));

function apply(name, ...args) {
  ops[name](...args);
  lines.push(['op', name, ...args.map(fmt)].join(' '));
  expectAll();
}

function expectAll() {
  lines.push('placed ' + placedIds().join(','));
  web.parts.rib.J.updateWorldMatrix(true, true);
  for (const id of ids) {
    const p = web.parts[id];
    const pos = p.J.getWorldPosition(new T.Vector3());
    const q = p.J.getWorldQuaternion(new T.Quaternion());
    const s = p.J.getWorldScale(new T.Vector3()).x;
    const row = ['part', id, pos.x, pos.y, pos.z, q.x, q.y, q.z, q.w, s];
    if (!p.root) {
      const dot = p.dot.getWorldPosition(new T.Vector3());
      row.push(p.state.phi, p.state.psi, p.state.roll, dot.x, dot.y, dot.z);
    }
    lines.push(row.map((v) => (typeof v === 'number' ? fmt(v) : v)).join(' '));
  }
}

function scenario(name, fn) {
  // 毎回、初期状態からやり直す(肋骨以外を外し、肋骨と体型を戻す)
  for (const id of ids) if (web.parts[id].parent && web.parts[id].parent.root) web.removePart(web.parts[id]);
  web.resetPart(web.parts.rib);
  web.setShoulderWidth(1); web.setPelvisWidth(1); web.setHipInset(0.0407);
  web.parts.head.size = 1; web.parts.head.J.scale.setScalar(1);
  web.select('rib');
  lines.push(`scenario ${name}`);
  expectAll();
  fn();
}

/* 乱数は再現できるように固定の種から作る(mulberry32) */
function rng(seed) {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function randomUnit(r) {
  for (;;) {
    const v = new T.Vector3(r() * 2 - 1, r() * 2 - 1, r() * 2 - 1);
    const l = v.length();
    if (l > 0.1 && l <= 1) return v.multiplyScalar(1 / l);
  }
}

const worldPos = (id) => { web.parts.rib.J.updateWorldMatrix(true, true); return web.parts[id].guide
  ? web.parts[id].guide.getWorldPosition(new T.Vector3()) : web.parts[id].J.getWorldPosition(new T.Vector3()); };

/* ---------- 手で決めた場面 ---------- */

scenario('全部位を置いて休止状態', () => {
  for (const id of ids) apply('place', id);
});

scenario('肋骨を回して腕と脚を向ける', () => {
  apply('root', 0.2, 1.0, -0.1, 0.3, 0.8, -0.2, 1.2);
  for (const id of ['clavL', 'upperArmL', 'forearmL', 'handL', 'pelvis', 'hipR', 'thighR', 'shinR', 'footR']) apply('place', id);
  apply('aim', 'upperArmL', 0, 0, 1);
  apply('aim', 'forearmL', 0, 1, 0);
  { const d = new T.Vector3(0.3, -0.5, 0.81).normalize(); apply('aim', 'thighR', d.x, d.y, d.z); }
  apply('aim', 'shinR', 0, 0, -1);
  apply('aim', 'footR', 0, -1, 0);
});

scenario('可動域の外へ向ける', () => {
  for (const id of ['neck', 'head', 'clavR', 'upperArmR']) apply('place', id);
  apply('aim', 'neck', 0, -1, 0);
  apply('aim', 'head', 1, 0, 0);
  apply('aim', 'clavR', 0, 1, 0);
  apply('aim', 'upperArmR', 0, 1, 0);
});

scenario('体型の調整と外す連鎖', () => {
  for (const id of ['neck', 'head', 'clavL', 'upperArmL', 'pelvis', 'hipL', 'thighL']) apply('place', id);
  apply('body', 1.3, 1.4, 1.5, 0.02);
  apply('body', 0.6, 0.4, 0.6, 0.08);
  apply('setState', 'upperArmL', 1.0, 2.0, 0.5);
  apply('remove', 'neck');
  apply('remove', 'clavL');
  apply('reset', 'thighL');
  apply('reset', 'rib');
});

/* ---------- 乱数の場面 ---------- */

for (const seed of [1, 2, 3, 4, 5, 6]) {
  const r = rng(seed);
  const pick = (arr) => arr[Math.floor(r() * arr.length)];
  scenario(`乱数${seed}`, () => {
    for (let i = 0; i < 40; i++) {
      const placed = placedIds();
      const movable = placed.filter((n) => !web.parts[n].root);
      const canPlace = ids.filter((n) => !web.parts[n].placed && available(n));
      const kind = movable.length === 0 ? 'place' : pick(['place', 'place', 'aim', 'aimAt', 'drag', 'drag', 'orb', 'orb', 'orbRoot', 'setState', 'root', 'body', 'remove', 'reset']);
      if (kind === 'place' && canPlace.length) { apply('place', pick(canPlace)); continue; }
      if (movable.length === 0) continue;
      const id = pick(movable);
      switch (kind) {
        case 'aim': { const d = randomUnit(r); apply('aim', id, d.x, d.y, d.z); break; }
        case 'aimAt': { const c = worldPos(id); const d = randomUnit(r).multiplyScalar(0.05 + r() * 0.4); apply('aimAt', id, c.x + d.x, c.y + d.y, c.z + d.z); break; }
        case 'drag': {
          // 関節の近くを通るレイ。当たる場合も外れる場合もある
          const c = worldPos(id);
          const from = randomUnit(r).multiplyScalar(3);
          const target = c.clone().add(randomUnit(r).multiplyScalar(r() * 0.5));
          const o = c.clone().add(from);
          const d = target.sub(o).normalize();
          apply('drag', id, o.x, o.y, o.z, d.x, d.y, d.z);
          break;
        }
        case 'orb': { const q = new T.Quaternion().setFromAxisAngle(randomUnit(r), (r() * 2 - 1) * 1.2); apply('orb', id, q.x, q.y, q.z, q.w); break; }
        case 'orbRoot': { const q = new T.Quaternion().setFromAxisAngle(randomUnit(r), (r() * 2 - 1) * 1.0); apply('orb', 'rib', q.x, q.y, q.z, q.w); break; }
        case 'setState': apply('setState', id, r() * 2, (r() * 2 - 1) * Math.PI, (r() * 2 - 1)); break;
        case 'root': apply('root', (r() * 2 - 1) * 0.5, 0.8 + r() * 0.6, (r() * 2 - 1) * 0.5, (r() * 2 - 1) * 1.5, (r() * 2 - 1) * 3, (r() * 2 - 1) * 1.5, 0.3 + r() * 1.3); break;
        case 'body': apply('body', 0.6 + r() * 0.8, 0.4 + r() * 1.1, 0.6 + r() * 0.9, r() * 0.08); break;
        case 'remove': apply('remove', id); break;
        case 'reset': apply('reset', pick(placed)); break;
        default: break;
      }
    }
  });
}

mkdirSync(dirname(outPath), { recursive: true });
writeFileSync(outPath, lines.join('\n') + '\n');
console.log(`書き出し: ${outPath}(${lines.length} 行)`);

/* ---------- カメラ(E02-01) ---------- */
// Web版の updateCamera・setRay・screenDelta と同じ手順を three.js の PerspectiveCamera・Raycaster で行う

const camOutPath = resolve(root, 'core/src/test/resources/golden/camera-golden.txt');
const camLines = [
  '# 自動生成。手で編集しない。tools/golden で `npm run generate` を実行して作り直す',
  '# 元: docs/web-reference.html の updateCamera・setRay・screenDelta(three.js r128)',
];
{
  const r = rng(100);
  const D2R = Math.PI / 180;
  for (let i = 0; i < 30; i++) {
    const target = new T.Vector3((r() * 2 - 1) * 0.5, 0.5 + r(), (r() * 2 - 1) * 0.5);
    const yaw = (r() * 2 - 1) * 4;
    const pitch = -1.2 + r() * 2.6;
    const dist = 0.8 + r() * 8.2;
    const width = 300 + Math.floor(r() * 1200);
    const height = 300 + Math.floor(r() * 2000);
    const camera = new T.PerspectiveCamera(35, width / height, 0.1, 60);
    const cp = Math.cos(pitch);
    camera.position.set(target.x + dist * Math.sin(yaw) * cp, target.y + dist * Math.sin(pitch), target.z + dist * Math.cos(yaw) * cp);
    camera.lookAt(target);
    camera.updateMatrixWorld();
    camera.updateProjectionMatrix();
    camLines.push(['camera', target.x, target.y, target.z, yaw, pitch, dist, width, height].map(fmt).join(' '));
    camLines.push(['position', camera.position.x, camera.position.y, camera.position.z].map(fmt).join(' '));
    camLines.push(['rotation', camera.quaternion.x, camera.quaternion.y, camera.quaternion.z, camera.quaternion.w].map(fmt).join(' '));
    camLines.push(['projection', ...camera.projectionMatrix.elements].map(fmt).join(' '));
    camLines.push(['view', ...camera.matrixWorldInverse.elements].map(fmt).join(' '));
    const raycaster = new T.Raycaster();
    for (let k = 0; k < 4; k++) {
      const sx = r() * width;
      const sy = r() * height;
      const ndc = new T.Vector2((sx / width) * 2 - 1, -(sy / height) * 2 + 1);
      raycaster.setFromCamera(ndc, camera);
      const o = raycaster.ray.origin;
      const d = raycaster.ray.direction;
      camLines.push(['ray', sx, sy, o.x, o.y, o.z, d.x, d.y, d.z].map(fmt).join(' '));
    }
    for (let k = 0; k < 2; k++) {
      const p = target.clone().add(randomUnit(r).multiplyScalar(r() * 0.5));
      const dx = (r() * 2 - 1) * 50;
      const dy = (r() * 2 - 1) * 50;
      const unit = 2 * camera.position.distanceTo(p) * Math.tan(camera.fov * D2R / 2) / Math.max(1, height);
      const right = new T.Vector3(1, 0, 0).applyQuaternion(camera.quaternion);
      const up = new T.Vector3(0, 1, 0).applyQuaternion(camera.quaternion);
      const out = right.multiplyScalar(dx * unit).addScaledVector(up, -dy * unit);
      camLines.push(['delta', p.x, p.y, p.z, dx, dy, out.x, out.y, out.z].map(fmt).join(' '));
    }
  }
}
writeFileSync(camOutPath, camLines.join('\n') + '\n');
console.log(`書き出し: ${camOutPath}(${camLines.length} 行)`);
