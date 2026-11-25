package be.dbsdw.minigames.core.travel;

import be.dbsdw.minigames.core.queue.QueueSizes;
import be.dbsdw.minigames.travel.TravelConstants;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

/**
 * A player who clicked a queue notification in survival arrives with the queue's key in the
 * {@code dbsdw:queue} cookie and is put in that queue once in the lobby. The cookie is cleared
 * either way, so a later normal visit starts without a queue.
 */
public final class QueueArrival implements Listener {
    static final NamespacedKey COOKIE = new NamespacedKey(TravelConstants.COOKIE_NAMESPACE, TravelConstants.QUEUE_COOKIE_PATH);
    /** Lets the lobby set the player up first. */
    private static final long DELAY_TICKS = 20;

    private final Plugin plugin;
    private final BiPredicate<Player, String> join;
    private final Logger log;

    public QueueArrival(Plugin plugin, BiPredicate<Player, String> join, Logger log) {
        this.plugin = plugin;
        this.join = join;
        this.log = log;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!player.isTransferred()) {
            return;
        }
        UUID id = player.getUniqueId();
        player.retrieveCookie(COOKIE).whenComplete((data, error) -> Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player online = Bukkit.getPlayer(id);
            if (online == null || error != null || data == null || data.length == 0) {
                return;
            }
            online.storeCookie(COOKIE, new byte[0]);
            String key = new String(data, StandardCharsets.UTF_8);
            if (key.length() > 64 || !QueueSizes.KEY.matcher(key).matches()) {
                log.info("[queue] " + online.getName() + " arrived with a bad queue cookie");
                return;
            }
            log.info("[queue] " + online.getName() + " arrived from survival for " + key);
            join.test(online, key);
        }, DELAY_TICKS));
    }
}
