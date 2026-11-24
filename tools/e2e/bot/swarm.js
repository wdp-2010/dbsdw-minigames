// Load-test swarm: many idle headless players in ONE node process (a few MB each instead of ~60 MB
// for a hop.js process per bot). Test servers only. Pair it with swarm_spread.py, which teleports
// the bots two per Folia region over the world.
//
//   node swarm.js --host dbsdw-bot-viaproxy --port 25565 --count 50 --prefix Swarm --password pw
//                 [--start 1] [--stagger 5] [--reconnect 30]
//
// Each bot: registers/logs in through AuthMe's pre-join dialog, accepts the resource pack, confirms
// teleports, acknowledges chunk batches (or the server stops sending chunks) and ends every tick with
// tick_end like a real 26.x client. It never moves on its own. A kicked or dropped bot reconnects
// after --reconnect seconds; every connect waits its turn in one queue. One JSON status line every 30 s: online, joined, kicks, rss.
'use strict';

const mc = require('minecraft-protocol');
const nbt = require('prismarine-nbt');

const VERSION = process.env.MCV || '26.1';
const PACK = { SUCCESSFULLY_LOADED: 0, ACCEPTED: 3, DOWNLOADED: 4 };

const opts = { start: 1, stagger: 5, reconnect: 30, prefix: 'Swarm' };
for (let i = 2; i < process.argv.length; i++) {
  const a = process.argv[i];
  const v = process.argv[++i];
  if (a === '--host') opts.host = v;
  else if (a === '--port') opts.port = Number(v);
  else if (a === '--count') opts.count = Number(v);
  else if (a === '--start') opts.start = Number(v);
  else if (a === '--prefix') opts.prefix = v;
  else if (a === '--password') opts.password = v;
  else if (a === '--stagger') opts.stagger = Number(v);
  else if (a === '--reconnect') opts.reconnect = Number(v);
  else throw new Error(`unknown argument ${a}`);
}
if (!opts.host || !opts.port || !opts.count || !opts.password) throw new Error('--host, --port, --count and --password are required');

const emit = (event, data = {}) => console.log(JSON.stringify({ t: new Date().toISOString().slice(11, 23), event, ...data }));
const varint = (n) => { const o = []; do { let b = n & 0x7f; n >>>= 7; if (n) b |= 0x80; o.push(b); } while (n); return Buffer.from(o); };

// minecraft-data's model of custom_click_action is wrong: vanilla sends id + varint-length-prefixed NBT.
let clickPid;
function sendCustomClick(client, id, values) {
  const value = Object.fromEntries(Object.entries(values).map(([k, v]) => [k, { type: 'string', value: v }]));
  const tag = nbt.protos.big.createPacketBuffer('anonymousNbt', { type: 'compound', name: '', value });
  if (clickPid === undefined) {
    const map = require('minecraft-data')(VERSION).protocol[client.state].toServer.types.packet[1][0].type[1].mappings;
    clickPid = parseInt(Object.keys(map).find((k) => map[k] === 'custom_click_action'), 16);
  }
  const idb = Buffer.from(id, 'utf8');
  client.writeRaw(Buffer.concat([varint(clickPid), varint(idb.length), idb, varint(tag.length), tag]));
}

const stats = { joined: 0, kicks: 0 };
const live = new Set(); // clients in play state with a confirmed position

function startBot(name) {
  const client = mc.createClient({ host: opts.host, port: opts.port, username: name, version: VERSION, auth: 'offline', hideErrors: true });
  let done = false;
  const again = (why, reason) => {
    if (done) return;
    done = true;
    live.delete(client);
    if (why === 'kicked') stats.kicks++;
    emit(why, { bot: name, reason });
    setTimeout(() => enqueue(name), opts.reconnect * 1000);
  };

  client.on('cookie_request', (p) => client.write('cookie_response', { key: p.cookie, value: undefined }));
  client.on('add_resource_pack', (p) => {
    for (const result of [PACK.ACCEPTED, PACK.DOWNLOADED, PACK.SUCCESSFULLY_LOADED]) client.write('resource_pack_receive', { uuid: p.uuid, result });
  });
  client.on('show_dialog', (p) => {
    let text;
    try { text = JSON.stringify(nbt.simplify(p.dialog && p.dialog.data !== undefined && p.dialog.type !== 'compound' ? p.dialog.data : p.dialog)); } catch { text = JSON.stringify(p); }
    if (/prejoin-register/.test(text)) sendCustomClick(client, 'authme:prejoin-register/submit', { password: opts.password, confirm: opts.password });
    else if (/prejoin-login/.test(text)) sendCustomClick(client, 'authme:prejoin-login/submit', { password: opts.password });
  });
  // Confirm every server teleport and send the position back once (26.x takes it from that move packet).
  client.on('position', (p) => {
    client.write('teleport_confirm', { teleportId: p.teleportId });
    client.write('position_look', { x: p.x, y: p.y, z: p.z, yaw: p.yaw, pitch: p.pitch, flags: { onGround: false } });
    if (!live.has(client)) {
      client.write('player_loaded', {});
      live.add(client);
      stats.joined++;
      emit('joined', { bot: name, x: Math.round(p.x), y: Math.round(p.y), z: Math.round(p.z) });
    }
  });
  client.on('update_health', (p) => { if (p.health <= 0) client.write('client_command', { actionId: 'perform_respawn' }); });
  client.on('chunk_batch_finished', () => client.write('chunk_batch_received', { chunksPerTick: 20 }));
  client.on('kick_disconnect', (p) => again('kicked', JSON.stringify(p.reason).slice(0, 200)));
  client.on('disconnect', (p) => again('kicked', JSON.stringify(p.reason).slice(0, 200)));
  client.on('error', (e) => again('error', String(e.message || e).slice(0, 200)));
  client.on('end', () => again('ended'));
}

// One shared tick for every bot: a real client ends each tick with tick_end.
setInterval(() => { for (const c of live) c.write('tick_end', {}); }, 50);
setInterval(() => emit('status', { online: live.size, joined: stats.joined, kicks: stats.kicks, rssMB: Math.round(process.memoryUsage().rss / 1048576) }), 30000);

// Every (re)connect goes through one queue, --stagger seconds apart: all bots share one source IP and
// the server throttles a second connection from it within 4 s (bukkit connection-throttle).
const queue = [];
const enqueue = (name) => { if (!queue.includes(name)) queue.push(name); };
setInterval(() => { const name = queue.shift(); if (name) startBot(name); }, opts.stagger * 1000);
for (let i = 0; i < opts.count; i++) enqueue(`${opts.prefix}${String(opts.start + i).padStart(2, '0')}`);
