package be.dbsdw.minigames.paintball.rules;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** What each player bought and still holds, and whether they may buy more. */
public final class Loadout {
    /** Why a purchase is refused, or OK. */
    public enum Check { OK, TOO_POOR, MAXED, CLOSED, NOT_FOR_YOU, NOT_NEEDED }

    private final Map<UUID, EnumMap<ShopItem, Integer>> held = new HashMap<>();

    public int count(UUID id, ShopItem item) {
        EnumMap<ShopItem, Integer> m = held.get(id);
        return m == null ? 0 : m.getOrDefault(item, 0);
    }

    public boolean has(UUID id, ShopItem item) {
        return count(id, item) > 0;
    }

    /**
     * @param open      the shop is open for this player now (alive; in the bomb mode, buy phase)
     * @param defender  the player defends the bomb site (bomb mode only; false elsewhere)
     * @param bombMode  the match is the bomb mode
     * @param damaged   the player has taken a hit (a repair kit does something)
     */
    public Check check(UUID id, ShopItem item, int cash, boolean open, boolean bombMode, boolean defender,
                       boolean damaged) {
        if (!open) {
            return Check.CLOSED;
        }
        if (item.defendersOnly() && !(bombMode && defender)) {
            return Check.NOT_FOR_YOU;
        }
        if (item == ShopItem.REPAIR && !damaged) {
            return Check.NOT_NEEDED;
        }
        if (item.kind() != ShopItem.Kind.INSTANT && count(id, item) >= item.max()) {
            return Check.MAXED;
        }
        return cash < item.price() ? Check.TOO_POOR : Check.OK;
    }

    /** Adds one (instant items are not kept). */
    public void add(UUID id, ShopItem item) {
        if (item.kind() != ShopItem.Kind.INSTANT) {
            held.computeIfAbsent(id, k -> new EnumMap<>(ShopItem.class)).merge(item, 1, Integer::sum);
        }
    }

    /** Uses one up; false if the player had none. */
    public boolean take(UUID id, ShopItem item) {
        int n = count(id, item);
        if (n <= 0) {
            return false;
        }
        held.get(id).put(item, n - 1);
        return true;
    }

    /** The player loses everything (out, or the sides swap). */
    public void clear(UUID id) {
        held.remove(id);
    }

    public void clearAll() {
        held.clear();
    }
}
