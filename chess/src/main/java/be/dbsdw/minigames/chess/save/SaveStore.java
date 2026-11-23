package be.dbsdw.minigames.chess.save;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Unfinished games, one {@code <id>.yml} per game in the games folder. Everything is kept in memory;
 * files are written and deleted on the given executor (off the main thread on the server), each
 * write as a whole new file moved into place, so a crash never leaves half a file.
 */
public final class SaveStore {
    private static final String SUFFIX = ".yml";
    private static final String ID_CHARS = "abcdefghijkmnpqrstuvwxyz23456789";

    private final Path dir;
    private final Executor io;
    private final Logger log;
    private final Map<String, SavedGame> games = new LinkedHashMap<>();
    private final SecureRandom rnd = new SecureRandom();

    public SaveStore(Path dir, Executor io, Logger log) {
        this.dir = dir;
        this.io = io;
        this.log = log;
    }

    /** Reads every game file; unreadable ones are skipped with a warning and left on disk. */
    public void load() throws IOException {
        games.clear();
        if (!Files.isDirectory(dir)) {
            return;
        }
        List<Path> files;
        try (Stream<Path> listing = Files.list(dir)) {
            files = listing.filter(f -> f.getFileName().toString().endsWith(SUFFIX)).sorted().toList();
        }
        for (Path file : files) {
            String name = file.getFileName().toString();
            try {
                YamlConfiguration yaml = new YamlConfiguration();
                yaml.loadFromString(Files.readString(file, StandardCharsets.UTF_8));
                SavedGame game = read(name.substring(0, name.length() - SUFFIX.length()), yaml);
                games.put(game.id(), game);
            } catch (InvalidConfigurationException | RuntimeException e) {
                log.warning("[chess] skipped saved game " + name + ": unreadable (" + e.getMessage() + ")");
            }
        }
    }

    /** A new id no saved game has. */
    public String newId() {
        while (true) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(ID_CHARS.charAt(rnd.nextInt(ID_CHARS.length())));
            }
            if (!games.containsKey(sb.toString())) {
                return sb.toString();
            }
        }
    }

    public Optional<SavedGame> get(String id) {
        return Optional.ofNullable(games.get(id));
    }

    /** The player's unfinished games, last played first. */
    public List<SavedGame> of(UUID player) {
        return games.values().stream().filter(g -> g.involves(player))
                .sorted(Comparator.comparingLong(SavedGame::updatedAt).reversed()).toList();
    }

    public int size() {
        return games.size();
    }

    public int count(UUID player) {
        return (int) games.values().stream().filter(g -> g.involves(player)).count();
    }

    public void put(SavedGame game) {
        games.put(game.id(), game);
        String text = write(game).saveToString();
        Path target = dir.resolve(game.id() + SUFFIX);
        run(() -> {
            try {
                Files.createDirectories(dir);
                Path temp = dir.resolve(game.id() + SUFFIX + ".tmp");
                Files.writeString(temp, text, StandardCharsets.UTF_8);
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                log.severe("[chess] could not save game " + game.id() + ": " + e.getMessage());
            }
        });
    }

    public void delete(String id) {
        if (games.remove(id) == null) {
            return;
        }
        Path target = dir.resolve(id + SUFFIX);
        run(() -> {
            try {
                Files.deleteIfExists(target);
            } catch (IOException e) {
                log.severe("[chess] could not delete game " + id + ": " + e.getMessage());
            }
        });
    }

    /** On the executor; right here once it no longer takes work (the plugin is shutting down). */
    private void run(Runnable task) {
        try {
            io.execute(task);
        } catch (RejectedExecutionException e) {
            task.run();
        }
    }

    /** Deletes games not played since {@code now - maxAgeMillis}; returns them. */
    public List<SavedGame> expire(long now, long maxAgeMillis) {
        List<SavedGame> old = new ArrayList<>();
        for (SavedGame g : List.copyOf(games.values())) {
            if (g.updatedAt() < now - maxAgeMillis) {
                old.add(g);
                delete(g.id());
            }
        }
        return old;
    }

    private static YamlConfiguration write(SavedGame g) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("white", g.white().toString());
        yaml.set("white-name", g.whiteName());
        yaml.set("black", g.black().toString());
        yaml.set("black-name", g.blackName());
        yaml.set("mode", g.mode());
        yaml.set("moves", String.join(" ", g.moves()));
        yaml.set("clock.initial-ms", g.initialMillis());
        yaml.set("clock.increment-ms", g.incrementMillis());
        yaml.set("clock.white-ms", g.whiteMillis());
        yaml.set("clock.black-ms", g.blackMillis());
        yaml.set("created", g.createdAt());
        yaml.set("updated", g.updatedAt());
        return yaml;
    }

    private static SavedGame read(String id, YamlConfiguration y) {
        String moves = y.getString("moves", "");
        return new SavedGame(id, uuid(y, "white"), y.getString("white-name", "?"), uuid(y, "black"),
                y.getString("black-name", "?"), required(y, "mode"),
                moves.isBlank() ? List.of() : List.of(moves.trim().split("\\s+")),
                y.getLong("clock.initial-ms"), y.getLong("clock.increment-ms"), y.getLong("clock.white-ms"),
                y.getLong("clock.black-ms"), y.getLong("created"), y.getLong("updated"));
    }

    private static UUID uuid(YamlConfiguration y, String key) {
        return UUID.fromString(required(y, key));
    }

    private static String required(YamlConfiguration y, String key) {
        String v = y.getString(key);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("no " + key);
        }
        return v;
    }
}
