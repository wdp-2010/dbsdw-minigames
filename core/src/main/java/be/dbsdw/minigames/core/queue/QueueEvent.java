package be.dbsdw.minigames.core.queue;

import be.dbsdw.minigames.core.api.ModeSpec;
import be.dbsdw.minigames.core.arena.ArenaDefinition;
import java.util.List;
import java.util.UUID;

/** What happened to the queues during one {@link QueueBook#tick()}; {@code mode} strings are queue keys. */
public sealed interface QueueEvent {
    record CountdownStarted(String mode, String arena, int seconds) implements QueueEvent {}

    record CountdownTick(String mode, int seconds, List<UUID> queued) implements QueueEvent {
        public CountdownTick {
            queued = List.copyOf(queued);
        }
    }

    record CountdownCancelled(String mode, List<UUID> queued) implements QueueEvent {
        public CountdownCancelled {
            queued = List.copyOf(queued);
        }
    }

    record WaitingForArena(String mode, List<UUID> queued) implements QueueEvent {
        public WaitingForArena {
            queued = List.copyOf(queued);
        }
    }

    /** These players leave the queue {@code queue} for a match on the arena, which stays reserved. */
    record Start(String queue, ModeSpec mode, ArenaDefinition arena, List<UUID> players) implements QueueEvent {
        public Start {
            players = List.copyOf(players);
        }
    }
}
