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
 */
public record RoomView(int id, UUID host, List<UUID> members, ModeSpec mode, Optional<String> arena,
                       RoomTeams.Rule rule, Map<UUID, Integer> picks, OptionalInt secondsLeft, boolean playing,
                       VoiceRule voice) {
    public RoomView {
        members = List.copyOf(members);
        picks = Map.copyOf(picks);
    }

    public OptionalInt teamOf(UUID member) {
        Integer team = picks.get(member);
        return team == null ? OptionalInt.empty() : OptionalInt.of(team);
    }

    public boolean counting() {
        return secondsLeft.isPresent();
    }
}
