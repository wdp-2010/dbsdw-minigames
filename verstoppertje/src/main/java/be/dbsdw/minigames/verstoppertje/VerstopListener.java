package be.dbsdw.minigames.verstoppertje;

import be.dbsdw.minigames.verstoppertje.match.VerstopMatch;
import io.papermc.paper.event.player.PlayerArmSwingEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import java.util.List;
import org.bukkit.block.BlockState;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Seekers hitting blocks, building within the limits (players are in creative), and no flying,
 * dropping, buckets or spawning things for anyone in a match.
 */
final class VerstopListener implements Listener {
    private final VerstopGame game;

    VerstopListener(VerstopGame game) {
        this.game = game;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAttack(PrePlayerAttackEntityEvent event) {
        game.matchOf(event.getPlayer()).ifPresent(m -> {
            m.attack(event.getPlayer(), event.getAttacked());
            event.setCancelled(true);
        });
    }

    // Paper 26.3 fires only PlayerInteractAtEntityEvent for entity clicks; this parent-class listener gets it.
    @EventHandler(priority = EventPriority.HIGH)
    public void onClickEntity(PlayerInteractEntityEvent event) {
        game.matchOf(event.getPlayer()).ifPresent(m -> {
            if (event.getRightClicked() instanceof Interaction && event.getHand() == EquipmentSlot.HAND) {
                m.attack(event.getPlayer(), event.getRightClicked());
            }
            event.setCancelled(true);
        });
    }

    @EventHandler
    public void onSwing(PlayerArmSwingEvent event) {
        if (event.getHand() == EquipmentSlot.HAND) {
            game.matchOf(event.getPlayer()).ifPresent(m -> m.swing(event.getPlayer()));
        }
    }

    /** Right click only places blocks: no doors, barrels, spawn eggs, buckets, food or bows. Left click breaks. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        game.matchOf(event.getPlayer()).ifPresent(m -> {
            Action a = event.getAction();
            if (a == Action.LEFT_CLICK_AIR || a == Action.LEFT_CLICK_BLOCK) {
                return;
            }
            event.setUseInteractedBlock(Event.Result.DENY);
            ItemStack item = event.getItem();
            boolean block = a == Action.RIGHT_CLICK_BLOCK && item != null && VerstopMatch.allowed(item.getType());
            event.setUseItemInHand(block ? Event.Result.ALLOW : Event.Result.DENY);
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        game.matchOf(event.getPlayer()).ifPresent(m -> {
            List<BlockState> replaced = event instanceof BlockMultiPlaceEvent multi
                    ? multi.getReplacedBlockStates() : List.of(event.getBlockReplacedState());
            if (!m.place(event.getPlayer(), event.getBlockPlaced(), replaced)) {
                event.setCancelled(true);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        game.matchOf(event.getPlayer()).ifPresent(m -> {
            if (m.dig(event.getPlayer(), event.getBlock())) {
                event.setDropItems(false);
                event.setExpToDrop(0);
            } else {
                event.setCancelled(true);
            }
        });
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
