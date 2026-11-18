package be.dbsdw.minigames.paintball.rules;

import java.util.ArrayList;
import java.util.List;

/** Which power-up spawn points hold a power-up, and when an empty one fills again. */
public final class PowerupSpawns {
    private final boolean[] present;
    private final long[] readyAt;
    private final long respawnTicks;

    /** Every point fills at the first tick. */
    public PowerupSpawns(int points, long respawnTicks) {
        this.present = new boolean[points];
        this.readyAt = new long[points];
        this.respawnTicks = respawnTicks;
    }

    /** Points that get a new power-up now (marked present). */
    public List<Integer> due(long now) {
        List<Integer> due = new ArrayList<>();
        for (int i = 0; i < present.length; i++) {
            if (!present[i] && now >= readyAt[i]) {
                present[i] = true;
                due.add(i);
            }
        }
        return due;
    }

    public boolean present(int point) {
        return present[point];
    }

    /** Picked up: empty until {@code now + respawn}. */
    public void taken(int point, long now) {
        present[point] = false;
        readyAt[point] = now + respawnTicks;
    }
}
