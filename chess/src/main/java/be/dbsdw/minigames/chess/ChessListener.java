package be.dbsdw.minigames.chess;

import be.dbsdw.minigames.chess.match.ChessMatch;
import be.dbsdw.minigames.chess.match.Hotbar;
import be.dbsdw.minigames.chess.menu.GamesMenu;
import be.dbsdw.minigames.chess.menu.PromotionMenu;
import io.papermc.paper.event.player.PlayerArmSwingEvent;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;

/** Clicks on the board, the chess hotbar and menus, and players coming back to an away game. */
final class ChessListener implements Listener {
    private final ChessGame game;

    ChessListener(ChessGame game) {
        this.game = game;
    }

    /** The match this player is on the board of right now (not while away). */
    private Optional<ChessMatch> playing(Player player) {
        return game.matchOf(player.getUniqueId()).filter(m -> !m.isAway(player.getUniqueId()));
    }

    // Clicks in the air arrive already cancelled, so cancelled events are handled too.
    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Optional<ChessMatch> match = playing(player);
        if (match.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() == Action.PHYSICAL) {
            return;
        }
        boolean right = event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK;
        Optional<Hotbar.Action> action = Hotbar.action(event.getItem());
        if (right && action.isPresent()) {
            match.get().action(player, action.get());
        } else {
            match.get().click(player);
        }
    }

    /**
     * A left click is an arm swing, sent whatever the player looks at. The server reports no "click in
     * the air" while a block is within reach (the stand's floor or railing when looking down), so the
     * swing is what makes clicking the board reliable. A swing that comes with a block click is one
     * click: the match ignores clicks a few ticks apart.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onSwing(PlayerArmSwingEvent event) {
        if (event.getHand() == EquipmentSlot.HAND) {
            playing(event.getPlayer()).ifPresent(m -> m.click(event.getPlayer()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // After MinigameCore has put them in the lobby.
        Bukkit.getScheduler().runTaskLater(game.plugin(), () -> {
            if (player.isOnline()) {
                game.joined(player);
            }
        }, 5);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder(false);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        boolean top = event.getClickedInventory() == event.getView().getTopInventory();
        if (holder instanceof PromotionMenu menu) {
            event.setCancelled(true);
            if (top) {
                menu.typeAt(event.getSlot()).ifPresent(type -> Bukkit.getScheduler().runTask(game.plugin(), () -> menu.pick(type)));
            }
        } else if (holder instanceof GamesMenu menu) {
            event.setCancelled(true);
            if (top) {
                boolean shift = event.isShiftClick();
                int slot = event.getSlot();
                Bukkit.getScheduler().runTask(game.plugin(), () -> menu.click(player, slot, shift));
            }
        } else if (playing(player).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder(false);
        if (holder instanceof PromotionMenu || holder instanceof GamesMenu
                || (event.getWhoClicked() instanceof Player p && playing(p).isPresent())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (playing(event.getPlayer()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (playing(event.getPlayer()).isPresent()) {
            event.setCancelled(true);
        }
    }
}
