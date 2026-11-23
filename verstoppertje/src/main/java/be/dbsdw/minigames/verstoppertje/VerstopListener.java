package be.dbsdw.minigames.verstoppertje;

import io.papermc.paper.event.player.PlayerArmSwingEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Seekers hitting blocks, placing and breaking the arena's blocks within the limits, and no flying,
 * dropping, buckets or spawning things for anyone in a match.
 */
final class VerstopListener implements Listener {
    private final VerstopGame game;
    /** Server tick of each player's last right click or entity hit: the swing that follows is not a dig. */
    private final Map<UUID, Integer> lastUse = new HashMap<>();

    VerstopListener(VerstopGame game) {
        this.game = game;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAttack(PrePlayerAttackEntityEvent event) {
        game.matchOf(event.getPlayer()).ifPresent(m -> {
            lastUse.put(event.getPlayer().getUniqueId(), Bukkit.getCurrentTick());
            m.attack(event.getPlayer(), event.getAttacked());
            event.setCancelled(true);
        });
    }

    // Paper 26.3 fires only PlayerInteractAtEntityEvent for entity clicks; this parent-class listener gets it.
    @EventHandler(priority = EventPriority.HIGH)
    public void onClickEntity(PlayerInteractEntityEvent event) {
        game.matchOf(event.getPlayer()).ifPresent(m -> {
            lastUse.put(event.getPlayer().getUniqueId(), Bukkit.getCurrentTick());
            if (event.getRightClicked() instanceof Interaction && event.getHand() == EquipmentSlot.HAND) {
                m.attack(event.getPlayer(), event.getRightClicked());
            }
            event.setCancelled(true);
        });
    }

    /**
     * A swing finds a hider (seekers) or else breaks the block looked at. Adventure clients send no
     * "start breaking" packet, only the swing; a swing in the same tick as a right click belongs to
     * that click and breaks nothing.
     */
    @EventHandler
    public void onSwing(PlayerArmSwingEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player p = event.getPlayer();
        game.matchOf(p).ifPresent(m -> {
            if (!m.swing(p) && lastUse.getOrDefault(p.getUniqueId(), -1) != Bukkit.getCurrentTick()) {
                m.swingDig(p);
            }
        });
    }

    /** Right click only places the arena's blocks: no doors, barrels, food or other items. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        Action a = event.getAction();
        if (a == Action.LEFT_CLICK_AIR || a == Action.LEFT_CLICK_BLOCK) {
            return;
        }
        Player p = event.getPlayer();
        game.matchOf(p).ifPresent(m -> {
            event.setCancelled(true);
            if (a == Action.PHYSICAL) {
                return;
            }
            lastUse.put(p.getUniqueId(), Bukkit.getCurrentTick());
            if (a == Action.RIGHT_CLICK_BLOCK && event.getHand() == EquipmentSlot.HAND && event.getClickedBlock() != null) {
                m.place(p, event.getClickedBlock(), event.getBlockFace(), event.getItem());
            }
        });
    }

    /** The plugin places and breaks; vanilla never does for players in a match. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (game.matchOf(event.getPlayer()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (game.matchOf(event.getPlayer()).isPresent()) {
            event.setCancelled(true);
        }
    }

    /** Blocks stay in the hotbar and inventory: not into armour slots or the off hand. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player p && game.matchOf(p).isPresent()
                && (event.getSlotType() == InventoryType.SlotType.ARMOR || event.getClick() == ClickType.SWAP_OFFHAND
                || event.getSlot() == 40)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (game.matchOf(event.getPlayer()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastUse.remove(event.getPlayer().getUniqueId());
    }

    /** Water and lava stay put, so a broken wall does not flood the arena. */
    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (game.inAnyArena(event.getToBlock())) {
            event.setCancelled(true);
        }
    }

    /** Sand and gravel stay put when the block under them is broken. */
    @EventHandler(ignoreCancelled = true)
    public void onFall(EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof FallingBlock && game.inAnyArena(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (game.matchOf(event.getPlayer()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEmpty(PlayerBucketEmptyEvent event) {
        if (game.matchOf(event.getPlayer()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFill(PlayerBucketFillEvent event) {
        if (game.matchOf(event.getPlayer()).isPresent()) {
            event.setCancelled(true);
        }
    }

    /** Armor stands, boats, minecarts. */
    @EventHandler(ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        Player p = event.getPlayer();
        if (p != null && game.matchOf(p).isPresent()) {
            event.setCancelled(true);
        }
    }

    /** Paintings and item frames. */
    @EventHandler(ignoreCancelled = true)
    public void onHang(HangingPlaceEvent event) {
        Player p = event.getPlayer();
        if (p != null && game.matchOf(p).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onFly(PlayerToggleFlightEvent event) {
        if (event.isFlying() && game.matchOf(event.getPlayer()).isPresent()) {
            event.setCancelled(true);
        }
    }
}
