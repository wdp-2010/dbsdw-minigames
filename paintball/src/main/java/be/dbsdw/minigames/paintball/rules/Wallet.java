package be.dbsdw.minigames.paintball.rules;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Everyone's cash in one match. Unknown players have the start amount. */
public final class Wallet {
    private final int start;
    private final int cap;
    private final Map<UUID, Integer> cash = new HashMap<>();

    public Wallet(int start, int cap) {
        this.start = start;
        this.cap = cap;
    }

    public int cash(UUID id) {
        return cash.getOrDefault(id, start);
    }

    /** Adds (never above the cap); returns what was really added. */
    public int earn(UUID id, int amount) {
        int before = cash(id);
        int after = (int) Math.min(cap, Math.max(0, (long) before + Math.max(0, amount)));
        cash.put(id, after);
        return after - before;
    }

    /** Takes the price if the player has enough; false (and nothing taken) if not. */
    public boolean spend(UUID id, int price) {
        int have = cash(id);
        if (price < 0 || have < price) {
            return false;
        }
        cash.put(id, have - price);
        return true;
    }

    /** Everyone back to the start amount. */
    public void reset() {
        cash.clear();
    }
}
