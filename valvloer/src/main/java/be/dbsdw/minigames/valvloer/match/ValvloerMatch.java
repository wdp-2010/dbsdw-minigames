package be.dbsdw.minigames.valvloer.match;

import be.dbsdw.minigames.core.Messages;
import be.dbsdw.minigames.core.api.MatchContext;
import be.dbsdw.minigames.core.api.MatchHandler;
import be.dbsdw.minigames.core.api.MatchOutcome;
import be.dbsdw.minigames.core.pack.PackItems;
import be.dbsdw.minigames.valvloer.PackAssets;
import be.dbsdw.minigames.valvloer.rules.BlockKey;
import be.dbsdw.minigames.valvloer.rules.FloorDecay;
import be.dbsdw.minigames.valvloer.rules.Footprint;
import be.dbsdw.minigames.valvloer.rules.Settings;
import be.dbsdw.minigames.valvloer.rules.Snapshot;
import be.dbsdw.minigames.valvloer.rules.Survivors;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/** One Valvloer match: every floor block someone stands on drops away; falling below the floor is out. */
public final class ValvloerMatch implements MatchHandler {
    /** How long a dropped block's falling ghost shows, in ticks. */
    private static final int FALL_TICKS = 12;
    /** At most this many falling ghosts at once; the oldest goes first. */
    private static final int MAX_FALLING = 96;

    /** A display entity showing a dropped block on its way down; removed {@link #FALL_TICKS} after it appeared. */
    private record Falling(BlockDisplay display, long spawned) {}

    private final MatchContext ctx;
    private final Settings settings;
    private final Messages messages;
    private final Restorer restorer;
    private final Logger log;
    private final Set<Material> floorTypes;
    private final Map<UUID, Integer> campTicks = new HashMap<>();
    private final Map<UUID, Long> drops = new HashMap<>();
    private final Map<UUID, Integer> places = new HashMap<>();
    private final Map<UUID, String> names = new LinkedHashMap<>();
    private World world;
    private BoundingBox box;
    private Snapshot snapshot;
    private FloorDecay decay;
    private Survivors survivors;
    private Location spectate;
    private int lowestY;
    private boolean restored;
    private final Deque<Falling> falling = new ArrayDeque<>();

    public ValvloerMatch(MatchContext ctx, Settings settings, Set<Material> floorTypes,
                         Messages messages, Restorer restorer, Logger log) {
        this.ctx = ctx;
        this.settings = settings;
        this.floorTypes = floorTypes;
        this.messages = messages;
        this.restorer = restorer;
        this.log = log;
    }

    @Override
    public void start() {
        world = ctx.arena().world();
        box = ctx.arena().bounds();
        snapshot = capture();
        if (snapshot.blocks().isEmpty()) {
            throw new IllegalStateException("arena " + ctx.arena().name() + " has no floor blocks (floor.blocks in config.yml)");
        }
        restorer.save(ctx.arena().name(), snapshot);
        lowestY = snapshot.lowestY();
        int highest = snapshot.blocks().keySet().stream().mapToInt(BlockKey::y).max().orElse(lowestY);
        Location c = box.getCenter().toLocation(world);
        spectate = new Location(world, c.getX(), Math.min(box.getMaxY() - 2, highest + 7), c.getZ(), 0, 60);
        decay = new FloorDecay(settings.dropDelayTicks());
        Map<UUID, Integer> teams = new LinkedHashMap<>();
        for (int t = 0; t < ctx.teamCount(); t++) {
            for (Player p : ctx.team(t)) {
                teams.put(p.getUniqueId(), t);
                names.put(p.getUniqueId(), p.getName());
                p.showTitle(Title.title(messages.get("grace.title"), messages.get("grace.subtitle")));
            }
        }
        survivors = new Survivors(ctx.teamCount(), teams);
        log.info("[valvloer] " + ctx.arena().name() + ": " + snapshot.blocks().size() + " floor blocks, lowest y " + lowestY);
    }

    /** Every floor block inside the arena bounds. */
    private Snapshot capture() {
        Map<Long, String> blocks = new LinkedHashMap<>();
        int x1 = (int) Math.floor(box.getMinX());
        int y1 = (int) Math.floor(box.getMinY());
        int z1 = (int) Math.floor(box.getMinZ());
        int x2 = (int) Math.ceil(box.getMaxX()) - 1;
        int y2 = (int) Math.ceil(box.getMaxY()) - 1;
        int z2 = (int) Math.ceil(box.getMaxZ()) - 1;
        for (int y = y1; y <= y2; y++) {
            for (int x = x1; x <= x2; x++) {
                for (int z = z1; z <= z2; z++) {
                    Block b = world.getBlockAt(x, y, z);
                    if (floorTypes.contains(b.getType())) {
                        blocks.put(BlockKey.of(x, y, z), b.getBlockData().getAsString());
                    }
                }
            }
        }
        return new Snapshot(world.getName(), blocks);
    }

    @Override
    public void tick() {
        long now = ctx.ticks();
        long grace = settings.graceTicks();
        if (now < grace && (grace - now) % 20 == 0) {
            everyone().forEach(p -> p.sendActionBar(messages.get("grace.bar",
                    Placeholder.unparsed("seconds", String.valueOf((grace - now) / 20)))));
        }
        if (now == grace) {
            everyone().forEach(p -> {
                p.showTitle(Title.title(messages.get("go.title"), messages.get("go.subtitle")));
                PackItems.play(p, PackAssets.SOUND_GO, 1f, 1f);
            });
        }
        boolean fell = false;
        for (Player p : everyone()) {
            if (!survivors.alive(p.getUniqueId())) {
                keepInside(p);
                continue;
            }
            Location l = p.getLocation();
            if (l.getY() < lowestY || !inside(l)) {
                fall(p);
                fell = true;
                continue;
            }
            if (now >= grace && step(p, l, now)) {
                fall(p);
                fell = true;
            }
        }
        List<Player> viewers = everyone();
        for (long key : decay.due(now)) {
            Block b = world.getBlockAt(BlockKey.x(key), BlockKey.y(key), BlockKey.z(key));
            if (snapshot.blocks().containsKey(key) && !b.isEmpty()) {
                Location at = b.getLocation();
                viewers.forEach(v -> v.sendBlockDamage(at, 0f, crackId(key)));
                BlockData data = b.getBlockData();
                b.setType(Material.AIR, false);
                dropGhost(at, data, now);
                PackItems.play(at.clone().add(0.5, 0.5, 0.5), PackAssets.SOUND_DROP, 0.7f, 0.9f + (key & 3) * 0.06f);
            }
        }
        if (now % 2 == 0) {
            for (FloorDecay.Crack c : decay.cracking(now)) {
                Location at = new Location(world, BlockKey.x(c.key()), BlockKey.y(c.key()), BlockKey.z(c.key()));
                viewers.forEach(v -> v.sendBlockDamage(at, c.progress(), crackId(c.key())));
            }
        }
        tickFalling(now);
        if (fell && decide(survivors.result())) {
            return;
        }
        if (now >= settings.timeLimitTicks()) {
            everyone().forEach(p -> p.sendMessage(messages.get("result.time")));
            Survivors.Result r = survivors.atTimeLimit();
            decide(r instanceof Survivors.Result.Running ? new Survivors.Result.Draw() : r);
            return;
        }
        if (now % 10 == 0 && now >= grace) {
            Component bar = hud(now);
            everyone().forEach(p -> p.sendActionBar(bar));
        }
    }

    /** Marks the floor under a standing player. True when they camp too long on something that is not floor. */
    private boolean step(Player p, Location l, long now) {
        boolean onFloor = false;
        boolean onOther = false;
        for (long key : Footprint.under(l.getX(), l.getY(), l.getZ())) {
            Block b = world.getBlockAt(BlockKey.x(key), BlockKey.y(key), BlockKey.z(key));
            if (b.isEmpty()) {
                continue;
            }
            if (snapshot.blocks().containsKey(key)) {
                onFloor = true;
                if (decay.touch(key, now)) {
                    drops.merge(p.getUniqueId(), 1L, Long::sum);
                    Location top = b.getLocation().add(0.5, 1.02, 0.5);
                    world.spawnParticle(Particle.BLOCK, top, 6, 0.3, 0.02, 0.3, 0, b.getBlockData());
                    PackItems.play(top, PackAssets.SOUND_CRACK, 0.45f, 0.9f + (key & 7) * 0.04f);
                }
            } else if (b.getType().isSolid()) {
                onOther = true;
            }
        }
        UUID id = p.getUniqueId();
        if (!onOther || onFloor) {
            campTicks.remove(id);
            return false;
        }
        int t = campTicks.merge(id, 1, Integer::sum);
        if (t == 20) {
            p.sendActionBar(messages.get("camp.warn"));
            p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.7f);
        }
        return t >= settings.campTicks();
    }

    private void fall(Player p) {
        OptionalInt place = survivors.fall(p.getUniqueId());
        if (place.isEmpty()) {
            return;
        }
        places.put(p.getUniqueId(), place.getAsInt());
        campTicks.remove(p.getUniqueId());
        p.setGameMode(GameMode.SPECTATOR);
        p.teleport(spectate);
        p.showTitle(Title.title(messages.get("out.title"), messages.get("out.subtitle",
                Placeholder.unparsed("place", String.valueOf(place.getAsInt())),
                Placeholder.unparsed("total", String.valueOf(survivors.total())))));
        PackItems.play(p, PackAssets.SOUND_FALL, 1f, 1f);
        Component line = messages.get("out.broadcast", Placeholder.unparsed("player", p.getName()),
                Placeholder.unparsed("left", String.valueOf(survivors.left())));
        everyone().forEach(o -> o.sendMessage(line));
    }

    /** A stable id per block for its crack overlay, so each block keeps its own stage. */
    private static int crackId(long key) {
        return Integer.MIN_VALUE + 1 + (Long.hashCode(key) & 0x3fffffff);
    }

    /** Shows the block that just dropped sinking and shrinking for a moment; the world itself is air. */
    private void dropGhost(Location at, BlockData data, long now) {
        if (falling.size() >= MAX_FALLING) {
            falling.removeFirst().display().remove();
        }
        BlockDisplay d = world.spawn(at, BlockDisplay.class, e -> {
            e.setPersistent(false);
            e.setBlock(data);
            e.setTeleportDuration(0);
        });
        falling.addLast(new Falling(d, now));
    }

    /** Starts each new ghost's fall a tick after it appeared (so the client interpolates it) and removes old ones. */
    private void tickFalling(long now) {
        while (!falling.isEmpty() && now - falling.peekFirst().spawned() >= FALL_TICKS) {
            falling.removeFirst().display().remove();
        }
        for (Falling f : falling) {
            if (now - f.spawned() == 1 && f.display().isValid()) {
                f.display().setInterpolationDelay(0);
                f.display().setInterpolationDuration(FALL_TICKS - 2);
                f.display().setTransformation(new Transformation(new Vector3f(0.2f, -2.5f, 0.2f),
                        new AxisAngle4f((float) Math.toRadians(25), 0.7071f, 0f, 0.7071f), new Vector3f(0.6f, 0.6f, 0.6f),
                        new AxisAngle4f()));
            }
        }
    }

    private boolean inside(Location l) {
        return l.getX() >= box.getMinX() && l.getX() <= box.getMaxX() && l.getZ() >= box.getMinZ() && l.getZ() <= box.getMaxZ();
    }

    private void keepInside(Player p) {
        Location l = p.getLocation();
        if (!inside(l) || l.getY() < box.getMinY() || l.getY() > box.getMaxY()) {
            p.teleport(spectate);
        }
    }

    private Component hud(long now) {
        long left = Math.max(0, settings.timeLimitTicks() - now) / 20;
        return messages.get("hud.bar",
                Placeholder.component("team0", ctx.teamName(0)), Placeholder.unparsed("a", String.valueOf(survivors.left(0))),
                Placeholder.component("team1", ctx.teamName(1)), Placeholder.unparsed("b", String.valueOf(survivors.left(1))),
                Placeholder.unparsed("time", String.format("%d:%02d", left / 60, left % 60)));
    }

    private List<Player> everyone() {
        List<Player> all = new ArrayList<>();
        for (int t = 0; t < ctx.teamCount(); t++) {
            all.addAll(ctx.team(t));
        }
        return all;
    }

    /** Ends the match on a win or draw; false while it is still running. */
    private boolean decide(Survivors.Result result) {
        return switch (result) {
            case Survivors.Result.Win(int team) -> {
                ctx.finish(MatchOutcome.win(team, resultLines()));
                yield true;
            }
            case Survivors.Result.Draw() -> {
                ctx.finish(MatchOutcome.draw(resultLines()));
                yield true;
            }
            case Survivors.Result.Running() -> false;
        };
    }

    @Override
    public long ticksLeft() {
        return Math.max(0, settings.timeLimitTicks() - ctx.ticks());
    }

    @Override
    public void playerLeft(Player player, int team) {
        survivors.remove(player.getUniqueId());
        if (!everyone().isEmpty()) {
            decide(survivors.result());
        }
    }

    @Override
    public void end() {
        if (restored || snapshot == null) {
            return;
        }
        restored = true;
        falling.forEach(f -> f.display().remove());
        falling.clear();
        if (decay != null) {
            List<Player> viewers = everyone();
            for (FloorDecay.Crack c : decay.cracking(Long.MAX_VALUE / 2)) {
                Location at = new Location(world, BlockKey.x(c.key()), BlockKey.y(c.key()), BlockKey.z(c.key()));
                viewers.forEach(v -> v.sendBlockDamage(at, 0f, crackId(c.key())));
            }
        }
        int changed = Restorer.restore(snapshot, world);
        restorer.done(ctx.arena().name());
        for (var e : drops.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) {
                ctx.addStat(p, "drops", e.getValue());
            }
        }
        for (Player p : everyone()) {
            if (survivors.alive(p.getUniqueId())) {
                ctx.addStat(p, "survived", 1);
            }
        }
        log.info("[valvloer] " + ctx.arena().name() + ": floor restored (" + changed + " blocks)");
    }

    @Override
    public List<Component> resultLines() {
        List<String> standing = names.entrySet().stream()
                .filter(e -> survivors != null && survivors.alive(e.getKey())).map(Map.Entry::getValue).toList();
        if (standing.isEmpty()) {
            return List.of();
        }
        return List.of(messages.get("result.standing", Placeholder.unparsed("players", String.join(", ", standing))));
    }

    @Override
    public Component personalLine(UUID player) {
        String n = String.valueOf(drops.getOrDefault(player, 0L));
        Integer place = places.get(player);
        if (place == null) {
            return messages.get("personal.standing", Placeholder.unparsed("drops", n));
        }
        return messages.get("personal.out", Placeholder.unparsed("place", String.valueOf(place)),
                Placeholder.unparsed("total", String.valueOf(survivors.total())), Placeholder.unparsed("drops", n));
    }
}
