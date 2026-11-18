package be.dbsdw.minigames.core.match;

import be.dbsdw.minigames.core.Messages;
import be.dbsdw.minigames.core.api.GameType;
import be.dbsdw.minigames.core.api.ModeSpec;
import be.dbsdw.minigames.core.arena.ArenaDefinition;
import be.dbsdw.minigames.core.arena.ArenaPool;
import be.dbsdw.minigames.core.arena.BukkitArenaView;
import be.dbsdw.minigames.core.lobby.LobbyService;
import be.dbsdw.minigames.core.menu.GamesMenu;
import be.dbsdw.minigames.core.menu.MenuListener;
import be.dbsdw.minigames.core.menu.MenuModel;
import be.dbsdw.minigames.core.menu.ModesMenu;
import be.dbsdw.minigames.core.queue.QueueBook;
import be.dbsdw.minigames.core.queue.QueueEvent;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.random.RandomGenerator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.Plugin;
import be.dbsdw.minigames.core.stats.StatsStore;

/** Turns queues into matches and matches back into lobby players. Ticked every server tick. */
public final class MatchManager implements MenuModel, Listener {
    private static final Title.Times COUNTDOWN_TIMES =
            Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ofMillis(200));

    private final Plugin plugin;
    private final GameRegistryImpl registry;
    private final ArenaPool pool;
    private final QueueBook queues;
    private final LobbyService lobby;
    private final Messages messages;
    private final int resultsTicks;
    private final RandomGenerator rnd;
    private final MenuListener menus;
    private final Map<String, RunningMatch> matchesByArena = new LinkedHashMap<>();
    private final Map<UUID, RunningMatch> matchByPlayer = new HashMap<>();
    private final StatsStore stats;
    private long tickCount;

    public MatchManager(Plugin plugin, GameRegistryImpl registry, ArenaPool pool, QueueBook queues,
                        LobbyService lobby, Messages messages, int resultsTicks, RandomGenerator rnd,
                        StatsStore stats) {
        this.plugin = plugin;
        this.registry = registry;
        this.pool = pool;
        this.queues = queues;
        this.lobby = lobby;
        this.messages = messages;
        this.resultsTicks = resultsTicks;
        this.rnd = rnd;
        this.stats = stats;
        this.menus = new MenuListener(plugin, this, messages);
        registry.onRegister(game -> game.modes().forEach(queues::addMode));
    }

    public MenuListener menuListener() {
        return menus;
    }

    public void tick() {
        for (QueueEvent event : queues.tick()) {
            handle(event);
        }
        for (RunningMatch match : List.copyOf(matchesByArena.values())) {
            if (match.tick()) {
                close(match, true);
            }
        }
        if (++tickCount % 20 == 0) {
            menus.refreshOpen();
        }
    }

    private void handle(QueueEvent event) {
        switch (event) {
            case QueueEvent.CountdownStarted(String mode, String arena, int seconds) -> {
                plugin.getLogger().info("[queue] " + mode + " countdown " + seconds + "s on arena " + arena);
                countdown(queues.starting(mode), seconds);
            }
            case QueueEvent.CountdownTick(String mode, int seconds, List<UUID> queued) -> countdown(queued, seconds);
            case QueueEvent.CountdownCancelled(String mode, List<UUID> queued) -> {
                plugin.getLogger().info("[queue] " + mode + " countdown cancelled");
                tell(queued, messages.get("queue.cancelled"));
            }
            case QueueEvent.WaitingForArena(String mode, List<UUID> queued) -> {
                plugin.getLogger().info("[queue] " + mode + " waiting for a free arena");
                tell(queued, messages.get("queue.waiting"));
            }
            case QueueEvent.Start(ModeSpec mode, ArenaDefinition arena, List<UUID> players) -> start(mode, arena, players);
        }
    }

    private void countdown(List<UUID> queued, int seconds) {
        Title title = Title.title(messages.get("queue.countdown.title", Placeholder.unparsed("seconds", String.valueOf(seconds))),
                messages.get("queue.countdown.subtitle"), COUNTDOWN_TIMES);
        players(queued).forEach(p -> p.showTitle(title));
    }

    private void start(ModeSpec mode, ArenaDefinition arena, List<UUID> players) {
        List<UUID> online = players.stream().filter(id -> Bukkit.getPlayer(id) != null).toList();
        Optional<GameType> game = registry.game(mode.gameId());
        World world = Bukkit.getWorld(arena.world());
        if (online.size() < mode.startMinimum() || game.isEmpty() || world == null) {
            if (world == null) {
                plugin.getLogger().severe("[match] arena " + arena.name() + ": world " + arena.world() + " is not loaded");
            }
            pool.release(arena.name());
            if (game.isPresent()) {
                queues.requeueFront(mode.key(), online);
            }
            plugin.getLogger().info("[queue] " + mode.key() + " start fell through: " + online.size() + " player(s) online");
            return;
        }
        RunningMatch match = new RunningMatch(game.get(), mode, new BukkitArenaView(arena, world),
                TeamSplit.split(online, mode.teams(), rnd), resultsTicks, messages, plugin.getLogger(), stats);
        matchesByArena.put(arena.name(), match);
        online.forEach(id -> matchByPlayer.put(id, match));
        match.start();
        if (match.failed()) {
            close(match, true);
        }
    }

    /** Ends a match's bookkeeping: players back to the lobby (unless shutting down), arena free. */
    private void close(RunningMatch match, boolean toLobby) {
        String arena = match.arena().name();
        matchesByArena.remove(arena);
        matchByPlayer.values().removeIf(m -> m == match);
        pool.release(arena);
        if (toLobby) {
            for (Player player : players(match.players())) {
                lobby.send(player);
                if (match.failed()) {
                    player.sendMessage(messages.get("match.error"));
                }
            }
        }
    }

    public void openGames(Player player) {
        if (!inMatch(player.getUniqueId())) {
            GamesMenu.open(player, this, messages);
        }
    }

    public void openModes(Player player, String gameId) {
        if (!inMatch(player.getUniqueId())) {
            registry.game(gameId).ifPresent(game -> ModesMenu.open(player, game, this, messages));
        }
    }

    /** {@code /leave}: out of the match or queue, back to the lobby. */
    public void leave(Player player) {
        RunningMatch match = matchByPlayer.remove(player.getUniqueId());
        if (match != null) {
            match.leave(player);
            lobby.send(player);
            plugin.getLogger().info("[match] " + player.getName() + " left " + match.mode().key());
            return;
        }
        if (queues.remove(player.getUniqueId())) {
            player.sendMessage(messages.get("queue.left"));
            plugin.getLogger().info("[queue] " + player.getName() + " left the queue");
            return;
        }
        player.sendMessage(messages.get("leave.nothing"));
    }

    public void quit(Player player) {
        if (queues.remove(player.getUniqueId())) {
            plugin.getLogger().info("[queue] " + player.getName() + " left the queue (quit)");
        }
        RunningMatch match = matchByPlayer.remove(player.getUniqueId());
        if (match != null) {
            match.leave(player);
            plugin.getLogger().info("[match] " + player.getName() + " left " + match.mode().key() + " (quit)");
        }
    }

    /** Server stopping: abort every match so games clean up, before players are transferred. */
    public void shutdown() {
        for (RunningMatch match : List.copyOf(matchesByArena.values())) {
            match.abort("server stopping");
            close(match, false);
        }
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        // While the server stops, game plugins disable before MinigameCore: end their matches quietly,
        // players are transferred to survival right after.
        boolean stopping = Bukkit.isStopping();
        for (GameType game : registry.gamesOf(event.getPlugin())) {
            for (RunningMatch match : List.copyOf(matchesByArena.values())) {
                if (match.game() == game) {
                    match.abort(stopping ? "server stopping" : "game " + game.id() + " disabled");
                    List<Player> players = players(match.players());
                    close(match, !stopping);
                    if (!stopping) {
                        tell(players.stream().map(Player::getUniqueId).toList(), messages.get("match.error"));
                    }
                }
            }
            for (ModeSpec mode : game.modes()) {
                List<UUID> queued = queues.removeMode(mode.key());
                if (!stopping) {
                    tell(queued, messages.get("queue.closed"));
                }
            }
            registry.unregister(game.id());
            plugin.getLogger().info("[match] game " + game.id() + " unregistered (plugin disabled)");
        }
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        for (ArenaDefinition arena : pool.all()) {
            for (String key : arena.modes()) {
                if (registry.mode(key).isEmpty()) {
                    plugin.getLogger().warning("[arena] " + arena.name() + ": mode " + key + " is not provided by any game");
                }
            }
        }
    }

    // MenuModel

    @Override
    public Collection<GameType> games() {
        return registry.games();
    }

    @Override
    public int queued(String modeKey) {
        return queues.size(modeKey);
    }

    @Override
    public OptionalInt secondsLeft(String modeKey) {
        return queues.secondsLeft(modeKey);
    }

    @Override
    public boolean anyArena(ModeSpec mode) {
        return pool.anyFits(mode);
    }

    @Override
    public boolean isQueued(UUID player, String modeKey) {
        return queues.queueOf(player).filter(modeKey::equals).isPresent();
    }

    @Override
    public boolean inMatch(UUID player) {
        return matchByPlayer.containsKey(player);
    }

    @Override
    public void toggle(Player player, String modeKey) {
        if (inMatch(player.getUniqueId())) {
            return;
        }
        Optional<ModeSpec> mode = registry.mode(modeKey);
        if (mode.isEmpty()) {
            return;
        }
        Component name = MiniMessage.miniMessage().deserialize(mode.get().displayName());
        QueueBook.Toggle result = queues.toggle(player.getUniqueId(), modeKey);
        switch (result) {
            case JOINED -> player.sendMessage(messages.get("queue.joined", Placeholder.component("mode", name)));
            case MOVED -> player.sendMessage(messages.get("queue.moved", Placeholder.component("mode", name)));
            case LEFT -> player.sendMessage(messages.get("queue.left"));
        }
        plugin.getLogger().info("[queue] " + player.getName() + " " + (result == QueueBook.Toggle.LEFT ? "left " : "joined ") + modeKey);
    }

    private static List<Player> players(Collection<UUID> ids) {
        List<Player> result = new ArrayList<>();
        ids.stream().map(Bukkit::getPlayer).filter(Objects::nonNull).forEach(result::add);
        return result;
    }

    private static void tell(Collection<UUID> ids, Component message) {
        players(ids).forEach(p -> p.sendMessage(message));
    }
}
