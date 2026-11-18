package be.dbsdw.minigames.paintball;

import be.dbsdw.minigames.core.Messages;
import be.dbsdw.minigames.core.api.GameRegistry;
import be.dbsdw.minigames.paintball.rules.Settings;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class PaintballPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        saveDefaultConfig();
        FileConfiguration c = getConfig();
        Settings settings;
        try {
            settings = new Settings(
                    c.getInt("tdm.min-per-team"),
                    c.getInt("tdm.max-per-team"),
                    c.getInt("tdm.win-eliminations"),
                    Math.round(c.getDouble("tdm.time-limit-minutes") * 60 * 20),
                    c.getInt("player.hits-to-out"),
                    Math.round(c.getDouble("player.respawn-seconds") * 20),
                    Math.round(c.getDouble("player.spawn-protection-seconds") * 20),
                    c.getInt("gun.shot-interval-ticks"),
                    c.getDouble("gun.speed"),
                    c.getDouble("gun.spread-degrees"),
                    c.getLong("gun.ball-lifetime-ticks"));
        } catch (IllegalArgumentException e) {
            getLogger().severe("[paintball] config.yml: " + e.getMessage() + " - Paintball is disabled");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        GameRegistry registry = getServer().getServicesManager().load(GameRegistry.class);
        if (registry == null) {
            throw new IllegalStateException("MinigameCore's GameRegistry is not available");
        }
        PaintballGame game = new PaintballGame(this, settings, Messages.load(this),
                c.getString("tdm.display-name", "Team Deathmatch"));
        registry.register(this, game);
        getServer().getPluginManager().registerEvents(new PaintballListener(game), this);
        getLogger().info("[paintball] enabled: tdm " + settings.minPerTeam() + "-" + settings.maxPerTeam()
                + " per team, first to " + settings.winEliminations() + ", "
                + settings.timeLimitTicks() / 20 / 60.0 + " min");
    }
}
