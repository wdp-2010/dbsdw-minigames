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
import org.bukkit.Axis;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * One Verstoppertje match. Team 0 hides, team 1 seeks; a hider who is hit joins the seekers. The
 * seekers win when nobody is left hiding, the hiders when one of them is still hidden at the end.
 * Players get the arena's own blocks in their inventory (E shows them all) and may place and break a
 * limited number of them; the plugin does the placing and breaking (players are in adventure mode),
 * and the arena is put back when the match ends. A hider is the block they hold.
 */
public final class VerstopMatch implements MatchHandler {
    public static final int HIDERS = 0;
    public static final int SEEKERS = 1;
    /** How far a player reaches to break a block (adventure's block reach). */
    private static final double BREAK_REACH = 4.5;

    private final Plugin plugin;
    private final MatchContext ctx;
    private final Settings settings;
    /** The arena's own blocks, most common first: what players get and may become. */
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
            p.setGameMode(GameMode.ADVENTURE);
            // Players may stand on fake blocks the server does not know about; without this the
            // server would kick them for flying. Flight itself is stopped in the listener.
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
            fill(h, 0);
            // No body in third person: the hider sees only their block, like everyone else.
            h.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false, false));
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
        hideHiders();
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
            hideHiders();
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
        boolean moved = d.still.update(Stillness.key(feet.getX(), feet.getY(), feet.getZ()), now)
                | d.still.look(l.getYaw(), l.getPitch(), now);
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

    /**
     * Hiders are hidden from every player on the server (body, nametag, tab list), not only from the
     * match: players in the lobby or watching would see them otherwise. Again every 2 s for newcomers.
     */
    private void hideHiders() {
        for (Disguise d : hidden.values()) {
            for (Player o : Bukkit.getOnlinePlayers()) {
                if (!o.equals(d.hider) && o.canSee(d.hider)) {
                    o.hidePlayer(plugin, d.hider);
                }
            }
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
    public boolean swing(Player seeker) {
        if (!isSeeker(seeker) || !released) {
            return false;
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
            return true;
        }
        return false;
    }

    /** A swing that found no hider breaks the block looked at, unless a hider's block is in front of it. */
    public void swingDig(Player p) {
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        RayTraceResult r = eye.getWorld().rayTraceBlocks(eye, dir, BREAK_REACH, FluidCollisionMode.NEVER, true);
        if (r == null || r.getHitBlock() == null) {
            return;
        }
        double dist = r.getHitPosition().distance(eye.toVector());
        for (Disguise d : hidden.values()) {
            double t = d.hider.equals(p) ? -1 : d.hit(eye, dir);
            if (t >= 0 && t < dist) {
                return;
            }
        }
        dig(p, r.getHitBlock());
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
        h.removePotionEffect(PotionEffectType.INVISIBILITY);
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
        if (m == d.data().getMaterial() || !disguises.contains(m)) {
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

    /**
     * Puts the arena's blocks in the hotbar and the storage slots (from slot {@code from} on), so E
     * shows every block the player may use. The stacks never run out: the plugin places the blocks.
     */
    private void fill(Player p, int from) {
        p.getInventory().clear();
        for (int i = 0; i < disguises.size() && from + i < 36; i++) {
            p.getInventory().setItem(from + i, ItemStack.of(disguises.get(i)));
        }
    }

    // ------------------------------------------------------------------ building

    /** A player right-clicks a block face holding an item: places that block against it. */
    public void place(Player p, Block clicked, BlockFace face, ItemStack item) {
        Material m = item == null ? null : item.getType();
        if (m == null || !disguises.contains(m)) {
            return;
        }
        Block target = clicked.isReplaceable() ? clicked : clicked.getRelative(face);
        if (!target.isReplaceable() || !canBuild(p, target)
                || !target.getWorld().getNearbyEntities(BoundingBox.of(target).expand(-0.01), e -> e instanceof LivingEntity).isEmpty()) {
            return;
        }
        int used = placed.getOrDefault(p.getUniqueId(), 0);
        if (used >= settings.placeLimit()) {
            p.sendActionBar(messages.get("build.place-full"));
            return;
        }
        remember(target, true);
        BlockData data = m.createBlockData();
        if (data instanceof Orientable o && o.getAxes().contains(axis(face))) {
            o.setAxis(axis(face));
        } else if (data instanceof Directional d && d.getFaces().contains(face)) {
            d.setFacing(face);
        }
        target.setBlockData(data, false);
        target.getWorld().playSound(target.getLocation().add(0.5, 0.5, 0.5), data.getSoundGroup().getPlaceSound(), 1f, 0.8f);
        placed.put(p.getUniqueId(), used + 1);
        p.sendActionBar(messages.get("build.placed", Placeholder.unparsed("left",
                String.valueOf(BlockRules.left(settings.placeLimit(), used + 1)))));
    }

    /** A player hits a block: breaks it at once, unless they hold the seeker's sword. */
    private void dig(Player p, Block block) {
        Material m = block.getType();
        if (p.getInventory().getItemInMainHand().getType() == Material.WOODEN_SWORD || block.isEmpty()
                || block.isLiquid() || m.getHardness() < 0 || !canBuild(p, block)) {
            return;
        }
        int used = broken.getOrDefault(p.getUniqueId(), 0);
        if (used >= settings.breakLimit()) {
            p.sendActionBar(messages.get("build.break-full"));
            return;
        }
        remember(block, true);
        BlockData was = block.getBlockData();
        block.setType(Material.AIR, false);
        block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5), was.getSoundGroup().getBreakSound(), 1f, 0.8f);
        block.getWorld().spawnParticle(Particle.BLOCK, block.getLocation().add(0.5, 0.5, 0.5), 20, 0.25, 0.25, 0.25, 0, was);
        broken.put(p.getUniqueId(), used + 1);
        p.sendActionBar(messages.get("build.broken", Placeholder.unparsed("left",
                String.valueOf(BlockRules.left(settings.breakLimit(), used + 1)))));
    }

    private static Axis axis(BlockFace face) {
        return switch (face) {
            case EAST, WEST -> Axis.X;
            case NORTH, SOUTH -> Axis.Z;
            default -> Axis.Y;
        };
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
        // Slot 0 is the sword (hitting with it never breaks blocks), the rest the arena's blocks.
        fill(s, 1);
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
        p.removePotionEffect(PotionEffectType.INVISIBILITY);
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
            p.removePotionEffect(PotionEffectType.INVISIBILITY);
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
