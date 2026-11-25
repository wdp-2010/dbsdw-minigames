package be.dbsdw.minigames.core.match;

import be.dbsdw.minigames.core.Messages;
import be.dbsdw.minigames.core.api.GameType;
import be.dbsdw.minigames.core.api.MatchStarter;
import be.dbsdw.minigames.core.api.ModeSpec;
import be.dbsdw.minigames.core.arena.ArenaChunks;
import be.dbsdw.minigames.core.arena.ArenaCopier;
import be.dbsdw.minigames.core.arena.ArenaDefinition;
import be.dbsdw.minigames.core.arena.ArenaPool;
import be.dbsdw.minigames.core.pack.PackAssets;
import be.dbsdw.minigames.core.pack.PackItems;
import be.dbsdw.minigames.core.arena.BukkitArenaView;
import be.dbsdw.minigames.core.lobby.LobbyService;
import be.dbsdw.minigames.core.menu.GamesMenu;
import be.dbsdw.minigames.core.menu.MenuListener;
import be.dbsdw.minigames.core.menu.MenuModel;
import be.dbsdw.minigames.core.menu.ModesMenu;
import be.dbsdw.minigames.core.queue.QueueBook;
import be.dbsdw.minigames.core.queue.QueueEvent;
import be.dbsdw.minigames.core.queue.QueueSizes;
import be.dbsdw.minigames.travel.QueueStatus;
import be.dbsdw.minigames.core.room.RoomBook;
import be.dbsdw.minigames.core.room.RoomHooks;
import be.dbsdw.minigames.core.room.RoomService;
import be.dbsdw.minigames.core.solo.SoloMenu;
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
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import be.dbsdw.minigames.core.progress.ProgressMenu;
import be.dbsdw.minigames.core.progress.ProgressService;
import be.dbsdw.minigames.core.stats.StatsStore;
import be.dbsdw.minigames.core.voice.MatchVoice;
import be.dbsdw.minigames.core.voice.VoiceRule;

/** Turns queues into matches and matches back into lobby players. Ticked every server tick. */
public final class MatchManager implements MenuModel, RoomHooks, Listener, MatchStarter {
    /** The countdown ticks audibly for the last seconds only. */
    private static final int COUNTDOWN_SOUND_FROM = 5;
    private static final Title.Times COUNTDOWN_TIMES =
            Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ofMillis(200));

    private final Plugin plugin;
    /** Arenas whose chunks are held loaded for a running match, by arena name. */
    private final Map<String, ArenaDefinition> heldChunks = new HashMap<>();
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
    private final ProgressService progress;
    private final RoomService rooms;
    private final SoloMenu solo;
    /** Room id of each running room match. */
    private final Map<RunningMatch, Integer> roomOfMatch = new HashMap<>();
    /** Countdown second last shown in each queued player's action bar. */
    private final Map<UUID, Integer> barSeconds = new HashMap<>();
    private long tickCount;
    private final ArenaCopier copier;
    private final MatchVoice voice;
    /** Voice rule of public queue matches and the start value of new rooms. */
    private final VoiceRule defaultVoice;
    private long matchCount;
    /** The public queues with players waiting, as {@link QueueStatus} text for the survival server; set every second. */
    private volatile String statusText = "";

    public MatchManager(Plugin plugin, GameRegistryImpl registry, ArenaPool pool, QueueBook queues,
                        LobbyService lobby, Messages messages, int resultsTicks, RandomGenerator rnd,
                        StatsStore stats, ProgressService progress, MatchVoice voice, VoiceRule defaultVoice) {
        this.plugin = plugin;
        this.voice = voice;
        this.defaultVoice = defaultVoice;
        this.registry = registry;
        this.pool = pool;
        this.queues = queues;
        this.lobby = lobby;
        this.messages = messages;
        this.resultsTicks = resultsTicks;
        this.rnd = rnd;
        this.stats = stats;
        this.progress = progress;
        this.copier = new ArenaCopier(plugin);
        this.menus = new MenuListener(plugin, this, messages);
        this.rooms = new RoomService(plugin, new RoomBook(pool, rnd, defaultVoice), pool, messages, this);
        this.solo = new SoloMenu(plugin, messages, this::soloModes, this::gameIcon, this::modeIcon, rooms::startSolo);
        registry.onRegister(game -> game.modes().forEach(mode -> addQueues(game, mode)));
    }

    /** The mode's public queue, or one per team size when the game splits it (GameType#queueSizes). */
    private void addQueues(GameType game, ModeSpec mode) {
        if (!game.publicQueue(mode)) {
            return;
        }
        List<Integer> sizes;
        try {
            sizes = game.queueSizes(mode).stream().filter(n -> n != null && n >= 1).distinct().toList();
        } catch (RuntimeException e) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "[queue] queueSizes of " + game.id() + " failed", e);
            sizes = List.of();
        }
        if (sizes.isEmpty()) {
            queues.addMode(mode);
            return;
        }
        for (int perTeam : sizes) {
            queues.addQueue(QueueSizes.key(mode, perTeam), QueueSizes.sized(mode, perTeam));
        }
    }

    public MenuListener menuListener() {
        return menus;
    }

    public RoomService rooms() {
        return rooms;
    }

    /** The {@code /solo} menu; also its click listener. */
    public SoloMenu solo() {
        return solo;
    }

    /** The modes of the games that can be played alone against a bot, for {@code /solo}. */
    private List<ModeSpec> soloModes() {
        return registry.games().stream().filter(GameType::supportsSolo).flatMap(g -> g.modes().stream()).toList();
    }

    public void tick() {
        for (QueueEvent event : queues.tick()) {
            handle(event);
        }
        rooms.tick();
        for (RunningMatch match : List.copyOf(matchesByArena.values())) {
            if (match.tick()) {
                close(match, true);
            }
        }
        boolean fullSecond = ++tickCount % 20 == 0;
        if (fullSecond) {
            menus.refreshOpen();
            statusText = QueueStatus.encode(status());
        }
        queueBars(fullSecond);
    }

    /**
     * Players waiting in a queue see its state in the action bar, once a second; during a countdown
     * also the very tick its second changes, so the bar counts in step with the countdown title.
     */
    private void queueBars(boolean fullSecond) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            if (matchByPlayer.containsKey(id)) {
                barSeconds.remove(id);
                continue;
            }
            queues.queueOf(id).ifPresentOrElse(key -> {
                ModeSpec mode = queues.mode(key).orElseThrow();
                OptionalInt left = queues.secondsLeft(key);
                Integer shown = left.isPresent() ? left.getAsInt() : null;
                if (!fullSecond && Objects.equals(shown, barSeconds.get(id))) {
                    return;
                }
                if (shown == null) {
                    barSeconds.remove(id);
                } else {
                    barSeconds.put(id, shown);
                }
                Component modeName = queueName(key);
                player.sendActionBar(left.isPresent()
                        ? messages.get("queue.bar-countdown", Placeholder.component("mode", modeName),
                                Placeholder.unparsed("seconds", String.valueOf(left.getAsInt())))
                        : messages.get("queue.bar", Placeholder.component("mode", modeName),
                                Placeholder.unparsed("queued", String.valueOf(queues.size(key))),
                                Placeholder.unparsed("needed", String.valueOf(mode.startMinimum()))));
            }, () -> barSeconds.remove(id));
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
            case QueueEvent.Start(String queue, ModeSpec mode, ArenaDefinition arena, List<UUID> players) ->
                    start(queue, mode, arena, players);
        }
    }

    private void countdown(List<UUID> queued, int seconds) {
        Title title = Title.title(messages.get("queue.countdown.title", Placeholder.unparsed("seconds", String.valueOf(seconds))),
                messages.get("queue.countdown.subtitle"), COUNTDOWN_TIMES);
        players(queued).forEach(p -> {
            p.showTitle(title);
            if (seconds <= COUNTDOWN_SOUND_FROM) {
                PackItems.play(p, PackAssets.SOUND_COUNTDOWN, 0.8f, 1f);
            }
        });
    }

    private void start(String queue, ModeSpec mode, ArenaDefinition arena, List<UUID> players) {
        List<UUID> online = players.stream().filter(id -> Bukkit.getPlayer(id) != null).toList();
        Optional<GameType> game = registry.game(mode.gameId());
        World world = Bukkit.getWorld(arena.world());
        if (online.size() < mode.startMinimum() || game.isEmpty() || world == null) {
            if (world == null) {
                plugin.getLogger().severe("[match] arena " + arena.name() + ": world " + arena.world() + " is not loaded");
            }
            pool.release(arena.name());
            if (game.isPresent()) {
                queues.requeueFront(queue, online);
            }
            plugin.getLogger().info("[queue] " + queue + " start fell through: " + online.size() + " player(s) online");
            return;
        }
        open(game.get(), mode, arena, world, TeamSplit.split(online, mode.teams(), rnd), null, defaultVoice,
                java.util.Set.of());
    }

    /**
     * Starts a match with ready-made teams on the reserved arena; a copy of a template arena is built
     * first. False (arena released) if the copy could not be built.
     */
    private boolean open(GameType game, ModeSpec mode, ArenaDefinition arena, World world, List<List<UUID>> teams,
                         Integer roomId, VoiceRule voiceRule, java.util.Set<String> options) {
        Optional<ArenaDefinition> template = pool.templateOf(arena.name());
        if (template.isPresent()) {
            try {
                copier.place(world, template.get(), arena);
            } catch (RuntimeException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "[arena] could not build copy " + arena.name(), e);
                pool.release(arena.name());
                tell(teams.stream().flatMap(List::stream).toList(), messages.get("match.error"));
                return false;
            }
        }
        ArenaChunks.hold(plugin, world, arena);
        heldChunks.put(arena.name(), arena);
        RunningMatch[] self = new RunningMatch[1];
        RunningMatch match = new RunningMatch(game, mode, new BukkitArenaView(arena, world),
                teams, resultsTicks, messages, plugin.getLogger(), stats, progress, id -> {
                    queues.remove(id);
                    matchByPlayer.put(id, self[0]);
                }, voice, voiceRule, "dbsdw-" + (++matchCount), options);
        self[0] = match;
        matchesByArena.put(arena.name(), match);
        if (roomId != null) {
            roomOfMatch.put(match, roomId);
        }
        teams.forEach(team -> team.forEach(id -> matchByPlayer.put(id, match)));
        match.start();
        if (match.failed()) {
            close(match, true);
        }
        return true;
    }

    // RoomHooks

    @Override
    public boolean launch(int roomId, ModeSpec mode, ArenaDefinition arena, List<List<UUID>> teams,
                          VoiceRule voiceRule, List<Integer> bots, int botLevel, java.util.Set<String> options) {
        List<List<UUID>> online = teams.stream().map(team -> team.stream()
                .filter(id -> Bukkit.getPlayer(id) != null && !matchByPlayer.containsKey(id)).toList()).toList();
        Optional<GameType> game = registry.game(mode.gameId());
        World world = Bukkit.getWorld(arena.world());
        // A team may be empty when the room had fewer members than teams (solo start); a team that
        // had members but lost them all before the start still cancels it.
        boolean lostTeam = false;
        for (int i = 0; i < teams.size(); i++) {
            lostTeam |= !teams.get(i).isEmpty() && online.get(i).isEmpty();
        }
        boolean nobody = online.stream().allMatch(List::isEmpty);
        if (lostTeam || nobody || game.isEmpty() || world == null) {
            if (world == null) {
                plugin.getLogger().severe("[match] arena " + arena.name() + ": world " + arena.world() + " is not loaded");
            }
            pool.release(arena.name());
            plugin.getLogger().info("[room] room " + roomId + " start of " + mode.key() + " fell through");
            return false;
        }
        for (UUID id : online.stream().flatMap(List::stream).toList()) {
            Player player = Bukkit.getPlayer(id);
            Component block = player == null ? null : queueBlock(game.get(), player, mode);
            if (block != null) {
                player.sendMessage(block);
                pool.release(arena.name());
                plugin.getLogger().info("[room] room " + roomId + " start of " + mode.key() + ": " + player.getName()
                        + " may not play it now");
                return false;
            }
        }
        online.forEach(team -> team.forEach(queues::remove));
        boolean withBots = bots.stream().anyMatch(b -> b > 0);
        List<UUID> players = online.stream().flatMap(List::stream).toList();
        if (withBots && !roomBots(game.get(), players, mode, bots, botLevel)) {
            pool.release(arena.name());
            return false;
        }
        boolean opened = open(game.get(), mode, arena, world, online, roomId, voiceRule, options);
        if (!opened && withBots) {
            roomBots(game.get(), players, mode, bots.stream().map(b -> 0).toList(), botLevel);
        }
        return opened;
    }

    private boolean roomBots(GameType game, List<UUID> players, ModeSpec mode, List<Integer> bots, int level) {
        try {
            game.roomBots(players, mode, bots, level);
            return true;
        } catch (RuntimeException e) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "[room] roomBots of " + game.id() + " failed", e);
            return false;
        }
    }

    @Override
    public boolean startAlone(Player host, ModeSpec mode, java.util.function.IntConsumer startOnTeam) {
        Optional<GameType> game = registry.game(mode.gameId());
        if (game.isEmpty()) {
            return false;
        }
        try {
            return game.get().startAlone(host, mode, startOnTeam);
        } catch (RuntimeException e) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "[room] startAlone of " + game.get().id() + " failed", e);
            return false;
        }
    }

    @Override
    public List<GameType.RoomOption> roomOptions(ModeSpec mode) {
        Optional<GameType> game = registry.game(mode.gameId());
        if (game.isEmpty()) {
            return List.of();
        }
        try {
            return List.copyOf(game.get().roomOptions(mode));
        } catch (RuntimeException e) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "[room] roomOptions of " + game.get().id() + " failed", e);
            return List.of();
        }
    }

    @Override
    public Component optionLock(Player host, ModeSpec mode, GameType.RoomOption option) {
        Optional<GameType> game = registry.game(mode.gameId());
        if (game.isEmpty() || option.requiredLevel() <= 1) {
            return null;
        }
        int have = progress.level(host.getUniqueId(), game.get().id());
        if (have >= option.requiredLevel()) {
            return null;
        }
        return messages.get("progress.option-locked", Placeholder.component("game", game.get().displayName()),
                Placeholder.component("option", option.name()),
                Placeholder.unparsed("need", String.valueOf(option.requiredLevel())),
                Placeholder.unparsed("have", String.valueOf(have)));
    }

    @Override
    public List<Component> botLevels(ModeSpec mode) {
        Optional<GameType> game = registry.game(mode.gameId());
        if (game.isEmpty()) {
            return List.of();
        }
        try {
            return List.copyOf(game.get().roomBotLevels(mode));
        } catch (RuntimeException e) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "[room] roomBotLevels of " + game.get().id() + " failed", e);
            return List.of();
        }
    }

    // MatchStarter

    @Override
    public boolean available(UUID player) {
        return Bukkit.getPlayer(player) != null && !matchByPlayer.containsKey(player) && !rooms.inRoom(player);
    }

    @Override
    public MatchStarter.Result start(ModeSpec mode, List<List<UUID>> teams) {
        Optional<GameType> game = registry.game(mode.gameId());
        if (game.isEmpty() || registry.mode(mode.key()).isEmpty()) {
            return MatchStarter.Result.UNKNOWN_MODE;
        }
        List<UUID> everyone = teams.stream().flatMap(List::stream).toList();
        if (everyone.isEmpty() || !everyone.stream().allMatch(this::available)) {
            return MatchStarter.Result.PLAYER_BUSY;
        }
        Optional<ArenaDefinition> arena = pool.reserve(mode, rnd);
        if (arena.isEmpty()) {
            return MatchStarter.Result.NO_ARENA;
        }
        World world = Bukkit.getWorld(arena.get().world());
        if (world == null) {
            plugin.getLogger().severe("[match] arena " + arena.get().name() + ": world " + arena.get().world() + " is not loaded");
            pool.release(arena.get().name());
            return MatchStarter.Result.NO_ARENA;
        }
        everyone.forEach(queues::remove);
        plugin.getLogger().info("[match] " + mode.key() + " started directly by the game on " + arena.get().name());
        return open(game.get(), mode, arena.get(), world, teams, null, defaultVoice, java.util.Set.of())
                ? MatchStarter.Result.STARTED : MatchStarter.Result.NO_ARENA;
    }

    @Override
    public void openProgress(Player player) {
        ProgressMenu.open(player, progress, registry.games(), messages, this::openGames);
    }

    @Override
    public Component lockLore(Player viewer, ModeSpec mode) {
        return registry.game(mode.gameId()).map(g -> progress.lockLore(viewer.getUniqueId(), g, mode)).orElse(null);
    }

    @Override
    public Component lockReason(Player host, ModeSpec mode) {
        return registry.game(mode.gameId()).map(g -> progress.lockReason(host, g, mode)).orElse(null);
    }

    /** The game's reason why the player may not play the mode now, or null (a failing game is ignored). */
    private Component queueBlock(GameType game, Player player, ModeSpec mode) {
        try {
            return game.queueBlock(player, mode);
        } catch (RuntimeException e) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "[match] queueBlock of " + game.id() + " failed", e);
            return null;
        }
    }

    @Override
    public void leaveQueue(UUID player) {
        if (queues.remove(player)) {
            plugin.getLogger().info("[queue] " + player + " left the queue (room)");
        }
    }

    @Override
    public List<ModeSpec> modes() {
        return registry.games().stream().flatMap(g -> g.modes().stream()).toList();
    }

    @Override
    public ItemStack gameIcon(String gameId) {
        return registry.game(gameId).map(g -> {
            ItemStack icon = g.iconItem();
            icon.editMeta(meta -> meta.itemName(g.displayName()));
            return icon;
        }).orElseGet(() -> ItemStack.of(Material.PAPER));
    }

    @Override
    public ItemStack modeIcon(ModeSpec mode) {
        return registry.game(mode.gameId()).map(g -> g.modeIcon(mode)).orElseGet(() -> ItemStack.of(Material.PAPER));
    }

    /** Ends a match's bookkeeping: players back to the lobby (unless shutting down), arena free. */
    private void close(RunningMatch match, boolean toLobby) {
        String arena = match.arena().name();
        ArenaDefinition held = heldChunks.remove(arena);
        if (held != null) {
            if (pool.templateOf(arena).isPresent()) {
                try {
                    copier.clear(match.arena().world(), held);
                } catch (RuntimeException e) {
                    plugin.getLogger().log(java.util.logging.Level.SEVERE, "[arena] could not clear copy " + arena, e);
                }
            }
            ArenaChunks.release(plugin, match.arena().world(), held);
        }
        matchesByArena.remove(arena);
        match.releaseVoice();
        matchByPlayer.values().removeIf(m -> m == match);
        pool.release(arena);
        Integer roomId = roomOfMatch.remove(match);
        if (toLobby) {
            for (Player player : players(match.players())) {
                lobby.send(player);
                if (match.failed()) {
                    player.sendMessage(messages.get("match.error"));
                }
            }
            if (roomId != null) {
                rooms.matchEnded(roomId);
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
        if (rooms.inRoom(player.getUniqueId())) {
            rooms.leave(player);
            return;
        }
        player.sendMessage(messages.get("leave.nothing"));
    }

    public void quit(Player player) {
        rooms.quit(player);
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
                for (String key : queues.keysOf(mode.key())) {
                    List<UUID> queued = queues.removeQueue(key);
                    if (!stopping) {
                        tell(queued, messages.get("queue.closed"));
                    }
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
    public List<String> queueKeys(ModeSpec mode) {
        return queues.keysOf(mode.key());
    }

    /** The mode's name, followed by the team size for a sized queue: "TDM 2v2". */
    @Override
    public Component queueName(String queueKey) {
        Optional<ModeSpec> mode = queues.mode(queueKey);
        if (mode.isEmpty()) {
            return Component.text(queueKey);
        }
        Component name = MiniMessage.miniMessage().deserialize(mode.get().displayName());
        return queueKey.equals(mode.get().key()) ? name
                : name.append(Component.text(" " + queueKey.substring(queueKey.lastIndexOf(':') + 1)));
    }

    @Override
    public int needed(String queueKey) {
        return queues.mode(queueKey).map(ModeSpec::startMinimum).orElse(0);
    }

    /** Every public queue key, for {@code /queue}. */
    public List<String> queueKeys() {
        return queues.keys();
    }

    /** The public queues with players waiting (MiniMessage names), for the survival server. */
    private List<QueueStatus> status() {
        List<QueueStatus> result = new ArrayList<>();
        for (String key : queues.keys()) {
            int size = queues.size(key);
            ModeSpec mode = queues.mode(key).orElse(null);
            if (size == 0 || mode == null) {
                continue;
            }
            Optional<GameType> type = registry.game(mode.gameId());
            Component game = type.map(GameType::displayName).orElse(Component.empty());
            String name = MiniMessage.miniMessage().serialize(game.append(Component.text(" ")).append(queueName(key)));
            int level = type.map(t -> t.requiredLevel(mode)).orElse(0);
            result.add(new QueueStatus(key, name, size, mode.startMinimum(), mode.maximum(), mode.gameId(), level));
        }
        return result;
    }

    /** The latest {@link #status()} as text; safe to read from any thread. */
    public String statusText() {
        return statusText;
    }

    /**
     * Puts the player in the queue unless they already wait in it ({@code /queue}, the switch link,
     * arriving from survival through a queue notification). False when the queue does not exist.
     */
    public boolean join(Player player, String queueKey) {
        if (queues.mode(queueKey).isEmpty()) {
            player.sendMessage(messages.get("queue.unknown"));
            return false;
        }
        if (isQueued(player.getUniqueId(), queueKey)) {
            player.sendMessage(messages.get("queue.already", Placeholder.component("mode", queueName(queueKey))));
            return true;
        }
        toggle(player, queueKey);
        return true;
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
        Optional<ModeSpec> mode = queues.mode(modeKey);
        if (mode.isEmpty()) {
            return;
        }
        if (rooms.inRoom(player.getUniqueId())) {
            player.sendMessage(messages.get("room.in-room"));
            return;
        }
        Component name = queueName(modeKey);
        if (!isQueued(player.getUniqueId(), modeKey)) {
            // Joining the public queue needs the level; guests of a room do not (only its host is checked).
            Component block = registry.game(mode.get().gameId()).map(g -> {
                Component locked = progress.lockReason(player, g, mode.get());
                return locked != null ? locked : queueBlock(g, player, mode.get());
            }).orElse(null);
            if (block != null) {
                player.sendMessage(block);
                return;
            }
        }
        QueueBook.Toggle result = queues.toggle(player.getUniqueId(), modeKey);
        switch (result) {
            case JOINED -> player.sendMessage(messages.get("queue.joined", Placeholder.component("mode", name)));
            case MOVED -> player.sendMessage(messages.get("queue.moved", Placeholder.component("mode", name)));
            case LEFT -> player.sendMessage(messages.get("queue.left"));
        }
        if (result != QueueBook.Toggle.LEFT) {
            siblingHints(player, modeKey);
        }
        plugin.getLogger().info("[queue] " + player.getName() + " " + (result == QueueBook.Toggle.LEFT ? "left " : "joined ") + modeKey);
    }

    /** Tells a player who just joined a sized queue where players already wait for another size, with a switch link. */
    private void siblingHints(Player player, String queueKey) {
        for (String other : queues.waitingSiblings(queueKey)) {
            Component name = queueName(other);
            Component click = messages.get("queue.sibling-click")
                    .clickEvent(ClickEvent.runCommand("/queue " + other))
                    .hoverEvent(HoverEvent.showText(messages.get("queue.sibling-hover", Placeholder.component("mode", name))));
            player.sendMessage(messages.get("queue.sibling",
                    Placeholder.unparsed("queued", String.valueOf(queues.size(other))),
                    Placeholder.component("mode", name), Placeholder.component("click", click)));
        }
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
