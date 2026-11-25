package be.dbsdw.minigames.core.progress;

import be.dbsdw.minigames.core.Messages;
import be.dbsdw.minigames.core.api.GameRegistry;
import be.dbsdw.minigames.core.api.GameType;
import be.dbsdw.minigames.core.stats.StatsStore;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Map;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** {@code /level [player]}: the minigames level and the level in each game. */
public final class LevelCommand implements BasicCommand {
    private final Plugin plugin;
    private final StatsStore store;
    private final ProgressService progress;
    private final GameRegistry games;
    private final Messages messages;
    private final Consumer<Player> openMenu;

    /** {@code store} is null when the database could not be opened. */
    public LevelCommand(Plugin plugin, StatsStore store, ProgressService progress, GameRegistry games,
                        Messages messages, Consumer<Player> openMenu) {
        this.openMenu = openMenu;
        this.plugin = plugin;
        this.store = store;
        this.progress = progress;
        this.games = games;
        this.messages = messages;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        if (args.length == 0 && sender instanceof Player self) {
            openMenu.accept(self);
            return;
        }
        String name = args.length > 0 ? args[0] : sender.getName();
        if (store == null) {
            sender.sendMessage(messages.get("stats.unavailable"));
            return;
        }
        store.profile(name).thenCompose(profile -> profile.isEmpty()
                ? java.util.concurrent.CompletableFuture.completedFuture(java.util.Optional.<Map.Entry<String, Map<String, Long>>>empty())
                : store.xp(profile.get().id()).thenApply(xp -> java.util.Optional.of(Map.entry(profile.get().name(), xp))))
                .whenComplete((found, error) -> {
                    if (!plugin.isEnabled()) {
                        return;
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (error != null) {
                            plugin.getLogger().severe("[progress] /level failed: " + error.getMessage());
                            sender.sendMessage(messages.get("stats.unavailable"));
                        } else if (found.isEmpty()) {
                            sender.sendMessage(messages.get("progress.unknown-player"));
                        } else {
                            show(sender, found.get().getKey(), found.get().getValue());
                        }
                    });
                });
    }

    private void show(CommandSender sender, String player, Map<String, Long> xp) {
        sender.sendMessage(messages.get("progress.title", Placeholder.unparsed("player", player)));
        if (xp.getOrDefault(ProgressService.ACCOUNT, 0L) <= 0) {
            sender.sendMessage(messages.get("progress.none"));
            return;
        }
        sender.sendMessage(line("progress.account", Component.empty(), xp.get(ProgressService.ACCOUNT)));
        for (GameType game : games.games()) {
            long total = xp.getOrDefault(game.id(), 0L);
            if (total > 0) {
                sender.sendMessage(line("progress.game", game.displayName(), total));
            }
        }
    }

    private Component line(String key, Component game, long total) {
        LevelCurve curve = progress.curve();
        long missing = curve.xpToNext(total);
        int level = curve.levelOf(total);
        return messages.get(key, Placeholder.component("game", game),
                Placeholder.unparsed("level", String.valueOf(level)),
                Placeholder.unparsed("xp", String.valueOf(total)),
                Placeholder.unparsed("missing", String.valueOf(missing)),
                Placeholder.component("next", missing == 0 ? messages.get("progress.max")
                        : Component.text(level + 1)));
    }
}
