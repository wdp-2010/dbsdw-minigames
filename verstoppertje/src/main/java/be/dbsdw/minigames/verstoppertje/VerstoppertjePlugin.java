package be.dbsdw.minigames.verstoppertje;

import be.dbsdw.minigames.core.Messages;
import be.dbsdw.minigames.core.api.GameRegistry;
import be.dbsdw.minigames.verstoppertje.rules.Settings;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class VerstoppertjePlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        saveDefaultConfig();
        // Write keys added by a newer version into an existing config.yml, so they can be edited.
        getConfig().options().copyDefaults(true);
        saveConfig();
        Settings settings;
        List<Material> disguises = new ArrayList<>();
        try {
            FileConfiguration c = getConfig();
            for (String name : c.getStringList("disguises")) {
                Material m = Material.matchMaterial(name.trim());
                if (m == null || !m.isBlock() || !m.isItem()) {
                    throw new IllegalArgumentException("disguises: " + name + " is not a block");
                }
                if (!disguises.contains(m)) {
                    disguises.add(m);
                }
            }
            if (disguises.isEmpty()) {
                throw new IllegalArgumentException("disguises is empty");
            }
            settings = new Settings(c.getInt("match.max-per-team"), c.getInt("match.players-per-seeker"),
                    seconds(c, "match.hide-seconds"), Math.round(c.getDouble("match.seek-minutes") * 60 * 20),
                    seconds(c, "match.solid-seconds"), seconds(c, "match.hint-seconds"), c.getDouble("match.reach"),
                    c.getInt("match.place-limit"), c.getInt("match.break-limit"));
        } catch (IllegalArgumentException e) {
            getLogger().severe("[verstoppertje] config.yml: " + e.getMessage() + " - Verstoppertje is disabled");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        GameRegistry registry = getServer().getServicesManager().load(GameRegistry.class);
        if (registry == null) {
            throw new IllegalStateException("MinigameCore's GameRegistry is not available");
        }
        VerstopGame game = new VerstopGame(this, settings, disguises, Messages.load(this));
        registry.register(this, game);
        getServer().getPluginManager().registerEvents(new VerstopListener(game), this);
        getLogger().info("[verstoppertje] enabled: " + disguises.size() + " disguises, hide "
                + settings.hideTicks() / 20 + "s, seek " + settings.seekTicks() / 20 + "s");
    }

    private static long seconds(FileConfiguration c, String key) {
        return Math.round(c.getDouble(key) * 20);
    }
}
