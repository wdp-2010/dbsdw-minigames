// Block world and player physics for hop.js --walk.
//
// Keeps the chunk columns the server sends (map_chunk, kept current with block updates) and moves
// a player-sized box through them the way a client does: walls stop it per axis (so it slides
// along them), it steps up slabs and stairs, jumps one-block ledges, falls with gravity and
// refuses to walk into water, lava or a drop deeper than a few blocks. Assumes the overworld
// height range (-64..320), which every minigames world uses.
'use strict';

const HALF = 0.3; // half the player's width
const HEIGHT = 1.8;
const EYE = 1.62;
const STEP = 0.6;
const EPS = 1e-4;
const MAX_DROP = 4;

module.exports = function createWorld(version) {
  const registry = require('prismarine-registry')(version);
  const Chunk = require('prismarine-chunk')(registry);
  const Block = require('prismarine-block')(registry);
  const columns = new Map();
  const key = (cx, cz) => `${cx},${cz}`;
  const MIN_Y = -64;
  const WORLD_HEIGHT = 384;

  const loadChunk = (p) => {
    const column = new Chunk({ minY: MIN_Y, worldHeight: WORLD_HEIGHT });
    column.load(p.chunkData);
    columns.set(key(p.x, p.z), column);
  };
  const unloadChunk = (p) => columns.delete(key(p.chunkX, p.chunkZ));
  const setState = (x, y, z, state) => {
    const column = columns.get(key(x >> 4, z >> 4));
    if (column) column.setBlockStateId({ x: x & 15, y, z: z & 15 }, state);
  };
  const blockChange = (p) => setState(p.location.x, p.location.y, p.location.z, p.type);
  // Each record is stateId << 12 | localX << 8 | localZ << 4 | localY.
  const multiBlockChange = (p) => {
    const c = p.chunkCoordinates;
    for (const r of p.records) {
      const local = r % 4096;
      setState(c.x * 16 + ((local >> 8) & 15), c.y * 16 + (local & 15), c.z * 16 + ((local >> 4) & 15), Math.floor(r / 4096));
    }
  };

  // Collision boxes of one block, relative to its corner; null when its chunk is not loaded.
  const cache = new Map();
  const info = (state) => {
    let b = cache.get(state);
    if (!b) {
      const block = Block.fromStateId(state, 0);
      b = { shapes: block.boundingBox === 'block' ? block.shapes : [], liquid: /water|lava/.test(block.name) };
      cache.set(state, b);
    }
    return b;
  };
  const blockAt = (x, y, z) => {
    if (y < MIN_Y || y >= MIN_Y + WORLD_HEIGHT) return { shapes: [], liquid: false };
    const column = columns.get(key(x >> 4, z >> 4));
    return column ? info(column.getBlockStateId({ x: x & 15, y, z: z & 15 })) : null;
  };
  const loaded = (x, z) => columns.has(key(Math.floor(x) >> 4, Math.floor(z) >> 4));

  // Calls fn(box in world space) for every collision box near the given area; true if fn says stop.
  const eachBox = (x0, y0, z0, x1, y1, z1, fn) => {
    for (let bx = Math.floor(x0); bx <= Math.floor(x1 - EPS); bx++) {
      for (let bz = Math.floor(z0); bz <= Math.floor(z1 - EPS); bz++) {
        // a fence or wall reaches 1.5 high, so look one block below the area too
        for (let by = Math.floor(y0) - 1; by <= Math.floor(y1 - EPS); by++) {
          const b = blockAt(bx, by, bz);
          if (b === null) { if (fn(null)) return true; continue; }
          for (const s of b.shapes) {
            if (fn([bx + s[0], by + s[1], bz + s[2], bx + s[3], by + s[4], bz + s[5]])) return true;
          }
        }
      }
    }
    return false;
  };
  // Does a player standing with its feet at (x, y, z) overlap a block (or an unloaded chunk)?
  const collides = (x, y, z) => {
    const x0 = x - HALF; const x1 = x + HALF; const z0 = z - HALF; const z1 = z + HALF;
    const y0 = y + EPS; const y1 = y + HEIGHT;
    return eachBox(x0, y0, z0, x1, y1, z1, (b) => b === null
      || (b[0] < x1 && b[3] > x0 && b[1] < y1 && b[4] > y0 && b[2] < z1 && b[5] > z0));
  };
  // Highest block top under the player's box between yLow and yHigh, or -Infinity.
  const topBetween = (x, z, yLow, yHigh) => {
    const x0 = x - HALF; const x1 = x + HALF; const z0 = z - HALF; const z1 = z + HALF;
    let top = -Infinity;
    eachBox(x0, yLow, z0, x1, yHigh, z1, (b) => {
      if (b && b[0] < x1 && b[3] > x0 && b[2] < z1 && b[5] > z0 && b[4] <= yHigh + EPS && b[4] >= yLow - EPS) top = Math.max(top, b[4]);
      return false;
    });
    return top;
  };
  // Is the spot safe to walk onto: ground within MAX_DROP and no liquid at the feet or under them?
  const safe = (x, y, z) => {
    const fx = Math.floor(x); const fz = Math.floor(z);
    for (let by = Math.floor(y); by >= Math.floor(y) - 1; by--) {
      const b = blockAt(fx, by, fz);
      if (b === null || b.liquid) return false;
    }
    return topBetween(x, z, y - MAX_DROP, y + EPS) > -Infinity;
  };

  // A player body: position plus vertical speed. step(dx, dz) moves it one tick and returns
  // 'moved', 'jumped' or 'blocked' (no progress on either axis).
  const body = (pos) => {
    const state = { vy: 0, onGround: false };
    const stepAxis = (nx, nz) => {
      if (!safe(nx, pos.y, nz) && state.onGround) return false;
      if (!collides(nx, pos.y, nz)) { pos.x = nx; pos.z = nz; return true; }
      if (state.onGround) {
        const top = topBetween(nx, nz, pos.y, pos.y + STEP);
        if (top > pos.y && !collides(nx, top, nz)) { pos.x = nx; pos.z = nz; pos.y = top; return true; }
      }
      return false;
    };
    return {
      state,
      reset() { state.vy = 0; state.onGround = false; },
      step(dx, dz) {
        let result = 'blocked';
        const mx = Math.abs(dx) > EPS && stepAxis(pos.x + dx, pos.z);
        const mz = Math.abs(dz) > EPS && stepAxis(pos.x, pos.z + dz);
        if (mx || mz) result = 'moved';
        else if (state.onGround && (dx || dz)) {
          // a one-block ledge ahead with room above it: jump
          const nx = pos.x + Math.sign(dx) * 0.4; const nz = pos.z + Math.sign(dz) * 0.4;
          const top = topBetween(nx, nz, pos.y, pos.y + 1.25);
          if (top > pos.y && !collides(nx, top, nz) && !collides(pos.x, pos.y + 1.25, pos.z)) {
            state.vy = 0.42;
            state.onGround = false;
            result = 'jumped';
          }
        }
        // vertical: gravity and landing
        if (!state.onGround || state.vy > 0) {
          const ny = pos.y + state.vy;
          if (state.vy > 0 && collides(pos.x, ny, pos.z)) state.vy = 0;
          else if (state.vy <= 0 && collides(pos.x, ny, pos.z)) {
            const top = topBetween(pos.x, pos.z, ny, pos.y);
            pos.y = top > -Infinity ? top : pos.y;
            state.vy = 0;
            state.onGround = true;
          } else {
            pos.y = ny;
            state.vy = (state.vy - 0.08) * 0.98;
          }
        }
        // walked off an edge?
        if (state.onGround && !collides(pos.x, pos.y - 0.05, pos.z)) { state.onGround = false; state.vy = 0; }
        if (!state.onGround && state.vy <= 0 && collides(pos.x, pos.y - 0.05, pos.z)) {
          const top = topBetween(pos.x, pos.z, pos.y - 0.05, pos.y);
          if (top > -Infinity) pos.y = top;
          state.onGround = true;
          state.vy = 0;
        }
        return result;
      },
    };
  };

  // Can an eye at `from` see the point `to` (no collision box in between)? Samples every 0.2 block.
  const sees = (from, to) => {
    const dx = to.x - from.x; const dy = to.y - from.y; const dz = to.z - from.z;
    const n = Math.ceil(Math.hypot(dx, dy, dz) / 0.2);
    for (let i = 1; i < n; i++) {
      const x = from.x + dx * i / n; const y = from.y + dy * i / n; const z = from.z + dz * i / n;
      const b = blockAt(Math.floor(x), Math.floor(y), Math.floor(z));
      if (b === null) return false;
      const lx = x - Math.floor(x); const ly = y - Math.floor(y); const lz = z - Math.floor(z);
      if (b.shapes.some((s) => lx >= s[0] && lx <= s[3] && ly >= s[1] && ly <= s[4] && lz >= s[2] && lz <= s[5])) return false;
    }
    return true;
  };

  // Height a player stands at in block column (bx, bz), at most one block above and three below
  // yRef; null when there is no floor there, no room to stand, or liquid.
  // Columns the server would not let the bot walk into (it kept being put back): A* avoids them.
  const avoid = new Map(); // column key -> until (ms)
  const avoidColumn = (x, z, ms) => avoid.set(key(Math.floor(x), Math.floor(z)), Date.now() + ms);
  const avoided = (bx, bz) => {
    const until = avoid.get(key(bx, bz));
    if (until === undefined) return false;
    if (until > Date.now()) return true;
    avoid.delete(key(bx, bz));
    return false;
  };
  const standAt = (bx, bz, yRef) => {
    if (avoided(bx, bz)) return null;
    const x = bx + 0.5; const z = bz + 0.5;
    const top = topBetween(x, z, yRef - 3.01, yRef + 1.01);
    if (top === -Infinity || collides(x, top, z)) return null;
    const feet = blockAt(bx, Math.floor(top + EPS), bz);
    const under = blockAt(bx, Math.floor(top - EPS), bz);
    if (!feet || !under || feet.liquid || under.liquid) return null;
    return top;
  };

  // A* over block columns from `from` toward `to` (positions with x, y, z). Returns waypoints
  // (column centres with standing height), ending at the goal or, when it is out of reach or
  // too far to search, at the explored spot closest to it; null when the start is not standable.
  const findPath = (from, to, maxNodes = 5000) => {
    const sx = Math.floor(from.x); const sz = Math.floor(from.z);
    const sy = standAt(sx, sz, from.y + 0.5);
    if (sy === null) return null;
    const gx = Math.floor(to.x); const gz = Math.floor(to.z);
    const h = (x, z) => { const dx = Math.abs(x - gx); const dz = Math.abs(z - gz); return Math.max(dx, dz) + 0.414 * Math.min(dx, dz); };
    const k = (x, z) => x * 100003 + z;
    const nodes = new Map();
    const open = [];
    const start = { x: sx, z: sz, y: sy, g: 0, f: h(sx, sz), parent: null, closed: false };
    nodes.set(k(sx, sz), start);
    open.push(start);
    let best = start;
    let expanded = 0;
    const DIRS = [[1, 0], [-1, 0], [0, 1], [0, -1], [1, 1], [1, -1], [-1, 1], [-1, -1]];
    while (open.length && expanded < maxNodes) {
      let bi = 0;
      for (let i = 1; i < open.length; i++) if (open[i].f < open[bi].f) bi = i;
      const n = open[bi];
      open[bi] = open[open.length - 1];
      open.pop();
      if (n.closed) continue;
      n.closed = true;
      expanded++;
      if (h(n.x, n.z) < h(best.x, best.z)) best = n;
      if (n.x === gx && n.z === gz) { best = n; break; }
      for (const [dx, dz] of DIRS) {
        const x = n.x + dx; const z = n.z + dz;
        const y = standAt(x, z, n.y);
        if (y === null) continue;
        if (y > n.y + 0.6 && collides(n.x + 0.5, n.y + 1.25, n.z + 0.5)) continue; // no room to jump
        if (dx && dz && (standAt(n.x + dx, n.z, n.y) === null || standAt(n.x, n.z + dz, n.y) === null)) continue; // no corner cutting
        const g = n.g + (dx && dz ? 1.414 : 1) + (y > n.y + 0.6 ? 1 : 0);
        const old = nodes.get(k(x, z));
        if (old && (old.closed || old.g <= g)) continue;
        const node = { x, z, y, g, f: g + h(x, z), parent: n, closed: false };
        nodes.set(k(x, z), node);
        open.push(node);
      }
    }
    const path = [];
    for (let n = best; n && n !== start; n = n.parent) path.push({ x: n.x + 0.5, y: n.y, z: n.z + 0.5 });
    return path.reverse();
  };

  // Debug: block names and collision boxes around a spot.
  const describe = (x, y, z) => {
    const out = [];
    for (let dy = -1; dy <= 1; dy++) for (let dz = -1; dz <= 1; dz++) for (let dx = -1; dx <= 1; dx++) {
      const bx = Math.floor(x) + dx; const by = Math.floor(y) + dy; const bz = Math.floor(z) + dz;
      const column = columns.get(key(bx >> 4, bz >> 4));
      if (!column) continue;
      const state = column.getBlockStateId({ x: bx & 15, y: by, z: bz & 15 });
      const b = Block.fromStateId(state, 0);
      if (b.name !== 'air') out.push(`${dx},${dy},${dz}:${b.name}${b.boundingBox === 'block' ? JSON.stringify(b.shapes) : ''}`);
    }
    return out;
  };

  return { avoidColumn, describe, findPath, loadChunk, unloadChunk, blockChange, multiBlockChange, loaded, collides, body, sees, EYE, chunks: () => columns.size };
};
