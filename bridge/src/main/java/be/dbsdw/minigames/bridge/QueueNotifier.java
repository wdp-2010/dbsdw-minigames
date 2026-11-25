package be.dbsdw.minigames.bridge;

import be.dbsdw.minigames.travel.QueueAnnouncer;
import be.dbsdw.minigames.travel.QueueStatus;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
 * tells survival players (who could travel) with a click that sends them into that queue. At most
 * one notification per {@code minutes-between}; see {@link QueueAnnouncer}. Folia-safe.
 */
final class QueueNotifier {
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final Plugin plugin;
    private final BridgeTravel travel;
    private final Messages messages;
    private final URI status;
    private final QueueAnnouncer announcer;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private boolean failing;

    private QueueNotifier(Plugin plugin, BridgeTravel travel, Messages messages, URI status, Duration gap) {
        this.plugin = plugin;
        this.travel = travel;
        this.messages = messages;
        this.status = status;
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
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(status).timeout(TIMEOUT).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 && response.statusCode() != 204) {
                throw new java.io.IOException("HTTP " + response.statusCode());
            }
            queues = QueueStatus.decode(response.body() == null ? "" : response.body());
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
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            return;
        }
        Optional<QueueStatus> next = announcer.next(queues, Instant.now());
        next.ifPresent(this::announce);
    }

    private void announce(QueueStatus queue) {
        Component name = MiniMessage.miniMessage().deserialize(queue.name());
        Component click = messages.get("queue-notify-click")
                .clickEvent(ClickEvent.runCommand("/minigames queue " + queue.key()))
                .hoverEvent(HoverEvent.showText(messages.get("queue-notify-hover", Placeholder.component("name", name))));
        Component text = messages.get("queue-notify", Placeholder.component("name", name),
                Placeholder.unparsed("queued", Integer.toString(queue.queued())),
                Placeholder.unparsed("needed", Integer.toString(queue.needed())),
                Placeholder.unparsed("max", Integer.toString(queue.max())),
                Placeholder.component("click", click));
        int[] count = {0};
        for (Player player : List.copyOf(Bukkit.getOnlinePlayers())) {
            player.getScheduler().run(plugin, t -> {
                if (travel.eligible(player)) {
                    player.sendMessage(text);
                }
            }, null);
            count[0]++;
        }
        plugin.getLogger().info("[queue-notify] " + queue.key() + " (" + queue.queued() + "/" + queue.max()
                + ") announced to up to " + count[0] + " player(s)");
    }
}
