package be.dbsdw.minigames.paintball.tdm;

import be.dbsdw.minigames.core.Messages;
import be.dbsdw.minigames.core.api.MatchContext;
import be.dbsdw.minigames.core.api.MatchHandler;
import be.dbsdw.minigames.core.api.MatchOutcome;
import be.dbsdw.minigames.paintball.Gun;
import be.dbsdw.minigames.paintball.PaintballGame;
import be.dbsdw.minigames.paintball.rules.Clock;
import be.dbsdw.minigames.paintball.rules.Combat;
import be.dbsdw.minigames.paintball.rules.FireGate;
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
import org.bukkit.util.Vector;

/** One paintball team deathmatch: shooting, hits, outs, respawns, bounds, time limit, HUD. */
public final class TdmMatch implements MatchHandler {
    private static final NamespacedKey FROZEN = new NamespacedKey("paintball", "frozen");
    private static final long MARKER_TICKS = 20;

    private record Shot(UUID shooter, int team, long spawnTick) {
    }

    private final MatchContext ctx;
    private final PaintballGame game;
    private final Settings settings;
    private final Messages messages;
    private final TeamScore score;
    private final Hud hud;
    private final Map<UUID, Integer> teams = new LinkedHashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private final Map<UUID, FireGate> gates = new HashMap<>();
    private final Map<UUID, Shot> balls = new HashMap<>();
    private final Map<UUID, Long> markerUntil = new HashMap<>();
    private final Map<UUID, Component> marker = new HashMap<>();
    private Combat combat;
    private boolean over;

    public TdmMatch(MatchContext ctx, PaintballGame game) {
        this.ctx = ctx;
        this.game = game;
        this.settings = game.settings();
        this.messages = game.messages();
        this.score = new TeamScore(2, settings.winEliminations());
        this.hud = new Hud(ctx, messages, settings);
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
                hud.show(p);
            }
        }
        combat = new Combat(teams, settings);
        combat.start(0);
        hud.refresh(0, score);
        online().forEach(p -> updateBar(p, 0));
    }

    @Override
    public void tick() {
        long now = ctx.ticks();
        if (now >= settings.timeLimitTicks()) {
            OptionalInt leader = score.leader();
            finish(leader.isPresent()
                    ? MatchOutcome.win(leader.getAsInt(), resultLines())
                    : MatchOutcome.draw(resultLines()));
            return;
        }
        for (Player p : online()) {
            FireGate gate = gates.get(p.getUniqueId());
            if (gate != null && !combat.isOut(p.getUniqueId()) && gate.pollQueued(now, settings.shotIntervalTicks())) {
                fire(p, now);
            }
        }
        expireBalls(now);
        checkBounds(now);
        for (UUID id : combat.respawnsDue(now)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                respawn(p, now);
            }
        }
        boolean wholeSecond = now % 20 == 0;
        if (wholeSecond) {
            hud.refresh(now, score);
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
        combat.remove(id);
        gates.remove(id);
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
    }

    /** The player pulled the trigger. */
    public void trigger(Player p) {
        UUID id = p.getUniqueId();
        FireGate gate = gates.get(id);
        if (over || gate == null || combat.isOut(id)) {
            return;
        }
        long now = ctx.ticks();
        if (gate.request(now, settings.shotIntervalTicks()) == FireGate.Decision.FIRE) {
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
        if (!(event.getHitEntity() instanceof Player victim)) {
            untrack(ballId);
            return;
        }
        long now = ctx.ticks();
        Combat.HitResult result = combat.hit(victim.getUniqueId(), shot.shooter(), shot.team(), now);
        if (result == Combat.HitResult.PASS_THROUGH) {
            event.setCancelled(true);
            return;
        }
        untrack(ballId);
        Player shooter = Bukkit.getPlayer(shot.shooter());
        switch (result) {
            case ABSORBED -> victim.getWorld().playSound(victim.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.6f, 1.6f);
            case HIT -> {
                victim.playHurtAnimation(0);
                victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_SLIME_SQUISH, 1f, 1.2f);
                paintBurst(victim.getLocation().add(0, 1, 0), shot.team(), 20, 1.4f);
                updateBar(victim, now);
                if (shooter != null) {
                    shooter.playSound(shooter.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 0.8f, 1.4f);
                    showMarker(shooter, messages.get("hud.marker"), now);
                }
            }
            case OUT -> {
                victim.playHurtAnimation(0);
                if (shooter != null) {
                    shooter.playSound(shooter.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.8f);
                    showMarker(shooter, messages.get("hud.eliminated",
                            Placeholder.component("victim", Component.text(victim.getName()))), now);
                }
                eliminate(victim, shot.team(), now);
                feed(messages.get("feed.out",
                        Placeholder.component("victim", coloured(victim.getName(), teams.getOrDefault(victim.getUniqueId(), 0))),
                        Placeholder.component("shooter", coloured(names.getOrDefault(shot.shooter(), "?"), shot.team()))));
                OptionalInt winner = score.add(shot.team());
                hud.refresh(now, score);
                if (winner.isPresent()) {
                    finish(MatchOutcome.win(winner.getAsInt(), resultLines()));
                }
            }
            default -> {
            }
        }
    }

    private void fire(Player p, long now) {
        UUID id = p.getUniqueId();
        int team = teams.get(id);
        boolean wasProtected = combat.isProtected(id, now);
        combat.shot(id);
        Vector aim = p.getLocation().getDirection();
        double[] d = Spread.apply(aim.getX(), aim.getY(), aim.getZ(), settings.spreadDegrees(), ThreadLocalRandom.current());
        Snowball ball = p.launchProjectile(Snowball.class,
                new Vector(d[0], d[1], d[2]).multiply(settings.speed()));
        ball.setItem(Gun.ball(team));
        Gun.tagBall(ball);
        balls.put(ball.getUniqueId(), new Shot(id, team, now));
        game.balls().put(ball.getUniqueId(), this);
        ItemStack held = p.getInventory().getItemInMainHand();
        if (Gun.isGun(held)) {
            p.setCooldown(held, settings.shotIntervalTicks());
        }
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 0.7f, 1.7f);
        if (wasProtected) {
            updateBar(p, now);
        }
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

    private void checkBounds(long now) {
        for (Player p : online()) {
            UUID id = p.getUniqueId();
            if (combat.isOut(id)) {
                continue;
            }
            Location loc = p.getLocation();
            if (loc.getWorld() != ctx.arena().world() || !ctx.arena().bounds().contains(loc.toVector())) {
                combat.outOfBounds(id, now);
                int team = teams.get(id);
                eliminate(p, team, now);
                p.teleport(randomSpawn(team));
                feed(messages.get("feed.out-bounds", Placeholder.component("victim", coloured(p.getName(), team))));
            }
        }
    }

    /** Out: splash, hidden from everyone, frozen, gun disabled until the respawn. */
    private void eliminate(Player victim, int paintTeam, long now) {
        paintBurst(victim.getLocation().add(0, 1, 0), paintTeam, 80, 2.2f);
        victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_GENERIC_SPLASH, 1f, 1f);
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other != victim) {
                other.hideEntity(game.plugin(), victim);
            }
        }
        freeze(victim, true);
        FireGate gate = gates.get(victim.getUniqueId());
        if (gate != null) {
            gate.clear();
        }
        hud.outTitle(victim, Clock.secondsLeft(combat.respawnAt(victim.getUniqueId()) - now));
    }

    private void respawn(Player p, long now) {
        p.teleport(randomSpawn(teams.get(p.getUniqueId())));
        p.setFallDistance(0);
        for (Player other : Bukkit.getOnlinePlayers()) {
            other.showEntity(game.plugin(), p);
        }
        freeze(p, false);
        p.clearTitle();
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.5f);
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

    private void updateBar(Player p, long now) {
        UUID id = p.getUniqueId();
        if (combat.isOut(id)) {
            return;
        }
        Component prefix = now < markerUntil.getOrDefault(id, Long.MIN_VALUE) ? marker.get(id) : Component.empty();
        hud.actionBar(p, teams.get(id), combat.hitsLeft(id), combat.isProtected(id, now), prefix);
    }

    private void showMarker(Player shooter, Component text, long now) {
        markerUntil.put(shooter.getUniqueId(), now + MARKER_TICKS);
        marker.put(shooter.getUniqueId(), text);
        updateBar(shooter, now);
    }

    private void finish(MatchOutcome outcome) {
        if (!over) {
            ctx.finish(outcome);
        }
    }

    private List<Component> resultLines() {
        List<Component> lines = new ArrayList<>();
        lines.add(messages.get("result.score",
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

    private Component coloured(String name, int team) {
        return Component.text(name, team == 0
                ? NamedTextColor.RED
                : NamedTextColor.BLUE);
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
