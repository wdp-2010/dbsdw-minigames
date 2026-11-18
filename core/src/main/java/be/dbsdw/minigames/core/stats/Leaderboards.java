package be.dbsdw.minigames.core.stats;

import be.dbsdw.minigames.core.Messages;
import be.dbsdw.minigames.core.api.GameRegistry;
import be.dbsdw.minigames.core.api.GameType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Text-display leaderboards in the lobby, kept in config.yml under {@code leaderboards}. The
 * displays are not saved with the world; they are respawned while their chunk is loaded and their
 * text is refreshed every minute.
 */
public final class Leaderboards {
    private record Board(String game, String stat, String world, double x, double y, double z) {
    }

    private static final int ROWS = 10;

    private final JavaPlugin plugin;
    private final StatsStore store;
    private final GameRegistry games;
    private final Messages messages;
    private final List<Board> boards = new ArrayList<>();
    private final Map<Board, TextDisplay> displays = new HashMap<>();
    private final Map<Board, Component> texts = new HashMap<>();
    private long ticks;

    public Leaderboards(JavaPlugin plugin, StatsStore store, GameRegistry games, Messages messages) {
        this.plugin = plugin;
        this.store = store;
        this.games = games;
        this.messages = messages;
        for (Map<?, ?> m : plugin.getConfig().getMapList("leaderboards")) {
            try {
                boards.add(new Board(String.valueOf(m.get("game")), String.valueOf(m.get("stat")),
                        String.valueOf(m.get("world")), ((Number) m.get("x")).doubleValue(),
                        ((Number) m.get("y")).doubleValue(), ((Number) m.get("z")).doubleValue()));
            } catch (RuntimeException e) {
                plugin.getLogger().warning("[stats] skipped a broken leaderboards entry in config.yml: " + m);
            }
        }
    }

    /** Called every tick: respawn every 5 s, refresh text every 60 s (and once at start). */
    public void tick() {
        if (ticks % 1200 == 40) {
            refresh();
        }
        if (ticks % 100 == 0) {
            boards.forEach(this::ensure);
        }
        ticks++;
    }

    public void add(String game, String stat, Location at) {
        Board b = new Board(game, stat, at.getWorld().getName(), at.getX(), at.getY(), at.getZ());
        boards.add(b);
        save();
        ensure(b);
        refresh();
    }

    /** Removes the boards within 5 blocks; returns how many. */
    public int removeNear(Location at) {
        List<Board> near = boards.stream().filter(b -> b.world().equals(at.getWorld().getName())
                && at.distanceSquared(new Location(at.getWorld(), b.x(), b.y(), b.z())) <= 25).toList();
        for (Board b : near) {
            boards.remove(b);
            TextDisplay d = displays.remove(b);
            if (d != null) {
                d.remove();
            }
            texts.remove(b);
        }
        save();
        return near.size();
    }

    public void removeAll() {
        displays.values().forEach(TextDisplay::remove);
        displays.clear();
    }

    private void ensure(Board b) {
        World world = Bukkit.getWorld(b.world());
        if (world == null || !world.isChunkLoaded((int) Math.floor(b.x()) >> 4, (int) Math.floor(b.z()) >> 4)) {
            return;
        }
        TextDisplay d = displays.get(b);
        if (d != null && d.isValid()) {
            return;
        }
        d = world.spawn(new Location(world, b.x(), b.y(), b.z()), TextDisplay.class, e -> {
            e.setPersistent(false);
            e.setBillboard(Display.Billboard.VERTICAL);
            e.setBackgroundColor(Color.fromARGB(0x60, 0, 0, 0));
            e.setLineWidth(220);
            e.text(texts.getOrDefault(b, messages.get("leaderboard.loading")));
        });
        displays.put(b, d);
    }

    private void refresh() {
        if (store == null) {
            return;
        }
        for (Board b : boards) {
            Optional<GameType> game = games.game(b.game());
            if (game.isEmpty()) {
                continue;
            }
            store.top(b.game(), b.stat(), ROWS).whenComplete((rows, error) -> {
                if (error != null || !plugin.isEnabled()) {
                    return;
                }
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Component text = messages.get("leaderboard.title",
                            Placeholder.component("stat", StatNames.display(game.get(), b.stat(), messages)),
                            Placeholder.component("game", game.get().displayName()));
                    int rank = 1;
                    for (StatsStore.Row row : rows) {
                        text = text.appendNewline().append(messages.get("leaderboard.line",
                                Placeholder.unparsed("rank", Integer.toString(rank++)),
                                Placeholder.unparsed("player", row.name()),
                                Placeholder.unparsed("value", Long.toString(row.value()))));
                    }
                    if (rows.isEmpty()) {
                        text = text.appendNewline().append(messages.get("leaderboard.empty"));
                    }
                    texts.put(b, text);
                    TextDisplay d = displays.get(b);
                    if (d != null && d.isValid()) {
                        d.text(text);
                    }
                });
            });
        }
    }

    private void save() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Board b : boards) {
            list.add(Map.of("game", b.game(), "stat", b.stat(), "world", b.world(), "x", b.x(), "y", b.y(), "z", b.z()));
        }
        plugin.getConfig().set("leaderboards", list);
        plugin.saveConfig();
    }
}
