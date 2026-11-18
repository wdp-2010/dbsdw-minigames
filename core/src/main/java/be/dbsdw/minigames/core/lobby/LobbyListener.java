package be.dbsdw.minigames.core.lobby;

import be.dbsdw.minigames.core.Messages;
import be.dbsdw.minigames.core.travel.CoreTravel;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Lobby on join, lobby item clicks, and the protections that hold everywhere on this server. */
public final class LobbyListener implements Listener {
    private final LobbyService lobby;
    private final CoreTravel travel;
    private final Messages messages;
    private final Consumer<Player> openGames;
    private final Consumer<Player> openStats;
    private final Consumer<Player> onQuit;
    private final Logger log;

    /** {@code travel} is null when the ticket secret is invalid. */
    public LobbyListener(LobbyService lobby, CoreTravel travel, Messages messages, Consumer<Player> openGames,
                         Consumer<Player> openStats,
                         Consumer<Player> onQuit, Logger log) {
        this.lobby = lobby;
        this.travel = travel;
        this.messages = messages;
        this.openGames = openGames;
        this.openStats = openStats;
        this.onQuit = onQuit;
        this.log = log;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        lobby.send(event.getPlayer());
        log.info("[lobby] " + event.getPlayer().getName() + " in lobby");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        onQuit.accept(event.getPlayer());
    }

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        Optional<LobbyService.LobbyItem> item = lobby.lobbyItem(event.getItem());
        if (item.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        switch (item.get()) {
            case GAMES -> openGames.accept(player);
            case STATS -> openStats.accept(player);
            case SURVIVAL -> {
                if (travel == null) {
                    player.sendMessage(messages.get("unavailable"));
                } else {
                    travel.sendToSurvival(player);
                }
            }
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onHunger(FoodLevelChangeEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (lobby.isLobbyItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (lobby.isLobbyItem(event.getMainHandItem()) || lobby.isLobbyItem(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        boolean touchesLobbyItem = lobby.isLobbyItem(event.getCurrentItem()) || lobby.isLobbyItem(event.getCursor());
        if (!touchesLobbyItem && event.getWhoClicked() instanceof Player player) {
            if (event.getClick() == ClickType.NUMBER_KEY) {
                touchesLobbyItem = lobby.isLobbyItem(player.getInventory().getItem(event.getHotbarButton()));
            } else if (event.getClick() == ClickType.SWAP_OFFHAND) {
                touchesLobbyItem = lobby.isLobbyItem(player.getInventory().getItemInOffHand());
            }
        }
        if (touchesLobbyItem) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (lobby.isLobbyItem(event.getOldCursor())) {
            event.setCancelled(true);
        }
    }
}
