package be.dbsdw.minigames.paintball;

import be.dbsdw.minigames.core.Messages;
import be.dbsdw.minigames.core.api.GameRegistry;
import be.dbsdw.minigames.paintball.match.PracticeRange;
import be.dbsdw.minigames.paintball.rules.ModeRules;
import be.dbsdw.minigames.paintball.rules.PowerupSettings;
import be.dbsdw.minigames.paintball.rules.PowerupType;
import be.dbsdw.minigames.paintball.rules.RangeBox;
import be.dbsdw.minigames.paintball.rules.Settings;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class PaintballPlugin extends JavaPlugin {
    private PracticeRange range;

    @Override
    public void onDisable() {
        if (range != null) {
            range.shutdown();
        }
    }

    /** The lobby practice range from config, or null when it is off. */
    private static PracticeRange practice(FileConfiguration c, PaintballGame game) {
        if (!c.getBoolean("practice.enabled")) {
            return null;
        }
        int[] a = block(c, "practice.corner-1");
        int[] b = block(c, "practice.corner-2");
        List<PracticeRange.Spot> spots = new ArrayList<>();
        for (List<?> t : c.getList("practice.targets", List.of()).stream().map(o -> (List<?>) o).toList()) {
            if (t.size() != 4) {
                throw new IllegalArgumentException("targets: every target needs x, y, z and yaw");
            }
            spots.add(new PracticeRange.Spot(num(t.get(0)), num(t.get(1)), num(t.get(2)), (float) num(t.get(3))));
        }
        return new PracticeRange(game, c.getString("practice.world", "world"),
                RangeBox.of(a[0], a[1], a[2], b[0], b[1], b[2]), spots,
                c.getInt("practice.target-hits"), seconds(c, "practice.target-down-seconds"));
    }

    private static int[] block(FileConfiguration c, String key) {
        List<Integer> v = c.getIntegerList(key);
        if (v.size() != 3) {
            throw new IllegalArgumentException(key.substring(key.indexOf('.') + 1) + " needs x, y and z");
        }
        return new int[] {v.get(0), v.get(1), v.get(2)};
    }

    private static double num(Object o) {
        if (!(o instanceof Number n)) {
            throw new IllegalArgumentException("targets: " + o + " is not a number");
        }
        return n.doubleValue();
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // Write keys added by a newer version into an existing config.yml, so they can be edited.
        getConfig().options().copyDefaults(true);
        saveConfig();
        FileConfiguration c = getConfig();
        Settings settings;
        try {
            settings = load(c);
        } catch (IllegalArgumentException e) {
            getLogger().severe("[paintball] config.yml: " + e.getMessage() + " - Paintball is disabled");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        GameRegistry registry = getServer().getServicesManager().load(GameRegistry.class);
        if (registry == null) {
            throw new IllegalStateException("MinigameCore's GameRegistry is not available");
        }
        Messages messages = Messages.load(this);
        PaintballGame game = new PaintballGame(this, settings, messages, messages.raw("mode.tdm"), messages.raw("mode.ctf"));
        registry.register(this, game);
        try {
            range = practice(c, game);
        } catch (IllegalArgumentException e) {
            getLogger().severe("[paintball] config.yml practice: " + e.getMessage() + " - no practice range");
        }
        if (range != null) {
            getServer().getScheduler().runTaskTimer(this, range::tick, 1, 1);
            getLogger().info("[paintball] practice range: " + c.getList("practice.targets", List.of()).size()
                    + " targets in world " + c.getString("practice.world"));
        }
        getServer().getPluginManager().registerEvents(new PaintballListener(game, range), this);
        getLogger().info("[paintball] enabled: tdm " + describe(settings.tdm()) + "; ctf " + describe(settings.ctf())
                + "; power-ups " + settings.powerups().enabled().size());
    }

    private static Settings load(FileConfiguration c) {
        List<PowerupType> enabled = new ArrayList<>();
        for (String name : c.getStringList("powerups.enabled")) {
            try {
                enabled.add(PowerupType.valueOf(name.trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("powerups.enabled: unknown power-up " + name);
            }
        }
        return new Settings(
                mode(c, "tdm", "win-eliminations"),
                mode(c, "ctf", "win-captures"),
                c.getInt("player.hits-to-out"),
                seconds(c, "player.respawn-seconds"),
                seconds(c, "player.spawn-protection-seconds"),
                c.getInt("gun.shot-interval-ticks"),
                c.getDouble("gun.speed"),
                c.getDouble("gun.spread-degrees"),
                c.getLong("gun.ball-lifetime-ticks"),
                seconds(c, "ctf.flag-return-seconds"),
                new PowerupSettings(enabled, seconds(c, "powerups.respawn-seconds"),
                        seconds(c, "powerups.rapid-fire-seconds"), seconds(c, "powerups.triple-shot-seconds"),
                        seconds(c, "powerups.speed-seconds"), c.getDouble("powerups.triple-shot-fan-degrees"),
                        c.getInt("powerups.max-active"), c.getDouble("powerups.catch-up")),
                seconds(c, "splats.lifetime-seconds"),
                c.getInt("splats.max-per-arena"),
                c.getDouble("gun.gravity"),
                c.getDouble("gun.scope-spread-degrees"));
    }

    private static ModeRules mode(FileConfiguration c, String key, String winKey) {
        return new ModeRules(key, c.getInt(key + ".min-per-team"), c.getInt(key + ".max-per-team"),
                c.getInt(key + "." + winKey), c.getInt(key + "." + winKey + "-per-player", 0), Math.round(c.getDouble(key + ".time-limit-minutes") * 60 * 20));
    }

    private static long seconds(FileConfiguration c, String key) {
        return Math.round(c.getDouble(key) * 20);
    }

    private static String describe(ModeRules r) {
        return r.minPerTeam() + "-" + r.maxPerTeam() + " per team, first to " + r.winScore()
                + (r.winScorePerPlayer() > 0 ? " (" + r.winScorePerPlayer() + " per player on the bigger team)" : "") + ", "
                + r.timeLimitTicks() / 20 / 60.0 + " min";
    }
}
