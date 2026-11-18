package be.dbsdw.minigames.core.travel;

import be.dbsdw.minigames.core.Messages;
import be.dbsdw.minigames.core.pack.PackHost;
import net.kyori.adventure.resource.ResourcePackStatus;
import be.dbsdw.minigames.travel.CookieReads;
import be.dbsdw.minigames.travel.TicketException;
import be.dbsdw.minigames.travel.TicketVerifier;
import be.dbsdw.minigames.travel.TravelConstants;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
import java.util.logging.Logger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/** Lets a player in only with a valid ticket from survival, checked before they enter the world. */
public final class ArrivalListener implements Listener {
    private final TicketVerifier verifier;
    private final Messages messages;
    private final Logger log;
    private final PackHost pack;

    /**
     * {@code verifier} is null when the ticket secret is invalid: everyone is then refused.
     * {@code pack} is null when no resource pack is hosted.
     */
    public ArrivalListener(TicketVerifier verifier, Messages messages, Logger log, PackHost pack) {
        this.verifier = verifier;
        this.messages = messages;
        this.log = log;
        this.pack = pack;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onConfigure(AsyncPlayerConnectionConfigureEvent event) {
        PlayerConfigurationConnection connection = event.getConnection();
        String name = connection.getProfile().getName();
        if (verifier == null) {
            connection.disconnect(messages.get("unavailable"));
            log.info("[travel] " + name + " refused: ticket-secret is invalid");
            return;
        }
        if (!connection.isTransferred()) {
            connection.disconnect(messages.get("no-ticket"));
            log.info("[travel] " + name + " refused: not transferred");
            return;
        }
        byte[] data = CookieReads.await(connection.retrieveCookie(CoreTravel.COOKIE), TravelConstants.COOKIE_TIMEOUT);
        try {
            verifier.verify(data, connection.getProfile().getId(), name);
        } catch (TicketException e) {
            connection.disconnect(messages.get("no-ticket"));
            log.info("[travel] " + name + " refused: " + e.logText());
            return;
        }
        connection.storeCookie(CoreTravel.COOKIE, new byte[0]);
        log.info("[travel] " + name + " arrived");
        if (pack != null) {
            ResourcePackStatus status = pack.sendAndWait(connection.getAudience(), 120);
            if (status != ResourcePackStatus.SUCCESSFULLY_LOADED) {
                connection.disconnect(messages.get(status == ResourcePackStatus.DECLINED ? "pack.declined" : "pack.failed"));
                log.info("[pack] " + name + " refused: pack " + (status == null ? "timed out" : status.name()));
            }
        }
    }
}
