package be.dbsdw.minigames.core.queue;

import be.dbsdw.minigames.core.api.ModeSpec;
import java.util.Collections;
import java.util.regex.Pattern;

/**
 * Public queues with a fixed team size, e.g. paintball team deathmatch 2v2. Such a queue plays its
 * mode with exactly that many players per team, under the key {@code <game>:<mode>:<n>v<n>}.
 */
public final class QueueSizes {
    /** What any queue key looks like: a mode key, maybe followed by a size. */
    public static final Pattern KEY = Pattern.compile("[a-z0-9_-]+:[a-z0-9_-]+(:[0-9]+(v[0-9]+)+)?");

    private QueueSizes() {}

    /** The key of the mode's queue with {@code perTeam} players per team. */
    public static String key(ModeSpec mode, int perTeam) {
        return mode.key() + ":" + label(mode.teams(), perTeam);
    }

    /** {@code 2v2}, or {@code 2v2v2} for three teams. */
    public static String label(int teams, int perTeam) {
        return String.join("v", Collections.nCopies(teams, Integer.toString(perTeam)));
    }

    /** The mode with exactly {@code perTeam} players per team (same game, id and arenas). */
    public static ModeSpec sized(ModeSpec mode, int perTeam) {
        if (perTeam < 1) {
            throw new IllegalArgumentException("a team needs at least 1 player: " + perTeam);
        }
        return new ModeSpec(mode.gameId(), mode.id(), mode.displayName(), mode.teams(), perTeam, perTeam,
                mode.features());
    }
}
