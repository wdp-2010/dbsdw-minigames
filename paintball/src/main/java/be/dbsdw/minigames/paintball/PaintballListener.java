package be.dbsdw.minigames.paintball;

import be.dbsdw.minigames.paintball.match.PaintballMatch;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Gun trigger, scope zoom, paintball hits, and keeping the gun in the hotbar. */
final class PaintballListener implements Listener {
    private final PaintballGame game;

    PaintballListener(PaintballGame game) {
        this.game = game;
    }

    // No ignoreCancelled: Paper fires right-clicks in the air already marked cancelled.
    @EventHandler
    public void onTrigger(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)
                || !Gun.isGun(event.getItem())) {
            return;
        }
        event.setCancelled(true);
        PaintballMatch match = game.players().get(event.getPlayer().getUniqueId());
        if (match != null) {
            match.trigger(event.getPlayer());
        }
    }

    /** The mouse wheel while aiming through the scope zooms instead of switching slots. */
    @EventHandler(ignoreCancelled = true)
    public void onScroll(PlayerItemHeldEvent event) {
        PaintballMatch match = game.players().get(event.getPlayer().getUniqueId());
        if (match != null && match.scroll(event.getPlayer(), event.getPreviousSlot(), event.getNewSlot())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onHit(ProjectileHitEvent event) {
        Projectile ball = event.getEntity();
        if (!Gun.isBall(ball)) {
            return;
        }
        PaintballMatch match = game.balls().get(ball.getUniqueId());
        if (match == null) {
            event.setCancelled(true);
            ball.remove();
            return;
        }
        match.onBallHit(event);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (Gun.isGun(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (Gun.isGun(event.getMainHandItem()) || Gun.isGun(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        boolean gun = Gun.isGun(event.getCurrentItem()) || Gun.isGun(event.getCursor());
        if (!gun && event.getWhoClicked() instanceof Player player) {
            if (event.getClick() == ClickType.NUMBER_KEY) {
                gun = Gun.isGun(player.getInventory().getItem(event.getHotbarButton()));
            } else if (event.getClick() == ClickType.SWAP_OFFHAND) {
                gun = Gun.isGun(player.getInventory().getItemInOffHand());
            }
        }
        if (gun) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (Gun.isGun(event.getOldCursor())) {
            event.setCancelled(true);
        }
    }
}
