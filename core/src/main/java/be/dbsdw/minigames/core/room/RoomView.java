package be.dbsdw.minigames.core.room;

import be.dbsdw.minigames.core.api.ModeSpec;
import be.dbsdw.minigames.core.voice.VoiceRule;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * A snapshot of one room.
 *
 * @param members in join order; the first is the longest-standing member
 * @param arena the arena the host chose; empty means a random free one
 * @param picks team index per member who has one
 * @param secondsLeft the start countdown, while it runs
 * @param playing whether the room's match is running
 * @param voice who hears whom in voice chat during the room's matches
 * @param bots bots the host added, per team
 * @param botLevel the bots' level, an index into the game's levels
 */
public record RoomView(int id, UUID host, List<UUID> members, ModeSpec mode, Optional<String> arena,
                       RoomTeams.Rule rule, Map<UUID, Integer> picks, OptionalInt secondsLeft, boolean playing,
                       VoiceRule voice, List<Integer> bots, int botLevel) {
    public RoomView {
        members = List.copyOf(members);
        picks = Map.copyOf(picks);
        bots = List.copyOf(bots);
    }

    public int botsIn(int team) {
        return team < bots.size() ? bots.get(team) : 0;
    }

    public int botCount() {
        return bots.stream().mapToInt(Integer::intValue).sum();
    }

    /** Members and bots. */
    public int headcount() {
        return members.size() + botCount();
    }

    public OptionalInt teamOf(UUID member) {
        Integer team = picks.get(member);
        return team == null ? OptionalInt.empty() : OptionalInt.of(team);
    }

    public boolean counting() {
        return secondsLeft.isPresent();
    }
}
