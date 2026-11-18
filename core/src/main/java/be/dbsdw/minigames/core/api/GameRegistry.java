package be.dbsdw.minigames.core.api;

import java.util.Collection;
import java.util.Optional;
import org.bukkit.plugin.Plugin;

/**
 * Games known to MinigameCore. Game plugins get it with
 * {@code getServer().getServicesManager().load(GameRegistry.class)} and register in {@code onEnable}.
 */
public interface GameRegistry {
    /** @throws IllegalArgumentException on a duplicate game id or a mode of another game */
    void register(Plugin owner, GameType game);

    Collection<GameType> games();

    Optional<GameType> game(String id);

    Optional<ModeSpec> mode(String key);
}
