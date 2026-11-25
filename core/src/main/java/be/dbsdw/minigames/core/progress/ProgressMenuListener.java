package be.dbsdw.minigames.core.progress;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.plugin.Plugin;

/** Clicks in the progress menu. Nothing can be taken out of or put into it. */
public final class ProgressMenuListener implements Listener {
    private final Plugin plugin;

    public ProgressMenuListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof ProgressMenu menu)) {
            return;
        }
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player player
                && event.getClickedInventory() == event.getView().getTopInventory()) {
            int slot = event.getSlot();
            // Opening another menu from inside the click event is unreliable: do it next tick.
            Bukkit.getScheduler().runTask(plugin, () -> menu.click(player, slot));
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof ProgressMenu) {
            event.setCancelled(true);
        }
    }
}
