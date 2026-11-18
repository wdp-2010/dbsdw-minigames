package be.dbsdw.minigames.testgame;

import java.util.OptionalInt;

/** First team to {@code target} points wins. */
public final class PointsRace {
    private final int[] points;
    private final int target;

    public PointsRace(int teams, int target) {
        this.points = new int[teams];
        this.target = target;
    }

    /** Adds a point; returns the team if this point won the race. */
    public OptionalInt score(int team) {
        points[team]++;
        return points[team] == target ? OptionalInt.of(team) : OptionalInt.empty();
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
