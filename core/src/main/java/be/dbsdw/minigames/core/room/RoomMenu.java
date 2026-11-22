package be.dbsdw.minigames.core.room;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * A chest menu of the rooms feature (the room itself or its invite list): what each slot does when
 * the viewer clicks it. {@link RoomService} fills it.
 */
final class RoomMenu implements InventoryHolder {
    enum Kind { ROOM, INVITE }

    final Kind kind;
    private final Map<Integer, Consumer<Player>> actions = new HashMap<>();
    private Inventory inventory;

    RoomMenu(Kind kind) {
        this.kind = kind;
    }

    void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    void clearActions() {
        actions.clear();
    }

    void action(int slot, Consumer<Player> action) {
        actions.put(slot, action);
    }

    void click(int slot, Player player) {
        Consumer<Player> action = actions.get(slot);
        if (action != null) {
            action.accept(player);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
