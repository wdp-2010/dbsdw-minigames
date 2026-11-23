package be.dbsdw.minigames.valvloer;

import be.dbsdw.minigames.core.Messages;
import be.dbsdw.minigames.core.api.GameRegistry;
import be.dbsdw.minigames.valvloer.match.Restorer;
import be.dbsdw.minigames.valvloer.rules.Settings;
import java.util.EnumSet;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class ValvloerPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        saveDefaultConfig();
        // Write keys added by a newer version into an existing config.yml, so they can be edited.
        getConfig().options().copyDefaults(true);
        saveConfig();
        Restorer restorer = new Restorer(getDataFolder().toPath().resolve("restore"), getLogger());
        restorer.restoreLeftovers();
        Settings settings;
        Set<Material> floor = EnumSet.noneOf(Material.class);
        try {
            FileConfiguration c = getConfig();
            for (String name : c.getStringList("floor.blocks")) {
                Material m = Material.matchMaterial(name.trim());
                if (m == null || !m.isBlock()) {
                    throw new IllegalArgumentException("floor.blocks: " + name + " is not a block");
                }
                floor.add(m);
            }
            settings = new Settings(c.getInt("match.max-per-team"), seconds(c, "match.grace-seconds"),
                    Math.round(c.getDouble("match.time-limit-minutes") * 60 * 20),
                    floor.stream().map(m -> m.getKey().toString()).collect(java.util.stream.Collectors.toSet()),
                    c.getLong("floor.drop-delay-ticks"), seconds(c, "floor.camp-seconds"));
        } catch (IllegalArgumentException e) {
            getLogger().severe("[valvloer] config.yml: " + e.getMessage() + " - Valvloer is disabled");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        GameRegistry registry = getServer().getServicesManager().load(GameRegistry.class);
        if (registry == null) {
            throw new IllegalStateException("MinigameCore's GameRegistry is not available");
        }
        registry.register(this, new ValvloerGame(settings, floor, Messages.load(this), restorer, getLogger()));
        getLogger().info("[valvloer] enabled: up to " + settings.maxPerTeam() + " per team, " + floor.size()
                + " floor block types, drop after " + settings.dropDelayTicks() + " ticks");
    }

    private static long seconds(FileConfiguration c, String key) {
        return Math.round(c.getDouble(key) * 20);
    }
}
