package be.dbsdw.minigames.core.match;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;

/** Who is in which team of one match, and the match's phase: running, showing results, done. */
public final class MatchState {
    public enum Phase { RUNNING, ENDING, DONE }

    public sealed interface LeaveResult {
        record None() implements LeaveResult {}

        /** Only this team still has players; it wins. */
        record LastTeamStanding(int team) implements LeaveResult {}
    }

    private final List<List<UUID>> teams = new ArrayList<>();
    private final int resultsTicks;
    private Phase phase = Phase.RUNNING;
    private int endingTicks;

    public MatchState(List<List<UUID>> teams, int resultsTicks) {
        teams.forEach(team -> this.teams.add(new ArrayList<>(team)));
        this.resultsTicks = resultsTicks;
    }

    public Phase phase() {
        return phase;
    }

    public int teamCount() {
        return teams.size();
    }

    public OptionalInt teamOf(UUID player) {
        for (int i = 0; i < teams.size(); i++) {
            if (teams.get(i).contains(player)) {
                return OptionalInt.of(i);
            }
        }
        return OptionalInt.empty();
    }

    public List<UUID> members(int team) {
        return List.copyOf(teams.get(team));
    }

    public List<UUID> everyone() {
        return teams.stream().flatMap(List::stream).toList();
    }

    public LeaveResult leave(UUID player) {
        teams.forEach(team -> team.remove(player));
        if (phase != Phase.RUNNING) {
            return new LeaveResult.None();
        }
        int left = -1;
        int withPlayers = 0;
        for (int i = 0; i < teams.size(); i++) {
            if (!teams.get(i).isEmpty()) {
                withPlayers++;
                left = i;
            }
        }
        return withPlayers == 1 ? new LeaveResult.LastTeamStanding(left) : new LeaveResult.None();
    }

    /** Moves a player to another team. False if not running, not in the match, or already in that team. */
    public boolean move(UUID player, int team) {
        OptionalInt from = teamOf(player);
        if (phase != Phase.RUNNING || team < 0 || team >= teams.size() || from.isEmpty() || from.getAsInt() == team) {
            return false;
        }
        teams.get(from.getAsInt()).remove(player);
        teams.get(team).add(player);
        return true;
    }

    /** RUNNING → ENDING. False if the match was already past RUNNING. */
    public boolean finish() {
        if (phase != Phase.RUNNING) {
            return false;
        }
        phase = Phase.ENDING;
        endingTicks = resultsTicks;
        return true;
    }

    public void abort() {
        phase = Phase.DONE;
    }

    /** Counts the results screen down; true on the tick it is over (at once if nobody is left). */
    public boolean tickEnding() {
        if (phase != Phase.ENDING) {
            return false;
        }
        endingTicks--;
        if (endingTicks <= 0 || everyone().isEmpty()) {
            phase = Phase.DONE;
            return true;
        }
        return false;
    }
}
