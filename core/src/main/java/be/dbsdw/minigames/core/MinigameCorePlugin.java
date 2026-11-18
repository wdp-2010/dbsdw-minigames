package be.dbsdw.minigames.core;

import org.bukkit.plugin.java.JavaPlugin;

public final class MinigameCorePlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        getLogger().info("MinigameCore " + getPluginMeta().getVersion() + " enabled");
    }
}
