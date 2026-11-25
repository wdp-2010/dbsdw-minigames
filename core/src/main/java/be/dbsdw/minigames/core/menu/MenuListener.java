package be.dbsdw.minigames.core.menu;

import be.dbsdw.minigames.core.Messages;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;

/** Clicks in the queue menus. Nothing can be taken out of or put into them. */
public final class MenuListener implements Listener {
    private final Plugin plugin;
    private final MenuModel model;
    private final Messages messages;

    public MenuListener(Plugin plugin, MenuModel model, Messages messages) {
        this.plugin = plugin;
        this.model = model;
        this.messages = messages;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder(false);
        if (!(holder instanceof GamesMenu) && !(holder instanceof ModesMenu)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        if (holder instanceof GamesMenu games) {
            if (games.isProgress(event.getSlot())) {
                Bukkit.getScheduler().runTask(plugin, () -> model.openProgress(player));
                return;
            }
            Optional<String> gameId = games.gameAt(event.getSlot());
            gameId.flatMap(id -> model.games().stream().filter(g -> g.id().equals(id)).findFirst())
                    .ifPresent(game -> Bukkit.getScheduler().runTask(plugin,
                            () -> ModesMenu.open(player, game, model, messages)));
        } else if (holder instanceof ModesMenu modes) {
            modes.modeAt(event.getSlot()).ifPresent(key -> {
                model.toggle(player, key);
                refreshOpen();
            });
            modes.entryAt(event.getSlot()).ifPresent(entry -> Bukkit.getScheduler().runTask(plugin, () -> {
                player.closeInventory();
                entry.onClick().accept(player);
            }));
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder(false);
        if (holder instanceof GamesMenu || holder instanceof ModesMenu) {
            event.setCancelled(true);
        }
    }

    /** Redraws every open modes menu (queue sizes, countdowns). */
    public void refreshOpen() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof ModesMenu menu) {
                menu.render(player);
            }
        }
    }
}
