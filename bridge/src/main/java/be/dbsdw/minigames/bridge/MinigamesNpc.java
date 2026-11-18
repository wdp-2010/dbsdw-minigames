package be.dbsdw.minigames.bridge;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;

/** The minigames NPC at survival spawn: a vanilla Mannequin that sends whoever right-clicks it. */
final class MinigamesNpc implements Listener {
    private static final NamespacedKey TAG = new NamespacedKey("dbsdw", "minigames_npc");
    private static final String NAME = "<gradient:#ff4d4d:#4d79ff><bold>Minigames</bold></gradient>";
    private static final double REMOVE_RADIUS = 5;

    private final BridgeTravel travel;

    MinigamesNpc(BridgeTravel travel) {
        this.travel = travel;
    }

    /** Spawns the NPC where the admin stands. Runs on the admin's thread, so it lands in their Folia region. */
    void spawn(Player admin) {
        admin.getWorld().spawn(admin.getLocation(), Mannequin.class, npc -> {
            npc.customName(MiniMessage.miniMessage().deserialize(NAME));
            npc.setCustomNameVisible(true);
            npc.setInvulnerable(true);
            npc.setGravity(false);
            npc.setImmovable(true);
            npc.setSilent(true);
            npc.setPersistent(true);
            npc.getPersistentDataContainer().set(TAG, PersistentDataType.BYTE, (byte) 1);
        });
    }

    /** Removes minigames NPCs within 5 blocks of the admin and returns how many. */
    int removeNear(Player admin) {
        int removed = 0;
        for (Entity entity : admin.getNearbyEntities(REMOVE_RADIUS, REMOVE_RADIUS, REMOVE_RADIUS)) {
            if (isNpc(entity)) {
                entity.remove();
                removed++;
            }
        }
        return removed;
    }

    // ignoreCancelled: respect AuthMe and region protection that already cancelled the click.
    // Login itself is checked again in BridgeTravel.sendToMinigames for every entry point.
    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !isNpc(event.getRightClicked())) {
            return;
        }
        event.setCancelled(true);
        travel.sendToMinigames(event.getPlayer());
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (isNpc(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    private static boolean isNpc(Entity entity) {
        return entity instanceof Mannequin && entity.getPersistentDataContainer().has(TAG, PersistentDataType.BYTE);
    }
}
