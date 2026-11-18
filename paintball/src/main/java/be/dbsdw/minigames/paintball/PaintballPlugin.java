package be.dbsdw.minigames.paintball;

import org.bukkit.plugin.java.JavaPlugin;

public final class PaintballPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        getLogger().info("Paintball " + getPluginMeta().getVersion() + " enabled");
    }
}
