package be.dbsdw.minigames.valvloer.rules;

import java.util.ArrayList;
import java.util.List;

/** The blocks a standing player's feet rest on: up to four, under the corners of their hitbox. */
public final class Footprint {
    /** Half the width of a player's hitbox. */
    public static final double HALF_WIDTH = 0.3;
    /** How far above a block top the feet may be and still count as standing on it. */
    static final double ON_TOP = 0.08;

    private Footprint() {}

    /** Block keys under the feet at (x, y, z), or none while the feet are not on a block top. */
    public static List<Long> under(double x, double y, double z) {
        int below = (int) Math.floor(y + 0.001) - 1;
        double gap = y - (below + 1);
        if (gap < -0.001 || gap >= ON_TOP) {
            return List.of();
        }
        List<Long> keys = new ArrayList<>(4);
        for (double dx : new double[] {-HALF_WIDTH, HALF_WIDTH}) {
            for (double dz : new double[] {-HALF_WIDTH, HALF_WIDTH}) {
                long key = BlockKey.of((int) Math.floor(x + dx), below, (int) Math.floor(z + dz));
                if (!keys.contains(key)) {
                    keys.add(key);
                }
            }
        }
        return keys;
    }
}
