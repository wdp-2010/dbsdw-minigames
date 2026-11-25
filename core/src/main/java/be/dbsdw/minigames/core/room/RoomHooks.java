package be.dbsdw.minigames.core.room;

import be.dbsdw.minigames.core.api.ModeSpec;
import be.dbsdw.minigames.core.arena.ArenaDefinition;
import be.dbsdw.minigames.core.voice.VoiceRule;
import java.util.List;
import java.util.UUID;
import java.util.function.IntConsumer;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** What rooms need from the match side; implemented by the match manager. */
public interface RoomHooks {
    /**
     * Starts the room's match with these teams and voice rule on the reserved arena, plus the bots per
     * team the host added at that level. Returns false when it could not start; the arena is then released.
     */
    boolean launch(int roomId, ModeSpec mode, ArenaDefinition arena, List<List<UUID>> teams, VoiceRule voice,
                   List<Integer> bots, int botLevel);

    boolean inMatch(UUID player);

    /** Takes the player out of the public queue, if they are in one. */
    void leaveQueue(UUID player);

    /** Every mode of every registered game, in menu order. */
    List<ModeSpec> modes();

    ItemStack modeIcon(ModeSpec mode);

    /** See {@link be.dbsdw.minigames.core.api.GameType#startAlone}; false when the game does not take over. */
    boolean startAlone(Player host, ModeSpec mode, IntConsumer startOnTeam);

    /** See {@link be.dbsdw.minigames.core.api.GameType#roomBotLevels}; empty when the game has no room bots. */
    List<Component> botLevels(ModeSpec mode);

    /** Why this host may not play the mode yet (their level is too low), or null. Guests are not checked. */
    default Component lockReason(Player host, ModeSpec mode) {
        return null;
    }

    /** A game's icon as in the games menu, named after the game. */
    ItemStack gameIcon(String gameId);
}
