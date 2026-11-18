package be.dbsdw.minigames.core;

import be.dbsdw.minigames.core.admin.MgCommand;
import be.dbsdw.minigames.core.arena.ArenaDefinition;
import be.dbsdw.minigames.core.arena.ArenaPool;
import be.dbsdw.minigames.core.arena.ArenaStore;
import be.dbsdw.minigames.core.match.MatchManager;
import be.dbsdw.minigames.core.npc.GameNpc;
import be.dbsdw.minigames.core.queue.QueueBook;
import be.dbsdw.minigames.core.queue.QueueTimings;
import java.io.IOException;
import java.util.List;
import java.util.random.RandomGenerator;
import org.bukkit.plugin.PluginManager;
import be.dbsdw.minigames.core.api.GameRegistry;
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

    @Override
    public void onEnable() {
        saveDefaultConfig();
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
        getServer().getPluginManager().registerEvents(new ArrivalListener(verifier, messages, getLogger()), this);

        ArenaStore store = new ArenaStore(getDataFolder().toPath().resolve("arenas"), getLogger());
        List<ArenaDefinition> arenas;
        try {
            arenas = store.loadAll(world -> getServer().getWorld(world) != null);
        } catch (IOException e) {
            getLogger().severe("[arena] cannot read the arenas folder: " + e.getMessage());
            arenas = List.of();
        }
        getLogger().info("[arena] loaded " + arenas.size() + " arena(s)");
        ArenaPool pool = new ArenaPool(arenas);
        RandomGenerator rnd = new SecureRandom();
        QueueBook queues = new QueueBook(pool, new QueueTimings(getConfig().getInt("queue.countdown-seconds"),
                getConfig().getInt("queue.full-countdown-seconds")), rnd);
        LobbyService lobby = new LobbyService(this, messages);
        if (lobby.spawn().isEmpty()) {
            getLogger().warning("[lobby] lobby spawn not set — use /mg setlobby");
        }
        MatchManager manager = new MatchManager(this, games, pool, queues, lobby, messages,
                getConfig().getInt("match.results-seconds") * 20, rnd);
        matches = manager;
        GameNpc npc = new GameNpc(games::game, manager::openModes);

        PluginManager plugins = getServer().getPluginManager();
        plugins.registerEvents(new LobbyListener(lobby, travel, messages, manager::openGames, manager::quit), this);
        plugins.registerEvents(manager.menuListener(), this);
        plugins.registerEvents(npc, this);
        plugins.registerEvents(manager, this);

        CoreTravel commandTravel = travel;
        MgCommand mg = new MgCommand(games, pool, store, lobby, npc, getLogger());
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            if (commandTravel != null) {
                event.registrar().register("survival", "Terug naar survival", new SurvivalCommand(commandTravel));
            }
            event.registrar().register("leave", "Verlaat de wachtrij of het spel", new LeaveCommand(manager::leave));
            event.registrar().register("mg", "Minigames beheer", mg);
        });
        getServer().getScheduler().runTaskTimer(this, manager::tick, 1, 1);
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
