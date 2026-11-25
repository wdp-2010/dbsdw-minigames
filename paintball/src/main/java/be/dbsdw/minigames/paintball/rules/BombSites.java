package be.dbsdw.minigames.paintball.rules;

import java.util.List;

/**
 * Where the bomb can be planted, and which spawn each team uses in a round.
 *
 * <p>Two kinds of bomb arena:
 * <ul>
 *   <li>A paintball arena with two flags: the bomb site is the defending team's flag spot, and each
 *       team keeps its own spawn when the sides swap.</li>
 *   <li>A bomb map with two fixed sites, A and B (arena flags {@value #SITE_A} and {@value #SITE_B}):
 *       both sites are on the defenders' side of the map. Spawn 0 is the attackers' spawn and spawn 1
 *       the defenders', so when the sides swap the teams also swap spawns.</li>
 * </ul>
 */
public final class BombSites {
    /** Arena flag key of site A on a bomb map. */
    public static final int SITE_A = 2;
    /** Arena flag key of site B on a bomb map. */
    public static final int SITE_B = 3;
    /** How far above or below a site's centre a player still counts as on it. */
    public static final double REACH_Y = 3;

    private BombSites() {
    }

    /** The spawn list (arena spawn key) a team uses while {@code attackers} attack. */
    public static int spawnSide(int team, int attackers, boolean fixedSites) {
        if (!fixedSites) {
            return team;
        }
        return team == attackers ? 0 : 1;
    }

    /** The index of the site (x, y, z) stands on, or -1; each site is {x, y, z}. */
    public static int siteAt(List<double[]> sites, double radius, double x, double y, double z) {
        for (int i = 0; i < sites.size(); i++) {
            double[] s = sites.get(i);
            double dx = x - s[0];
            double dz = z - s[2];
            if (dx * dx + dz * dz <= radius * radius && Math.abs(y - s[1]) <= REACH_Y) {
                return i;
            }
        }
        return -1;
    }

    /** The letter of a site: A, B, ... */
    public static String label(int index) {
        return String.valueOf((char) ('A' + index));
    }
}
