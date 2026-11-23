package be.dbsdw.minigames.verstoppertje.match;

import be.dbsdw.minigames.core.Messages;
import be.dbsdw.minigames.core.api.MatchContext;
import be.dbsdw.minigames.core.api.MatchHandler;
import be.dbsdw.minigames.core.api.MatchOutcome;
import be.dbsdw.minigames.verstoppertje.rules.BlockRules;
import be.dbsdw.minigames.verstoppertje.rules.Phase;
import be.dbsdw.minigames.verstoppertje.rules.Roles;
import be.dbsdw.minigames.verstoppertje.rules.Settings;
import be.dbsdw.minigames.verstoppertje.rules.Stillness;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.random.RandomGenerator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * One Verstoppertje match. Team 0 hides, team 1 seeks; a hider who is hit joins the seekers. The
 * seekers win when nobody is left hiding, the hiders when one of them is still hidden at the end.
 * Everyone plays in creative without flying and may place and break a limited number of blocks; the
 * arena is put back when the match ends. A hider is the block they hold.
 */
public final class VerstopMatch implements MatchHandler {
    public static final int HIDERS = 0;
    public static final int SEEKERS = 1;

    private final Plugin plugin;
    private final MatchContext ctx;
    private final Settings settings;
    private final List<Material> disguises;
    private final Messages messages;
    private final Logger log;
    private final RandomGenerator rnd;
    private final Set<VerstopMatch> active;
    private final Map<UUID, Disguise> hidden = new LinkedHashMap<>();
    private final Map<UUID, Location> waiting = new HashMap<>();
    private final Map<UUID, Integer> foundBy = new HashMap<>();
    private final Map<UUID, String> finder = new HashMap<>();
    private final Map<UUID, Long> foundAt = new HashMap<>();
    private final Map<UUID, String> names = new LinkedHashMap<>();
    private final Map<UUID, Integer> placed = new HashMap<>();
    private final Map<UUID, Integer> broken = new HashMap<>();
    /** Every block a player changed (or that may change with it), with what it was at the start. */
    private final Map<Block, BlockData> original = new LinkedHashMap<>();
    private boolean released;

    public VerstopMatch(Plugin plugin, MatchContext ctx, Settings settings, List<Material> disguises, Messages messages,
                        Logger log, RandomGenerator rnd, Set<VerstopMatch> active) {
        this.plugin = plugin;
        this.active = active;
        this.ctx = ctx;
        this.settings = settings;
        this.disguises = List.copyOf(disguises);
        this.messages = messages;
        this.log = log;
        this.rnd = rnd;
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void start() {
        active.add(this);
        List<UUID> hiders = ctx.team(HIDERS).stream().map(Player::getUniqueId).toList();
        List<UUID> seekers = ctx.team(SEEKERS).stream().map(Player::getUniqueId).toList();
        int total = hiders.size() + seekers.size();
        Set<UUID> chosen = Roles.pick(hiders, seekers, Roles.seekers(total, settings.playersPerSeeker()), rnd);
        for (Player p : everyone()) {
            names.put(p.getUniqueId(), p.getName());
            ctx.moveToTeam(p, chosen.contains(p.getUniqueId()) ? SEEKERS : HIDERS);
            p.setGameMode(GameMode.CREATIVE);
            // Creative allows flight, and players may stand on fake blocks the server does not know
            // about (it would kick them for flying without it). Flying itself is stopped in the listener.
            p.setAllowFlight(true);
            p.setFlying(false);
        }
        List<Location> hideSpawns = ctx.arena().spawns(HIDERS);
        List<Location> seekSpawns = ctx.arena().spawns(SEEKERS);
        List<Player> hiderList = ctx.team(HIDERS);
        for (int i = 0; i < hiderList.size(); i++) {
            Player h = hiderList.get(i);
            h.teleport(hideSpawns.get(i % hideSpawns.size()));
            int slot = rnd.nextInt(Math.min(9, disguises.size()));
            hiderItems(h);
            h.getInventory().setHeldItemSlot(slot);
            hidden.put(h.getUniqueId(), new Disguise(plugin, h, disguises.get(slot).createBlockData()));
            h.showTitle(Title.title(messages.get("hide.title"), messages.get("hide.subtitle",
                    Placeholder.unparsed("seconds", String.valueOf(settings.hideTicks() / 20)))));
        }
        List<Player> seekerList = ctx.team(SEEKERS);
        for (int i = 0; i < seekerList.size(); i++) {
            Player s = seekerList.get(i);
            Location at = seekSpawns.get(i % seekSpawns.size());
            s.teleport(at);
            s.getInventory().clear();
            waiting.put(s.getUniqueId(), at);
            s.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, (int) settings.hideTicks() + 40, 0, false, false));
            s.showTitle(Title.title(messages.get("wait.title"), messages.get("wait.subtitle",
                    Placeholder.unparsed("seconds", String.valueOf(settings.hideTicks() / 20)))));
        }
        // Hiders are invisible to everyone else: only their block shows.
        for (Disguise d : hidden.values()) {
            for (Player o : everyone()) {
                if (!o.equals(d.hider)) {
                    o.hidePlayer(plugin, d.hider);
                }
            }
        }
        log.info("[verstoppertje] " + ctx.arena().name() + ": " + hiderList.size() + " hiders, " + seekerList.size() + " seekers");
    }

    @Override
    public void tick() {
        long now = ctx.ticks();
        Phase phase = Phase.at(now, settings.hideTicks(), settings.seekTicks());
        if (phase == Phase.HIDE) {
            holdSeekers();
            if ((settings.hideTicks() - now) % 20 == 0) {
                Component bar = messages.get("hide.bar", Placeholder.unparsed("seconds", String.valueOf((settings.hideTicks() - now) / 20)));
                everyone().forEach(p -> p.sendActionBar(bar));
            }
        } else if (!released) {
            release();
        }
        List<Player> all = everyone();
        for (Disguise d : List.copyOf(hidden.values())) {
            update(d, now, all);
        }
        if (now % 40 == 0) {
            hidden.values().forEach(d -> d.sendFake(all));
        }
        if (phase == Phase.SEEK && settings.hints() && now > settings.hideTicks()
                && (now - settings.hideTicks()) % settings.hintTicks() == 0) {
            hint();
        }
        if (phase == Phase.OVER) {
            ctx.finish(MatchOutcome.win(HIDERS, resultLines()));
            return;
        }
        if (now % 10 == 0 && phase == Phase.SEEK) {
            long left = (settings.hideTicks() + settings.seekTicks() - now) / 20;
            Component bar = messages.get("hud.bar", Placeholder.unparsed("hiders", String.valueOf(hidden.size())),
                    Placeholder.unparsed("time", String.format("%d:%02d", left / 60, left % 60)));
            all.forEach(p -> p.sendActionBar(bar));
        }
    }

    private void holdSeekers() {
        for (var e : waiting.entrySet()) {
            Player s = Bukkit.getPlayer(e.getKey());
            if (s != null && s.getLocation().distanceSquared(e.getValue()) > 0.25) {
                Location back = e.getValue().clone();
                back.setYaw(s.getLocation().getYaw());
                back.setPitch(s.getLocation().getPitch());
                s.teleport(back);
            }
        }
    }

    private void release() {
        released = true;
        waiting.clear();
        for (Player s : ctx.team(SEEKERS)) {
            seekerSetup(s);
            s.showTitle(Title.title(messages.get("seek.title"), messages.get("seek.subtitle")));
            s.playSound(s, Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.2f);
        }
        for (Player h : ctx.team(HIDERS)) {
            h.showTitle(Title.title(messages.get("coming.title"), messages.get("coming.subtitle")));
            h.playSound(h, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.8f);
        }
    }

    private void update(Disguise d, long now, List<Player> all) {
        held(d, all);
        if (d.solid() && !Disguise.canSolidify(d.solidBlock())) {
            // Someone broke the block under it, or built into its spot.
            d.loosen(all);
        }
        Location l = d.hider.getLocation();
        Block feet = l.getBlock();
        boolean moved = d.still.update(Stillness.key(feet.getX(), feet.getY(), feet.getZ()), now);
        if (moved && d.solid()) {
            d.loosen(all);
            d.hider.sendActionBar(messages.get("solid.loose"));
        }
        if (d.solid()) {
            return;
        }
        if (d.still.stillFor(now) >= settings.solidTicks() && Disguise.canSolidify(feet)) {
            d.solidify(feet, all);
            d.hider.sendActionBar(messages.get("solid.on"));
            d.hider.playSound(d.hider, Sound.BLOCK_WOOD_PLACE, 0.6f, 1.2f);
        } else {
            d.follow();
        }
    }

    private void hint() {
        for (Disguise d : hidden.values()) {
            Location at = d.hider.getLocation().add(0, 0.6, 0);
            at.getWorld().playSound(at, Sound.ENTITY_CHICKEN_AMBIENT, 1.2f, 0.8f + rnd.nextFloat() * 0.6f);
            at.getWorld().spawnParticle(Particle.NOTE, at.clone().add(0, 0.8, 0), 1);
        }
        Component line = messages.get("hint");
        everyone().forEach(p -> p.sendActionBar(line));
    }

    // ------------------------------------------------------------------ hits

    /** A seeker hits an entity; true when it was a hider's block (the hider is found). */
    public boolean attack(Player seeker, Entity target) {
        if (!isSeeker(seeker) || !released) {
            return false;
        }
        for (Disguise d : hidden.values()) {
            if (d.owns(target)) {
                found(d, seeker);
                return true;
            }
        }
        return false;
    }

    /** A seeker swings (in the air or at a fake block): finds the nearest hider block on their line of sight. */
    public void swing(Player seeker) {
        if (!isSeeker(seeker) || !released) {
            return;
        }
        Location eye = seeker.getEyeLocation();
        Vector dir = eye.getDirection();
        double limit = settings.reach();
        RayTraceResult wall = eye.getWorld().rayTraceBlocks(eye, dir, limit, FluidCollisionMode.NEVER, true);
        if (wall != null) {
            limit = Math.min(limit, wall.getHitPosition().distance(eye.toVector()));
        }
        Disguise best = null;
        double bestDist = limit;
        for (Disguise d : hidden.values()) {
            double t = d.hit(eye, dir);
            if (t >= 0 && t <= bestDist) {
                best = d;
                bestDist = t;
            }
        }
        if (best != null) {
            found(best, seeker);
        }
    }

    private void found(Disguise d, Player seeker) {
        Player h = d.hider;
        hidden.remove(h.getUniqueId());
        List<Player> all = everyone();
        d.remove(all);
        for (Player o : Bukkit.getOnlinePlayers()) {
            o.showPlayer(plugin, h);
        }
        foundBy.merge(seeker.getUniqueId(), 1, Integer::sum);
        finder.put(h.getUniqueId(), seeker.getName());
        foundAt.put(h.getUniqueId(), ctx.ticks() - settings.hideTicks());
        ctx.addStat(seeker, "found", 1);
        ctx.moveToTeam(h, SEEKERS);
        h.getInventory().clear();
        seekerSetup(h);
        h.showTitle(Title.title(messages.get("found.title"), messages.get("found.subtitle",
                Placeholder.unparsed("seeker", seeker.getName()))));
        h.getWorld().playSound(h.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 0.6f);
        h.getWorld().spawnParticle(Particle.BLOCK, h.getLocation().add(0, 0.5, 0), 25, 0.3, 0.3, 0.3, 0, d.data());
        seeker.playSound(seeker, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        Component line = messages.get("found.broadcast", Placeholder.unparsed("seeker", seeker.getName()),
                Placeholder.unparsed("hider", h.getName()), Placeholder.unparsed("left", String.valueOf(hidden.size())));
        all.forEach(p -> p.sendMessage(line));
        log.info("[verstoppertje] " + seeker.getName() + " found " + h.getName() + ", " + hidden.size() + " left");
        if (hidden.isEmpty()) {
            ctx.finish(MatchOutcome.win(SEEKERS, resultLines()));
        }
    }

    // ------------------------------------------------------------------ disguise items

    /** A hider is the block in their hand: switching hotbar slots (or taking one from the menu) changes it. */
    private void held(Disguise d, List<Player> all) {
        Material m = d.hider.getInventory().getItemInMainHand().getType();
        if (m == d.data().getMaterial() || !allowed(m)) {
            return;
        }
        d.change(m.createBlockData(), all);
        d.hider.sendActionBar(messages.get("item.picked", Placeholder.component("block", Component.translatable(m.translationKey()))));
        d.hider.playSound(d.hider, Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
    }

    /** Whether players may place this block and hide as it. */
    public static boolean allowed(Material m) {
        return m != null && BlockRules.allowed(m.name(), m.isBlock(), m.isSolid(), m.hasGravity());
    }

    private void hiderItems(Player h) {
        h.getInventory().clear();
        for (int i = 0; i < Math.min(9, disguises.size()); i++) {
            h.getInventory().setItem(i, ItemStack.of(disguises.get(i)));
        }
    }

    // ------------------------------------------------------------------ building

    /**
     * A player places a block (the world already shows it). False when they may not: the event is then
     * cancelled. {@code replaced} are the states it replaced (two for a door).
     */
    public boolean place(Player p, Block block, List<BlockState> replaced) {
        if (!canBuild(p, block) || !allowed(block.getType())) {
            return false;
        }
        int used = placed.getOrDefault(p.getUniqueId(), 0);
        if (used >= settings.placeLimit()) {
            p.sendActionBar(messages.get("build.place-full"));
            return false;
        }
        for (BlockState st : replaced) {
            original.putIfAbsent(st.getBlock(), st.getBlockData());
        }
        for (BlockState st : replaced) {
            remember(st.getBlock(), false);
        }
        placed.put(p.getUniqueId(), used + 1);
        p.sendActionBar(messages.get("build.placed", Placeholder.unparsed("left",
                String.valueOf(BlockRules.left(settings.placeLimit(), used + 1)))));
        return true;
    }

    /** A player breaks a block (it is still there). False when they may not. */
    public boolean dig(Player p, Block block) {
        if (!canBuild(p, block)) {
            return false;
        }
        int used = broken.getOrDefault(p.getUniqueId(), 0);
        if (used >= settings.breakLimit()) {
            p.sendActionBar(messages.get("build.break-full"));
            return false;
        }
        remember(block, true);
        broken.put(p.getUniqueId(), used + 1);
        p.sendActionBar(messages.get("build.broken", Placeholder.unparsed("left",
                String.valueOf(BlockRules.left(settings.breakLimit(), used + 1)))));
        return true;
    }

    private boolean canBuild(Player p, Block block) {
        return !frozen(p) && inArena(block);
    }

    /** Inside this match's arena. */
    public boolean inArena(Block b) {
        return b.getWorld().equals(ctx.arena().world()) && ctx.arena().bounds().contains(b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5);
    }

    /** Saves the block (when {@code self}) and its neighbours, which may drop or change with it, as they are now. */
    private void remember(Block b, boolean self) {
        if (self) {
            original.putIfAbsent(b, b.getBlockData());
        }
        for (BlockFace f : new BlockFace[] {BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Block n = b.getRelative(f);
            original.putIfAbsent(n, n.getBlockData());
        }
        Block up2 = b.getRelative(0, 2, 0);
        original.putIfAbsent(up2, up2.getBlockData());
    }

    /** Puts every changed block back, last change first. */
    private void restore() {
        List<Map.Entry<Block, BlockData>> list = new ArrayList<>(original.entrySet());
        for (int i = list.size() - 1; i >= 0; i--) {
            Block b = list.get(i).getKey();
            if (!b.getBlockData().equals(list.get(i).getValue())) {
                b.setBlockData(list.get(i).getValue(), false);
            }
        }
        if (!list.isEmpty()) {
            log.info("[verstoppertje] " + ctx.arena().name() + ": restored " + list.size() + " blocks");
        }
        original.clear();
    }

    private void seekerSetup(Player s) {
        s.removePotionEffect(PotionEffectType.BLINDNESS);
        // A sword: in creative it cannot break blocks, so hitting a hider never breaks the arena.
        ItemStack sword = ItemStack.of(Material.WOODEN_SWORD);
        sword.editMeta(meta -> meta.customName(messages.get("item.seeker")));
        s.getInventory().setItem(0, sword);
        s.getInventory().setHeldItemSlot(0);
    }

    // ------------------------------------------------------------------ bookkeeping

    /** Whether the player is in this match. */
    public boolean has(Player p) {
        return ctx.teamOf(p).isPresent();
    }

    public boolean isSeeker(Player p) {
        return ctx.teamOf(p).orElse(-1) == SEEKERS;
    }

    public boolean isHider(Player p) {
        return hidden.containsKey(p.getUniqueId());
    }

    /** Seekers in the hide phase may look around but not walk. */
    public boolean frozen(Player p) {
        return waiting.containsKey(p.getUniqueId());
    }

    private List<Player> everyone() {
        List<Player> all = new ArrayList<>(ctx.team(HIDERS));
        all.addAll(ctx.team(SEEKERS));
        return all;
    }

    private void unhide(Player p) {
        Disguise d = hidden.remove(p.getUniqueId());
        if (d != null) {
            d.remove(everyone());
        }
        for (Player o : Bukkit.getOnlinePlayers()) {
            o.showPlayer(plugin, p);
            p.showPlayer(plugin, o);
        }
        p.setFlying(false);
        p.setAllowFlight(false);
        p.setGameMode(GameMode.ADVENTURE);
    }

    @Override
    public void playerLeft(Player player, int team) {
        waiting.remove(player.getUniqueId());
        unhide(player);
        // Whoever left still sees the other hiders' fake blocks until their chunks reload; clear those.
        hidden.values().forEach(d -> d.forget(player));
        if (team == HIDERS && hidden.isEmpty() && !ctx.team(SEEKERS).isEmpty()) {
            ctx.finish(MatchOutcome.win(SEEKERS, resultLines()));
        }
    }

    @Override
    public void end() {
        active.remove(this);
        for (Player p : everyone()) {
            p.setFlying(false);
            p.setAllowFlight(false);
            p.setGameMode(GameMode.ADVENTURE);
        }
        List<Player> all = everyone();
        for (Disguise d : hidden.values()) {
            d.remove(all);
            for (Player o : Bukkit.getOnlinePlayers()) {
                o.showPlayer(plugin, d.hider);
            }
        }
        for (Player p : everyone()) {
            if (hidden.containsKey(p.getUniqueId())) {
                ctx.addStat(p, "hidden", 1);
            }
        }
        waiting.clear();
        restore();
    }

    @Override
    public List<Component> resultLines() {
        List<String> left = hidden.keySet().stream().map(names::get).toList();
        if (left.isEmpty()) {
            return List.of(messages.get("result.all-found"));
        }
        return List.of(messages.get("result.hidden", Placeholder.unparsed("players", String.join(", ", left))));
    }

    @Override
    public Component personalLine(UUID player) {
        if (hidden.containsKey(player)) {
            return messages.get("personal.hidden");
        }
        int n = foundBy.getOrDefault(player, 0);
        String by = finder.get(player);
        if (by != null) {
            long secs = Math.max(0, foundAt.getOrDefault(player, 0L)) / 20;
            return messages.get("personal.found", Placeholder.unparsed("seeker", by),
                    Placeholder.unparsed("time", String.format("%d:%02d", secs / 60, secs % 60)),
                    Placeholder.unparsed("count", String.valueOf(n)));
        }
        return messages.get("personal.seeker", Placeholder.unparsed("count", String.valueOf(n)));
    }
}
