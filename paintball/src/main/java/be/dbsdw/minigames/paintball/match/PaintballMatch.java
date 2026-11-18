package be.dbsdw.minigames.paintball.match;

import be.dbsdw.minigames.core.Messages;
import be.dbsdw.minigames.core.api.MatchContext;
import be.dbsdw.minigames.core.api.MatchHandler;
import be.dbsdw.minigames.core.api.MatchOutcome;
import be.dbsdw.minigames.paintball.Gun;
import be.dbsdw.minigames.paintball.PaintballGame;
import be.dbsdw.minigames.paintball.rules.Buffs;
import be.dbsdw.minigames.paintball.rules.Clock;
import be.dbsdw.minigames.paintball.rules.Combat;
import be.dbsdw.minigames.paintball.rules.FireGate;
import be.dbsdw.minigames.paintball.rules.Flags;
import be.dbsdw.minigames.paintball.rules.ModeRules;
import be.dbsdw.minigames.paintball.rules.PowerupSettings;
import be.dbsdw.minigames.paintball.rules.PowerupSpawns;
import be.dbsdw.minigames.paintball.rules.PowerupType;
import be.dbsdw.minigames.paintball.rules.Settings;
import be.dbsdw.minigames.paintball.rules.Spread;
import be.dbsdw.minigames.paintball.rules.TeamScore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * One paintball match, team deathmatch or capture the flag: shooting, hits, outs, respawns, bounds,
 * flags, power-ups, splats, HUD and stats. The core calls it on the main thread every tick.
 */
public final class PaintballMatch implements MatchHandler {
    public enum Mode { TDM, CTF }

    private static final NamespacedKey FROZEN = new NamespacedKey("paintball", "frozen");
    private static final long MARKER_TICKS = 20;
    private static final double TOUCH_RADIUS = 1.5;
    private static final double POWERUP_RADIUS = 1.3;

    private record Shot(UUID shooter, int team, long spawnTick) {
    }

    private final MatchContext ctx;
    private final PaintballGame game;
    private final Mode mode;
    private final Settings settings;
    private final ModeRules rules;
    private final Messages messages;
    private final TeamScore score;
    private final Hud hud;
    private final Map<UUID, Integer> teams = new LinkedHashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private final Map<UUID, FireGate> gates = new HashMap<>();
    private final Map<UUID, Shot> balls = new HashMap<>();
    private final Map<UUID, Long> markerUntil = new HashMap<>();
    private final Map<UUID, Component> marker = new HashMap<>();
    private final Buffs buffs = new Buffs();
    private final Splats splats;
    private Combat combat;
    private Flags flags;
    private FlagView flagView;
    private Location[] flagHomes;
    private PowerupSpawns powerupSpawns;
    private PowerupView powerups;
    private boolean over;

    public PaintballMatch(MatchContext ctx, PaintballGame game, Mode mode) {
        this.ctx = ctx;
        this.game = game;
        this.mode = mode;
        this.settings = game.settings();
        this.rules = mode == Mode.CTF ? settings.ctf() : settings.tdm();
        this.messages = game.messages();
        this.score = new TeamScore(2, rules.winScore());
        this.hud = new Hud(ctx, messages, settings, rules, mode == Mode.CTF);
        this.splats = new Splats(settings.splatCap(), settings.splatLifetimeTicks());
    }

    @Override
    public void start() {
        for (int team = 0; team < 2; team++) {
            for (Player p : ctx.team(team)) {
                teams.put(p.getUniqueId(), team);
                names.put(p.getUniqueId(), p.getName());
                gates.put(p.getUniqueId(), new FireGate());
                game.players().put(p.getUniqueId(), this);
                p.getInventory().setItem(0, Gun.gun(messages));
                p.getInventory().setHeldItemSlot(0);
                hud.show(p, team);
            }
        }
        combat = new Combat(teams, settings);
        combat.start(0);
        if (mode == Mode.CTF) {
            flags = new Flags(2, settings.flagReturnTicks());
            flagHomes = new Location[] {
                ctx.arena().flag(0).orElseThrow(() -> new IllegalStateException("arena has no flag for team 0")),
                ctx.arena().flag(1).orElseThrow(() -> new IllegalStateException("arena has no flag for team 1")),
            };
            flagView = new FlagView(flagHomes);
            flagView.sync(flags);
        }
        PowerupSettings ps = settings.powerups();
        List<Location> points = ctx.arena().powerups();
        if (!ps.enabled().isEmpty() && !points.isEmpty()) {
            powerupSpawns = new PowerupSpawns(points.size(), ps.respawnTicks());
            powerups = new PowerupView(points);
        }
        hud.refresh(0, score, flagStatus());
        online().forEach(p -> updateBar(p, 0));
    }

    @Override
    public void tick() {
        long now = ctx.ticks();
        if (now >= rules.timeLimitTicks()) {
            OptionalInt leader = score.leader();
            finish(leader.isPresent()
                    ? MatchOutcome.win(leader.getAsInt(), resultLines())
                    : MatchOutcome.draw(resultLines()));
            return;
        }
        for (Player p : online()) {
            FireGate gate = gates.get(p.getUniqueId());
            if (gate != null && !combat.isOut(p.getUniqueId()) && gate.pollQueued(now, interval(p, now))) {
                fire(p, now);
            }
        }
        expireBalls(now);
        checkBounds(now);
        if (over) {
            return;
        }
        for (UUID id : combat.respawnsDue(now)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                respawn(p, now);
            }
        }
        if (mode == Mode.CTF) {
            tickFlags(now);
            if (over) {
                return;
            }
        }
        tickPowerups(now);
        splats.tick(now);
        if (now % 20 == 0) {
            hud.refresh(now, score, flagStatus());
        }
        for (Player p : online()) {
            UUID id = p.getUniqueId();
            if (combat.isOut(id)) {
                long left = combat.respawnAt(id) - now;
                if (left % 20 == 0) {
                    hud.outTitle(p, Clock.secondsLeft(left));
                }
            } else if (now % 10 == 0) {
                updateBar(p, now);
            }
        }
    }

    @Override
    public void playerLeft(Player player, int team) {
        UUID id = player.getUniqueId();
        if (flags != null) {
            dropFlag(player, ctx.ticks());
        }
        combat.remove(id);
        gates.remove(id);
        buffs.clear(id);
        cleanup(player);
        game.players().remove(id);
    }

    @Override
    public void end() {
        over = true;
        for (UUID id : teams.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && game.players().get(id) == this) {
                cleanup(p);
            }
            game.players().remove(id, this);
        }
        for (UUID ballId : balls.keySet()) {
            Entity ball = Bukkit.getEntity(ballId);
            if (ball != null) {
                ball.remove();
            }
            game.balls().remove(ballId, this);
        }
        balls.clear();
        if (flagView != null) {
            flagView.removeAll();
        }
        if (powerups != null) {
            powerups.removeAll();
        }
        splats.removeAll();
    }

    /** The player pulled the trigger. */
    public void trigger(Player p) {
        UUID id = p.getUniqueId();
        FireGate gate = gates.get(id);
        if (over || gate == null || combat.isOut(id)) {
            return;
        }
        long now = ctx.ticks();
        if (gate.request(now, interval(p, now)) == FireGate.Decision.FIRE) {
            fire(p, now);
        }
    }

    /** A paintball of this match hit something. */
    public void onBallHit(ProjectileHitEvent event) {
        UUID ballId = event.getEntity().getUniqueId();
        Shot shot = balls.get(ballId);
        if (over || shot == null) {
            return;
        }
        long now = ctx.ticks();
        if (!(event.getHitEntity() instanceof Player victim)) {
            untrack(ballId);
            if (event.getHitBlock() != null) {
                splats.add(event.getHitBlock(), event.getHitBlockFace(), shot.team(), now);
            }
            return;
        }
        Combat.HitResult result = combat.hit(victim.getUniqueId(), shot.shooter(), shot.team(), now);
        if (result == Combat.HitResult.PASS_THROUGH) {
            event.setCancelled(true);
            return;
        }
        untrack(ballId);
        Player shooter = Bukkit.getPlayer(shot.shooter());
        Location at = victim.getLocation();
        switch (result) {
            case ABSORBED -> victim.getWorld().playSound(at, Sound.ITEM_SHIELD_BLOCK, 0.6f, 1.6f);
            case SHIELDED -> {
                victim.getWorld().playSound(at, Sound.ITEM_SHIELD_BREAK, 0.9f, 1.2f);
                updateBar(victim, now);
                if (shooter != null) {
                    showMarker(shooter, messages.get("hud.marker-shield"), now);
                }
            }
            case HIT -> {
                victim.playHurtAnimation(0);
                victim.getWorld().playSound(at, Sound.ENTITY_SLIME_SQUISH, 1f, 1.2f);
                paintBurst(at.clone().add(0, 1, 0), shot.team(), 20, 1.4f);
                updateBar(victim, now);
                if (shooter != null) {
                    ctx.addStat(shooter, "hits", 1);
                    shooter.playSound(shooter.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 0.8f, 1.4f);
                    showMarker(shooter, messages.get("hud.marker"), now);
                }
            }
            case OUT -> {
                victim.playHurtAnimation(0);
                if (shooter != null) {
                    ctx.addStat(shooter, "hits", 1);
                    ctx.addStat(shooter, "eliminations", 1);
                    shooter.playSound(shooter.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.8f);
                    showMarker(shooter, messages.get("hud.eliminated",
                            Placeholder.component("victim", Component.text(victim.getName()))), now);
                }
                eliminate(victim, shot.team(), now, false);
                feed(messages.get("feed.out",
                        Placeholder.component("victim", coloured(victim.getName(), team(victim))),
                        Placeholder.component("shooter", coloured(names.getOrDefault(shot.shooter(), "?"), shot.team()))));
                if (mode == Mode.TDM) {
                    OptionalInt winner = score.add(shot.team());
                    hud.refresh(now, score, flagStatus());
                    if (winner.isPresent()) {
                        finish(MatchOutcome.win(winner.getAsInt(), resultLines()));
                    }
                }
            }
            default -> {
            }
        }
    }

    // --- shooting ---

    private int interval(Player p, long now) {
        int base = settings.shotIntervalTicks();
        return buffs.active(p.getUniqueId(), PowerupType.RAPID_FIRE, now) ? Math.max(1, base / 2) : base;
    }

    private void fire(Player p, long now) {
        UUID id = p.getUniqueId();
        int team = team(p);
        boolean wasProtected = combat.isProtected(id, now);
        combat.shot(id);
        ctx.addStat(p, "shots", 1);
        Vector aim = p.getLocation().getDirection();
        launch(p, team, aim, now);
        if (buffs.active(id, PowerupType.TRIPLE_SHOT, now)) {
            double fan = Math.toRadians(settings.powerups().tripleFanDegrees());
            launch(p, team, aim.clone().rotateAroundY(fan), now);
            launch(p, team, aim.clone().rotateAroundY(-fan), now);
        }
        ItemStack held = p.getInventory().getItemInMainHand();
        if (Gun.isGun(held)) {
            p.setCooldown(held, interval(p, now));
        }
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 0.7f, 1.7f);
        if (wasProtected) {
            updateBar(p, now);
        }
    }

    private void launch(Player p, int team, Vector aim, long now) {
        double[] d = Spread.apply(aim.getX(), aim.getY(), aim.getZ(), settings.spreadDegrees(), ThreadLocalRandom.current());
        Snowball ball = p.launchProjectile(Snowball.class, new Vector(d[0], d[1], d[2]).multiply(settings.speed()));
        ball.setItem(Gun.ball(team));
        Gun.tagBall(ball);
        balls.put(ball.getUniqueId(), new Shot(p.getUniqueId(), team, now));
        game.balls().put(ball.getUniqueId(), this);
    }

    private void untrack(UUID ballId) {
        balls.remove(ballId);
        game.balls().remove(ballId, this);
    }

    private void expireBalls(long now) {
        Iterator<Map.Entry<UUID, Shot>> it = balls.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Shot> e = it.next();
            Entity ball = Bukkit.getEntity(e.getKey());
            if (ball == null || !ball.isValid() || now - e.getValue().spawnTick() >= settings.ballLifetimeTicks()) {
                if (ball != null) {
                    ball.remove();
                }
                it.remove();
                game.balls().remove(e.getKey(), this);
            }
        }
    }

    // --- outs and respawns ---

    private void checkBounds(long now) {
        for (Player p : online()) {
            UUID id = p.getUniqueId();
            if (combat.isOut(id)) {
                continue;
            }
            Location loc = p.getLocation();
            if (loc.getWorld() != ctx.arena().world() || !ctx.arena().bounds().contains(loc.toVector())) {
                combat.outOfBounds(id, now);
                int team = team(p);
                eliminate(p, team, now, true);
                p.teleport(randomSpawn(team));
                feed(messages.get("feed.out-bounds", Placeholder.component("victim", coloured(p.getName(), team))));
            }
        }
    }

    /** Out: splash, flag dropped, hidden from everyone, frozen, gun disabled until the respawn. */
    private void eliminate(Player victim, int paintTeam, long now, boolean outOfBounds) {
        UUID id = victim.getUniqueId();
        ctx.addStat(victim, "outs", 1);
        if (flags != null) {
            if (outOfBounds) {
                flags.carriedBy(id).ifPresent(f -> {
                    flags.sendHome(f);
                    victim.getInventory().setHelmet(null);
                    victim.setGlowing(false);
                    flagEvent("flag.returned-auto", f, null);
                    flagView.sync(flags);
                });
            } else {
                dropFlag(victim, now);
            }
        }
        paintBurst(victim.getLocation().add(0, 1, 0), paintTeam, 80, 2.2f);
        victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_GENERIC_SPLASH, 1f, 1f);
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other != victim) {
                other.hideEntity(game.plugin(), victim);
            }
        }
        freeze(victim, true);
        buffs.clear(id);
        victim.removePotionEffect(PotionEffectType.SPEED);
        FireGate gate = gates.get(id);
        if (gate != null) {
            gate.clear();
        }
        hud.outTitle(victim, Clock.secondsLeft(combat.respawnAt(id) - now));
    }

    private void respawn(Player p, long now) {
        p.teleport(randomSpawn(team(p)));
        p.setFallDistance(0);
        for (Player other : Bukkit.getOnlinePlayers()) {
            other.showEntity(game.plugin(), p);
        }
        freeze(p, false);
        p.clearTitle();
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.5f);
        updateBar(p, now);
    }

    // --- capture the flag ---

    private void tickFlags(long now) {
        for (int f : flags.autoReturns(now)) {
            flagEvent("flag.returned-auto", f, null);
        }
        for (Player p : online()) {
            UUID id = p.getUniqueId();
            if (combat.isOut(id)) {
                continue;
            }
            int team = team(p);
            int enemy = 1 - team;
            Location loc = p.getLocation();
            if (near(loc, flagHomes[team])) {
                OptionalInt captured = flags.capture(id, team);
                if (captured.isPresent()) {
                    p.getInventory().setHelmet(null);
                    p.setGlowing(false);
                    ctx.addStat(p, "captures", 1);
                    flagEvent("flag.captured", captured.getAsInt(), p);
                    OptionalInt winner = score.add(team);
                    hud.refresh(now, score, flagStatus());
                    if (winner.isPresent()) {
                        finish(MatchOutcome.win(winner.getAsInt(), resultLines()));
                        return;
                    }
                }
            }
            boolean atEnemyFlag = switch (flags.state(enemy)) {
                case HOME -> near(loc, flagHomes[enemy]);
                case DROPPED -> near(loc, flags.dropPos(enemy));
                case CARRIED -> false;
            };
            if (atEnemyFlag && flags.pickup(enemy, id, team)) {
                p.getInventory().setHelmet(FlagView.banner(enemy));
                p.setGlowing(true);
                flagEvent("flag.taken", enemy, p);
            }
            if (flags.state(team) == Flags.State.DROPPED && near(loc, flags.dropPos(team)) && flags.returnHome(team, team)) {
                ctx.addStat(p, "returns", 1);
                flagEvent("flag.returned", team, p);
            }
        }
        if (now % 2 == 0) {
            flagView.sync(flags);
        }
    }

    private void dropFlag(Player carrier, long now) {
        Location at = carrier.getLocation();
        OptionalInt dropped = flags.drop(carrier.getUniqueId(), at.getX(), at.getY(), at.getZ(), now);
        if (dropped.isPresent()) {
            carrier.getInventory().setHelmet(null);
            carrier.setGlowing(false);
            flagEvent("flag.dropped", dropped.getAsInt(), carrier);
            flagView.sync(flags);
        }
    }

    /** Chat line, title for everyone, sound, HUD refresh. */
    private void flagEvent(String key, int flag, Player who) {
        Component line = messages.get(key,
                Placeholder.component("flag", ctx.teamName(flag)),
                Placeholder.component("player", who == null ? Component.empty() : coloured(who.getName(), team(who))));
        Sound sound = switch (key) {
            case "flag.captured" -> Sound.UI_TOAST_CHALLENGE_COMPLETE;
            case "flag.taken" -> Sound.BLOCK_NOTE_BLOCK_BELL;
            case "flag.dropped" -> Sound.BLOCK_NOTE_BLOCK_BASS;
            default -> Sound.BLOCK_NOTE_BLOCK_CHIME;
        };
        for (Player p : online()) {
            p.sendMessage(line);
            p.playSound(p.getLocation(), sound, 0.8f, 1f);
            if (key.equals("flag.captured") || key.equals("flag.taken")) {
                hud.bigTitle(p, Component.empty(), line);
            }
        }
        hud.refresh(ctx.ticks(), score, flagStatus());
    }

    private Component flagStatus() {
        if (flags == null) {
            return null;
        }
        Component status = Component.empty();
        for (int f = 0; f < 2; f++) {
            Component state = switch (flags.state(f)) {
                case HOME -> messages.get("flag.state-home");
                case CARRIED -> messages.get("flag.state-carried", Placeholder.component("player",
                        Component.text(names.getOrDefault(flags.carrier(f).orElseThrow(), "?"))));
                case DROPPED -> messages.get("flag.state-dropped", Placeholder.unparsed("seconds",
                        Long.toString(Clock.secondsLeft(settings.flagReturnTicks() - (ctx.ticks() - flags.droppedAt(f))))));
            };
            if (f > 0) {
                status = status.append(Component.text("  |  ", NamedTextColor.DARK_GRAY));
            }
            status = status.append(messages.get("flag.status", Placeholder.component("team", ctx.teamName(f)),
                    Placeholder.component("state", state)));
        }
        return status;
    }

    // --- power-ups ---

    private void tickPowerups(long now) {
        if (powerups == null) {
            return;
        }
        List<PowerupType> enabled = settings.powerups().enabled();
        for (int i : powerupSpawns.due(now)) {
            powerups.spawn(i, enabled.get(ThreadLocalRandom.current().nextInt(enabled.size())));
        }
        if (now % 2 == 0) {
            powerups.animate();
        }
        for (Player p : online()) {
            if (combat.isOut(p.getUniqueId())) {
                continue;
            }
            for (int i = 0; i < powerups.size(); i++) {
                if (powerupSpawns.present(i) && near(p.getLocation(), powerups.point(i), POWERUP_RADIUS)) {
                    PowerupType type = powerups.type(i);
                    powerups.remove(i);
                    powerupSpawns.taken(i, now);
                    apply(p, type, now);
                }
            }
        }
    }

    private void apply(Player p, PowerupType type, long now) {
        UUID id = p.getUniqueId();
        long duration = settings.powerups().duration(type);
        switch (type) {
            case RAPID_FIRE, TRIPLE_SHOT -> buffs.give(id, type, now, duration);
            case SPEED -> {
                buffs.give(id, type, now, duration);
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, (int) duration, 1, false, false, true));
            }
            case SHIELD -> combat.giveShield(id);
            case REPAIR -> combat.repair(id);
        }
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.3f);
        showMarker(p, messages.get("powerup.picked", Placeholder.component("powerup", messages.get("powerup." + type.key()))), now);
    }

    private Component buffsText(UUID id, long now) {
        Component text = Component.empty();
        for (PowerupType type : List.of(PowerupType.RAPID_FIRE, PowerupType.TRIPLE_SHOT, PowerupType.SPEED)) {
            long left = buffs.left(id, type, now);
            if (left > 0) {
                text = text.append(messages.get("hud.buff",
                        Placeholder.component("powerup", messages.get("powerup." + type.key())),
                        Placeholder.unparsed("seconds", Long.toString(Clock.secondsLeft(left)))));
            }
        }
        return text;
    }

    // --- HUD, results, helpers ---

    private void updateBar(Player p, long now) {
        UUID id = p.getUniqueId();
        if (combat.isOut(id) || !teams.containsKey(id)) {
            return;
        }
        Component prefix = now < markerUntil.getOrDefault(id, Long.MIN_VALUE) ? marker.get(id) : Component.empty();
        hud.actionBar(p, team(p), combat.hitsLeft(id), combat.isProtected(id, now), combat.hasShield(id),
                buffsText(id, now), prefix);
    }

    private void showMarker(Player p, Component text, long now) {
        markerUntil.put(p.getUniqueId(), now + MARKER_TICKS);
        marker.put(p.getUniqueId(), text);
        updateBar(p, now);
    }

    /** Undoes everything this match did to the player; safe to call twice. */
    private void cleanup(Player p) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            other.showEntity(game.plugin(), p);
        }
        freeze(p, false);
        hud.hide(p);
        p.clearTitle();
        p.setGlowing(false);
        p.removePotionEffect(PotionEffectType.SPEED);
        p.getInventory().setHelmet(null);
        for (int slot = 0; slot < p.getInventory().getSize(); slot++) {
            if (Gun.isGun(p.getInventory().getItem(slot))) {
                p.getInventory().setItem(slot, null);
            }
        }
    }

    private void freeze(Player p, boolean frozen) {
        for (Attribute attribute : new Attribute[] {Attribute.MOVEMENT_SPEED, Attribute.JUMP_STRENGTH}) {
            AttributeInstance instance = p.getAttribute(attribute);
            if (instance == null) {
                continue;
            }
            instance.removeModifier(FROZEN);
            if (frozen) {
                instance.addTransientModifier(
                        new AttributeModifier(FROZEN, -1, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
            }
        }
    }

    private void finish(MatchOutcome outcome) {
        if (!over) {
            ctx.finish(outcome);
        }
    }

    private List<Component> resultLines() {
        List<Component> lines = new ArrayList<>();
        lines.add(messages.get(mode == Mode.CTF ? "result.score-ctf" : "result.score",
                Placeholder.component("red_team", ctx.teamName(0)),
                Placeholder.unparsed("red", Integer.toString(score.points(0))),
                Placeholder.component("blue_team", ctx.teamName(1)),
                Placeholder.unparsed("blue", Integer.toString(score.points(1)))));
        lines.add(messages.get("result.top-header"));
        int rank = 1;
        for (UUID id : combat.top(3)) {
            lines.add(messages.get("result.top",
                    Placeholder.unparsed("rank", Integer.toString(rank++)),
                    Placeholder.unparsed("name", names.getOrDefault(id, "?")),
                    Placeholder.unparsed("count", Integer.toString(combat.eliminations(id)))));
        }
        return lines;
    }

    private void feed(Component line) {
        online().forEach(p -> p.sendMessage(line));
    }

    private int team(Player p) {
        return teams.getOrDefault(p.getUniqueId(), 0);
    }

    private static Component coloured(String name, int team) {
        return Component.text(name, team == 0 ? NamedTextColor.RED : NamedTextColor.BLUE);
    }

    private static boolean near(Location loc, Location target) {
        return near(loc, target, TOUCH_RADIUS);
    }

    private static boolean near(Location loc, double[] p) {
        return p != null && near(loc, new Location(loc.getWorld(), p[0], p[1], p[2]), TOUCH_RADIUS);
    }

    private static boolean near(Location loc, Location target, double radius) {
        double dx = loc.getX() - target.getX();
        double dz = loc.getZ() - target.getZ();
        double dy = loc.getY() - target.getY();
        return loc.getWorld() == target.getWorld() && dx * dx + dz * dz <= radius * radius && Math.abs(dy) <= 2;
    }

    private void paintBurst(Location at, int team, int count, float size) {
        Color colour = team == 0 ? Color.fromRGB(0xFF3B3B) : Color.fromRGB(0x3B7BFF);
        at.getWorld().spawnParticle(Particle.DUST, at, count, 0.35, 0.5, 0.35, 0,
                new Particle.DustOptions(colour, size));
    }

    private Location randomSpawn(int team) {
        List<Location> spawns = ctx.arena().spawns(team);
        return spawns.get(ThreadLocalRandom.current().nextInt(spawns.size()));
    }

    /** Online players still in this match. */
    private List<Player> online() {
        List<Player> players = new ArrayList<>(ctx.team(0));
        players.addAll(ctx.team(1));
        return players;
    }
}
