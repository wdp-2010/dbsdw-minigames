package be.dbsdw.minigames.paintball;

import be.dbsdw.minigames.paintball.match.PaintballMatch;
import be.dbsdw.minigames.paintball.match.PracticeRange;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Gun trigger, scope zoom, paintball hits, practice dummies, and keeping the gun in the hotbar. */
final class PaintballListener implements Listener {
    private final PaintballGame game;
    /** The lobby practice range, or null when it is off. */
    private final PracticeRange range;

    PaintballListener(PaintballGame game, PracticeRange range) {
        this.game = game;
        this.range = range;
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
        } else if (range != null) {
            range.trigger(event.getPlayer());
        }
    }

    /** The mouse wheel while aiming through the scope zooms instead of switching slots. */
    @EventHandler(ignoreCancelled = true)
    public void onScroll(PlayerItemHeldEvent event) {
        PaintballMatch match = game.players().get(event.getPlayer().getUniqueId());
        boolean zoomed = match != null
                ? match.scroll(event.getPlayer(), event.getPreviousSlot(), event.getNewSlot())
                : range != null && range.scroll(event.getPlayer(), event.getPreviousSlot(), event.getNewSlot());
        if (zoomed) {
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
        if (match != null) {
            match.onBallHit(event);
        } else if (range == null || !range.onBallHit(event)) {
            event.setCancelled(true);
            ball.remove();
            return;
        }
        if (!event.isCancelled()) {
            pop(event);
        }
    }

    /**
     * Ends a paintball that hit something ourselves. Left to vanilla, the snowball breaks into item
     * particles of its pack model, which players saw as missing-texture squares. A cancelled hit
     * sends no break particles, so the ball is removed here and a small puff of paint in its colour
     * takes their place.
     */
    private static void pop(ProjectileHitEvent event) {
        Projectile ball = event.getEntity();
        event.setCancelled(true);
        Color colour = ball instanceof Snowball snowball ? Gun.ballColour(snowball.getItem()) : null;
        if (colour != null) {
            ball.getWorld().spawnParticle(Particle.DUST, ball.getLocation(), 8, 0.1, 0.1, 0.1, 0,
                    new Particle.DustOptions(colour, 1.0f));
        }
        ball.remove();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (range != null) {
            range.quit(event.getPlayer());
        }
    }

    /** Practice dummies can't be broken, robbed or dressed. */
    @EventHandler
    public void onDummyDamage(EntityDamageEvent event) {
        if (Gun.isDummy(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDummyManipulate(PlayerArmorStandManipulateEvent event) {
        if (Gun.isDummy(event.getRightClicked())) {
            event.setCancelled(true);
        }
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
        if (event.getView().getTopInventory().getHolder(false) instanceof BotMenu menu) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player
                    && event.getClickedInventory() == event.getView().getTopInventory()) {
                menu.click(player, event.getSlot());
            }
            return;
        }
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
        if (event.getView().getTopInventory().getHolder(false) instanceof BotMenu) {
            event.setCancelled(true);
            return;
        }
        if (Gun.isGun(event.getOldCursor())) {
            event.setCancelled(true);
        }
    }
}
