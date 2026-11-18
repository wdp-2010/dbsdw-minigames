package be.dbsdw.minigames.core.match;

import be.dbsdw.minigames.core.api.GameRegistry;
import be.dbsdw.minigames.core.api.GameType;
import be.dbsdw.minigames.core.api.ModeSpec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.bukkit.plugin.Plugin;

public final class GameRegistryImpl implements GameRegistry {
    private record Entry(Plugin owner, GameType game) {}

    private final Map<String, Entry> games = new LinkedHashMap<>();
    private final List<Consumer<GameType>> onRegister = new ArrayList<>();

    @Override
    public void register(Plugin owner, GameType game) {
        if (games.containsKey(game.id())) {
            throw new IllegalArgumentException("game " + game.id() + " is already registered");
        }
        for (ModeSpec mode : game.modes()) {
            if (!mode.gameId().equals(game.id())) {
                throw new IllegalArgumentException("mode " + mode.key() + " does not belong to game " + game.id());
            }
        }
        games.put(game.id(), new Entry(owner, game));
        onRegister.forEach(listener -> listener.accept(game));
    }

    /** Called for every game registered from now on. */
    public void onRegister(Consumer<GameType> listener) {
        onRegister.add(listener);
    }

    public List<GameType> gamesOf(Plugin owner) {
        return games.values().stream().filter(e -> e.owner().equals(owner)).map(Entry::game).toList();
    }

    public void unregister(String id) {
        games.remove(id);
    }

    @Override
    public Collection<GameType> games() {
        return games.values().stream().map(Entry::game).toList();
    }

    @Override
    public Optional<GameType> game(String id) {
        return Optional.ofNullable(games.get(id)).map(Entry::game);
    }

    @Override
    public Optional<ModeSpec> mode(String key) {
        return games.values().stream()
                .flatMap(e -> e.game().modes().stream())
                .filter(m -> m.key().equals(key))
                .findFirst();
    }
}
