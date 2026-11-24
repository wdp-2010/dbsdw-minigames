package be.dbsdw.minigames.paintball.match;

import be.dbsdw.minigames.core.Messages;
import be.dbsdw.minigames.core.api.MatchContext;
import be.dbsdw.minigames.core.api.MatchHandler;
import be.dbsdw.minigames.core.api.MatchOutcome;
import be.dbsdw.minigames.core.pack.PackItems;
import be.dbsdw.minigames.paintball.Gun;
import be.dbsdw.minigames.paintball.PackAssets;
import be.dbsdw.minigames.paintball.PaintballGame;
import be.dbsdw.minigames.paintball.bot.BotNav;
import be.dbsdw.minigames.paintball.rules.Buffs;
import be.dbsdw.minigames.paintball.rules.Clock;
import be.dbsdw.minigames.paintball.rules.Combat;
import be.dbsdw.minigames.paintball.rules.FireGate;
import be.dbsdw.minigames.paintball.rules.Flags;
import be.dbsdw.minigames.paintball.rules.ModeRules;
import be.dbsdw.minigames.paintball.rules.PowerupDirector;
import be.dbsdw.minigames.paintball.rules.SpawnPicker;
import be.dbsdw.minigames.paintball.rules.Streaks;
import be.dbsdw.minigames.paintball.rules.PowerupSettings;
import be.dbsdw.minigames.paintball.rules.WalkableArea;
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
import java.util.Optional;
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
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
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
    /** Set in {@link #start()}, once the teams (bots included) are known. */
    private TeamScore score;
    private final Hud hud;
    private final Map<UUID, Integer> teams = new LinkedHashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private final Map<UUID, FireGate> gates = new HashMap<>();
    private final Map<UUID, Shot> balls = new HashMap<>();
    private final Buffs buffs = new Buffs();
    private final Splats splats;
    private Combat combat;
    private Flags flags;
    private FlagView flagView;
    private Location[] flagHomes;
    private PowerupSpawns powerupSpawns;
    private PowerupView powerups;
    private PowerupDirector powerupDirector;
    private final Streaks streaks = new Streaks();
    private final Map<UUID, Integer> matchShots = new HashMap<>();
    private final Map<UUID, Integer> matchHits = new HashMap<>();
    private final Map<UUID, Long> lastDry = new HashMap<>();
    private final Scope scope;
    private final Goggles goggles = new Goggles();
    /** Vanilla snowball gravity per tick. */
    private static final double BALL_GRAVITY = 0.03;
    private boolean over;
    /** Bots by entity id, when a player started alone (see {@link PaintballGame#startAlone}). */
    private final Map<UUID, PaintBot> bots = new LinkedHashMap<>();
    /** Last tick's position of everyone, to tell bots how fast their targets move. */
    private final Map<UUID, double[]> lastPos = new HashMap<>();
    private static final List<String> BOT_NAMES = List.of("Kwast", "Spetter", "Klodder", "Vlek", "Spat",
            "Druppel", "Verfje", "Pip", "Plons", "Sproet", "Kliek", "Smeer");

    public PaintballMatch(MatchContext ctx, PaintballGame game, Mode mode) {
        this.ctx = ctx;
        this.game = game;
        this.mode = mode;
        this.settings = game.settings();
        this.rules = mode == Mode.CTF ? settings.ctf() : settings.tdm();
        this.messages = game.messages();
        this.hud = new Hud(ctx, messages, settings, rules, mode == Mode.CTF);
        this.splats = new Splats(settings.splatCap(), settings.splatLifetimeTicks());
        this.scope = new Scope(settings, messages);
    }

    @Override
    public void start() {
        for (int team = 0; team < 2; team++) {
            for (Player p : ctx.team(team)) {
                teams.put(p.getUniqueId(), team);
                names.put(p.getUniqueId(), p.getName());
                gates.put(p.getUniqueId(), new FireGate());
                game.players().put(p.getUniqueId(), this);
                p.getInventory().setItem(0, Gun.gun(messages, team));
                p.getInventory().setHeldItemSlot(0);
                hud.show(p, team);
            }
        }
        spawnBots();
        int[] size = new int[2];
        teams.values().forEach(t -> size[t]++);
        score = new TeamScore(2, rules.winScoreFor(Math.max(size[0], size[1])));
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
        if (!ps.enabled().isEmpty()) {
            powerupDirector = PowerupPlaces.director(ctx.arena(), ps.catchUp());
            if (powerupDirector.hasSpots()) {
                powerupSpawns = new PowerupSpawns(ps.maxActive(), ps.respawnTicks());
                powerups = new PowerupView(ps.maxActive());
            }
        }
        hud.refresh(0, score, flagStatus());
        online().forEach(p -> updateBar(p, 0));
    }

    @Override
    public long ticksLeft() {
        return Math.max(0, rules.timeLimitTicks() - ctx.ticks());
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
        tickScopes();
        goggles.tick(now, this::headWithoutPaint);
        checkBounds(now);
        if (over) {
            return;
        }
        for (UUID id : combat.respawnsDue(now)) {
            PaintBot bot = bots.get(id);
            if (bot != null) {
                respawnBot(bot);
                continue;
            }
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                respawn(p, now);
            }
        }
        tickBots(now);
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
        // Bots never play on alone: the last real player leaving ends the match (counting nothing).
        if (!bots.isEmpty() && online().isEmpty()) {
            game.plugin().getLogger().info("[paintball] last player left a bot match; ending it");
            finish(MatchOutcome.draw(resultLines()));
        }
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
        bots.values().forEach(PaintBot::remove);
        bots.clear();
    }

    /** The player pulled the trigger. */
    public void trigger(Player p) {
        UUID id = p.getUniqueId();
        FireGate gate = gates.get(id);
        if (over || gate == null) {
            return;
        }
        long now = ctx.ticks();
        if (combat.isOut(id)) {
            if (now - lastDry.getOrDefault(id, Long.MIN_VALUE / 2) >= 6) {
                lastDry.put(id, now);
                PackItems.play(p, PackAssets.SOUND_DRY_FIRE, 0.8f, 1f);
            }
            return;
        }
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
        PaintBot botVictim = event.getHitEntity() == null ? null : bots.get(event.getHitEntity().getUniqueId());
        if (botVictim != null) {
            onBotHit(event, ballId, shot, botVictim, now);
            return;
        }
        if (!(event.getHitEntity() instanceof Player victim)) {
            untrack(ballId);
            if (event.getHitBlock() != null) {
                splats.add(event.getHitBlock(), event.getHitBlockFace(), event.getEntity(), shot.team(), now);
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
            case ABSORBED -> PackItems.play(at, PackAssets.SOUND_SHIELD_BLOCK, 0.8f, 1f);
            case SHIELDED -> {
                PackItems.play(at, PackAssets.SOUND_SHIELD_BREAK, 1f, 1f);
                updateBar(victim, now);
                if (shooter != null) {
                    showMarker(shooter, messages.get("hud.marker-shield"), now);
                }
            }
            case HIT -> {
                victim.playHurtAnimation(0);
                PackItems.play(at, PackAssets.SOUND_HIT, 1f, 0.95f + ThreadLocalRandom.current().nextFloat() * 0.1f);
                paintBurst(at.clone().add(0, 1, 0), shot.team(), 20, 1.4f);
                goggles.splash(victim, shot.team(), now);
                updateBar(victim, now);
                creditHit(shooter, now);
            }
            case OUT -> {
                victim.playHurtAnimation(0);
                creditOut(shot, shooter, victim.getUniqueId(), victim.getName(), team(victim), now);
                eliminate(victim, shot.team(), now, false);
                afterOut(shot, victim.getName(), team(victim), now);
            }
            default -> {
            }
        }
    }

    /** A paintball hit a bot of this match. */
    private void onBotHit(ProjectileHitEvent event, UUID ballId, Shot shot, PaintBot bot, long now) {
        Combat.HitResult result = combat.hit(bot.id, shot.shooter(), shot.team(), now);
        if (result == Combat.HitResult.PASS_THROUGH) {
            event.setCancelled(true);
            return;
        }
        untrack(ballId);
        Player shooter = Bukkit.getPlayer(shot.shooter());
        Location at = bot.location();
        switch (result) {
            case ABSORBED -> PackItems.play(at, PackAssets.SOUND_SHIELD_BLOCK, 0.8f, 1f);
            case SHIELDED -> PackItems.play(at, PackAssets.SOUND_SHIELD_BREAK, 1f, 1f);
            case HIT -> {
                bot.body.playHurtAnimation(0);
                PackItems.play(at, PackAssets.SOUND_HIT, 1f, 0.95f + ThreadLocalRandom.current().nextFloat() * 0.1f);
                paintBurst(at.clone().add(0, 1, 0), shot.team(), 20, 1.4f);
                creditHit(shooter, now);
            }
            case OUT -> {
                bot.body.playHurtAnimation(0);
                creditOut(shot, shooter, bot.id, bot.name, bot.team, now);
                eliminateBot(bot, shot.team(), now);
                afterOut(shot, bot.name, bot.team, now);
            }
            default -> {
            }
        }
    }

    private void creditHit(Player shooter, long now) {
        if (shooter != null) {
            ctx.addStat(shooter, "hits", 1);
            matchHits.merge(shooter.getUniqueId(), 1, Integer::sum);
            PackItems.play(shooter, PackAssets.SOUND_HITMARKER, 0.9f, 1f);
            showMarker(shooter, messages.get("hud.marker"), now);
        }
    }

    /** The shooter's reward and the streak lines for knocking a player or bot out. */
    private void creditOut(Shot shot, Player shooter, UUID victimId, String victimName, int victimTeam, long now) {
        int ended = streaks.out(victimId);
        if (shooter != null) {
            ctx.addStat(shooter, "hits", 1);
            ctx.addStat(shooter, "eliminations", 1);
            matchHits.merge(shooter.getUniqueId(), 1, Integer::sum);
            PackItems.play(shooter, PackAssets.SOUND_ELIMINATE, 1f, 1f);
            int streak = streaks.eliminated(shooter.getUniqueId());
            if (Streaks.announce(streak)) {
                showMarker(shooter, messages.get("hud.streak",
                        Placeholder.unparsed("count", Integer.toString(streak))), now);
                PackItems.play(shooter, PackAssets.SOUND_FLAG_CAPTURED, 0.7f, 1.3f);
                feed(messages.get(streak >= Streaks.UNSTOPPABLE ? "streak.unstoppable" : "streak.on-fire",
                        Placeholder.component("player", coloured(shooter.getName(), shot.team())),
                        Placeholder.unparsed("count", Integer.toString(streak))));
            } else {
                showMarker(shooter, messages.get("hud.eliminated",
                        Placeholder.component("victim", Component.text(victimName))), now);
            }
        }
        if (ended >= Streaks.ON_FIRE) {
            feed(messages.get("streak.ended",
                    Placeholder.component("player", coloured(names.getOrDefault(shot.shooter(), "?"), shot.team())),
                    Placeholder.component("victim", coloured(victimName, victimTeam)),
                    Placeholder.unparsed("count", Integer.toString(ended))));
        }
    }

    /** The kill feed line and, in team deathmatch, the point (which may end the match). */
    private void afterOut(Shot shot, String victimName, int victimTeam, long now) {
        feed(messages.get("feed.out",
                Placeholder.component("victim", coloured(victimName, victimTeam)),
                Placeholder.component("shooter", coloured(names.getOrDefault(shot.shooter(), "?"), shot.team()))));
        if (mode == Mode.TDM) {
            OptionalInt winner = score.add(shot.team());
            hud.refresh(now, score, flagStatus());
            if (winner.isPresent()) {
                finish(MatchOutcome.win(winner.getAsInt(), resultLines()));
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
        matchShots.merge(p.getUniqueId(), 1, Integer::sum);
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
        PackItems.play(p.getLocation(), PackAssets.SOUND_SHOT, 0.8f, 0.95f + ThreadLocalRandom.current().nextFloat() * 0.1f);
        if (wasProtected) {
            updateBar(p, now);
        }
    }

    private void launch(Player p, int team, Vector aim, long now) {
        double spread = scope.spread(p.getUniqueId());
        double[] d = Spread.apply(aim.getX(), aim.getY(), aim.getZ(), spread, ThreadLocalRandom.current());
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
            } else if (settings.ballGravity() < 1) {
                // give back part of the gravity: a flatter arc that still drops a little
                ball.setVelocity(ball.getVelocity().add(new Vector(0, BALL_GRAVITY * (1 - settings.ballGravity()), 0)));
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
                p.teleport(safestSpawn(team));
                feed(messages.get("feed.out-bounds", Placeholder.component("victim", coloured(p.getName(), team))));
            }
        }
    }

    /** Out: splash, flag dropped, hidden from everyone, frozen, gun disabled until the respawn. */
    private void eliminate(Player victim, int paintTeam, long now, boolean outOfBounds) {
        UUID id = victim.getUniqueId();
        ctx.addStat(victim, "outs", 1);
        streaks.out(id);
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
        // The victim's own camera sits inside this burst, so only the others see it.
        Location burst = victim.getLocation().add(0, 1, 0);
        Color colour = Color.fromRGB(PackAssets.teamRgb(paintTeam));
        for (Player other : burst.getWorld().getPlayers()) {
            if (other != victim) {
                other.spawnParticle(Particle.DUST, burst, 80, 0.35, 0.5, 0.35, 0, new Particle.DustOptions(colour, 2.2f));
            }
        }
        PackItems.play(victim.getLocation(), PackAssets.SOUND_OUT, 1f, 1f);
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
        p.teleport(safestSpawn(team(p)));
        p.setFallDistance(0);
        for (Player other : Bukkit.getOnlinePlayers()) {
            other.showEntity(game.plugin(), p);
        }
        freeze(p, false);
        p.clearTitle();
        PackItems.play(p, PackAssets.SOUND_RESPAWN, 0.9f, 1f);
        updateBar(p, now);
    }

    // --- capture the flag ---

    private void tickFlags(long now) {
        for (int f : flags.autoReturns(now)) {
            flagEvent("flag.returned-auto", f, null);
        }
        for (LivingEntity p : runners()) {
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
                    p.getEquipment().setHelmet(null);
                    p.setGlowing(false);
                    if (p instanceof Player player) {
                        ctx.addStat(player, "captures", 1);
                    }
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
                p.getEquipment().setHelmet(FlagView.flag(enemy));
                p.setGlowing(true);
                flagEvent("flag.taken", enemy, p);
            }
            if (flags.state(team) == Flags.State.DROPPED && near(loc, flags.dropPos(team)) && flags.returnHome(team, team)) {
                if (p instanceof Player player) {
                    ctx.addStat(player, "returns", 1);
                }
                flagEvent("flag.returned", team, p);
            }
        }
        if (now % 2 == 0) {
            flagView.sync(flags);
        }
    }

    private void dropFlag(LivingEntity carrier, long now) {
        Location at = carrier.getLocation();
        OptionalInt dropped = flags.drop(carrier.getUniqueId(), at.getX(), at.getY(), at.getZ(), now);
        if (dropped.isPresent()) {
            carrier.getEquipment().setHelmet(null);
            carrier.setGlowing(false);
            flagEvent("flag.dropped", dropped.getAsInt(), carrier);
            flagView.sync(flags);
        }
    }

    /** Chat line, title for everyone, sound, HUD refresh. */
    private void flagEvent(String key, int flag, LivingEntity who) {
        Component line = messages.get(key,
                Placeholder.component("flag", ctx.teamName(flag)),
                Placeholder.component("player", who == null ? Component.empty()
                        : coloured(names.getOrDefault(who.getUniqueId(), who.getName()), team(who))));
        String sound = switch (key) {
            case "flag.captured" -> PackAssets.SOUND_FLAG_CAPTURED;
            case "flag.taken" -> PackAssets.SOUND_FLAG_TAKEN;
            case "flag.dropped" -> PackAssets.SOUND_FLAG_DROPPED;
            default -> PackAssets.SOUND_FLAG_RETURNED;
        };
        for (Player p : online()) {
            p.sendMessage(line);
            PackItems.play(p, sound, 0.9f, 1f);
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
        List<Integer> due = powerupSpawns.due(now);
        if (!due.isEmpty()) {
            List<PowerupDirector.Mover> movers = new ArrayList<>();
            for (Player p : online()) {
                if (!combat.isOut(p.getUniqueId())) {
                    Location l = p.getLocation();
                    movers.add(new PowerupDirector.Mover(l.getX(), l.getY(), l.getZ(), team(p)));
                }
            }
            int[] points = {score.points(0), score.points(1)};
            for (int i : due) {
                List<WalkableArea.Cell> occupied = new ArrayList<>();
                for (int j = 0; j < powerups.size(); j++) {
                    Location o = powerups.point(j);
                    if (o != null) {
                        occupied.add(new WalkableArea.Cell(o.getBlockX(), o.getBlockY(), o.getBlockZ()));
                    }
                }
                Optional<WalkableArea.Cell> spot = powerupDirector.pickSpot(movers, occupied, points, score.target());
                if (spot.isEmpty()) {
                    powerupSpawns.taken(i, now); // try again later
                    continue;
                }
                WalkableArea.Cell c = spot.get();
                PowerupType type = powerupDirector.pickType(c, movers, enabled, points, score.target());
                powerups.spawn(i, new Location(ctx.arena().world(), c.x() + 0.5, c.y(), c.z() + 0.5), type);
            }
        }
        if (now % 2 == 0) {
            powerups.animate();
        }
        for (Player p : online()) {
            if (combat.isOut(p.getUniqueId())) {
                continue;
            }
            for (int i = 0; i < powerups.size(); i++) {
                if (powerupSpawns.present(i) && powerups.point(i) != null
                        && near(p.getLocation(), powerups.point(i), POWERUP_RADIUS)) {
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
        PackItems.play(p, PackAssets.SOUND_POWERUP, 1f, 1f);
        showMarker(p, messages.get("powerup.picked",
                Placeholder.component("powerup", messages.get("powerup." + type.key())),
                Placeholder.component("icon", messages.get("powerup.icon." + type.key()))), now);
    }

    private Component buffsText(UUID id, long now) {
        Component text = Component.empty();
        for (PowerupType type : List.of(PowerupType.RAPID_FIRE, PowerupType.TRIPLE_SHOT, PowerupType.SPEED)) {
            long left = buffs.left(id, type, now);
            if (left > 0) {
                text = text.append(messages.get("hud.buff",
                        Placeholder.component("powerup", messages.get("powerup." + type.key())),
                        Placeholder.component("icon", messages.get("powerup.icon." + type.key())),
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
        hud.actionBar(p, team(p), combat.hitsLeft(id), combat.isProtected(id, now), combat.hasShield(id),
                buffsText(id, now));
    }

    /** Short feedback under the crosshair (subtitle); not while the player is out (the out title wins). */
    private void showMarker(Player p, Component text, long now) {
        if (!combat.isOut(p.getUniqueId())) {
            hud.marker(p, text);
        }
        updateBar(p, now);
    }

    /** What the head slot holds when there is no paint on the goggles: a carried flag, the scope or nothing. */
    private ItemStack headWithoutPaint(Player p) {
        UUID id = p.getUniqueId();
        if (flags != null) {
            OptionalInt carried = flags.carriedBy(id);
            if (carried.isPresent()) {
                return FlagView.flag(carried.getAsInt());
            }
        }
        return scope.isScoped(id) ? Gun.scope() : null;
    }

    /** Undoes everything this match did to the player; safe to call twice. */
    private void cleanup(Player p) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            other.showEntity(game.plugin(), p);
        }
        scope.forget(p);
        goggles.forget(p);
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
            // Against bots nothing counts: no win, loss or stat.
            ctx.finish(bots.isEmpty() ? outcome : outcome.asPractice());
        }
    }

    @Override
    public Component personalLine(UUID id) {
        if (!teams.containsKey(id)) {
            return null;
        }
        int shots = matchShots.getOrDefault(id, 0);
        int hits = matchHits.getOrDefault(id, 0);
        int accuracy = shots == 0 ? 0 : Math.round(100f * hits / shots);
        return messages.get("result.you",
                Placeholder.unparsed("eliminations", Integer.toString(combat.eliminations(id))),
                Placeholder.unparsed("hits", Integer.toString(hits)),
                Placeholder.unparsed("accuracy", Integer.toString(accuracy)),
                Placeholder.unparsed("streak", Integer.toString(streaks.best(id))));
    }

    @Override
    public List<Component> resultLines() {
        List<Component> lines = new ArrayList<>();
        lines.add(messages.get(mode == Mode.CTF ? "result.score-ctf" : "result.score",
                Placeholder.component("red_team", ctx.teamName(0)),
                Placeholder.unparsed("red", Integer.toString(score.points(0))),
                Placeholder.component("blue_team", ctx.teamName(1)),
                Placeholder.unparsed("blue", Integer.toString(score.points(1)))));
        List<UUID> top = combat.top(1);
        if (!top.isEmpty() && combat.eliminations(top.get(0)) > 0) {
            UUID mvp = top.get(0);
            lines.add(messages.get("result.mvp",
                    Placeholder.component("name", coloured(names.getOrDefault(mvp, "?"), teams.getOrDefault(mvp, 0))),
                    Placeholder.unparsed("count", Integer.toString(combat.eliminations(mvp))),
                    Placeholder.unparsed("hits", Integer.toString(matchHits.getOrDefault(mvp, 0)))));
        }
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

    private int team(Entity p) {
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
        Color colour = Color.fromRGB(PackAssets.teamRgb(team));
        at.getWorld().spawnParticle(Particle.DUST, at, count, 0.35, 0.5, 0.35, 0,
                new Particle.DustOptions(colour, size));
    }

    /** Sneaking with the gun in hand aims through the scope; anything else puts it away. */
    private void tickScopes() {
        for (Player p : online()) {
            scope.update(p, !over && !combat.isOut(p.getUniqueId()));
        }
    }

    /**
     * The mouse wheel while aiming changes the zoom instead of the held slot. Returns true when the
     * slot change was used for that (the caller cancels it).
     */
    public boolean scroll(Player p, int from, int to) {
        Component marker = scope.scroll(p, from, to);
        if (marker == null) {
            return false;
        }
        showMarker(p, marker, ctx.ticks());
        return true;
    }

    /** The team spawn furthest from living enemies (ties picked at random). */
    private Location safestSpawn(int team) {
        List<Location> spawns = ctx.arena().spawns(team);
        List<double[]> at = spawns.stream().map(l -> new double[] {l.getX(), l.getY(), l.getZ()}).toList();
        List<double[]> enemies = new ArrayList<>();
        for (Player e : ctx.team(1 - team)) {
            if (combat == null || !combat.isOut(e.getUniqueId())) {
                Location l = e.getLocation();
                enemies.add(new double[] {l.getX(), l.getY(), l.getZ()});
            }
        }
        for (PaintBot b : bots.values()) {
            if (b.team != team && !b.isOut()) {
                enemies.add(b.feet());
            }
        }
        return spawns.get(SpawnPicker.safest(at, enemies, ThreadLocalRandom.current()));
    }

    // --- bots ---

    /** Spawns the bots a player chose before starting alone: teammates and the full enemy team. */
    private void spawnBots() {
        PaintballGame.BotSetup setup = null;
        Player host = null;
        for (Player p : online()) {
            PaintballGame.BotSetup s = game.takeBots(p.getUniqueId());
            if (s != null && setup == null) {
                setup = s;
                host = p;
            }
        }
        if (setup == null) {
            return;
        }
        BotNav nav = new BotNav(PowerupPlaces.walkable(ctx.arena()));
        if (nav.isEmpty()) {
            game.plugin().getLogger().warning("[paintball] no walkable floor for bots in " + ctx.arena().name());
            return;
        }
        int hostTeam = teams.getOrDefault(host.getUniqueId(), setup.team());
        List<String> pool = new ArrayList<>(BOT_NAMES);
        java.util.Collections.shuffle(pool);
        Component levelName = messages.get("bot.description",
                Placeholder.component("level", messages.get("bot.level." + setup.level().id())));
        int n = 0;
        for (int team = 0; team < 2; team++) {
            int count = team == hostTeam ? setup.perTeam() - 1 : setup.perTeam();
            List<Location> spawns = ctx.arena().spawns(team);
            for (int i = 0; i < count; i++) {
                Location at = spawns.get((i + 1) % spawns.size()).clone();
                String name = "Bot " + pool.get(n++ % pool.size());
                PaintBot bot = PaintBot.spawn(at, name, team, i, setup.level(), nav, settings, messages, levelName,
                        ThreadLocalRandom.current());
                bots.put(bot.id, bot);
                teams.put(bot.id, team);
                names.put(bot.id, name);
            }
        }
        game.plugin().getLogger().info("[paintball] " + host.getName() + " plays against " + bots.size()
                + " bots (" + setup.level().id() + ", " + setup.perTeam() + " per team)");
    }

    private void tickBots(long now) {
        if (bots.isEmpty()) {
            return;
        }
        List<PaintBot.Enemy> everyone = new ArrayList<>();
        for (Player p : online()) {
            if (!combat.isOut(p.getUniqueId())) {
                Location l = p.getLocation();
                everyone.add(enemy(p.getUniqueId(), new double[] {l.getX(), l.getY(), l.getZ()}, p.getEyeHeight()));
            }
        }
        for (PaintBot b : bots.values()) {
            if (!b.isOut()) {
                everyone.add(enemy(b.id, b.feet(), b.eye()[1] - b.feet()[1]));
            }
        }
        PaintBot.Orders orders = new PaintBot.Orders() {
            @Override
            public Optional<Location> objective(PaintBot bot) {
                return botObjective(bot);
            }

            @Override
            public void fire(PaintBot bot, double[] direction, long tick) {
                fireBot(bot, direction, tick);
            }
        };
        for (PaintBot bot : List.copyOf(bots.values())) {
            if (over) {
                return;
            }
            List<PaintBot.Enemy> enemies = everyone.stream().filter(e -> teams.getOrDefault(e.id(), -1) != bot.team).toList();
            bot.tick(now, enemies, orders);
        }
    }

    private PaintBot.Enemy enemy(UUID id, double[] feet, double eyeHeight) {
        double[] last = lastPos.put(id, feet);
        double[] v = last == null ? new double[3] : new double[] {feet[0] - last[0], feet[1] - last[1], feet[2] - last[2]};
        if (Math.abs(v[0]) > 1 || Math.abs(v[2]) > 1) {
            v = new double[3]; // a teleport, not a walk
        }
        return new PaintBot.Enemy(id, feet, new double[] {feet[0], feet[1] + 1.1, feet[2]},
                new double[] {feet[0], feet[1] + eyeHeight, feet[2]}, v);
    }

    /** Capture the flag: carry it home, chase the carrier of your flag, pick up a dropped one, attack or defend. */
    private Optional<Location> botObjective(PaintBot bot) {
        if (flags == null) {
            return Optional.empty();
        }
        int team = bot.team;
        int enemy = 1 - team;
        if (flags.carriedBy(bot.id).isPresent()) {
            return Optional.of(flagHomes[team]);
        }
        switch (flags.state(team)) {
            case CARRIED -> {
                Optional<LivingEntity> carrier = flags.carrier(team).flatMap(this::runner);
                if (carrier.isPresent()) {
                    return Optional.of(carrier.get().getLocation());
                }
            }
            case DROPPED -> {
                double[] d = flags.dropPos(team);
                return Optional.of(new Location(ctx.arena().world(), d[0], d[1], d[2]));
            }
            case HOME -> {
            }
        }
        if (bot.index % 2 == 1) {
            return Optional.of(flagHomes[team]); // a defender stays around its own flag
        }
        return switch (flags.state(enemy)) {
            case HOME -> Optional.of(flagHomes[enemy]);
            case DROPPED -> {
                double[] d = flags.dropPos(enemy);
                yield Optional.of(new Location(ctx.arena().world(), d[0], d[1], d[2]));
            }
            case CARRIED -> Optional.of(flagHomes[team]); // a teammate has it: go home with them
        };
    }

    private void fireBot(PaintBot bot, double[] d, long now) {
        if (over || combat.isOut(bot.id)) {
            return;
        }
        combat.shot(bot.id);
        matchShots.merge(bot.id, 1, Integer::sum);
        Snowball ball = bot.body.launchProjectile(Snowball.class, new Vector(d[0], d[1], d[2]).multiply(settings.speed()));
        ball.setItem(Gun.ball(bot.team));
        Gun.tagBall(ball);
        balls.put(ball.getUniqueId(), new Shot(bot.id, bot.team, now));
        game.balls().put(ball.getUniqueId(), this);
        PackItems.play(bot.location(), PackAssets.SOUND_SHOT, 0.8f, 0.95f + ThreadLocalRandom.current().nextFloat() * 0.1f);
    }

    /** A bot is out: flag dropped, splash, hidden from everyone until its respawn. */
    private void eliminateBot(PaintBot bot, int paintTeam, long now) {
        if (flags != null) {
            dropFlag(bot.body, now);
        }
        Location burst = bot.location().add(0, 1, 0);
        burst.getWorld().spawnParticle(Particle.DUST, burst, 80, 0.35, 0.5, 0.35, 0,
                new Particle.DustOptions(Color.fromRGB(PackAssets.teamRgb(paintTeam)), 2.2f));
        PackItems.play(bot.location(), PackAssets.SOUND_OUT, 1f, 1f);
        bot.knockOut();
        for (Player other : Bukkit.getOnlinePlayers()) {
            other.hideEntity(game.plugin(), bot.body);
        }
    }

    private void respawnBot(PaintBot bot) {
        bot.respawn(safestSpawn(bot.team));
        lastPos.remove(bot.id);
        for (Player other : Bukkit.getOnlinePlayers()) {
            other.showEntity(game.plugin(), bot.body);
        }
    }

    /** Players and bots still in the match, alive or not, for the flag rules. */
    private List<LivingEntity> runners() {
        List<LivingEntity> out = new ArrayList<>(online());
        for (PaintBot b : bots.values()) {
            out.add(b.body);
        }
        return out;
    }

    private Optional<LivingEntity> runner(UUID id) {
        PaintBot bot = bots.get(id);
        if (bot != null) {
            return Optional.of(bot.body);
        }
        return Optional.ofNullable(Bukkit.getPlayer(id));
    }

    /** Online players still in this match. */
    private List<Player> online() {
        List<Player> players = new ArrayList<>(ctx.team(0));
        players.addAll(ctx.team(1));
        return players;
    }
}
