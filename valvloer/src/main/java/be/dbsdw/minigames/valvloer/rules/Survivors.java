package be.dbsdw.minigames.valvloer.rules;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;

/** Who is still on the floor, by team, and when the match is decided. */
public final class Survivors {
    public sealed interface Result {
        record Running() implements Result {}

        record Win(int team) implements Result {}

        record Draw() implements Result {}
    }

    private final int teams;
    private final Map<UUID, Integer> alive = new LinkedHashMap<>();
    private final int total;

    public Survivors(int teams, Map<UUID, Integer> players) {
        this.teams = teams;
        this.alive.putAll(players);
        this.total = players.size();
    }

    public boolean alive(UUID player) {
        return alive.containsKey(player);
    }

    public int total() {
        return total;
    }

    public int left() {
        return alive.size();
    }

    public int left(int team) {
        return (int) alive.values().stream().filter(t -> t == team).count();
    }

    /** The player falls out; their place (last one out of 8 = 2nd... first one out = 8th). Empty if not alive. */
    public OptionalInt fall(UUID player) {
        if (alive.remove(player) == null) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(alive.size() + 1);
    }

    /** A player left the match: no place, just gone. */
    public void remove(UUID player) {
        alive.remove(player);
    }

    /** One team left on the floor wins; nobody left (fell in the same tick) is a draw. */
    public Result result() {
        int withPlayers = 0;
        int last = -1;
        for (int t = 0; t < teams; t++) {
            if (left(t) > 0) {
                withPlayers++;
                last = t;
            }
        }
        if (withPlayers == 0) {
            return new Result.Draw();
        }
        return withPlayers == 1 ? new Result.Win(last) : new Result.Running();
    }

    /** At the time limit: the team with the most players on the floor, a draw on a tie. */
    public Result atTimeLimit() {
        int best = -1;
        int bestCount = -1;
        boolean tie = false;
        for (int t = 0; t < teams; t++) {
            int n = left(t);
            if (n > bestCount) {
                best = t;
                bestCount = n;
                tie = false;
            } else if (n == bestCount) {
                tie = true;
            }
        }
        return tie || bestCount <= 0 ? new Result.Draw() : new Result.Win(best);
    }
}
