package be.dbsdw.minigames.paintball.match;

import be.dbsdw.minigames.core.Messages;
import be.dbsdw.minigames.core.api.MatchContext;
import be.dbsdw.minigames.core.api.MatchHandler;
import be.dbsdw.minigames.core.api.MatchOutcome;
import be.dbsdw.minigames.core.pack.PackItems;
import be.dbsdw.minigames.paintball.Gear;
import be.dbsdw.minigames.paintball.Gun;
import be.dbsdw.minigames.paintball.PackAssets;
import be.dbsdw.minigames.paintball.PaintballGame;
import be.dbsdw.minigames.paintball.ShopMenu;
import be.dbsdw.minigames.paintball.bot.BotNav;
import be.dbsdw.minigames.paintball.rules.BombRounds;
import be.dbsdw.minigames.paintball.rules.BombSites;
import be.dbsdw.minigames.paintball.rules.BombSettings;
import be.dbsdw.minigames.paintball.rules.Buffs;
import be.dbsdw.minigames.paintball.rules.Clock;
import be.dbsdw.minigames.paintball.rules.Combat;
import be.dbsdw.minigames.paintball.rules.FireGate;
import be.dbsdw.minigames.paintball.rules.Flags;
import be.dbsdw.minigames.paintball.rules.Loadout;
import be.dbsdw.minigames.paintball.rules.ModeRules;
import be.dbsdw.minigames.paintball.rules.PowerupDirector;
import be.dbsdw.minigames.paintball.rules.SpawnPicker;
import be.dbsdw.minigames.paintball.rules.Streaks;
import be.dbsdw.minigames.paintball.rules.PowerupSettings;
import be.dbsdw.minigames.paintball.rules.WalkableArea;
import be.dbsdw.minigames.paintball.rules.PowerupSpawns;
import be.dbsdw.minigames.paintball.rules.PowerupType;
import be.dbsdw.minigames.paintball.rules.Settings;
import be.dbsdw.minigames.paintball.rules.ShopCash;
import be.dbsdw.minigames.paintball.rules.ShopItem;
import be.dbsdw.minigames.paintball.rules.Smokes;
import be.dbsdw.minigames.paintball.rules.Spread;
import be.dbsdw.minigames.paintball.rules.TeamScore;
import be.dbsdw.minigames.paintball.rules.Wallet;
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
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
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
 * One paintball match, team deathmatch, capture the flag or the bomb mode: shooting, hits, outs,
 * respawns, bounds, flags, rounds and the bomb, power-ups, the shop and its gear, splats, HUD and
 * stats. The core calls it on the main thread every tick.
 */
public final class PaintballMatch implements MatchHandler {
    public enum Mode { TDM, CTF, BOMB }

    /** No respawn within a bomb round: the round ends first. */
    private static final long NO_RESPAWN = 1_000_000_000L;
    private static final double GRENADE_RADIUS = 3.5;
    private static final double SMOKE_RADIUS = 3.2;
    private static final long SMOKE_TICKS = 9 * 20;
    private static final long CARTRIDGE_RAPID_TICKS = 10 * 20;
    private static final long CARTRIDGE_TRIPLE_TICKS = 8 * 20;
    private static final double DEFUSE_REACH = 2.0;

    private record Grenade(UUID thrower, int team, ShopItem kind, long thrownAt) {
    }

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

    // --- shop (rooms with the shop on, and the bomb mode) ---
    private final boolean shopMode;
    private final ShopCash cash;
    private final Wallet wallet;
    private final Loadout loadout = new Loadout();
    private final Smokes smokes = new Smokes();
    private final Map<UUID, Grenade> grenades = new HashMap<>();
    private final ShopMenu.Shop shop = new MatchShop();

    // --- bomb mode ---
    private final BombSettings bombRules;
    private BombRounds rounds;
    private BombView bombView;
    /** Who carries the bomb (player or bot), or null. */
    private UUID bombCarrier;
    private Location bombDropped;
    private Location bombPlanted;
    /** Sites A and B of a bomb map (empty: the site is the defenders' flag spot, see {@link BombSites}). */
    private List<Location> fixedSites = List.of();
    /** The site the attacking bots go for this round. */
    private int botSite;
    /** Plant or defuse progress in ticks, per player or bot. */
    private final Map<UUID, Long> holds = new HashMap<>();
    /** Game mode of players watching as spectators after going out in a round, to give back. */
    private final Map<UUID, GameMode> watching = new HashMap<>();
    private static final List<String> BOT_NAMES = List.of("Kwast", "Spetter", "Klodder", "Vlek", "Spat",
            "Druppel", "Verfje", "Pip", "Plons", "Sproet", "Kliek", "Smeer");

    public PaintballMatch(MatchContext ctx, PaintballGame game, Mode mode) {
        this.ctx = ctx;
        this.game = game;
        this.mode = mode;
        this.settings = game.settings();
        this.bombRules = game.bombSettings();
        this.rules = switch (mode) {
            case CTF -> settings.ctf();
            case TDM -> settings.tdm();
            case BOMB -> new ModeRules("bomb", bombRules.minPerTeam(), bombRules.maxPerTeam(), bombRules.winRounds(), 0,
                    bombRules.longestMatchTicks());
        };
        this.messages = game.messages();
        this.hud = new Hud(ctx, messages, settings, rules, switch (mode) {
            case CTF -> "hud.title-ctf";
            case TDM -> "hud.title-tdm";
            case BOMB -> "bomb.hud-title";
        });
        this.shopMode = PaintballGame.hasShop(ctx);
        this.cash = game.shopCash();
        this.wallet = new Wallet(cash.start(), cash.cap());
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
                if (shopMode) {
                    p.getInventory().setItem(8, Gun.shopItem(messages));
                }
                p.getInventory().setHeldItemSlot(0);
                hud.show(p, team);
            }
        }
        spawnBots();
        int[] size = new int[2];
        teams.values().forEach(t -> size[t]++);
        score = new TeamScore(2, rules.winScoreFor(Math.max(size[0], size[1])));
        combat = new Combat(teams, settings, switch (mode) {
            case CTF -> settings.ctfRespawnTicks();
            case TDM -> settings.respawnTicks();
            case BOMB -> NO_RESPAWN;
        });
        combat.start(0);
        if (mode == Mode.BOMB) {
            Optional<Location> siteA = ctx.arena().flag(BombSites.SITE_A);
            Optional<Location> siteB = ctx.arena().flag(BombSites.SITE_B);
            if (siteA.isPresent() && siteB.isPresent()) {
                // A bomb map: sites A and B on the defenders' side, spawn 0 for the attackers.
                fixedSites = List.of(siteA.get(), siteB.get());
            } else {
                // A paintball arena: the bomb sites are the flag spots, each team defends its own.
                flagHomes = new Location[] {
                    ctx.arena().flag(0).orElseThrow(() -> new IllegalStateException("arena has no flag for team 0 and no bomb sites")),
                    ctx.arena().flag(1).orElseThrow(() -> new IllegalStateException("arena has no flag for team 1 and no bomb sites")),
                };
            }
            bombView = new BombView(messages);
            rounds = new BombRounds(bombRules, 0);
            startRound(0, false);
            return;
        }
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
        if (mode == Mode.BOMB) {
            return -1; // rounds have their own clock, in the HUD
        }
        return Math.max(0, rules.timeLimitTicks() - ctx.ticks());
    }

    @Override
    public void tick() {
        long now = ctx.ticks();
        if (now >= rules.timeLimitTicks()) {
            if (mode == Mode.BOMB) {
                finish(rounds.winner().isPresent() ? MatchOutcome.win(rounds.winner().getAsInt(), resultLines())
                        : MatchOutcome.draw(resultLines()));
                return;
            }
            OptionalInt leader = score.leader();
            finish(leader.isPresent()
                    ? MatchOutcome.win(leader.getAsInt(), resultLines())
                    : MatchOutcome.draw(resultLines()));
            return;
        }
        for (Player p : online()) {
            FireGate gate = gates.get(p.getUniqueId());
            if (gate != null && !combat.isOut(p.getUniqueId()) && !buyPhase() && gate.pollQueued(now, interval(p, now))) {
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
        tickGrenades(now);
        tickSmokes(now);
        if (mode == Mode.BOMB) {
            tickBomb(now);
            if (over) {
                return;
            }
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
        if (!buyPhase()) {
            tickBots(now);
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
            refreshHud(now);
            keepBoots();
        }
        for (Player p : online()) {
            UUID id = p.getUniqueId();
            if (mode == Mode.BOMB) {
                if (!combat.isOut(id) && now % 10 == 0 && !holds.containsKey(id)) {
                    updateBar(p, now);
                }
            } else if (combat.isOut(id)) {
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
        if (mode == Mode.BOMB) {
            dropBomb(player, false);
            holds.remove(id);
        }
        loadout.clear(id);
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
        if (bombView != null) {
            bombView.removeAll();
        }
        removeGrenades();
        smokes.clear();
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
        if (combat.isOut(id) || buyPhase()) {
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
        Grenade grenade = grenades.remove(ballId);
        if (grenade != null) {
            game.balls().remove(ballId, this);
            if (!over) {
                Location at = event.getEntity().getLocation();
                if (event.getHitBlock() != null && event.getHitBlockFace() != null) {
                    at = event.getHitBlock().getRelative(event.getHitBlockFace()).getLocation().add(0.5, 0, 0.5);
                }
                burst(grenade, at, event, ctx.ticks());
            }
            return;
        }
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
        afterPlayerHit(victim, shot, result, now);
    }

    /** What a hit on a player does, whatever hit them (a paintball or a paint grenade). */
    private void afterPlayerHit(Player victim, Shot shot, Combat.HitResult result, long now) {
        Player shooter = Bukkit.getPlayer(shot.shooter());
        Location at = victim.getLocation();
        if (result != Combat.HitResult.ABSORBED) {
            holds.remove(victim.getUniqueId()); // a hit stops planting or defusing
        }
        switch (result) {
            case ABSORBED -> PackItems.play(at, PackAssets.SOUND_SHIELD_BLOCK, 0.8f, 1f);
            case SHIELDED -> {
                PackItems.play(at, PackAssets.SOUND_SHIELD_BREAK, 1f, 1f);
                if (loadout.take(victim.getUniqueId(), ShopItem.VEST)) {
                    victim.getInventory().setChestplate(null);
                }
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
        afterBotHit(bot, shot, result, now);
    }

    private void afterBotHit(PaintBot bot, Shot shot, Combat.HitResult result, long now) {
        Player shooter = Bukkit.getPlayer(shot.shooter());
        Location at = bot.location();
        if (result != Combat.HitResult.ABSORBED) {
            holds.remove(bot.id);
        }
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
            pay(shooter, cash.elimination(), "elimination");
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
        if (mode == Mode.BOMB) {
            dropBomb(victim, outOfBounds);
            holds.remove(id);
        }
        if (shopMode) {
            loseGear(victim);
        }
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
        buffs.clear(id);
        victim.removePotionEffect(PotionEffectType.SPEED);
        FireGate gate = gates.get(id);
        if (gate != null) {
            gate.clear();
        }
        if (mode == Mode.BOMB) {
            // Out for the rest of the round: watch the others as a spectator.
            watching.putIfAbsent(id, victim.getGameMode());
            victim.setGameMode(GameMode.SPECTATOR);
            hud.bigTitle(victim, messages.get("bomb.out-title"), messages.get("bomb.out-subtitle"));
            return;
        }
        freeze(victim, true);
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
                        pay(player, cash.capture(), "capture");
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
                    pay(player, cash.flagReturn(), "return");
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
        Component extra = buffsText(id, now);
        if (shopMode) {
            extra = extra.append(messages.get("hud.cash", Placeholder.unparsed("cash", Integer.toString(wallet.cash(id)))));
        }
        if (id.equals(bombCarrier)) {
            extra = extra.append(messages.get("hud.bomb"));
        }
        hud.actionBar(p, team(p), combat.hitsLeft(id), combat.isProtected(id, now), combat.hasShield(id), extra);
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
        GameMode before = watching.remove(p.getUniqueId());
        if (before != null) {
            p.setGameMode(before);
        }
        for (int slot = 0; slot < p.getInventory().getSize(); slot++) {
            if (Gun.isKept(p.getInventory().getItem(slot))) {
                p.getInventory().setItem(slot, null);
            }
        }
        if (Gear.isGear(p.getInventory().getChestplate())) {
            p.getInventory().setChestplate(null);
        }
        if (Gear.isGear(p.getInventory().getBoots())) {
            p.getInventory().setBoots(null);
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
        lines.add(messages.get(mode == Mode.BOMB ? "result.score-bomb" : mode == Mode.CTF ? "result.score-ctf" : "result.score",
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
        List<Location> spawns = ctx.arena().spawns(spawnSide(team));
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

    /** Spawns the bots chosen for this match (alone, or added to the room's teams by its host). */
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
        int[] humans = new int[2];
        for (Player p : online()) {
            Integer t = teams.get(p.getUniqueId());
            if (t != null && t >= 0 && t < 2) {
                humans[t]++;
            }
        }
        ctx.markBots();
        int maxPerTeam = ctx.mode().maxPerTeam();
        List<String> pool = new ArrayList<>(BOT_NAMES);
        java.util.Collections.shuffle(pool);
        Component levelName = messages.get("bot.description",
                Placeholder.component("level", messages.get("bot.level." + setup.level().id())));
        int n = 0;
        for (int team = 0; team < 2; team++) {
            int count = Math.max(0, Math.min(setup.in(team), maxPerTeam - humans[team]));
            List<Location> spawns = ctx.arena().spawns(team);
            for (int i = 0; i < count; i++) {
                Location at = spawns.get((humans[team] + i) % spawns.size()).clone();
                String name = "Bot " + pool.get(n++ % pool.size());
                PaintBot bot = PaintBot.spawn(at, name, team, i, setup.level(), nav, settings, messages, levelName,
                        ThreadLocalRandom.current());
                bots.put(bot.id, bot);
                teams.put(bot.id, team);
                names.put(bot.id, name);
            }
        }
        game.plugin().getLogger().info("[paintball] " + host.getName() + " plays against " + bots.size()
                + " bots (" + setup.level().id() + ", per team " + setup.perTeam() + ")");
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
            double[] eye = bot.eye();
            List<PaintBot.Enemy> enemies = everyone.stream()
                    .filter(e -> teams.getOrDefault(e.id(), -1) != bot.team)
                    .filter(e -> !smokes.blocks(eye, e.chest(), now))
                    .toList();
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
        if (mode == Mode.BOMB) {
            return bombObjective(bot);
        }
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
        if (mode == Mode.BOMB) {
            dropBomb(bot.body, false);
            holds.remove(bot.id);
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

    // --- shop ---

    /** The bomb mode's buy phase: frozen at spawn, shop open, no shooting. */
    private boolean buyPhase() {
        return rounds != null && rounds.phase() == BombRounds.Phase.BUY;
    }

    /** The emerald was right-clicked: open the shop (in the bomb mode only in the buy phase). */
    public void openShop(Player p) {
        if (over || !shopMode || !teams.containsKey(p.getUniqueId())) {
            return;
        }
        if (mode == Mode.BOMB && !buyPhase()) {
            p.sendActionBar(messages.get("shop.closed-bomb"));
            return;
        }
        ShopMenu.open(p, messages, shop);
    }

    /** The shop as the menu sees it. */
    private final class MatchShop implements ShopMenu.Shop {
        @Override
        public int cash(Player player) {
            return wallet.cash(player.getUniqueId());
        }

        @Override
        public int owned(Player player, ShopItem item) {
            return loadout.count(player.getUniqueId(), item);
        }

        @Override
        public Loadout.Check check(Player player, ShopItem item) {
            UUID id = player.getUniqueId();
            boolean open = !over && teams.containsKey(id) && !combat.isOut(id) && (mode != Mode.BOMB || buyPhase());
            boolean defender = mode == Mode.BOMB && team(player) == rounds.defenders();
            boolean damaged = combat.hitsLeft(id) < settings.hitsToOut();
            return loadout.check(id, item, wallet.cash(id), open, mode == Mode.BOMB, defender, damaged);
        }

        @Override
        public Loadout.Check buy(Player player, ShopItem item) {
            Loadout.Check check = check(player, item);
            if (check != Loadout.Check.OK || !wallet.spend(player.getUniqueId(), item.price())) {
                return check == Loadout.Check.OK ? Loadout.Check.TOO_POOR : check;
            }
            loadout.add(player.getUniqueId(), item);
            equip(player, item);
            player.sendMessage(messages.get("shop.bought",
                    Placeholder.component("item", messages.get("shop.item." + item.key() + ".name"))));
            updateBar(player, ctx.ticks());
            return Loadout.Check.OK;
        }

        @Override
        public boolean sells(ShopItem item) {
            return !item.defendersOnly() || mode == Mode.BOMB;
        }

        @Override
        public Component note(Player player) {
            if (!buyPhase()) {
                return null;
            }
            return messages.get("shop.buy-time", Placeholder.unparsed("seconds",
                    Long.toString(Clock.secondsLeft(rounds.ticksLeft(ctx.ticks())))));
        }
    }

    /** Puts a bought item to work: worn, used at once, or into its hotbar slot. */
    private void equip(Player p, ShopItem item) {
        UUID id = p.getUniqueId();
        switch (item) {
            case VEST -> {
                combat.giveShield(id);
                p.getInventory().setChestplate(Gear.item(messages, item, 1));
            }
            case REPAIR -> combat.repair(id);
            case BOOTS -> {
                p.getInventory().setBoots(Gear.item(messages, item, 1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 0, false, false, true));
            }
            default -> refreshSlot(p, item);
        }
        PackItems.play(p, PackAssets.SOUND_POWERUP, 0.7f, 1.3f);
    }

    private void refreshSlot(Player p, ShopItem item) {
        int slot = Gear.slot(item);
        if (slot < 0) {
            return;
        }
        int n = loadout.count(p.getUniqueId(), item);
        p.getInventory().setItem(slot, n > 0 ? Gear.item(messages, item, n) : null);
    }

    /** Going out (or the sides swapping) costs everything bought. */
    private void loseGear(Player p) {
        loadout.clear(p.getUniqueId());
        for (ShopItem item : ShopItem.values()) {
            refreshSlot(p, item);
        }
        if (Gear.isGear(p.getInventory().getChestplate())) {
            p.getInventory().setChestplate(null);
        }
        if (Gear.isGear(p.getInventory().getBoots())) {
            p.getInventory().setBoots(null);
        }
    }

    /** Speed from boots comes back when a speed power-up runs out. */
    private void keepBoots() {
        if (!shopMode) {
            return;
        }
        for (Player p : online()) {
            if (!combat.isOut(p.getUniqueId()) && loadout.has(p.getUniqueId(), ShopItem.BOOTS)
                    && !p.hasPotionEffect(PotionEffectType.SPEED)) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 0, false, false, true));
            }
        }
    }

    /** Cash for a player in a match with the shop, with a short line saying why. */
    private void pay(Player p, int amount, String reason) {
        if (!shopMode || amount <= 0 || p == null || !teams.containsKey(p.getUniqueId())) {
            return;
        }
        int got = wallet.earn(p.getUniqueId(), amount);
        if (got > 0) {
            p.sendMessage(messages.get("shop.earned", Placeholder.unparsed("amount", Integer.toString(got)),
                    Placeholder.component("reason", messages.get("shop.reason." + reason))));
        }
    }

    /** A bought item or the bomb was right-clicked. */
    public void useGear(Player p, ItemStack stack) {
        UUID id = p.getUniqueId();
        if (over || !teams.containsKey(id)) {
            return;
        }
        long now = ctx.ticks();
        if (Gear.isBomb(stack)) {
            p.sendActionBar(messages.get("bomb.hint-plant"));
            return;
        }
        ShopItem item = Gear.of(stack);
        if (item == null || combat.isOut(id)) {
            return;
        }
        switch (item) {
            case RAPID_CARTRIDGE, TRIPLE_CARTRIDGE -> {
                if (!loadout.take(id, item)) {
                    return;
                }
                PowerupType type = item == ShopItem.RAPID_CARTRIDGE ? PowerupType.RAPID_FIRE : PowerupType.TRIPLE_SHOT;
                buffs.give(id, type, now, item == ShopItem.RAPID_CARTRIDGE ? CARTRIDGE_RAPID_TICKS : CARTRIDGE_TRIPLE_TICKS);
                PackItems.play(p, PackAssets.SOUND_POWERUP, 1f, 1f);
                showMarker(p, messages.get("powerup.picked",
                        Placeholder.component("powerup", messages.get("powerup." + type.key())),
                        Placeholder.component("icon", messages.get("powerup.icon." + type.key()))), now);
            }
            case PAINT_GRENADE, SMOKE_GRENADE -> {
                if (buyPhase() || !loadout.take(id, item)) {
                    return;
                }
                throwGrenade(p, item, now);
            }
            case DEFUSE_KIT -> p.sendActionBar(messages.get("bomb.hint-kit"));
            default -> {
                return;
            }
        }
        refreshSlot(p, item);
        p.getInventory().setHeldItemSlot(0);
    }

    // --- grenades and smoke ---

    private void throwGrenade(Player p, ShopItem kind, long now) {
        Snowball ball = p.launchProjectile(Snowball.class, p.getLocation().getDirection().multiply(1.25));
        ball.setItem(Gear.thrown(kind));
        Gun.tagBall(ball);
        grenades.put(ball.getUniqueId(), new Grenade(p.getUniqueId(), team(p), kind, now));
        game.balls().put(ball.getUniqueId(), this);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 1f, 0.6f);
        p.swingMainHand();
    }

    /** A grenade landed: paint on every enemy near it, or a cloud of smoke. */
    private void burst(Grenade g, Location at, ProjectileHitEvent event, long now) {
        if (g.kind() == ShopItem.SMOKE_GRENADE) {
            smokes.add(at.getX(), at.getY() + 1, at.getZ(), SMOKE_RADIUS, now + SMOKE_TICKS);
            at.getWorld().playSound(at, Sound.BLOCK_FIRE_EXTINGUISH, 1.5f, 0.6f);
            return;
        }
        paintBurst(at.clone().add(0, 0.6, 0), g.team(), 160, 2.6f);
        at.getWorld().spawnParticle(Particle.EXPLOSION, at, 1);
        PackItems.play(at, PackAssets.SOUND_SPLAT, 1.5f, 0.7f);
        at.getWorld().playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.6f);
        if (event.getHitBlock() != null && event.getHitBlockFace() != null) {
            splats.add(event.getHitBlock(), event.getHitBlockFace(), event.getEntity(), g.team(), now);
        }
        Shot shot = new Shot(g.thrower(), g.team(), now);
        double r2 = GRENADE_RADIUS * GRENADE_RADIUS;
        for (Player victim : online()) {
            if (teams.getOrDefault(victim.getUniqueId(), g.team()) != g.team() && !combat.isOut(victim.getUniqueId())
                    && victim.getLocation().distanceSquared(at) <= r2) {
                Combat.HitResult result = combat.hit(victim.getUniqueId(), g.thrower(), g.team(), now);
                if (result != Combat.HitResult.PASS_THROUGH) {
                    afterPlayerHit(victim, shot, result, now);
                }
                if (over) {
                    return;
                }
            }
        }
        for (PaintBot bot : List.copyOf(bots.values())) {
            if (bot.team != g.team() && !bot.isOut() && bot.location().distanceSquared(at) <= r2) {
                Combat.HitResult result = combat.hit(bot.id, g.thrower(), g.team(), now);
                if (result != Combat.HitResult.PASS_THROUGH) {
                    afterBotHit(bot, shot, result, now);
                }
                if (over) {
                    return;
                }
            }
        }
    }

    /** A grenade that never lands (fell out of the world) is dropped after a few seconds. */
    private void tickGrenades(long now) {
        Iterator<Map.Entry<UUID, Grenade>> it = grenades.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Grenade> e = it.next();
            Entity ball = Bukkit.getEntity(e.getKey());
            if (ball == null || !ball.isValid() || now - e.getValue().thrownAt() > 100) {
                if (ball != null) {
                    ball.remove();
                }
                it.remove();
                game.balls().remove(e.getKey(), this);
            }
        }
    }

    private void removeGrenades() {
        for (UUID id : grenades.keySet()) {
            Entity ball = Bukkit.getEntity(id);
            if (ball != null) {
                ball.remove();
            }
            game.balls().remove(id, this);
        }
        grenades.clear();
    }

    /** Thick grey dust and a little rising smoke in every cloud. */
    private void tickSmokes(long now) {
        if (now % 2 != 0) {
            return;
        }
        Particle.DustOptions grey = new Particle.DustOptions(Color.fromRGB(0xB8, 0xBC, 0xB0), 4f);
        Particle.DustOptions dark = new Particle.DustOptions(Color.fromRGB(0x8A, 0x8E, 0x84), 4f);
        for (Smokes.Cloud c : smokes.active(now)) {
            Location at = new Location(ctx.arena().world(), c.x(), c.y(), c.z());
            double spread = c.radius() * 0.55;
            at.getWorld().spawnParticle(Particle.DUST, at, 26, spread, spread * 0.7, spread, 0, grey, true);
            at.getWorld().spawnParticle(Particle.DUST, at, 12, spread, spread * 0.7, spread, 0, dark, true);
            if (now % 6 == 0) {
                at.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, at, 3, spread, 0.4, spread, 0.005, null, true);
            }
        }
    }

    // --- bomb mode ---

    /** This round's bomb sites: A and B on a bomb map, else the defenders' flag spot. */
    private List<Location> sites() {
        return fixedSites.isEmpty() ? List.of(flagHomes[rounds.defenders()]) : fixedSites;
    }

    /** The arena spawn list a team uses: on a bomb map the attackers always start at spawn 0. */
    private int spawnSide(int team) {
        return rounds == null ? team : BombSites.spawnSide(team, rounds.attackers(), !fixedSites.isEmpty());
    }

    /** The index of the site this spot is on, or -1. */
    private int siteAt(Location loc) {
        List<Location> sites = sites();
        if (loc.getWorld() != sites.getFirst().getWorld()) {
            return -1;
        }
        List<double[]> at = sites.stream().map(l -> new double[] {l.getX(), l.getY(), l.getZ()}).toList();
        return BombSites.siteAt(at, bombRules.siteRadius(), loc.getX(), loc.getY(), loc.getZ());
    }

    private boolean onSite(Location loc) {
        return siteAt(loc) >= 0;
    }

    /** "A" or "B" on a bomb map; empty for the single flag-spot site. */
    private String siteLabel(int index) {
        return fixedSites.isEmpty() || index < 0 ? "" : BombSites.label(index);
    }

    /** Players and bots of the team still alive in this round. */
    private int alive(int team) {
        int n = 0;
        for (Player p : ctx.team(team)) {
            if (!combat.isOut(p.getUniqueId())) {
                n++;
            }
        }
        for (PaintBot b : bots.values()) {
            if (b.team == team && !b.isOut()) {
                n++;
            }
        }
        return n;
    }

    private void tickBomb(long now) {
        BombRounds.Event event = rounds.tick(now, alive(rounds.attackers()), alive(rounds.defenders()));
        switch (event) {
            case GO -> {
                for (Player p : online()) {
                    if (!combat.isOut(p.getUniqueId())) {
                        freeze(p, false);
                        if (p.getOpenInventory().getTopInventory().getHolder(false) instanceof ShopMenu) {
                            p.closeInventory();
                        }
                    }
                    hud.bigTitle(p, messages.get("bomb.go"), Component.empty());
                    p.playSound(p, Sound.EVENT_RAID_HORN, 0.6f, 1.4f);
                }
            }
            case ROUND_OVER -> roundOver(now);
            case NEXT_ROUND -> startRound(now, false);
            case SIDES_SWAPPED -> startRound(now, true);
            case MATCH_OVER -> {
                OptionalInt winner = rounds.winner();
                finish(winner.isPresent() ? MatchOutcome.win(winner.getAsInt(), resultLines())
                        : MatchOutcome.draw(resultLines()));
                return;
            }
            case NONE -> {
            }
        }
        switch (rounds.phase()) {
            case LIVE -> {
                tickPlant(now);
                tickPickup();
            }
            case PLANTED -> {
                tickDefuse(now);
                bombView.tickPlanted(now, rounds.ticksLeft(now));
            }
            default -> {
            }
        }
        bombView.tickDropped();
        if (now % 10 == 0 && rounds.phase() != BombRounds.Phase.POST) {
            bombView.ringSites(bombRules.siteRadius());
        }
        if (now % 10 == 0) {
            keepWatchersInside();
        }
    }

    /** A new round: everyone alive at their spawn, frozen for the buy phase; the bomb to an attacker. */
    private void startRound(long now, boolean swapped) {
        int attackers = rounds.attackers();
        if (swapped) {
            wallet.reset();
            loadout.clearAll();
        }
        bombView.removeAll();
        bombCarrier = null;
        bombDropped = null;
        bombPlanted = null;
        holds.clear();
        smokes.clear();
        removeGrenades();
        for (UUID ballId : List.copyOf(balls.keySet())) {
            Entity ball = Bukkit.getEntity(ballId);
            if (ball != null) {
                ball.remove();
            }
            untrack(ballId);
        }
        splats.removeAll();
        combat.start(now);
        for (Player p : online()) {
            UUID id = p.getUniqueId();
            int team = team(p);
            GameMode before = watching.remove(id);
            if (before != null) {
                p.setGameMode(before);
            }
            for (Player other : Bukkit.getOnlinePlayers()) {
                other.showEntity(game.plugin(), p);
            }
            p.teleport(safestSpawn(team));
            p.setFallDistance(0);
            p.clearTitle();
            buffs.clear(id);
            if (swapped) {
                loseGear(p);
                combat.takeShield(id);
                p.removePotionEffect(PotionEffectType.SPEED);
            }
            p.getInventory().setItem(Gear.BOMB_SLOT, null);
            p.getInventory().setHeldItemSlot(0);
            freeze(p, true);
            Component side = team == attackers ? messages.get("bomb.attack") : messages.get("bomb.defend");
            String hintKey = (team == attackers ? "bomb.attack-hint" : "bomb.defend-hint") + (fixedSites.isEmpty() ? "" : "-sites");
            Component hint = messages.get(hintKey, Placeholder.component("team", ctx.teamName(1 - team)));
            hud.bigTitle(p, messages.get("bomb.round-title",
                    Placeholder.unparsed("round", Integer.toString(rounds.round())),
                    Placeholder.unparsed("max", Integer.toString(bombRules.maxRounds()))), side);
            p.sendMessage(side.append(Component.text(" ")).append(hint));
            if (swapped) {
                p.sendMessage(messages.get("bomb.swap", Placeholder.unparsed("cash", Integer.toString(cash.start()))));
            }
            PackItems.play(p, PackAssets.SOUND_RESPAWN, 0.9f, 1f);
            updateBar(p, now);
        }
        for (PaintBot bot : bots.values()) {
            bot.body.getEquipment().setItemInOffHand(null);
            respawnBot(bot);
        }
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < fixedSites.size(); i++) {
            labels.add(BombSites.label(i));
        }
        bombView.showSites(sites(), labels);
        botSite = ThreadLocalRandom.current().nextInt(sites().size());
        List<UUID> carriers = new ArrayList<>();
        for (Player p : ctx.team(attackers)) {
            carriers.add(p.getUniqueId());
        }
        if (carriers.isEmpty()) {
            for (PaintBot b : bots.values()) {
                if (b.team == attackers) {
                    carriers.add(b.id);
                }
            }
        }
        if (!carriers.isEmpty()) {
            giveBomb(carriers.get(ThreadLocalRandom.current().nextInt(carriers.size())), false);
        }
        refreshHud(now);
    }

    private void roundOver(long now) {
        BombRounds.Result result = rounds.last();
        int winner = result.winner();
        score.add(winner);
        holds.clear();
        if (result.how() == BombRounds.End.EXPLODED) {
            bombView.explode(rounds.attackers());
            Location at = bombPlanted;
            // The blast paints everyone still standing near it.
            if (at != null) {
                for (Player p : ctx.team(rounds.defenders())) {
                    if (!combat.isOut(p.getUniqueId()) && p.getLocation().distanceSquared(at) <= 64) {
                        paintBurst(p.getLocation().add(0, 1, 0), rounds.attackers(), 30, 1.6f);
                    }
                }
            }
        }
        for (Player p : online()) {
            int team = team(p);
            if (team == winner) {
                ctx.addStat(p, "rounds", 1);
            }
            pay(p, team == winner ? cash.roundWin() : cash.roundLoss(), team == winner ? "round-win" : "round-loss");
            hud.bigTitle(p, messages.get("bomb.won-title", Placeholder.component("team", ctx.teamName(winner))),
                    messages.get("bomb.end." + result.how().name().toLowerCase()));
            p.playSound(p, team == winner ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
        }
        refreshHud(now);
    }

    /** Attackers holding the bomb and sneaking on the site plant it; bots carrying it plant on the site. */
    private void tickPlant(long now) {
        if (bombCarrier == null) {
            return;
        }
        PaintBot bot = bots.get(bombCarrier);
        if (bot != null) {
            if (!bot.isOut() && onSite(bot.location())) {
                long held = holds.merge(bot.id, 1L, Long::sum);
                if (held >= bombRules.plantTicks()) {
                    plant(bot.location(), bot.id, bot.name, now);
                }
            }
            return;
        }
        Player p = Bukkit.getPlayer(bombCarrier);
        if (p == null || combat.isOut(p.getUniqueId())) {
            return;
        }
        UUID id = p.getUniqueId();
        if (p.isSneaking() && Gear.isBomb(p.getInventory().getItemInMainHand()) && onSite(p.getLocation())) {
            long held = holds.merge(id, 1L, Long::sum);
            p.sendActionBar(messages.get("bomb.planting",
                    Placeholder.component("bar", progressBar(held, bombRules.plantTicks(), TextColor.color(0xFFB52E)))));
            if (held % 5 == 1) {
                p.getWorld().playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 1.5f);
            }
            if (held >= bombRules.plantTicks()) {
                plant(p.getLocation(), id, p.getName(), now);
                ctx.addStat(p, "plants", 1);
                pay(p, cash.objective(), "plant");
            }
        } else if (holds.remove(id) != null) {
            updateBar(p, now);
        }
    }

    private void plant(Location at, UUID who, String name, long now) {
        if (!rounds.plant(now)) {
            return;
        }
        holds.clear();
        Location spot = at.getBlock().getLocation().add(0.5, 0, 0.5);
        spot.setYaw(at.getYaw());
        String label = siteLabel(siteAt(at));
        bombPlanted = spot;
        takeBombFrom(who);
        bombCarrier = null;
        bombView.plant(spot);
        Component by = label.isEmpty()
                ? messages.get("bomb.planted-by", Placeholder.component("player", coloured(name, rounds.attackers())))
                : messages.get("bomb.planted-by-site", Placeholder.component("player", coloured(name, rounds.attackers())),
                        Placeholder.unparsed("site", label));
        Component planted = messages.get("bomb.planted", Placeholder.unparsed("seconds",
                Long.toString(Clock.secondsLeft(bombRules.fuseTicks()))));
        for (Player p : online()) {
            p.sendMessage(by);
            hud.bigTitle(p, Component.empty(), planted);
            p.playSound(p, Sound.BLOCK_ANVIL_LAND, 0.5f, 1.8f);
        }
        refreshHud(now);
    }

    /** Defenders sneaking next to the planted bomb defuse it; a defuse kit halves the time. */
    private void tickDefuse(long now) {
        if (bombPlanted == null) {
            return;
        }
        int defenders = rounds.defenders();
        for (Player p : ctx.team(defenders)) {
            UUID id = p.getUniqueId();
            if (!combat.isOut(id) && p.isSneaking() && near(p.getLocation(), bombPlanted, DEFUSE_REACH)) {
                long need = loadout.has(id, ShopItem.DEFUSE_KIT) ? bombRules.kitDefuseTicks() : bombRules.defuseTicks();
                long held = holds.merge(id, 1L, Long::sum);
                p.sendActionBar(messages.get("bomb.defusing",
                        Placeholder.component("bar", progressBar(held, need, TextColor.color(0x9CC05A)))));
                if (held % 6 == 1) {
                    p.getWorld().playSound(p.getLocation(), Sound.BLOCK_TRIPWIRE_CLICK_ON, 0.8f, 1.6f);
                }
                if (held >= need) {
                    ctx.addStat(p, "defuses", 1);
                    pay(p, cash.objective(), "defuse");
                    defused(p.getName(), now);
                    return;
                }
            } else if (holds.remove(id) != null) {
                updateBar(p, now);
            }
        }
        for (PaintBot bot : bots.values()) {
            if (bot.team == defenders && !bot.isOut() && near(bot.location(), bombPlanted, DEFUSE_REACH)) {
                if (holds.merge(bot.id, 1L, Long::sum) >= bombRules.defuseTicks()) {
                    defused(bot.name, now);
                    return;
                }
            }
        }
    }

    private void defused(String name, long now) {
        if (!rounds.defuse(now)) {
            return;
        }
        bombView.defused(rounds.defenders());
        Component by = messages.get("bomb.defused-by", Placeholder.component("player", coloured(name, rounds.defenders())));
        online().forEach(p -> p.sendMessage(by));
        roundOver(now);
    }

    /** An attacker walking over the dropped bomb picks it up. */
    private void tickPickup() {
        if (bombDropped == null) {
            return;
        }
        int attackers = rounds.attackers();
        for (Player p : ctx.team(attackers)) {
            if (!combat.isOut(p.getUniqueId()) && near(p.getLocation(), bombDropped)) {
                giveBomb(p.getUniqueId(), true);
                return;
            }
        }
        for (PaintBot b : bots.values()) {
            if (b.team == attackers && !b.isOut() && near(b.location(), bombDropped)) {
                giveBomb(b.id, true);
                return;
            }
        }
    }

    private void giveBomb(UUID id, boolean pickedUp) {
        bombCarrier = id;
        bombDropped = null;
        bombView.removeDropped();
        PaintBot bot = bots.get(id);
        String name;
        if (bot != null) {
            bot.body.getEquipment().setItemInOffHand(Gear.bombModel());
            name = bot.name;
        } else {
            Player p = Bukkit.getPlayer(id);
            if (p == null) {
                return;
            }
            p.getInventory().setItem(Gear.BOMB_SLOT, Gear.bomb(messages));
            p.playSound(p, Sound.ITEM_ARMOR_EQUIP_IRON, 0.8f, 1f);
            name = p.getName();
        }
        // Only the attackers hear who has it.
        Component line = messages.get(pickedUp ? "bomb.picked" : "bomb.carrier",
                Placeholder.component("player", coloured(name, rounds.attackers())));
        ctx.team(rounds.attackers()).forEach(p -> p.sendMessage(line));
    }

    /** The carrier is out or left: the bomb drops where they were (at their spawn if out of bounds). */
    private void dropBomb(LivingEntity carrier, boolean outOfBounds) {
        if (rounds == null || !carrier.getUniqueId().equals(bombCarrier)) {
            return;
        }
        takeBombFrom(carrier.getUniqueId());
        bombCarrier = null;
        Location at = outOfBounds ? ctx.arena().spawns(spawnSide(rounds.attackers())).getFirst().clone() : carrier.getLocation().clone();
        bombDropped = at;
        bombView.drop(at);
        Component line = messages.get("bomb.dropped");
        ctx.team(rounds.attackers()).forEach(p -> {
            p.sendMessage(line);
            p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.5f);
        });
    }

    private void takeBombFrom(UUID id) {
        PaintBot bot = bots.get(id);
        if (bot != null) {
            bot.body.getEquipment().setItemInOffHand(null);
            return;
        }
        Player p = Bukkit.getPlayer(id);
        if (p != null) {
            for (int slot = 0; slot < p.getInventory().getSize(); slot++) {
                if (Gear.isBomb(p.getInventory().getItem(slot))) {
                    p.getInventory().setItem(slot, null);
                }
            }
        }
    }

    /** Spectating players stay over the arena. */
    private void keepWatchersInside() {
        for (Player p : online()) {
            if (watching.containsKey(p.getUniqueId()) && (p.getWorld() != ctx.arena().world()
                    || !ctx.arena().bounds().contains(p.getLocation().toVector()))) {
                p.teleport(ctx.arena().spawns(spawnSide(team(p))).getFirst());
            }
        }
    }

    /** Bomb mode bots: carry the bomb to the site, fetch it when dropped, guard and defuse. */
    private Optional<Location> bombObjective(PaintBot bot) {
        if (rounds == null) {
            return Optional.empty();
        }
        List<Location> sites = sites();
        Location target = sites.get(Math.min(botSite, sites.size() - 1));
        if (bot.team == rounds.attackers()) {
            if (bot.id.equals(bombCarrier) || bombPlanted != null) {
                return Optional.of(bombPlanted != null ? bombPlanted : target);
            }
            if (bombDropped != null) {
                return Optional.of(bombDropped);
            }
            return bot.index % 2 == 0 ? Optional.of(target) : Optional.empty();
        }
        if (bombPlanted != null) {
            return Optional.of(bombPlanted);
        }
        if (sites.size() > 1) {
            // On a bomb map the defenders split over the sites.
            return Optional.of(sites.get(bot.index % sites.size()));
        }
        return bot.index % 2 == 0 ? Optional.of(target) : Optional.empty();
    }

    private static Component progressBar(long done, long need, TextColor colour) {
        int filled = (int) Math.min(20, done * 20 / Math.max(1, need));
        return Component.text("|".repeat(filled), colour).append(Component.text("|".repeat(20 - filled), NamedTextColor.DARK_GRAY));
    }

    private void refreshHud(long now) {
        if (mode != Mode.BOMB) {
            hud.refresh(now, score, flagStatus());
            return;
        }
        long left = rounds.ticksLeft(now);
        String key = switch (rounds.phase()) {
            case BUY -> "bomb.bossbar.buy";
            case LIVE -> "bomb.bossbar.live";
            case PLANTED -> "bomb.bossbar.planted";
            case POST, OVER -> "bomb.bossbar.post";
        };
        Component status = messages.get(key, Placeholder.unparsed("time", Clock.format(left)),
                Placeholder.unparsed("round", Integer.toString(rounds.round())),
                Placeholder.unparsed("max", Integer.toString(bombRules.maxRounds())));
        hud.refreshRounds(score, left, rounds.clockTicks(), status, rounds.phase() == BombRounds.Phase.PLANTED);
    }

    /** Online players still in this match. */
    private List<Player> online() {
        List<Player> players = new ArrayList<>(ctx.team(0));
        players.addAll(ctx.team(1));
        return players;
    }
}
