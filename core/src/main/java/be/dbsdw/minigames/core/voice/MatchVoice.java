package be.dbsdw.minigames.core.voice;

import java.util.UUID;

/**
 * Puts match players into voice chat groups. {@link #NONE} when Simple Voice Chat is not installed.
 * Call on the main thread.
 */
public interface MatchVoice {
    MatchVoice NONE = new MatchVoice() {
        @Override
        public void put(UUID player, String groupKey, String groupName) {}

        @Override
        public void clear(UUID player) {}

        @Override
        public void removeGroups(String matchKey) {}

        @Override
        public boolean connected(UUID player) {
            return false;
        }
    };

    /** The player belongs in this group from now on, also after reconnecting their voice chat. */
    void put(UUID player, String groupKey, String groupName);

    /** The player leaves the match's voice group (back to proximity chat). */
    void clear(UUID player);

    /** Removes every group of this match; call after clearing its players. */
    void removeGroups(String matchKey);

    /** Whether the player has a working voice chat connection. */
    boolean connected(UUID player);
}
