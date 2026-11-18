package be.dbsdw.minigames.bridge;

import org.bukkit.plugin.java.JavaPlugin;

public final class BridgePlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        getLogger().info("DbsdwBridge " + getPluginMeta().getVersion() + " enabled");
    }
}
