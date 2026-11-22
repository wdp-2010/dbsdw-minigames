package be.dbsdw.minigames.core.room;

import be.dbsdw.minigames.core.api.ModeSpec;
import be.dbsdw.minigames.core.arena.ArenaDefinition;
import java.util.List;
import java.util.UUID;

/** What happened to the rooms during one {@link RoomBook#tick()}. */
public sealed interface RoomEvent {
    record CountdownTick(int roomId, List<UUID> members, int seconds) implements RoomEvent {
        public CountdownTick {
            members = List.copyOf(members);
        }
    }

    record CountdownCancelled(int roomId, List<UUID> members) implements RoomEvent {
        public CountdownCancelled {
            members = List.copyOf(members);
        }
    }

    /** The room's match starts on the arena, which stays reserved until the match closes. */
    record Start(int roomId, ModeSpec mode, ArenaDefinition arena, List<List<UUID>> teams) implements RoomEvent {
        public Start {
            teams = teams.stream().map(List::copyOf).toList();
        }
    }
}
