package be.dbsdw.minigames.core;

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
            CoreTravel commandTravel = travel;
            getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                    event.registrar().register("survival", "Terug naar survival", new SurvivalCommand(commandTravel)));
        }
        getServer().getPluginManager().registerEvents(new ArrivalListener(verifier, messages, getLogger()), this);
        LobbyService lobby = new LobbyService(this, messages);
        // wired in MatchManager (plan 2, task 7): opening the games menu, quit and /leave handling
        getServer().getPluginManager().registerEvents(
                new LobbyListener(lobby, travel, messages, player -> { }, player -> { }), this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register("leave", "Verlaat de wachtrij of het spel", new LeaveCommand(player -> { })));
        if (lobby.spawn().isEmpty()) {
            getLogger().warning("[lobby] lobby spawn not set — use /mg setlobby");
        }
        getLogger().info("MinigameCore " + getPluginMeta().getVersion() + " enabled");
    }

    @Override
    public void onDisable() {
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
