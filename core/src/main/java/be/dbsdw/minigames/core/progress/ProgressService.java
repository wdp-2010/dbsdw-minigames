package be.dbsdw.minigames.core.progress;

import be.dbsdw.minigames.core.Messages;
import be.dbsdw.minigames.core.api.GameType;
import be.dbsdw.minigames.core.api.ModeSpec;
import be.dbsdw.minigames.core.stats.StatsStore;
import be.dbsdw.minigames.core.progress.XpRules.Result;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * XP and levels. Total XP lives in the stats store (stat "xp", under the game's id and under
 * {@link #ACCOUNT} for all games together); online players' totals are cached here so menus and
 * queue checks on the main thread never wait for the database.
 */
public final class ProgressService {
    /** The "game" the all-games XP is stored under. */
    public static final String ACCOUNT = "account";
    public static final String XP = "xp";

    /** XP one player earned from one match, and the levels before and after. */
    public record Gain(long xp, int gameFrom, int gameTo, int accountFrom, int accountTo) {
    }

    private final Plugin plugin;
    private final StatsStore stats;
    private final LevelCurve curve;
    private final XpRules rules;
    private final Messages messages;
    private final Logger log;
    private final Map<UUID, Map<String, Long>> cache = new ConcurrentHashMap<>();

    public ProgressService(Plugin plugin, StatsStore stats, LevelCurve curve, XpRules rules, Messages messages,
                           Logger log) {
        this.plugin = plugin;
        this.stats = stats;
        this.curve = curve;
        this.rules = rules;
        this.messages = messages;
        this.log = log;
    }

    public LevelCurve curve() {
        return curve;
    }

    public XpRules rules() {
        return rules;
    }

    /** The vanilla XP bar shows the minigames level and the progress to the next one. */
    public void showBar(Player player) {
        long total = xp(player.getUniqueId(), ACCOUNT);
        if (total < 0) {
            return;
        }
        player.setLevel(curve.levelOf(total));
        player.setExp((float) Math.min(0.999, curve.fraction(total)));
    }

    /** Loads a joining player's XP into the cache (in the background). */
    public void load(UUID id) {
        if (stats == null) {
            ready(id, Map.of());
            return;
        }
        stats.xp(id).whenComplete((xp, error) -> {
            if (error != null) {
                log.log(Level.WARNING, "[progress] loading XP of " + id + " failed", error);
            }
            // Back on the main thread, and only for a player who is still here: a late answer for
            // someone who already left must not put them back in the cache.
            Map<String, Long> loaded = error == null ? xp : Map.of();
            if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> ready(id, loaded));
            }
        });
    }

    /**
     * Puts the loaded XP in the cache. When the XP could not be read the player counts as level 1: the
     * level locks stay shut rather than open.
     */
    private void ready(UUID id, Map<String, Long> loaded) {
        Player player = Bukkit.getPlayer(id);
        if (player == null) {
            return;
        }
        // Keep anything a match added while the load was running.
        cache.merge(id, new ConcurrentHashMap<>(loaded), (now, fresh) -> {
            fresh.forEach((game, value) -> now.merge(game, value, Math::max));
            return now;
        });
        showBar(player);
    }

    public void unload(UUID id) {
        cache.remove(id);
    }

    /** Total XP in a game (or {@link #ACCOUNT}), or -1 until the player's XP is loaded. */
    public long xp(UUID id, String game) {
        Map<String, Long> xp = cache.get(id);
        return xp == null ? -1 : xp.getOrDefault(game, 0L);
    }

    /** The player's level in a game (or {@link #ACCOUNT}); 1 until the XP is loaded. */
    public int level(UUID id, String game) {
        return curve.levelOf(Math.max(0, xp(id, game)));
    }

    /**
     * Why this player may not play the mode yet (shown to them), or null if they may. A player whose
     * XP is not loaded yet counts as level 1.
     */
    public Component lockReason(Player player, GameType game, ModeSpec mode) {
        int need = game.requiredLevel(mode);
        if (need <= 1) {
            return null;
        }
        int have = level(player.getUniqueId(), game.id());
        if (have >= need) {
            return null;
        }
        return messages.get("progress.locked", Placeholder.component("game", game.displayName()),
                Placeholder.parsed("mode", mode.displayName()),
                Placeholder.unparsed("need", String.valueOf(need)),
                Placeholder.unparsed("have", String.valueOf(have)));
    }

    /** Menu line for a mode this player is not yet allowed to play, or null. */
    public Component lockLore(UUID id, GameType game, ModeSpec mode) {
        if (!locked(id, game, mode)) {
            return null;
        }
        return messages.get("progress.locked-lore", Placeholder.component("game", game.displayName()),
                Placeholder.unparsed("need", String.valueOf(game.requiredLevel(mode))),
                Placeholder.unparsed("have", String.valueOf(level(id, game.id()))));
    }

    /** Whether the mode is locked for this player: they are below its required level. */
    public boolean locked(UUID id, GameType game, ModeSpec mode) {
        int need = game.requiredLevel(mode);
        return need > 1 && level(id, game.id()) < need;
    }

    /**
     * Works out the XP of a finished match and books it: adds a {@code xp} stat to each player's
     * {@code deltas} (saved with the game's stats), stores the all-games XP, and updates the cache.
     *
     * @param results the players who were in the match at the end, with their result
     * @param seconds how long the match lasted
     * @return what each rewarded player earned, to {@link #announce} after the match results
     */
    public Map<UUID, Gain> award(GameType game, Map<UUID, Result> results, Map<UUID, String> names,
                                 Map<UUID, Map<String, Long>> deltas, long seconds, boolean withBots) {
        Map<UUID, Gain> gains = new LinkedHashMap<>();
        Map<UUID, Map<String, Long>> account = new HashMap<>();
        for (Map.Entry<UUID, Result> e : results.entrySet()) {
            UUID id = e.getKey();
            Map<String, Long> own = deltas.getOrDefault(id, Map.of());
            long xp = rules.award(e.getValue(), own, game::xpPerStat, seconds, false, true, withBots);
            if (xp <= 0) {
                continue;
            }
            deltas.computeIfAbsent(id, k -> new HashMap<>()).merge(XP, xp, Long::sum);
            account.put(id, Map.of(XP, xp));
            Map<String, Long> cached = cache.get(id);
            long gameBefore = cached == null ? 0 : cached.getOrDefault(game.id(), 0L);
            long accountBefore = cached == null ? 0 : cached.getOrDefault(ACCOUNT, 0L);
            if (cached != null) {
                cached.merge(game.id(), xp, Long::sum);
                cached.merge(ACCOUNT, xp, Long::sum);
            }
            if (cached == null) {
                continue;
            }
            gains.put(id, new Gain(xp, curve.levelOf(gameBefore), curve.levelOf(gameBefore + xp),
                    curve.levelOf(accountBefore), curve.levelOf(accountBefore + xp)));
        }
        if (stats != null && !account.isEmpty()) {
            stats.add(ACCOUNT, names, account);
        }
        return gains;
    }

    /** Tells the player what they earned and which modes it unlocked. */
    public void announce(Player player, GameType game, Gain gain) {
        long total = Math.max(0, xp(player.getUniqueId(), game.id()));
        long missing = curve.xpToNext(total);
        player.sendMessage(messages.get("progress.xp-line",
                Placeholder.unparsed("xp", String.valueOf(gain.xp())),
                Placeholder.component("game", game.displayName()),
                Placeholder.unparsed("level", String.valueOf(gain.gameTo())),
                Placeholder.unparsed("missing", String.valueOf(missing)),
                Placeholder.unparsed("next", missing == 0 ? "-" : String.valueOf(gain.gameTo() + 1))));
        if (gain.gameTo() > gain.gameFrom()) {
            player.sendMessage(messages.get("progress.level-up",
                    Placeholder.component("game", game.displayName()),
                    Placeholder.unparsed("from", String.valueOf(gain.gameFrom())),
                    Placeholder.unparsed("to", String.valueOf(gain.gameTo()))));
            for (ModeSpec mode : game.modes()) {
                int need = game.requiredLevel(mode);
                if (need > gain.gameFrom() && need <= gain.gameTo()) {
                    player.sendMessage(messages.get("progress.unlocked",
                            Placeholder.component("mode", MiniMessage.miniMessage().deserialize(mode.displayName()))));
                }
            }
            player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        }
        showBar(player);
        if (gain.accountTo() > gain.accountFrom()) {
            player.sendMessage(messages.get("progress.account-level-up",
                    Placeholder.unparsed("to", String.valueOf(gain.accountTo()))));
        }
    }
}
