package be.dbsdw.minigames.verstoppertje;

import io.papermc.paper.event.player.PlayerArmSwingEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.entity.Interaction;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Seekers hitting blocks, hiders picking their block, and no flying for anyone in a match. */
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
        if (event.getRightClicked() instanceof Interaction && event.getHand() == EquipmentSlot.HAND) {
            game.matchOf(event.getPlayer()).ifPresent(m -> {
                m.attack(event.getPlayer(), event.getRightClicked());
                event.setCancelled(true);
            });
        }
    }

    @EventHandler
    public void onSwing(PlayerArmSwingEvent event) {
        if (event.getHand() == EquipmentSlot.HAND) {
            game.matchOf(event.getPlayer()).ifPresent(m -> m.swing(event.getPlayer()));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        game.matchOf(event.getPlayer()).ifPresent(m -> {
            Action a = event.getAction();
            if ((a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK) && m.isHider(event.getPlayer())) {
                m.pick(event.getPlayer(), event.getItem());
            }
            event.setCancelled(true);
        });
    }

    @EventHandler
    public void onFly(PlayerToggleFlightEvent event) {
        if (event.isFlying() && game.matchOf(event.getPlayer()).isPresent()) {
            event.setCancelled(true);
        }
    }
}
