package be.dbsdw.minigames.bridge;

import be.dbsdw.minigames.travel.TicketIssuer;
import be.dbsdw.minigames.travel.TicketSecrets;
import be.dbsdw.minigames.travel.TicketSigner;
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
        TicketIssuer issuer = signer == null ? null : new TicketIssuer(signer, Clock.systemUTC(), new SecureRandom());
        BridgeTravel travel = new BridgeTravel(this, issuer,
                getConfig().getString("minigames.host"), getConfig().getInt("minigames.port"), messages);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register("minigames", "Ga naar de minigames", new MinigamesCommand(travel)));
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
