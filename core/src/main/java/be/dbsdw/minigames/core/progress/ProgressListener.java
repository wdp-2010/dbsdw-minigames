package be.dbsdw.minigames.core.progress;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Loads a player's XP when they join and forgets it when they leave. */
public final class ProgressListener implements Listener {
    private final ProgressService progress;

    public ProgressListener(ProgressService progress) {
        this.progress = progress;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        progress.load(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        progress.unload(event.getPlayer().getUniqueId());
    }
}
