package be.dbsdw.minigames.core.api;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * One mode of a game, e.g. paintball team deathmatch.
 *
 * @param displayName MiniMessage text shown in menus
 */
public record ModeSpec(String gameId, String id, String displayName, int teams, int minPerTeam, int maxPerTeam,
                       Set<ArenaFeature> features) {
    private static final Pattern ID = Pattern.compile("[a-z0-9_-]+");

    public ModeSpec {
        if (!ID.matcher(gameId).matches() || !ID.matcher(id).matches()) {
            throw new IllegalArgumentException("game and mode ids must match [a-z0-9_-]+: " + gameId + ":" + id);
        }
        if (teams < 2) {
            throw new IllegalArgumentException("a mode needs at least 2 teams");
        }
        if (minPerTeam < 1 || minPerTeam > maxPerTeam) {
            throw new IllegalArgumentException("need 1 <= minPerTeam <= maxPerTeam");
        }
        features = Set.copyOf(features);
    }

    public String key() {
        return gameId + ":" + id;
    }

    public int startMinimum() {
        return teams * minPerTeam;
    }

    public int maximum() {
        return teams * maxPerTeam;
    }
}
