package be.dbsdw.minigames.bridge;

import be.dbsdw.minigames.travel.CookieReads;
import be.dbsdw.minigames.travel.TicketException;
import be.dbsdw.minigames.travel.TicketVerifier;
import be.dbsdw.minigames.travel.TravelConstants;
import fr.xephi.authme.api.v3.AuthMeApi;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
import java.util.logging.Logger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Players returning from the minigame server with a valid ticket skip AuthMe's login.
 * Runs at LOW, before AuthMe's pre-join dialog (HIGHEST). Survival never refuses anyone here:
 * without a valid ticket AuthMe simply asks for the password as usual.
 */
final class TicketLoginListener implements Listener {
    private final TicketVerifier verifier;
    private final Logger log;

    TicketLoginListener(TicketVerifier verifier, Logger log) {
        this.verifier = verifier;
        this.log = log;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onConfigure(AsyncPlayerConnectionConfigureEvent event) {
        PlayerConfigurationConnection connection = event.getConnection();
        if (!connection.isTransferred()) {
            return;
        }
        String name = connection.getProfile().getName();
        byte[] data = CookieReads.await(connection.retrieveCookie(BridgeTravel.COOKIE), TravelConstants.COOKIE_TIMEOUT);
        try {
            verifier.verify(data, connection.getProfile().getId(), name);
        } catch (TicketException e) {
            log.info("[travel] " + name + " ticket not accepted: " + e.logText());
            return;
        }
        connection.storeCookie(BridgeTravel.COOKIE, new byte[0]);
        AuthMeApi.getInstance().forceLoginFromProxy(name);
        log.info("[travel] " + name + " back from minigames, logged in by ticket");
    }
}
