package be.dbsdw.minigames.paintball.rules;

import java.util.OptionalInt;

/** Team scores; the first team to reach the target wins. */
public final class TeamScore {
    private final int[] points;
    private final int target;

    public TeamScore(int teams, int target) {
        this.points = new int[teams];
        this.target = target;
    }

    /** Adds a point; returns the team when this point reached the target (only once). */
    public OptionalInt add(int team) {
        points[team]++;
        return points[team] == target ? OptionalInt.of(team) : OptionalInt.empty();
    }

    public int target() {
        return target;
    }

    public int points(int team) {
        return points[team];
    }

    /** The team with the most points, empty on a tie for first. */
    public OptionalInt leader() {
        int best = 0;
        boolean tie = false;
        for (int i = 1; i < points.length; i++) {
            if (points[i] > points[best]) {
                best = i;
                tie = false;
            } else if (points[i] == points[best]) {
                tie = true;
            }
        }
        return tie ? OptionalInt.empty() : OptionalInt.of(best);
    }
}
