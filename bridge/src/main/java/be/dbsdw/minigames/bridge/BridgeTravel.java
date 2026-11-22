package be.dbsdw.minigames.bridge;

import be.dbsdw.minigames.travel.Destination;
import be.dbsdw.minigames.travel.Reachability;
import be.dbsdw.minigames.travel.TicketIssuer;
import be.dbsdw.minigames.travel.TravelConstants;
import com.viaversion.viaversion.api.Via;
import fr.xephi.authme.api.v3.AuthMeApi;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.geysermc.floodgate.api.FloodgateApi;

/** Sends a survival player to the minigame server with a signed ticket. Folia-safe. */
public final class BridgeTravel {
    static final NamespacedKey COOKIE = new NamespacedKey(TravelConstants.COOKIE_NAMESPACE, TravelConstants.COOKIE_PATH);

    private final Plugin plugin;
    private final TicketIssuer issuer;
    private final String host;
    private final int port;
    private final Messages messages;

    /** {@code issuer} is null when the ticket secret is missing: travel is then refused. */
    BridgeTravel(Plugin plugin, TicketIssuer issuer, String host, int port, Messages messages) {
        this.plugin = plugin;
        this.issuer = issuer;
        this.host = host;
        this.port = port;
        this.messages = messages;
    }

    public void sendToMinigames(Player player) {
        if (issuer == null) {
            refuse(player, "unavailable", "ticket-secret not set");
            return;
        }
        Eligibility eligibility = eligibility(player);
        switch (eligibility) {
            case NOT_LOGGED_IN -> refuse(player, "not-logged-in", eligibility.name());
            case BEDROCK -> refuse(player, "bedrock", eligibility.name());
            case WRONG_VERSION -> refuse(player, "wrong-version", eligibility.name());
            case OK -> probeThenTransfer(player);
        }
    }

    private Eligibility eligibility(Player player) {
        UUID id = player.getUniqueId();
        boolean loggedIn = AuthMeApi.getInstance().isAuthenticated(player);
        boolean bedrock = Bukkit.getPluginManager().isPluginEnabled("floodgate") && FloodgateHook.isBedrock(id);
        boolean via = Bukkit.getPluginManager().isPluginEnabled("ViaVersion");
        int client = via ? ViaHook.clientProtocol(id) : 0;
        return Eligibility.check(loggedIn, bedrock, client);
    }

    private void probeThenTransfer(Player player) {
        UUID id = player.getUniqueId();
        String name = player.getName();
        Bukkit.getAsyncScheduler().runNow(plugin, probe -> {
            boolean reachable = Reachability.isReachable(host, port, TravelConstants.PROBE_TIMEOUT);
            player.getScheduler().run(plugin, task -> {
                if (!reachable) {
                    refuse(player, "unavailable", "minigame server " + host + ":" + port + " unreachable");
                    return;
                }
                player.sendMessage(messages.get("transferring"));
                player.storeCookie(COOKIE, issuer.issue(id, name, Destination.MINIGAMES));
                player.transfer(host, port);
                plugin.getLogger().info("[travel] " + name + " -> minigames");
            }, null);
        });
    }

    private void refuse(Player player, String messageKey, String reason) {
        player.sendMessage(messages.get(messageKey));
        plugin.getLogger().info("[travel] " + player.getName() + " refused: " + reason);
    }

    /** Separate class so Floodgate's API is only loaded when the plugin is present. */
    private static final class FloodgateHook {
        static boolean isBedrock(UUID id) {
            return FloodgateApi.getInstance().isFloodgatePlayer(id);
        }
    }

    /** Separate class so ViaVersion's API is only loaded when the plugin is present. */
    private static final class ViaHook {
        static int clientProtocol(UUID id) {
            return Via.getAPI().getPlayerProtocolVersion(id).getVersion();
        }
    }
}
