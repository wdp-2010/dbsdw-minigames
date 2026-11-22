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
//               [--do use:0 --do click:10 --do cmd:/leave --do wait:2 --do interact:mannequin --do drop:0 ...]
//                   steps run in order (1 s apart) after the first join that has no --command left;
//                   use = select hotbar slot + right-click, click = left-click a slot of the open window,
//                   drop = select hotbar slot + drop key, quit = disconnect now,
//                   aim:<player> = look at that player's feet-to-feet line, fire:<n> = n right-clicks 200 ms apart.
//                   --stay counts from the end of the steps (and applies to every earlier join too).
//
// Events: start, cookie_stored, cookie_requested, pack_offered, pack_answered, dialog_shown,
// dialog_submitted, joined, chat, command_sent, transferred, kicked, ended, game_mode, position,
// title, subtitle, window_open, window_items, held_items, step, actionbar, entity_spawn (snowballs), aimed.
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
    lastPos = p;
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
    }
  });
  client.on('action_bar', (p) => emit('actionbar', { text: plain(p.text) }));

  // Inventory and windows, so steps can click and checks can see items.
  const playerSlots = new Map(); // window-0 slot -> item
  const stateIds = new Map(); // window id -> last state id
  let openWindow = 0;
  let sequence = 0;
  let lastPos = { x: 0, y: 0, z: 0, yaw: 0, pitch: 0 };
  let heldTimer;
  let inLobby = false;
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
  client.on('login', (p) => {
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
      return; // stays online; the lobby items trigger the queue
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
    if (++hops > opts.maxHops) { emit('ended', { reason: 'max hops' }); return; }
    setTimeout(() => connect(target.host, target.port, true), 300);
  });
  const kicked = (p) => emit('kicked', { reason: plain(typeof p.reason === 'string' ? safeJson(p.reason) : p.reason) });
  client.on('kick_disconnect', kicked);
  client.on('disconnect', kicked);
  client.on('error', (e) => emit('error', { message: e.message }));
  client.on('end', (reason) => { if (!transferring) emit('ended', { reason: String(reason || '') }); });
}

function safeJson(s) { try { return JSON.parse(s); } catch { return s; } }

connect(opts.host, opts.port, opts.transferIntent);
