package be.dbsdw.minigames.bridge;

import be.dbsdw.minigames.travel.QueueAnnouncer;
import be.dbsdw.minigames.travel.QueueStatus;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Reads the minigame server's queue status now and then and, when someone waits in a public queue,
 * tells survival players (who could travel) with a click that sends them into that queue. A queue that
 * takes a level (e.g. paintball's bomb mode) is only told to players with that level; when nobody here
 * could join any open queue, nothing is said and the next poll tries again. At most one notification per
 * {@code minutes-between}; see {@link QueueAnnouncer}. Folia-safe.
 */
final class QueueNotifier {
    private static final Duration TIMEOUT = Duration.ofSeconds(3);
    private static final int LEVELS_PER_REQUEST = 100;

    private final Plugin plugin;
    private final BridgeTravel travel;
    private final Messages messages;
    private final URI status;
    /** The minigame server's /levels, next to /queues. */
    private final URI levels;
    private final QueueAnnouncer announcer;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private boolean failing;

    private QueueNotifier(Plugin plugin, BridgeTravel travel, Messages messages, URI status, Duration gap) {
        this.plugin = plugin;
        this.travel = travel;
        this.messages = messages;
        this.status = status;
        this.levels = status.resolve("levels");
        this.announcer = new QueueAnnouncer(gap);
    }

    /** Starts polling, unless {@code url} is empty or invalid (then logs why and does nothing). */
    static void start(Plugin plugin, BridgeTravel travel, Messages messages, String url, int minutesBetween, int pollSeconds) {
        if (url == null || url.isBlank()) {
            plugin.getLogger().info("[queue-notify] queue-notify.status-url is empty - no queue notifications");
            return;
        }
        URI uri;
        try {
            uri = URI.create(url.trim());
            if (!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme())) {
                throw new IllegalArgumentException("not http(s)");
            }
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[queue-notify] bad queue-notify.status-url " + url + ": " + e.getMessage());
            return;
        }
        QueueNotifier notifier = new QueueNotifier(plugin, travel, messages, uri,
                Duration.ofMinutes(Math.max(1, minutesBetween)));
        long every = Math.max(5, pollSeconds);
        Bukkit.getAsyncScheduler().runAtFixedRate(plugin, task -> notifier.poll(), every, every, TimeUnit.SECONDS);
        plugin.getLogger().info("[queue-notify] reading " + uri + " every " + every + "s, at most one notification per "
                + Math.max(1, minutesBetween) + " min");
    }

    /** Async thread: fetch, decide, then send on each player's own thread. */
    private void poll() {
        List<QueueStatus> queues;
        try {
            queues = QueueStatus.decode(get(status));
            if (failing) {
                failing = false;
                plugin.getLogger().info("[queue-notify] status readable again");
            }
        } catch (java.io.IOException e) {
            if (!failing) {
                failing = true;
                plugin.getLogger().info("[queue-notify] cannot read " + status + ": " + e.getMessage());
            }
            return;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        List<Player> online = List.copyOf(Bukkit.getOnlinePlayers());
        if (online.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        List<QueueStatus> candidates = announcer.candidates(queues, now);
        if (candidates.isEmpty()) {
            return;
        }
        // The players' levels in each game whose queue takes a level; unknown counts as level 1.
        Map<String, Map<UUID, Integer>> levels = new HashMap<>();
        for (QueueStatus queue : candidates) {
            if (queue.leveled() && !levels.containsKey(queue.game())) {
                levels.put(queue.game(), levels(queue.game(), online));
            }
        }
        // Each player hears of the biggest queue they may join; nobody at all: wait for the next poll.
        Map<Player, QueueStatus> told = new LinkedHashMap<>();
        for (Player player : online) {
            QueueAnnouncer.firstAllowed(candidates,
                    game -> levels.getOrDefault(game, Map.of()).getOrDefault(player.getUniqueId(), 1))
                    .ifPresent(queue -> told.put(player, queue));
        }
        if (told.isEmpty()) {
            return;
        }
        QueueStatus biggest = candidates.stream().filter(told::containsValue).findFirst().orElseThrow();
        announcer.announced(biggest, now);
        Map<String, Component> texts = new HashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        told.forEach((player, queue) -> {
            Component text = texts.computeIfAbsent(queue.key(), k -> text(queue));
            counts.merge(queue.key(), 1, Integer::sum);
            player.getScheduler().run(plugin, t -> {
                if (travel.eligible(player)) {
                    player.sendMessage(text);
                }
            }, null);
        });
        plugin.getLogger().info("[queue-notify] announced " + counts + " (up to that many players each; "
                + (online.size() - told.size()) + " online without the level for any open queue)");
    }

    private String get(URI uri) throws java.io.IOException, InterruptedException {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200 && response.statusCode() != 204) {
            throw new java.io.IOException("HTTP " + response.statusCode());
        }
        return response.body() == null ? "" : response.body();
    }

    /** The players' levels in this game, read from the minigame server; empty when that fails (logged). */
    private Map<UUID, Integer> levels(String game, List<Player> players) {
        Map<UUID, Integer> levels = new HashMap<>();
        for (int from = 0; from < players.size(); from += LEVELS_PER_REQUEST) {
            String ids = players.subList(from, Math.min(players.size(), from + LEVELS_PER_REQUEST)).stream()
                    .map(p -> p.getUniqueId().toString()).collect(Collectors.joining(","));
            URI uri = URI.create(this.levels + "?game=" + URLEncoder.encode(game, StandardCharsets.UTF_8) + "&ids=" + ids);
            try {
                levels.putAll(QueueStatus.decodeLevels(get(uri)));
            } catch (java.io.IOException e) {
                plugin.getLogger().info("[queue-notify] cannot read levels from " + this.levels + ": " + e.getMessage());
                return levels;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return levels;
            }
        }
        return levels;
    }

    private Component text(QueueStatus queue) {
        Component name = MiniMessage.miniMessage().deserialize(queue.name());
        Component click = messages.get("queue-notify-click")
                .clickEvent(ClickEvent.runCommand("/minigames queue " + queue.key()))
                .hoverEvent(HoverEvent.showText(messages.get("queue-notify-hover", Placeholder.component("name", name))));
        return messages.get("queue-notify", Placeholder.component("name", name),
                Placeholder.unparsed("queued", Integer.toString(queue.queued())),
                Placeholder.unparsed("needed", Integer.toString(queue.needed())),
                Placeholder.unparsed("max", Integer.toString(queue.max())),
                Placeholder.component("click", click));
    }
}
