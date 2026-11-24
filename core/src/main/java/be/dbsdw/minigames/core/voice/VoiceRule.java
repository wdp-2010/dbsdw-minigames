package be.dbsdw.minigames.core.voice;

import java.util.Locale;
import java.util.Optional;

/** Who hears whom in Simple Voice Chat during a match. Rooms let the host choose. */
public enum VoiceRule {
    /** One closed voice group per team: you only hear your own team. */
    TEAM,
    /** One closed voice group for the whole match. */
    ALL,
    /** No groups: normal proximity voice chat. */
    NEARBY;

    public VoiceRule next() {
        return values()[(ordinal() + 1) % values().length];
    }

    /** The config spelling: {@code team}, {@code all} or {@code nearby} (case does not matter). */
    public static Optional<VoiceRule> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "team" -> Optional.of(TEAM);
            case "all" -> Optional.of(ALL);
            case "nearby" -> Optional.of(NEARBY);
            default -> Optional.empty();
        };
    }

    /**
     * The voice group a player on {@code team} belongs to in the match with this key, or empty for no
     * group (proximity chat).
     */
    public Optional<String> groupKey(String matchKey, int team) {
        return switch (this) {
            case TEAM -> Optional.of(matchKey + ":" + team);
            case ALL -> Optional.of(matchKey + ":all");
            case NEARBY -> Optional.empty();
        };
    }

    /** A group name the voice chat client shows: at most 16 characters, never blank. */
    public static String groupName(String name) {
        String clean = name == null ? "" : name.replaceAll("\\s+", " ").trim();
        if (clean.isEmpty()) {
            return "Team";
        }
        return clean.length() > 16 ? clean.substring(0, 16).trim() : clean;
    }
}
