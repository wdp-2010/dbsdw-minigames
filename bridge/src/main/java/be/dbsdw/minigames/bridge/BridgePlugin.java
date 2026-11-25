package be.dbsdw.minigames.bridge;

import be.dbsdw.minigames.travel.Destination;
import be.dbsdw.minigames.travel.NonceCache;
import be.dbsdw.minigames.travel.TicketIssuer;
import be.dbsdw.minigames.travel.TicketSecrets;
import be.dbsdw.minigames.travel.TicketSigner;
import be.dbsdw.minigames.travel.TicketVerifier;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.security.SecureRandom;
import java.time.Clock;
import org.bukkit.plugin.java.JavaPlugin;

public final class BridgePlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        saveDefaultConfig();
        Messages messages = Messages.load(this);
        TicketSigner signer = loadSigner();
        Clock clock = Clock.systemUTC();
        TicketIssuer issuer = signer == null ? null : new TicketIssuer(signer, clock, new SecureRandom());
        if (signer != null) {
            TicketVerifier verifier = new TicketVerifier(signer, Destination.SURVIVAL, clock, new NonceCache(clock));
            getServer().getPluginManager().registerEvents(new TicketLoginListener(verifier, getLogger()), this);
        }
        BridgeTravel travel = new BridgeTravel(this, issuer,
                getConfig().getString("minigames.host"), getConfig().getInt("minigames.port"), messages);

        QueueNotifier.start(this, travel, messages, getConfig().getString("queue-notify.status-url", ""),
                getConfig().getInt("queue-notify.minutes-between", 10), getConfig().getInt("queue-notify.poll-seconds", 15));

        MinigamesNpc npc = new MinigamesNpc(travel);
        getServer().getPluginManager().registerEvents(npc, this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register("minigames", "Ga naar de minigames",
                        new MinigamesCommand(travel, npc, messages)));
        getLogger().info("DbsdwBridge " + getPluginMeta().getVersion() + " enabled");
    }

    /** The shared ticket signer, or null (travel disabled) when the secret is missing or invalid. */
    private TicketSigner loadSigner() {
        try {
            return new TicketSigner(TicketSecrets.fromBase64(getConfig().getString("ticket-secret")));
        } catch (IllegalArgumentException e) {
            getLogger().severe("ticket-secret is missing or invalid in plugins/DbsdwBridge/config.yml"
                    + " — travel to minigames is disabled until it is set");
            return null;
        }
    }
}
