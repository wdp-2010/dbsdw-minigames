package be.dbsdw.minigames.core.stats;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Player stats in SQLite, keyed by player, game and stat name, so a new game adds stats without a
 * schema change. Every query runs on one background thread; results come back as futures.
 */
public final class StatsStore {
    /** One player's stats: game -> stat -> value. */
    public record Profile(UUID id, String name, Map<String, Map<String, Long>> games) {
    }

    public record Row(String name, long value) {
    }

    private final ExecutorService db = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "MinigameCore-stats");
        t.setDaemon(true);
        return t;
    });
    private final Logger log;
    private Connection connection;

    private StatsStore(Logger log) {
        this.log = log;
    }

    /** Opens (and creates) the database; null if it cannot be opened (logged). */
    public static StatsStore open(Path file, Logger log) {
        StatsStore store = new StatsStore(log);
        try {
            store.db.submit(() -> {
                store.connection = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
                try (Statement s = store.connection.createStatement()) {
                    s.execute("PRAGMA journal_mode=WAL");
                    s.execute("CREATE TABLE IF NOT EXISTS stats (uuid TEXT NOT NULL, game TEXT NOT NULL,"
                            + " stat TEXT NOT NULL, value INTEGER NOT NULL, PRIMARY KEY (uuid, game, stat))");
                    s.execute("CREATE TABLE IF NOT EXISTS names (uuid TEXT PRIMARY KEY, name TEXT NOT NULL)");
                    s.execute("CREATE INDEX IF NOT EXISTS stats_top ON stats (game, stat, value)");
                    s.execute("CREATE INDEX IF NOT EXISTS names_name ON names (name COLLATE NOCASE)");
                }
                return null;
            }).get(10, TimeUnit.SECONDS);
            return store;
        } catch (Exception e) {
            log.log(Level.SEVERE, "[stats] cannot open " + file + " - stats are off", e);
            store.db.shutdownNow();
            return null;
        }
    }

    /** Adds the deltas (stat increments) of one match for one game. */
    public void add(String game, Map<UUID, String> names, Map<UUID, Map<String, Long>> deltas) {
        Map<UUID, String> namesCopy = Map.copyOf(names);
        Map<UUID, Map<String, Long>> copy = new LinkedHashMap<>();
        deltas.forEach((id, stats) -> copy.put(id, Map.copyOf(stats)));
        db.execute(() -> {
            try {
                connection.setAutoCommit(false);
                try (PreparedStatement name = connection.prepareStatement(
                        "INSERT INTO names (uuid, name) VALUES (?, ?) ON CONFLICT(uuid) DO UPDATE SET name = excluded.name");
                     PreparedStatement stat = connection.prepareStatement(
                        "INSERT INTO stats (uuid, game, stat, value) VALUES (?, ?, ?, ?)"
                                + " ON CONFLICT(uuid, game, stat) DO UPDATE SET value = value + excluded.value")) {
                    for (Map.Entry<UUID, String> e : namesCopy.entrySet()) {
                        name.setString(1, e.getKey().toString());
                        name.setString(2, e.getValue());
                        name.addBatch();
                    }
                    name.executeBatch();
                    for (Map.Entry<UUID, Map<String, Long>> e : copy.entrySet()) {
                        for (Map.Entry<String, Long> s : e.getValue().entrySet()) {
                            stat.setString(1, e.getKey().toString());
                            stat.setString(2, game);
                            stat.setString(3, s.getKey());
                            stat.setLong(4, s.getValue());
                            stat.addBatch();
                        }
                    }
                    stat.executeBatch();
                }
                connection.commit();
            } catch (SQLException e) {
                log.log(Level.SEVERE, "[stats] saving " + game + " stats failed", e);
                try {
                    connection.rollback();
                } catch (SQLException ignored) {
                    // the original error is logged
                }
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException ignored) {
                    // next statement reports it
                }
            }
        });
    }

    /** A player's stats by name (case-insensitive), empty if never played. */
    public CompletableFuture<Optional<Profile>> profile(String name) {
        return CompletableFuture.supplyAsync(() -> {
            try (PreparedStatement who = connection.prepareStatement(
                    "SELECT uuid, name FROM names WHERE name = ? COLLATE NOCASE LIMIT 1")) {
                who.setString(1, name);
                try (ResultSet r = who.executeQuery()) {
                    if (!r.next()) {
                        return Optional.empty();
                    }
                    UUID id = UUID.fromString(r.getString(1));
                    return Optional.of(new Profile(id, r.getString(2), games(id)));
                }
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        }, db);
    }

    /** Sets (not adds) a player's total XP in these games ("account" for all games together). */
    public CompletableFuture<Void> setXp(UUID id, String name, Map<String, Long> xpByGame) {
        Map<String, Long> copy = Map.copyOf(xpByGame);
        return CompletableFuture.runAsync(() -> {
            try {
                connection.setAutoCommit(false);
                try (PreparedStatement who = connection.prepareStatement(
                        "INSERT INTO names (uuid, name) VALUES (?, ?) ON CONFLICT(uuid) DO UPDATE SET name = excluded.name");
                     PreparedStatement stat = connection.prepareStatement(
                        "INSERT INTO stats (uuid, game, stat, value) VALUES (?, ?, 'xp', ?)"
                                + " ON CONFLICT(uuid, game, stat) DO UPDATE SET value = excluded.value")) {
                    who.setString(1, id.toString());
                    who.setString(2, name);
                    who.executeUpdate();
                    for (Map.Entry<String, Long> e : copy.entrySet()) {
                        stat.setString(1, id.toString());
                        stat.setString(2, e.getKey());
                        stat.setLong(3, e.getValue());
                        stat.addBatch();
                    }
                    stat.executeBatch();
                }
                connection.commit();
            } catch (SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException ignored) {
                    // the original error is thrown
                }
                throw new IllegalStateException(e);
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException ignored) {
                    // next statement reports it
                }
            }
        }, db);
    }

    /** One player's total XP per game (game id, or "account" for all games), empty if none yet. */
    public CompletableFuture<Map<String, Long>> xp(UUID id) {
        return CompletableFuture.supplyAsync(() -> {
            try (PreparedStatement q = connection.prepareStatement(
                    "SELECT game, value FROM stats WHERE uuid = ? AND stat = 'xp'")) {
                q.setString(1, id.toString());
                Map<String, Long> xp = new LinkedHashMap<>();
                try (ResultSet r = q.executeQuery()) {
                    while (r.next()) {
                        xp.put(r.getString(1), r.getLong(2));
                    }
                }
                return xp;
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        }, db);
    }

    /** The top players of one stat. */
    public CompletableFuture<List<Row>> top(String game, String stat, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            try (PreparedStatement q = connection.prepareStatement(
                    "SELECT n.name, s.value FROM stats s JOIN names n ON n.uuid = s.uuid"
                            + " WHERE s.game = ? AND s.stat = ? AND s.value > 0 ORDER BY s.value DESC, n.name LIMIT ?")) {
                q.setString(1, game);
                q.setString(2, stat);
                q.setInt(3, limit);
                List<Row> rows = new ArrayList<>();
                try (ResultSet r = q.executeQuery()) {
                    while (r.next()) {
                        rows.add(new Row(r.getString(1), r.getLong(2)));
                    }
                }
                return rows;
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        }, db);
    }

    /** Waits for pending writes, then closes the database. */
    public void close() {
        db.execute(() -> {
            try {
                connection.close();
            } catch (SQLException e) {
                log.log(Level.WARNING, "[stats] close failed", e);
            }
        });
        db.shutdown();
        try {
            if (!db.awaitTermination(10, TimeUnit.SECONDS)) {
                log.severe("[stats] pending stats writes did not finish within 10 s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Map<String, Map<String, Long>> games(UUID id) throws SQLException {
        Map<String, Map<String, Long>> games = new LinkedHashMap<>();
        try (PreparedStatement q = connection.prepareStatement(
                "SELECT game, stat, value FROM stats WHERE uuid = ? ORDER BY game, stat")) {
            q.setString(1, id.toString());
            try (ResultSet r = q.executeQuery()) {
                while (r.next()) {
                    games.computeIfAbsent(r.getString(1), g -> new LinkedHashMap<>()).put(r.getString(2), r.getLong(3));
                }
            }
        }
        return games;
    }
}
