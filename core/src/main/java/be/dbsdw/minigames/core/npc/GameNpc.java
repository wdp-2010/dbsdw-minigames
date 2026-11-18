package be.dbsdw.minigames.core.npc;

import be.dbsdw.minigames.core.api.GameType;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
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

/** A lobby NPC per game: a vanilla Mannequin that opens that game's queue menu. */
public final class GameNpc implements Listener {
    private static final NamespacedKey TAG = new NamespacedKey("dbsdw", "game_npc");
    private static final double REMOVE_RADIUS = 5;

    private final Function<String, Optional<GameType>> games;
    private final BiConsumer<Player, String> openModes;

    public GameNpc(Function<String, Optional<GameType>> games, BiConsumer<Player, String> openModes) {
        this.games = games;
        this.openModes = openModes;
    }

    /** Spawns the game's NPC where the admin stands. False if the game is unknown. */
    public boolean spawn(Player admin, String gameId) {
        Optional<GameType> game = games.apply(gameId);
        if (game.isEmpty()) {
            return false;
        }
        admin.getWorld().spawn(admin.getLocation(), Mannequin.class, npc -> {
            npc.customName(game.get().displayName());
            npc.setCustomNameVisible(true);
            npc.setInvulnerable(true);
            npc.setGravity(false);
            npc.setImmovable(true);
            npc.setSilent(true);
            npc.setPersistent(true);
            npc.getPersistentDataContainer().set(TAG, PersistentDataType.STRING, gameId);
        });
        return true;
    }

    /** Removes game NPCs within 5 blocks of the admin and returns how many. */
    public int removeNear(Player admin) {
        int removed = 0;
        for (Entity entity : admin.getNearbyEntities(REMOVE_RADIUS, REMOVE_RADIUS, REMOVE_RADIUS)) {
            if (gameOf(entity).isPresent()) {
                entity.remove();
                removed++;
            }
        }
        return removed;
    }

    // Paper 26.3 fires only PlayerInteractAtEntityEvent for entity clicks; this parent-class listener gets it.
    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        gameOf(event.getRightClicked()).ifPresent(gameId -> {
            event.setCancelled(true);
            openModes.accept(event.getPlayer(), gameId);
        });
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (gameOf(event.getEntity()).isPresent()) {
            event.setCancelled(true);
        }
    }

    private static Optional<String> gameOf(Entity entity) {
        if (!(entity instanceof Mannequin)) {
            return Optional.empty();
        }
        return Optional.ofNullable(entity.getPersistentDataContainer().get(TAG, PersistentDataType.STRING));
    }
}
