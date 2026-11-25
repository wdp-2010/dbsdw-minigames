package be.dbsdw.minigames.core.room;

import be.dbsdw.minigames.core.api.ModeSpec;
import be.dbsdw.minigames.core.arena.ArenaDefinition;
import be.dbsdw.minigames.core.voice.VoiceRule;
import java.util.List;
import java.util.Set;
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

    /**
     * The room's match starts on the arena, which stays reserved until the match closes. {@code bots}:
     * bots per team the game adds (empty or zeros: none), at level {@code botLevel}. {@code options}:
     * the room options the host switched on.
     */
    record Start(int roomId, ModeSpec mode, ArenaDefinition arena, List<List<UUID>> teams, VoiceRule voice,
                 List<Integer> bots, int botLevel, Set<String> options) implements RoomEvent {
        public Start {
            teams = teams.stream().map(List::copyOf).toList();
            bots = List.copyOf(bots);
            options = Set.copyOf(options);
        }
    }
}
