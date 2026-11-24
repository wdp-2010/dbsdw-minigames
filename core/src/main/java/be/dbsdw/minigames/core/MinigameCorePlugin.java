package be.dbsdw.minigames.core;

import be.dbsdw.minigames.core.admin.MgCommand;
import be.dbsdw.minigames.core.arena.ArenaDefinition;
import be.dbsdw.minigames.core.arena.ArenaCopies;
import be.dbsdw.minigames.core.arena.ArenaPool;
import be.dbsdw.minigames.core.arena.ArenaStore;
import be.dbsdw.minigames.core.match.MatchManager;
import be.dbsdw.minigames.core.npc.GameNpc;
import be.dbsdw.minigames.core.pack.PackHost;
import be.dbsdw.minigames.core.stats.Leaderboards;
import be.dbsdw.minigames.core.stats.StatsCommands;
import be.dbsdw.minigames.core.stats.StatsStore;
import be.dbsdw.minigames.core.queue.QueueBook;
import be.dbsdw.minigames.core.queue.QueueTimings;
import be.dbsdw.minigames.core.room.RoomCommand;
import be.dbsdw.minigames.core.voice.MatchVoice;
import be.dbsdw.minigames.core.voice.VoiceRule;
import be.dbsdw.minigames.core.voice.VoicechatBridge;
import be.dbsdw.minigames.core.solo.SoloCommand;
import java.io.IOException;
import java.util.List;
import java.util.random.RandomGenerator;
import org.bukkit.plugin.PluginManager;
import be.dbsdw.minigames.core.api.GameRegistry;
import be.dbsdw.minigames.core.api.MatchStarter;
import be.dbsdw.minigames.core.lobby.LeaveCommand;
import be.dbsdw.minigames.core.lobby.LobbyListener;
import be.dbsdw.minigames.core.lobby.LobbyService;
import be.dbsdw.minigames.core.match.GameRegistryImpl;
import be.dbsdw.minigames.core.travel.ArrivalListener;
import be.dbsdw.minigames.core.travel.CoreTravel;
import be.dbsdw.minigames.core.travel.SurvivalCommand;
import be.dbsdw.minigames.travel.Destination;
import be.dbsdw.minigames.travel.NonceCache;
import be.dbsdw.minigames.travel.TicketIssuer;
import be.dbsdw.minigames.travel.TicketSecrets;
import be.dbsdw.minigames.travel.TicketSigner;
import be.dbsdw.minigames.travel.TicketVerifier;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.security.SecureRandom;
import java.time.Clock;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class MinigameCorePlugin extends JavaPlugin {
    private final GameRegistryImpl games = new GameRegistryImpl();
    private CoreTravel travel;
    private MatchManager matches;
    private StatsStore stats;
    private PackHost pack;
    private Leaderboards leaderboards;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // Write keys added by a newer version into an existing config.yml, so they can be edited.
        getConfig().options().copyDefaults(true);
        saveConfig();
        Messages messages = Messages.load(this);
        getServer().getServicesManager().register(GameRegistry.class, games, this, ServicePriority.Normal);
        TicketSigner signer = loadSigner();
        TicketVerifier verifier = null;
        if (signer != null) {
            Clock clock = Clock.systemUTC();
            verifier = new TicketVerifier(signer, Destination.MINIGAMES, clock, new NonceCache(clock));
            travel = new CoreTravel(this, new TicketIssuer(signer, clock, new SecureRandom()),
                    getConfig().getString("survival.host"), getConfig().getInt("survival.port"), messages);
        }
        String packUrl = getConfig().getString("pack.public-url", "");
        if (packUrl.isBlank()) {
            getLogger().warning("[pack] pack.public-url is empty - players join without a resource pack");
        } else {
            pack = PackHost.start(getDataFolder().toPath().resolve(getConfig().getString("pack.file", "pack.zip")),
                    getConfig().getString("pack.bind", "0.0.0.0"), getConfig().getInt("pack.port"), packUrl,
                    messages, getLogger());
        }
        getServer().getPluginManager().registerEvents(new ArrivalListener(verifier, messages, getLogger(), pack), this);
        stats = StatsStore.open(getDataFolder().toPath().resolve("stats.db"), getLogger());

        ArenaStore store = new ArenaStore(getDataFolder().toPath().resolve("arenas"), getLogger());
        List<ArenaDefinition> arenas;
        try {
            arenas = store.loadAll(world -> getServer().getWorld(world) != null);
        } catch (IOException e) {
            getLogger().severe("[arena] cannot read the arenas folder: " + e.getMessage());
            arenas = List.of();
        }
        getLogger().info("[arena] loaded " + arenas.size() + " arena(s)");
        ArenaPool pool = new ArenaPool(arenas,
                Math.max(16, getConfig().getInt("arenas.copy-spacing", ArenaCopies.DEFAULT_SPACING)));
        RandomGenerator rnd = new SecureRandom();
        QueueBook queues = new QueueBook(pool, new QueueTimings(getConfig().getInt("queue.countdown-seconds"),
                getConfig().getInt("queue.full-countdown-seconds")), rnd);
        LobbyService lobby = new LobbyService(this, messages);
        if (lobby.spawn().isEmpty()) {
            getLogger().warning("[lobby] lobby spawn not set — use /mg setlobby");
        }
        MatchManager manager = new MatchManager(this, games, pool, queues, lobby, messages,
                getConfig().getInt("match.results-seconds") * 20, rnd, stats, loadVoice(), voiceRule());
        matches = manager;
        getServer().getServicesManager().register(MatchStarter.class, manager, this, ServicePriority.Normal);
        GameNpc npc = new GameNpc(games::game, manager::openModes);
        StatsCommands statsCommands = new StatsCommands(this, stats, games, messages);
        Leaderboards boards = new Leaderboards(this, stats, games, messages);
        leaderboards = boards;

        PluginManager plugins = getServer().getPluginManager();
        plugins.registerEvents(new LobbyListener(lobby, travel, messages, manager::openGames,
                statsCommands.ownStats(), manager.rooms()::openOrCreate, manager::quit,
                getLogger()), this);
        plugins.registerEvents(manager.menuListener(), this);
        plugins.registerEvents(manager.rooms().listener(), this);
        plugins.registerEvents(manager.solo(), this);
        plugins.registerEvents(npc, this);
        plugins.registerEvents(manager, this);

        CoreTravel commandTravel = travel;
        MgCommand mg = new MgCommand(games, pool, store, lobby, npc, boards, getLogger());
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            if (commandTravel != null) {
                event.registrar().register("survival", "Terug naar survival", new SurvivalCommand(commandTravel));
            }
            event.registrar().register("leave", "Verlaat de wachtrij of het spel", new LeaveCommand(manager::leave));
            event.registrar().register("mg", "Minigames beheer", mg);
            event.registrar().register("room", "Speel met je vrienden", new RoomCommand(manager.rooms(), messages));
            event.registrar().register("solo", "Speel alleen tegen een bot", new SoloCommand(manager.solo()::open));
            event.registrar().register("stats", "Bekijk statistieken", statsCommands.statsCommand());
            event.registrar().register("top", "Bekijk de ranglijst", statsCommands.topCommand());
        });
        getServer().getScheduler().runTaskTimer(this, manager::tick, 1, 1);
        getServer().getScheduler().runTaskTimer(this, boards::tick, 1, 1);
        getLogger().info("MinigameCore " + getPluginMeta().getVersion() + " enabled");
    }

    @Override
    public void onDisable() {
        if (matches != null) {
            matches.shutdown();
        }
        if (travel != null) {
            travel.sendAllToSurvivalForShutdown();
        }
        if (leaderboards != null) {
            leaderboards.removeAll();
        }
        if (pack != null) {
            pack.stop();
        }
        if (stats != null) {
            stats.close();
        }
    }

    /** Team voice groups when Simple Voice Chat is installed; otherwise nothing happens. */
    private MatchVoice loadVoice() {
        if (getServer().getPluginManager().getPlugin("voicechat") == null) {
            getLogger().info("[voice] Simple Voice Chat not installed: no voice groups in matches");
            return MatchVoice.NONE;
        }
        try {
            return VoicechatBridge.create(this);
        } catch (LinkageError | RuntimeException e) {
            getLogger().log(java.util.logging.Level.WARNING, "[voice] could not hook into Simple Voice Chat", e);
            return MatchVoice.NONE;
        }
    }

    private VoiceRule voiceRule() {
        String value = getConfig().getString("voice.rule", "team");
        return VoiceRule.parse(value).orElseGet(() -> {
            getLogger().warning("[voice] voice.rule '" + value + "' is not team, all or nearby; using team");
            return VoiceRule.TEAM;
        });
    }

    /**
     * The shared ticket signer. A blank secret is generated and saved; an invalid one returns null,
     * which makes every join fail closed.
     */
    private TicketSigner loadSigner() {
        String configured = getConfig().getString("ticket-secret", "");
        if (configured.isBlank()) {
            byte[] secret = TicketSecrets.generate(new SecureRandom());
            getConfig().set("ticket-secret", TicketSecrets.toBase64(secret));
            saveConfig();
            getLogger().warning("Generated a new ticket-secret in plugins/MinigameCore/config.yml."
                    + " Copy it to plugins/DbsdwBridge/config.yml on the survival server.");
            return new TicketSigner(secret);
        }
        try {
            return new TicketSigner(TicketSecrets.fromBase64(configured));
        } catch (IllegalArgumentException e) {
            getLogger().severe("ticket-secret in plugins/MinigameCore/config.yml is invalid"
                    + " — every join is refused until it is fixed");
            return null;
        }
    }
}
