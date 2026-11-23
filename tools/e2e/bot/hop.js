// Headless test client for the DBSDW e2e travel checks. Never point it at the live server.
//
// Behaves like a real client for cookies, transfers and resource packs, answers AuthMe's
// pre-join dialog, and prints one JSON line per event so checks can grep the output.
//
//   node hop.js --host 127.0.0.1 --port 25570 --name Bot [--password pw]
//               [--command "/minigames" --command "/survival" ...]   one command after each join, in order
//               [--decline-pack] [--stay 8] [--max-hops 4]
//               [--transfer-intent] [--cookies-in file] [--cookies-out file]   replay checks
//               [--keep-cookies]            ignore a server clearing a cookie (a modded client replaying it)
//               [--interact-entity mannequin]  right-click the nearest entity of that type after each join
//               [--host-map minigames=127.0.0.1:25571,survival=127.0.0.1:25570]  rewrite transfer targets
//               [--requeue 11]   stay online; every time the lobby items arrive (first arrival and after each
//                                match), open the games menu and join the mode in that slot of the modes menu
//               [--fight 35]     during a match, keep aiming at the nearest enemy player within 35 blocks
//                                (scoreboard teams tell friend from foe) and fire short bursts, with a little
//                                aim error and a random pause, like a so-so human
//               [--walk]         during a match, walk: close in on the nearest enemy, circle and back off,
//                                otherwise walk a path to the nearest known enemy or the way its spawn faces.
//                                Reads the chunks (world.js) to path around walls, step up, jump ledges and
//                                fall; --fight then also needs a clear line of sight before it fires
//               The process exits once the bot is disconnected for good (kick, end, refused), so a
//               container restart policy brings it back.
//               [--do use:0 --do click:10 --do cmd:/leave --do wait:2 --do interact:mannequin --do drop:0 ...]
//                   steps run in order (1 s apart) after the first join that has no --command left;
//                   use = select hotbar slot + right-click, click = left-click a slot of the open window,
//                   drop = select hotbar slot + drop key, quit = disconnect now,
//                   aim:<player> = look at that player's feet-to-feet line, fire:<n> = n right-clicks 200 ms apart,
//                   move:<dx>:<dz>:<ticks> = walk that far each tick (50 ms) for that many ticks,
//                   look:<yaw>:<pitch> = turn to that view, swing = left click (arm swing), sneak:on|off = hold or release sneak, slot:<n> = select a hotbar slot (one wheel notch
//                   when it is next to the current one).
//                   --stay counts from the end of the steps (and applies to every earlier join too).
//
// Events: start, cookie_stored, cookie_requested, pack_offered, pack_answered, dialog_shown,
// dialog_submitted, joined, chat, command_sent, transferred, kicked, ended, game_mode, position,
// title, subtitle, window_open, window_items, held_items, step, actionbar, entity_spawn (snowballs), aimed,
// held_slot (the server set the selected slot), speed (own movement_speed attribute and its modifiers).
'use strict';

const fs = require('fs');
const mc = require('minecraft-protocol');
const nbt = require('prismarine-nbt');

const VERSION = process.env.MCV || '26.1';
const PACK = { SUCCESSFULLY_LOADED: 0, DECLINED: 1, ACCEPTED: 3, DOWNLOADED: 4 };

function parseArgs(argv) {
  const opts = { command: [], steps: [], stay: 8, maxHops: 4 };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    const next = () => argv[++i];
    if (a === '--host') opts.host = next();
    else if (a === '--port') opts.port = Number(next());
    else if (a === '--name') opts.name = next();
    else if (a === '--password') opts.password = next();
    else if (a === '--command') opts.command.push(next());
    else if (a === '--decline-pack') opts.declinePack = true;
    else if (a === '--stay') opts.stay = Number(next());
    else if (a === '--max-hops') opts.maxHops = Number(next());
    else if (a === '--transfer-intent') opts.transferIntent = true;
    else if (a === '--cookies-in') opts.cookiesIn = next();
    else if (a === '--cookies-out') opts.cookiesOut = next();
    else if (a === '--host-map') opts.hostMap = next();
    else if (a === '--keep-cookies') opts.keepCookies = true;
    else if (a === '--interact-entity') opts.interactEntity = next();
    else if (a === '--do') opts.steps.push(next());
    else if (a === '--requeue') opts.requeue = Number(next());
    else if (a === '--fight') opts.fight = Number(next());
    else if (a === '--walk') opts.walk = true;
    else throw new Error(`unknown argument ${a}`);
  }
  if (!opts.host || !opts.port || !opts.name) throw new Error('--host, --port and --name are required');
  return opts;
}

const opts = parseArgs(process.argv.slice(2));
const emit = (event, data = {}) => console.log(JSON.stringify({ t: new Date().toISOString().slice(11, 23), event, ...data }));

// Cookie jar: key -> Buffer. Survives transfers, like the vanilla client.
const cookies = new Map();
if (opts.cookiesIn) {
  for (const [k, v] of Object.entries(JSON.parse(fs.readFileSync(opts.cookiesIn, 'utf8')))) cookies.set(k, Buffer.from(v, 'base64'));
}
const saveCookies = () => {
  if (!opts.cookiesOut) return;
  fs.writeFileSync(opts.cookiesOut, JSON.stringify(Object.fromEntries([...cookies].map(([k, v]) => [k, v.toString('base64')]))));
};

const hostMap = new Map((opts.hostMap || '').split(',').filter(Boolean).map((e) => {
  const [name, target] = e.split('=');
  const [h, p] = target.split(':');
  return [name, { host: h, port: Number(p) }];
}));

const plain = (x) => {
  if (x == null) return '';
  if (typeof x !== 'object') return String(x);
  if (x.type && x.value !== undefined) { try { return plain(nbt.simplify(x)); } catch { /* fall through */ } }
  if (Array.isArray(x)) return x.map(plain).join('');
  let o = (x.text ?? x[''] ?? '') + '';
  if (x.translate) o += x.translate;
  if (x.extra) o += plain(x.extra);
  return o;
};

const varint = (n) => { const o = []; do { let b = n & 0x7f; n >>>= 7; if (n) b |= 0x80; o.push(b); } while (n); return Buffer.from(o); };

// minecraft-data's model of custom_click_action is wrong: vanilla sends id + varint-length-prefixed NBT.
function sendCustomClick(client, id, values) {
  const value = Object.fromEntries(Object.entries(values).map(([k, v]) => [k, { type: 'string', value: v }]));
  const tag = nbt.protos.big.createPacketBuffer('anonymousNbt', { type: 'compound', name: '', value });
  const map = require('minecraft-data')(VERSION).protocol[client.state].toServer.types.packet[1][0].type[1].mappings;
  const pid = parseInt(Object.keys(map).find((k) => map[k] === 'custom_click_action'), 16);
  const idb = Buffer.from(id, 'utf8');
  client.writeRaw(Buffer.concat([varint(pid), varint(idb.length), idb, varint(tag.length), tag]));
}

let hops = 0;
let commandIndex = 0;
let stepsStarted = false;

// Display name of an item stack: its item_name or custom_name component, else the item id.
function itemName(item) {
  if (!item || !item.itemCount) return null;
  const named = (item.components || []).find((c) => c.type === 'item_name' || c.type === 'custom_name');
  if (named) return plain(named.data);
  const known = require('minecraft-data')(VERSION).items[item.itemId];
  return known ? known.name : `#${item.itemId}`;
}

// Deep search for the first numeric value under a key named like gamemode.
function findGameMode(x) {
  if (!x || typeof x !== 'object') return undefined;
  for (const [k, v] of Object.entries(x)) {
    if (/^game_?mode$/i.test(k) && (typeof v === 'number' || typeof v === 'string')) return v;
    const inner = findGameMode(v);
    if (inner !== undefined) return inner;
  }
  return undefined;
}
const GAME_MODES = { 0: 'survival', 1: 'creative', 2: 'adventure', 3: 'spectator' };

function connect(host, port, transferIntent) {
  emit('start', { host, port, transferIntent: Boolean(transferIntent) });
  const client = mc.createClient({ host, port, username: opts.name, version: VERSION, auth: 'offline', hideErrors: true });

  // minecraft-protocol always sends handshake intent 2 (login); a transferred client sends 3.
  const write = client.write.bind(client);
  client.write = (name, params) => write(name, name === 'set_protocol' && transferIntent ? { ...params, nextState: 3 } : params);

  let transferring = false;
  let stayTimer;

  client.on('store_cookie', (p) => {
    if (opts.keepCookies && p.value.length === 0 && cookies.has(p.key)) {
      emit('cookie_clear_ignored', { key: p.key });
      return;
    }
    cookies.set(p.key, Buffer.from(p.value));
    saveCookies();
    emit('cookie_stored', { key: p.key, bytes: p.value.length });
  });
  client.on('cookie_request', (p) => {
    const value = cookies.get(p.cookie);
    emit('cookie_requested', { key: p.cookie, present: Boolean(value), bytes: value ? value.length : 0 });
    client.write('cookie_response', { key: p.cookie, value: value && value.length ? value : undefined });
  });
  client.on('add_resource_pack', (p) => {
    emit('pack_offered', { url: p.url, hash: p.hash, forced: p.forced, state: client.state });
    const answer = (result) => client.write('resource_pack_receive', { uuid: p.uuid, result });
    if (opts.declinePack) {
      answer(PACK.DECLINED);
      emit('pack_answered', { result: 'DECLINED' });
    } else {
      answer(PACK.ACCEPTED);
      answer(PACK.DOWNLOADED);
      answer(PACK.SUCCESSFULLY_LOADED);
      emit('pack_answered', { result: 'SUCCESSFULLY_LOADED' });
    }
  });
  client.on('show_dialog', (p) => {
    let text;
    try { text = JSON.stringify(nbt.simplify(p.dialog && p.dialog.data !== undefined && p.dialog.type !== 'compound' ? p.dialog.data : p.dialog)); } catch { text = JSON.stringify(p); }
    const register = /prejoin-register/.test(text);
    emit('dialog_shown', { state: client.state, kind: register ? 'register' : /prejoin-login/.test(text) ? 'login' : 'other' });
    if (!opts.password) return;
    if (register) sendCustomClick(client, 'authme:prejoin-register/submit', { password: opts.password, confirm: opts.password });
    else sendCustomClick(client, 'authme:prejoin-login/submit', { password: opts.password });
    emit('dialog_submitted', { kind: register ? 'register' : 'login' });
  });
  // Like a real client: confirm each server teleport (26.3 takes the position from the next move
  // packet, so send one position_look), and report the world as loaded once, or the server ignores
  // the player's interactions.
  let loaded = false;
  client.on('position', (p) => {
    client.write('teleport_confirm', { teleportId: p.teleportId });
    teleportFrom = { x: lastPos.x, z: lastPos.z };
    Object.assign(lastPos, { x: p.x, y: p.y, z: p.z, yaw: p.yaw, pitch: p.pitch }); // --walk holds this object
    teleportedAt = Date.now();
    emit('position', { x: Number(p.x.toFixed(2)), y: Number(p.y.toFixed(2)), z: Number(p.z.toFixed(2)) });
    client.write('position_look', { x: p.x, y: p.y, z: p.z, yaw: p.yaw, pitch: p.pitch, flags: { onGround: true } });
    if (!loaded) {
      loaded = true;
      client.write('player_loaded', {});
    }
  });
  client.on('update_health', (p) => {
    if (p.health <= 0) {
      client.write('client_command', { actionId: 'perform_respawn' });
      emit('respawned');
    }
  });
  const entities = new Map();
  const playerNames = new Map(); // uuid -> name
  client.on('player_info', (p) => {
    for (const e of p.data || []) if (e.player && e.player.name) playerNames.set(e.uuid, e.player.name);
  });
  client.on('spawn_entity', (p) => {
    entities.set(p.entityId, p);
    if (playerNames.has(p.objectUUID)) emit('visible', { name: playerNames.get(p.objectUUID), shown: true });
    const md = require('minecraft-data')(VERSION);
    const type = md.entities[p.type] ? md.entities[p.type].name : p.type;
    if (type === 'snowball') emit('entity_spawn', { type });
  });
  // Keep other entities' positions current so aim:<player> points at where they are now.
  for (const name of ['entity_teleport', 'sync_entity_position']) {
    client.on(name, (p) => {
      const e = entities.get(p.entityId);
      const pos = p.position || p;
      if (e && pos.x !== undefined) Object.assign(e, { x: pos.x, y: pos.y, z: pos.z });
    });
  }
  for (const name of ['rel_entity_move', 'entity_move_look']) {
    client.on(name, (p) => {
      const e = entities.get(p.entityId);
      if (e) { e.x += p.dX / 4096; e.y += p.dY / 4096; e.z += p.dZ / 4096; }
    });
  }
  client.on('entity_destroy', (p) => {
    for (const id of p.entityIds || []) {
      const e = entities.get(id);
      if (e && playerNames.has(e.objectUUID)) emit('visible', { name: playerNames.get(e.objectUUID), shown: false });
      entities.delete(id);
    }
  });
  client.on('action_bar', (p) => emit('actionbar', { text: plain(p.text) }));

  // Inventory and windows, so steps can click and checks can see items.
  const playerSlots = new Map(); // window-0 slot -> item
  const stateIds = new Map(); // window id -> last state id
  let openWindow = 0;
  let sequence = 0;
  let lastPos = { x: 0, y: 0, z: 0, yaw: 0, pitch: 0 };
  let teleportedAt = 0; // last server teleport (--walk resets its fall speed)
  let teleportFrom = { x: 0, z: 0 }; // where the bot was just before it
  let heldTimer;
  let inLobby = false;
  let inMatch = false;
  // Scoreboard teams (the match puts each side in its own team), so --fight leaves teammates alone.
  const teamOf = new Map();
  client.on('teams', (p) => {
    if ((p.mode === 0 || p.mode === 'add' || p.mode === 3 || p.mode === 'join') && p.players) {
      for (const name of p.players) teamOf.set(name, p.team);
    } else if ((p.mode === 4 || p.mode === 'leave') && p.players) {
      for (const name of p.players) if (teamOf.get(name) === p.team) teamOf.delete(name);
    } else if (p.mode === 1 || p.mode === 'remove') {
      for (const [name, team] of teamOf) if (team === p.team) teamOf.delete(name);
    }
  });
  // Timers of this connection only; a transfer or disconnect stops them, so an old connection
  // never writes to a closed socket.
  const timers = [];
  client.on('end', () => timers.forEach(clearInterval));
  const live = () => client.state === 'play' && !client.ended;
  // Acknowledge chunk batches like a client, or the server stops sending chunks after the first
  // batches, and players in chunks it has not sent are never shown (no --fight target in an arena).
  client.on('chunk_batch_finished', () => client.write('chunk_batch_received', { chunksPerTick: 20 }));
  const walkWorld = opts.walk ? require('./world')(VERSION) : null;
  if (walkWorld) {
    client.on('map_chunk', (p) => { try { walkWorld.loadChunk(p); } catch (e) { emit('chunk_error', { message: e.message }); } });
    client.on('unload_chunk', walkWorld.unloadChunk);
    client.on('block_change', walkWorld.blockChange);
    client.on('multi_block_change', walkWorld.multiBlockChange);
  }
  // A real client ends every tick with tick_end; 26.3 kicks a player whose second processed move
  // packet arrives without one in between ("Invalid move player packet received").
  timers.push(setInterval(() => { if (loaded && live()) client.write('tick_end', {}); }, 50));

  // Nearest enemy player within maxD (scoreboard teams tell friend from foe).
  const nearestEnemy = (maxD) => {
    const md = require('minecraft-data')(VERSION);
    const mine = teamOf.get(opts.name);
    let best = null; let bestD = maxD;
    for (const e of entities.values()) {
      if (!md.entities[e.type] || md.entities[e.type].name !== 'player') continue;
      const name = playerNames.get(e.objectUUID);
      if (!name || name === opts.name || (mine && teamOf.get(name) === mine)) continue;
      const d = Math.hypot(e.x - lastPos.x, e.y - lastPos.y, e.z - lastPos.z);
      if (d < bestD) { best = e; bestD = d; }
    }
    return best ? { e: best, d: bestD } : null;
  };
  // Aim error of the current burst, so a walking bot keeps tracking with the same so-so aim.
  const aimErr = { yaw: 0, pitch: 0 };
  const gauss = () => (Math.random() + Math.random() + Math.random() - 1.5) * 1.4;
  // Turn to the chest from eye height, a bit higher with distance for the paintball's drop.
  const aimAt = (t) => {
    const dx = t.e.x - lastPos.x; const dz = t.e.z - lastPos.z;
    const dy = t.e.y + 1.1 + t.d * 0.025 - (lastPos.y + 1.62);
    lastPos.yaw = -Math.atan2(dx, dz) * 180 / Math.PI + aimErr.yaw;
    lastPos.pitch = -Math.atan2(dy, Math.hypot(dx, dz)) * 180 / Math.PI + aimErr.pitch;
  };
  if (opts.fight) {
    let nextBurst = 0;
    timers.push(setInterval(() => {
      if (!inMatch || !live() || Date.now() < nextBurst) return;
      const target = nearestEnemy(opts.fight);
      if (!target) return;
      if (walkWorld && !walkWorld.sees({ x: lastPos.x, y: lastPos.y + walkWorld.EYE, z: lastPos.z },
        { x: target.e.x, y: target.e.y + 1.2, z: target.e.z })) return;
      aimErr.yaw = gauss();
      aimErr.pitch = gauss() * 0.6;
      aimAt(target);
      client.write('look', { yaw: lastPos.yaw, pitch: lastPos.pitch, flags: { onGround: true } });
      const shots = 2 + Math.floor(Math.random() * 3);
      for (let k = 0; k < shots; k++) {
        setTimeout(() => live() && client.write('use_item', { hand: 0, sequence: ++sequence,
          rotation: { x: lastPos.yaw, y: lastPos.pitch } }), 150 + k * 260);
      }
      emit('fight', { at: playerNames.get(target.e.objectUUID), distance: Number(target.d.toFixed(1)), shots });
      nextBurst = Date.now() + 1200 + Math.random() * 1300;
    }, 200));
  }
  // --walk: during a match, move like a player. world.js keeps the blocks the server sends, so the
  // bot walks around walls (A* over block columns), steps up slabs, jumps ledges and falls like a
  // client. With an enemy in sight and close it fights: circles, closes in, backs off. Otherwise it
  // walks a path to the nearest known enemy, or toward the other side of the arena (the way its
  // spawn faces) when it knows of none.
  if (walkWorld) {
    const SPEED = 0.2; // blocks per tick, a bit under vanilla walking (0.216)
    const body = walkWorld.body(lastPos);
    const eye = () => ({ x: lastPos.x, y: lastPos.y + walkWorld.EYE, z: lastPos.z });
    let spawn = { ...lastPos };
    let goal = 0; // yaw the last spawn faced
    let path = [];
    let pathAt = 0;
    let strafe = 1;
    let strafeAt = 0;
    let pauseUntil = 0;
    let wanderUntil = 0;
    let wander = 0;
    let lastTeleport = 0;
    let stuck = 0;
    let fighting = false;
    let setback = null;
    timers.push(setInterval(() => {
      if (!inMatch || !live()) return;
      const t = nearestEnemy(Infinity);
      emit('walk', { x: Number(lastPos.x.toFixed(1)), y: Number(lastPos.y.toFixed(2)), z: Number(lastPos.z.toFixed(1)),
        path: path.length, fighting, target: t ? { name: playerNames.get(t.e.objectUUID), d: Number(t.d.toFixed(1)) } : null });
    }, 5000));
    timers.push(setInterval(() => {
      if (!inMatch || !live()) return;
      const now = Date.now();
      if (teleportedAt > lastTeleport) {
        // Put back on the same spot again shortly after: the server will not let the bot go where it
        // was heading. Avoid the next path column for a while and sidestep.
        const same = setback && Math.hypot(setback.x - lastPos.x, setback.z - lastPos.z) < 0.5 && now - setback.at < 4000;
        setback = { x: lastPos.x, z: lastPos.z, at: now, n: same ? setback.n + 1 : 0 };
        if (same) {
          emit('setback', { x: Number(lastPos.x.toFixed(2)), y: Number(lastPos.y.toFixed(4)), z: Number(lastPos.z.toFixed(2)), n: setback.n,
            next: path[0], blocks: process.env.WALK_DEBUG ? walkWorld.describe(lastPos.x, lastPos.y, lastPos.z) : undefined });
          for (const w of path.slice(0, 2)) walkWorld.avoidColumn(w.x, w.z, 20000);
          wander = lastPos.yaw + 90 + Math.random() * 180;
          wanderUntil = now + 1000 + Math.random() * 800;
        }
        lastTeleport = teleportedAt;
        body.reset();
        // a match start or respawn (a long jump, not a setback): its facing is the way to go
        if (Math.hypot(lastPos.x - teleportFrom.x, lastPos.z - teleportFrom.z) > 4) { spawn = { ...lastPos }; goal = lastPos.yaw; }
        path = [];
        pathAt = 0;
        pauseUntil = now + 300;
      }
      if (now < pauseUntil || !walkWorld.loaded(lastPos.x, lastPos.z)) return;
      const target = nearestEnemy(Infinity);
      fighting = Boolean(target && target.d < 22 && walkWorld.sees(eye(), { x: target.e.x, y: target.e.y + 1.2, z: target.e.z }));
      let move = null;
      if (stuck > 8) { // stuck against something the path did not foresee: sidestep a while
        stuck = 0;
        path = [];
        wander = lastPos.yaw + (Math.random() < 0.5 ? 90 : -90) + (Math.random() - 0.5) * 60;
        wanderUntil = now + 800 + Math.random() * 800;
      }
      if (now < wanderUntil) {
        move = wander;
      } else if (fighting) {
        if (now > strafeAt) {
          strafeAt = now + 800 + Math.random() * 2000;
          strafe = Math.random() < 0.5 ? -1 : 1;
          if (Math.random() < 0.15) { pauseUntil = now + 300 + Math.random() * 700; return; }
        }
        // close in from far, back off when very close, circle in between
        const toward = -Math.atan2(target.e.x - lastPos.x, target.e.z - lastPos.z) * 180 / Math.PI;
        if (target.d > 14) move = toward + strafe * 25;
        else if (target.d < 6) move = toward + 180 + strafe * 35;
        else move = toward + strafe * 90;
        path = [];
      } else {
        if (now > pathAt || !path.length) {
          const g = goal * Math.PI / 180;
          const dest = target ? target.e : { x: spawn.x - Math.sin(g) * 60, y: spawn.y, z: spawn.z + Math.cos(g) * 60 };
          path = walkWorld.findPath(lastPos, dest) || [];
          pathAt = now + 1000;
        }
        while (path.length && Math.hypot(path[0].x - lastPos.x, path[0].z - lastPos.z) < 0.45) path.shift();
        if (path.length) move = -Math.atan2(path[0].x - lastPos.x, path[0].z - lastPos.z) * 180 / Math.PI;
        else { wander = goal + (Math.random() - 0.5) * 160; wanderUntil = now + 1500; move = wander; }
      }
      if (target && (fighting || target.d < 35)) aimAt(target);
      else { lastPos.yaw = move; lastPos.pitch = 0; }
      const r = move * Math.PI / 180;
      const result = body.step(-Math.sin(r) * SPEED, Math.cos(r) * SPEED);
      stuck = result === 'blocked' ? stuck + 1 : 0;
      if (![lastPos.x, lastPos.y, lastPos.z, lastPos.yaw, lastPos.pitch].every(Number.isFinite)) {
        emit('bad_move', { pos: lastPos }); // the server kicks for a non-finite value
        pauseUntil = now + 1000;
        return;
      }
      client.write('position_look', { x: lastPos.x, y: lastPos.y, z: lastPos.z, yaw: lastPos.yaw,
        pitch: lastPos.pitch, flags: { onGround: body.state.onGround } });
      if (process.env.WALK_DEBUG && now - lastTeleport < 1500) emit('sent', { x: lastPos.x, y: lastPos.y, z: lastPos.z, g: body.state.onGround, result, move: Math.round(move) });
    }, 50));
  }
  const reportHeld = () => {
    clearTimeout(heldTimer);
    heldTimer = setTimeout(() => {
      const items = [];
      for (let i = 0; i < 9; i++) {
        const name = itemName(playerSlots.get(36 + i));
        if (name) items.push({ slot: i, name });
      }
      emit('held_items', { items });
      // Lobby = games item in slot 0 and back-to-survival item in slot 8 (a match leaves slot 8 empty).
      const lobbyNow = items.some((e) => e.slot === 0) && items.some((e) => e.slot === 8);
      if (opts.requeue !== undefined && lobbyNow && !inLobby) {
        emit('requeue', { slot: opts.requeue });
        setTimeout(() => runStep('use:0'), 2000);
        setTimeout(() => runStep('click:10'), 3500);
        setTimeout(() => runStep(`click:${opts.requeue}`), 5000);
      }
      inLobby = lobbyNow;
      inMatch = !lobbyNow && items.some((e) => e.slot === 0);
    }, 300);
  };
  client.on('window_items', (p) => {
    stateIds.set(p.windowId, p.stateId);
    if (p.windowId === 0) {
      p.items.forEach((item, slot) => playerSlots.set(slot, item));
      reportHeld();
    } else {
      emit('window_items', { items: p.items.map((item, slot) => ({ slot, name: itemName(item) })).filter((e) => e.name) });
    }
  });
  client.on('set_slot', (p) => {
    stateIds.set(p.windowId, p.stateId);
    if (p.windowId === 0) { playerSlots.set(p.slot, p.item); reportHeld(); }
  });
  client.on('set_player_inventory', (p) => {
    if (p.slotId >= 0 && p.slotId < 9) { playerSlots.set(36 + p.slotId, p.contents); reportHeld(); }
  });
  client.on('open_window', (p) => { openWindow = p.windowId; emit('window_open', { id: p.windowId, title: plain(p.windowTitle) }); });
  client.on('close_window', () => { openWindow = 0; });
  client.on('set_title_text', (p) => emit('title', { text: plain(p.text) }));
  client.on('set_title_subtitle', (p) => emit('subtitle', { text: plain(p.text) }));
  client.on('game_state_change', (p) => {
    if (p.reason === 'change_game_mode') emit('game_mode', { mode: GAME_MODES[p.gameMode] ?? p.gameMode });
  });
  const reportJoinMode = (p) => {
    const mode = findGameMode(p);
    if (mode !== undefined) emit('game_mode', { mode: GAME_MODES[mode] ?? mode });
  };
  client.on('respawn', reportJoinMode);

  const interact = (type) => {
    const typeId = require('minecraft-data')(VERSION).entitiesByName[type].id;
    const target = [...entities.values()].find((e) => e.type === typeId);
    if (!target) { emit('interact_failed', { reason: `no ${type} in view` }); return; }
    client.write('use_entity', { target: target.entityId, hand: 0, location: { x: 0, y: 1, z: 0 }, sneaking: false });
    emit('interacted', { entityId: target.entityId, type });
  };
  const runStep = (step) => {
    const [kind, ...rest] = step.split(':');
    const arg = rest.join(':');
    emit('step', { step });
    if (kind === 'use') {
      client.write('held_item_slot', { slotId: Number(arg) });
      client.write('use_item', { hand: 0, sequence: ++sequence, rotation: { x: lastPos.yaw, y: lastPos.pitch } });
    } else if (kind === 'click') {
      client.write('window_click', { windowId: openWindow, stateId: stateIds.get(openWindow) ?? 0, slot: Number(arg),
        mouseButton: 0, mode: 0, changedSlots: [], cursorItem: undefined });
    } else if (kind === 'drop') {
      client.write('held_item_slot', { slotId: Number(arg) });
      client.write('block_dig', { status: 4, location: { x: 0, y: 0, z: 0 }, face: 0, sequence: 0 });
    } else if (kind === 'cmd') {
      client.write('chat_command', { command: arg.replace(/^\//, '') });
      emit('command_sent', { command: arg });
    } else if (kind === 'interact') {
      interact(arg);
    } else if (kind === 'aim') {
      const md = require('minecraft-data')(VERSION);
      const target = [...entities.values()].find((e) => md.entities[e.type] && md.entities[e.type].name === 'player'
        && playerNames.get(e.objectUUID) === arg);
      if (!target) { emit('aim_failed', { reason: `no player ${arg} in view` }); return 1000; }
      const dx = target.x - lastPos.x; const dy = target.y - lastPos.y; const dz = target.z - lastPos.z;
      lastPos.yaw = -Math.atan2(dx, dz) * 180 / Math.PI;
      lastPos.pitch = -Math.atan2(dy, Math.hypot(dx, dz)) * 180 / Math.PI;
      client.write('look', { yaw: lastPos.yaw, pitch: lastPos.pitch, flags: { onGround: true } });
      emit('aimed', { at: arg, yaw: Number(lastPos.yaw.toFixed(1)), pitch: Number(lastPos.pitch.toFixed(1)) });
    } else if (kind === 'fire') {
      const n = Number(arg);
      for (let k = 0; k < n; k++) {
        setTimeout(() => client.write('use_item', { hand: 0, sequence: ++sequence, rotation: { x: lastPos.yaw, y: lastPos.pitch } }), k * 200);
      }
      return n * 200 + 1000;
    } else if (kind === 'swing') {
      client.write('arm_animation', { hand: 0 });
    } else if (kind === 'look') {
      const [yaw, pitch] = arg.split(':').map(Number);
      lastPos.yaw = yaw;
      lastPos.pitch = pitch;
      client.write('look', { yaw, pitch, flags: { onGround: true } });
    } else if (kind === 'sneak') {
      client.write('player_input', { inputs: { shift: arg === 'on' } });
    } else if (kind === 'move') {
      const [dx, dz, ticks] = arg.split(':').map(Number);
      for (let k = 0; k < (ticks || 1); k++) {
        setTimeout(() => {
          lastPos.x += dx; lastPos.z += dz;
          client.write('position_look', { x: lastPos.x, y: lastPos.y, z: lastPos.z, yaw: lastPos.yaw,
            pitch: lastPos.pitch, flags: { onGround: true } });
        }, k * 50);
      }
      return (ticks || 1) * 50 + 1000;
    } else if (kind === 'slot') {
      client.write('held_item_slot', { slotId: Number(arg) });
    } else if (kind === 'quit') {
      client.end('quit');
      return Infinity;
    } else if (kind !== 'wait') {
      emit('step_failed', { step, reason: 'unknown step' });
    }
    return kind === 'wait' ? Number(arg) * 1000 : 1000;
  };
  const runSteps = (i) => {
    if (i >= opts.steps.length) {
      stayTimer = setTimeout(() => client.end('done'), opts.stay * 1000);
      return;
    }
    const delay = runStep(opts.steps[i]);
    if (delay === Infinity) return;
    setTimeout(() => runSteps(i + 1), delay);
  };
  let selfId = null;
  client.on('held_item_slot', (p) => emit('held_slot', { slot: p.slot }));
  client.on('entity_update_attributes', (p) => {
    if (p.entityId !== selfId) return;
    for (const prop of p.properties) {
      if (String(prop.key).endsWith('movement_speed')) {
        emit('speed', { base: prop.value, modifiers: (prop.modifiers || []).map((m) => [m.uuid ?? m.id, m.amount, m.operation]) });
      }
    }
  });
  client.on('login', (p) => {
    selfId = p.entityId;
    emit('joined', { host, port });
    reportJoinMode(p);
    if (opts.interactEntity) setTimeout(() => interact(opts.interactEntity), 4000);
    const command = opts.command[commandIndex];
    if (command !== undefined) {
      commandIndex++;
      setTimeout(() => {
        client.write('chat_command', { command: command.replace(/^\//, '') });
        emit('command_sent', { command });
      }, 1500);
    } else if (opts.steps.length && !stepsStarted) {
      stepsStarted = true;
      setTimeout(() => runSteps(0), 2000);
      return;
    } else if (opts.requeue !== undefined) {
      // stays online; the lobby items trigger the queue. Landed somewhere else (sent back to
      // survival by a minigames restart)? Hop back to the minigames server.
      setTimeout(() => {
        if (live() && !inLobby && !inMatch) {
          client.write('chat_command', { command: 'minigames' });
          emit('command_sent', { command: '/minigames', reason: 'not in the minigames lobby' });
        }
      }, 20000);
      return;
    }
    stayTimer = setTimeout(() => client.end('done'), opts.stay * 1000);
  });
  client.on('system_chat', (p) => emit('chat', { text: plain(p.content) }));
  client.on('transfer', (p) => {
    transferring = true;
    clearTimeout(stayTimer);
    const target = hostMap.get(p.host) || { host: p.host, port: p.port };
    emit('transferred', { to: `${p.host}:${p.port}`, via: `${target.host}:${target.port}` });
    client.end('transfer');
    if (++hops > opts.maxHops) { emit('ended', { reason: 'max hops' }); quit(0); return; }
    setTimeout(() => connect(target.host, target.port, true), 300);
  });
  const kicked = (p) => emit('kicked', { reason: plain(typeof p.reason === 'string' ? safeJson(p.reason) : p.reason) });
  client.on('kick_disconnect', kicked);
  client.on('disconnect', kicked);
  client.on('error', (e) => {
    emit('error', { message: e.message });
    // a refused or dropped connection may never fire 'end'
    if (!transferring && /ECONNREFUSED|ECONNRESET|ETIMEDOUT|EPIPE|EHOSTUNREACH/.test(e.message)) quit(1);
  });
  // Once the bot is off the server for good, end the process: the --fight interval and other
  // timers would keep a dead bot running, and a container's restart policy only acts on an exit.
  client.on('end', (reason) => {
    if (transferring) return;
    emit('ended', { reason: String(reason || '') });
    quit(0);
  });
}

function safeJson(s) { try { return JSON.parse(s); } catch { return s; } }

let quitting = false;
function quit(code) {
  if (quitting) return;
  quitting = true;
  setTimeout(() => process.exit(code), 500); // let the last event lines flush
}

connect(opts.host, opts.port, opts.transferIntent);
