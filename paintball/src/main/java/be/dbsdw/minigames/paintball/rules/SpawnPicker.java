package be.dbsdw.minigames.paintball.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Respawn point choice: the team spawn furthest from the nearest living enemy, so a player is not
 * put straight back in someone's sights. Spawns within {@link #SLACK} blocks of the best one count as
 * equally good and one of them is picked at random, so respawns stay a little unpredictable.
 */
public final class SpawnPicker {
    static final double SLACK = 4;

    private SpawnPicker() {
    }

    /** @param spawns {x, y, z} per spawn; @param enemies {x, y, z} per living enemy. Returns an index. */
    public static int safest(List<double[]> spawns, List<double[]> enemies, RandomGenerator rng) {
        if (spawns.size() == 1 || enemies.isEmpty()) {
            return rng.nextInt(spawns.size());
        }
        double[] score = new double[spawns.size()];
        double best = 0;
        for (int i = 0; i < spawns.size(); i++) {
            double nearest = Double.POSITIVE_INFINITY;
            for (double[] e : enemies) {
                double[] s = spawns.get(i);
                double dx = s[0] - e[0];
                double dy = s[1] - e[1];
                double dz = s[2] - e[2];
                nearest = Math.min(nearest, Math.sqrt(dx * dx + dy * dy + dz * dz));
            }
            score[i] = nearest;
            best = Math.max(best, nearest);
        }
        List<Integer> good = new ArrayList<>();
        for (int i = 0; i < score.length; i++) {
            if (score[i] >= best - SLACK) {
                good.add(i);
            }
        }
        return good.get(rng.nextInt(good.size()));
    }
}
