package be.dbsdw.minigames.core;

/** Picks which team a joining player goes to: the smallest team, lowest index on a tie. */
public final class TeamBalancer {
    private TeamBalancer() {}

    public static int pickTeam(int[] teamSizes) {
        if (teamSizes.length == 0) {
            throw new IllegalArgumentException("no teams");
        }
        int best = 0;
        for (int i = 1; i < teamSizes.length; i++) {
            if (teamSizes[i] < teamSizes[best]) {
                best = i;
            }
        }
        return best;
    }
}
