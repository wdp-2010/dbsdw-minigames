package be.dbsdw.minigames.core.room;

import be.dbsdw.minigames.core.api.ModeSpec;
import be.dbsdw.minigames.core.arena.ArenaDefinition;
import java.util.List;
import java.util.UUID;
import org.bukkit.inventory.ItemStack;

/** What rooms need from the match side; implemented by the match manager. */
public interface RoomHooks {
    /**
     * Starts the room's match with these teams on the reserved arena. Returns false when it could
     * not start; the arena is then released.
     */
    boolean launch(int roomId, ModeSpec mode, ArenaDefinition arena, List<List<UUID>> teams);

    boolean inMatch(UUID player);

    /** Takes the player out of the public queue, if they are in one. */
    void leaveQueue(UUID player);

    /** Every mode of every registered game, in menu order. */
    List<ModeSpec> modes();

    ItemStack modeIcon(ModeSpec mode);

    /** A game's icon as in the games menu, named after the game. */
    ItemStack gameIcon(String gameId);
}
