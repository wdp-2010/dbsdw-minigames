package be.dbsdw.minigames.paintball;

import be.dbsdw.minigames.core.Messages;
import be.dbsdw.minigames.core.api.GameRegistry;
import be.dbsdw.minigames.paintball.rules.ModeRules;
import be.dbsdw.minigames.paintball.rules.PowerupSettings;
import be.dbsdw.minigames.paintball.rules.PowerupType;
import be.dbsdw.minigames.paintball.rules.Settings;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class PaintballPlugin extends JavaPlugin {
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
        getServer().getPluginManager().registerEvents(new PaintballListener(game), this);
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
                c.getInt(key + "." + winKey), Math.round(c.getDouble(key + ".time-limit-minutes") * 60 * 20));
    }

    private static long seconds(FileConfiguration c, String key) {
        return Math.round(c.getDouble(key) * 20);
    }

    private static String describe(ModeRules r) {
        return r.minPerTeam() + "-" + r.maxPerTeam() + " per team, first to " + r.winScore() + ", "
                + r.timeLimitTicks() / 20 / 60.0 + " min";
    }
}
