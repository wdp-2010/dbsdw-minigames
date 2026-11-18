package be.dbsdw.minigames.core.travel;

import be.dbsdw.minigames.core.Messages;
import be.dbsdw.minigames.travel.Destination;
import be.dbsdw.minigames.travel.Reachability;
import be.dbsdw.minigames.travel.TicketIssuer;
import be.dbsdw.minigames.travel.TravelConstants;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Sends players back to survival with a signed ticket. */
public final class CoreTravel {
    static final NamespacedKey COOKIE = new NamespacedKey(TravelConstants.COOKIE_NAMESPACE, TravelConstants.COOKIE_PATH);

    private final Plugin plugin;
    private final TicketIssuer issuer;
    private final String host;
    private final int port;
    private final Messages messages;

    public CoreTravel(Plugin plugin, TicketIssuer issuer, String host, int port, Messages messages) {
        this.plugin = plugin;
        this.issuer = issuer;
        this.host = host;
        this.port = port;
        this.messages = messages;
    }

    public void sendToSurvival(Player player) {
        UUID id = player.getUniqueId();
        String name = player.getName();
        Bukkit.getAsyncScheduler().runNow(plugin, probe -> {
            boolean reachable = Reachability.isReachable(host, port, TravelConstants.PROBE_TIMEOUT);
            player.getScheduler().run(plugin, task -> {
                if (!reachable) {
                    player.sendMessage(messages.get("survival-unavailable"));
                    plugin.getLogger().info("[travel] " + name + " refused: survival " + host + ":" + port + " unreachable");
                    return;
                }
                player.sendMessage(messages.get("to-survival"));
                transfer(player, id, name);
            }, null);
        });
    }

    /** On shutdown: everyone online goes to survival instead of being kicked. No probe; the server is going down. */
    public void sendAllToSurvivalForShutdown() {
        List<? extends Player> online = List.copyOf(Bukkit.getOnlinePlayers());
        for (Player player : online) {
            player.sendMessage(messages.get("shutdown"));
            transfer(player, player.getUniqueId(), player.getName());
        }
        if (!online.isEmpty()) {
            plugin.getLogger().info("[travel] shutdown: sent " + online.size() + " player(s) to survival");
        }
    }

    private void transfer(Player player, UUID id, String name) {
        player.storeCookie(COOKIE, issuer.issue(id, name, Destination.SURVIVAL));
        player.transfer(host, port);
        plugin.getLogger().info("[travel] " + name + " -> survival");
    }
}
