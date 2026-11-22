#!/usr/bin/env node
// Offline renderer for the pack's item models: draws a model the way the 26.3 client does
// (element boxes, face UVs, element rotations, tints, display transforms) in headless Chrome with
// three.js, so models can be tuned without starting Minecraft. Screenshots in the real client stay
// the last check only. See README.md.
//
//   node render.js --model dbsdw:item/gun [--pack ../../resourcepack] [--tint ff3b30]
//                  [--views fp,gui,east,west,top,north] [--size 360] [--out /path/to/sheet.png]
//
// Prints one JSON line with the numbers per view (for fp: how much of the screen the item covers and
// where), so most checks need no image at all.
'use strict';

const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright-core');

function parseArgs(argv) {
  const o = { pack: path.join(__dirname, '..', '..', 'resourcepack'), tint: 'ff3b30', views: 'fp,gui,east,top',
              size: 360, out: null, model: null };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i], next = () => argv[++i];
    if (a === '--model') o.model = next();
    else if (a === '--pack') o.pack = next();
    else if (a === '--tint') o.tint = next();
    else if (a === '--views') o.views = next();
    else if (a === '--size') o.size = Number(next());
    else if (a === '--out') o.out = next();
    else throw new Error(`unknown argument ${a}`);
  }
  if (!o.model) throw new Error('--model is required, e.g. dbsdw:item/gun');
  return o;
}

function resolve(pack, id, folder, ext) {
  const [ns, p] = id.includes(':') ? id.split(':') : ['minecraft', id];
  return path.join(pack, 'assets', ns, folder, p + ext);
}

/** Loads a model with its parents merged (elements, textures, display), as the client does. */
function loadModel(pack, id) {
  const json = JSON.parse(fs.readFileSync(resolve(pack, id, 'models', '.json'), 'utf8'));
  if (!json.parent || json.parent.startsWith('minecraft:') || json.parent.startsWith('item/')
      || json.parent.startsWith('builtin/')) {
    return json;
  }
  const parent = loadModel(pack, json.parent);
  return {
    ...parent, ...json,
    textures: { ...(parent.textures || {}), ...(json.textures || {}) },
    display: { ...(parent.display || {}), ...(json.display || {}) },
    elements: json.elements || parent.elements,
  };
}

function textureFor(model, ref, depth = 0) {
  let t = ref;
  while (t && t.startsWith('#') && depth++ < 8) t = model.textures[t.slice(1)];
  return t;
}

async function main() {
  const o = parseArgs(process.argv.slice(2));
  const model = loadModel(o.pack, o.model);
  if (!model.elements) throw new Error(`${o.model} has no elements (a flat sprite model?)`);
  const textures = {};
  for (const el of model.elements) {
    for (const f of Object.values(el.faces)) {
      const id = textureFor(model, f.texture);
      if (id && !textures[id]) {
        textures[id] = 'data:image/png;base64,' + fs.readFileSync(resolve(o.pack, id, 'textures', '.png')).toString('base64');
      }
      f.texture = id;
    }
  }
  const three = fs.readFileSync(path.join(__dirname, 'node_modules', 'three', 'build', 'three.min.js'), 'utf8');
  const browser = await chromium.launch({
    executablePath: process.env.CHROME || '/usr/bin/google-chrome',
    args: ['--headless=new', '--no-sandbox', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'],
  });
  try {
    const page = await browser.newPage();
    page.on('pageerror', (e) => { console.error('page error:', e.message); });
    await page.setContent('<html><body style="margin:0;background:#333"></body></html>');
    await page.addScriptTag({ content: three });
    const result = await page.evaluate(renderInPage, { model, textures, tint: o.tint, views: o.views.split(','), size: o.size });
    const png = Buffer.from(result.png.split(',')[1], 'base64');
    const out = o.out || path.join(process.cwd(), o.model.replace(/[:/]/g, '_') + '.png');
    fs.writeFileSync(out, png);
    console.log(JSON.stringify({ out, model: o.model, views: result.stats }));
  } finally {
    await browser.close();
  }
}

// ------------------------------------------------------------------ runs inside the page
async function renderInPage({ model, textures, tint, views, size }) {
  const THREE = window.THREE;
  const SHADE = { up: 1.0, down: 0.5, north: 0.8, south: 0.8, east: 0.6, west: 0.6 };
  const tintColour = new THREE.Color('#' + tint);

  const loaded = {};
  await Promise.all(Object.entries(textures).map(([id, url]) => new Promise((ok, fail) => {
    new THREE.TextureLoader().load(url, (t) => {
      t.magFilter = THREE.NearestFilter; t.minFilter = THREE.NearestFilter; t.generateMipmaps = false;
      t.encoding = THREE.sRGBEncoding;
      loaded[id] = t; ok();
    }, undefined, fail);
  })));

  // Corner positions (model units) per face, in the order: top-left, top-right, bottom-right, bottom-left
  // as the face is seen from outside with the client's UV orientation.
  function corners(d, f, t) {
    const [x0, y0, z0] = f, [x1, y1, z1] = t;
    switch (d) {
      case 'north': return [[x1, y1, z0], [x0, y1, z0], [x0, y0, z0], [x1, y0, z0]];
      case 'south': return [[x0, y1, z1], [x1, y1, z1], [x1, y0, z1], [x0, y0, z1]];
      case 'east': return [[x1, y1, z1], [x1, y1, z0], [x1, y0, z0], [x1, y0, z1]];
      case 'west': return [[x0, y1, z0], [x0, y1, z1], [x0, y0, z1], [x0, y0, z0]];
      case 'up': return [[x0, y1, z0], [x1, y1, z0], [x1, y1, z1], [x0, y1, z1]];
      case 'down': return [[x0, y0, z1], [x1, y0, z1], [x1, y0, z0], [x0, y0, z0]];
    }
  }

  function rotateAround(p, rot) {
    if (!rot || !rot.angle) return p;
    const a = THREE.MathUtils.degToRad(rot.angle);
    const axis = { x: new THREE.Vector3(1, 0, 0), y: new THREE.Vector3(0, 1, 0), z: new THREE.Vector3(0, 0, 1) }[rot.axis];
    const o = new THREE.Vector3(...rot.origin);
    return new THREE.Vector3(...p).sub(o).applyAxisAngle(axis, a).add(o).toArray();
  }

  /** The model as a group in block units: vertices / 16 - 0.5, so the 16-unit cube is centred. */
  function buildModel() {
    const group = new THREE.Group();
    const byTexture = {};
    for (const el of model.elements) {
      for (const [d, face] of Object.entries(el.faces)) {
        const key = face.texture + (face.tintindex !== undefined ? '#tint' : '');
        const b = byTexture[key] || (byTexture[key] = { pos: [], uv: [], col: [], idx: [], tex: face.texture, tinted: face.tintindex !== undefined });
        const c = corners(d, el.from, el.to).map((p) => rotateAround(p, el.rotation));
        let [u0, v0, u1, v1] = face.uv || [0, 0, 16, 16];
        const uvs = [[u0, v0], [u1, v0], [u1, v1], [u0, v1]];
        const rot = ((face.rotation || 0) / 90) | 0;
        const base = b.pos.length / 3;
        const shade = SHADE[d];
        for (let i = 0; i < 4; i++) {
          const p = c[i];
          b.pos.push(p[0] / 16 - 0.5, p[1] / 16 - 0.5, p[2] / 16 - 0.5);
          const [u, v] = uvs[(i + rot) % 4];
          b.uv.push(u / 16, 1 - v / 16);
          b.col.push(shade, shade, shade);
        }
        b.idx.push(base, base + 2, base + 1, base, base + 3, base + 2);
      }
    }
    for (const b of Object.values(byTexture)) {
      const g = new THREE.BufferGeometry();
      g.setAttribute('position', new THREE.Float32BufferAttribute(b.pos, 3));
      g.setAttribute('uv', new THREE.Float32BufferAttribute(b.uv, 2));
      g.setAttribute('color', new THREE.Float32BufferAttribute(b.col, 3));
      g.setIndex(b.idx);
      const m = new THREE.MeshBasicMaterial({
        map: loaded[b.tex], vertexColors: true, transparent: false, alphaTest: 0.1,
        side: THREE.FrontSide, color: b.tinted ? tintColour : new THREE.Color(1, 1, 1),
      });
      group.add(new THREE.Mesh(g, m));
    }
    return group;
  }

  /** Display transform as the client applies it: translate, rotate X then Y then Z, scale. */
  function displayMatrix(name) {
    const t = (model.display || {})[name] || {};
    const tr = (t.translation || [0, 0, 0]).map((v) => Math.max(-80, Math.min(80, v)) / 16);
    const ro = (t.rotation || [0, 0, 0]).map(THREE.MathUtils.degToRad);
    const sc = (t.scale || [1, 1, 1]).map((v) => Math.max(-4, Math.min(4, v)));
    const m = new THREE.Matrix4().makeTranslation(...tr);
    m.multiply(new THREE.Matrix4().makeRotationFromEuler(new THREE.Euler(ro[0], ro[1], ro[2], 'XYZ')));
    m.multiply(new THREE.Matrix4().makeScale(...sc));
    return m;
  }

  const renderer = new THREE.WebGLRenderer({ antialias: false, preserveDrawingBuffer: true, alpha: true });
  renderer.outputEncoding = THREE.sRGBEncoding;
  const stats = {};
  const shots = [];

  function shoot(name, scene, camera, w, h, bg) {
    renderer.setSize(w, h);
    renderer.setClearColor(new THREE.Color(bg), 1);
    renderer.render(scene, camera);
    const c = document.createElement('canvas'); c.width = w; c.height = h;
    c.getContext('2d').drawImage(renderer.domElement, 0, 0);
    shots.push({ name, canvas: c });
  }

  function projectedBox(group, camera) {
    const box = { x0: 1, y0: 1, x1: -1, y1: -1 };
    group.updateMatrixWorld(true);
    group.traverse((o) => {
      if (!o.isMesh) return;
      const p = o.geometry.getAttribute('position');
      const v = new THREE.Vector3();
      for (let i = 0; i < p.count; i++) {
        v.fromBufferAttribute(p, i).applyMatrix4(o.matrixWorld).project(camera);
        box.x0 = Math.min(box.x0, v.x); box.x1 = Math.max(box.x1, v.x);
        box.y0 = Math.min(box.y0, v.y); box.y1 = Math.max(box.y1, v.y);
      }
    });
    const pct = (v) => Math.round(v * 1000) / 10;
    // screen percentages, origin top-left
    return { left: pct((box.x0 + 1) / 2), right: pct((box.x1 + 1) / 2), top: pct((1 - box.y1) / 2),
             bottom: pct((1 - box.y0) / 2), width: pct((box.x1 - box.x0) / 2), height: pct((box.y1 - box.y0) / 2) };
  }

  for (const view of views) {
    const scene = new THREE.Scene();
    const item = buildModel();
    if (view === 'fp') {
      // First person, right hand: arm offset (0.56, -0.52, -0.72), hand camera FOV 70, 16:9 screen.
      const holder = new THREE.Group();
      holder.matrixAutoUpdate = false;
      holder.matrix.copy(new THREE.Matrix4().makeTranslation(0.56, -0.52, -0.72).multiply(displayMatrix('firstperson_righthand')));
      holder.add(item); scene.add(holder);
      const w = Math.round(size * 16 / 9);
      const cam = new THREE.PerspectiveCamera(70, 16 / 9, 0.05, 100);
      shoot('first person (right hand)', scene, cam, w, size, '#8fb3e8');
      stats.fp = projectedBox(holder, cam);
      // crosshair
      const g = shots[shots.length - 1].canvas.getContext('2d');
      g.fillStyle = '#fff'; g.fillRect(w / 2 - 6, size / 2 - 1, 12, 2); g.fillRect(w / 2 - 1, size / 2 - 6, 2, 12);
    } else if (view === 'gui') {
      const holder = new THREE.Group();
      holder.matrixAutoUpdate = false;
      holder.matrix.copy(displayMatrix('gui'));
      holder.add(item); scene.add(holder);
      const cam = new THREE.OrthographicCamera(-0.5, 0.5, 0.5, -0.5, -10, 10);
      shoot('gui (inventory slot)', scene, cam, size, size, '#8b8b8b');
      stats.gui = projectedBox(holder, cam);
    } else {
      scene.add(item);
      const box = new THREE.Box3().setFromObject(item);
      const centre = box.getCenter(new THREE.Vector3());
      const dims = box.getSize(new THREE.Vector3());
      const dir = { east: [1, 0, 0], west: [-1, 0, 0], north: [0, 0, -1], south: [0, 0, 1], top: [0, 1, 0] }[view];
      if (!dir) throw new Error('unknown view ' + view);
      const span = Math.max(dims.x, dims.y, dims.z) * 0.58;
      const cam = new THREE.OrthographicCamera(-span, span, span, -span, -10, 10);
      cam.position.copy(centre).add(new THREE.Vector3(...dir).multiplyScalar(3));
      if (view === 'top') cam.up.set(0, 0, -1);
      cam.lookAt(centre);
      shoot(view + ' side (model space)', scene, cam, size, size, '#5a5f55');
    }
  }

  // contact sheet with labels
  const pad = 6, label = 18;
  const W = shots.reduce((s, x) => s + x.canvas.width + pad, pad);
  const H = Math.max(...shots.map((x) => x.canvas.height)) + label + pad * 2;
  const sheet = document.createElement('canvas'); sheet.width = W; sheet.height = H;
  const g = sheet.getContext('2d');
  g.fillStyle = '#222'; g.fillRect(0, 0, W, H);
  g.font = '13px monospace'; g.fillStyle = '#ddd';
  let x = pad;
  for (const s of shots) {
    g.fillText(s.name, x, pad + 12);
    g.drawImage(s.canvas, x, pad + label);
    x += s.canvas.width + pad;
  }
  return { png: sheet.toDataURL('image/png'), stats };
}

main().catch((e) => { console.error(e.stack || String(e)); process.exit(1); });
