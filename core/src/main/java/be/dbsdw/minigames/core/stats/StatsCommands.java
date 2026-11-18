package be.dbsdw.minigames.core.stats;

import be.dbsdw.minigames.core.Messages;
import be.dbsdw.minigames.core.api.GameRegistry;
import be.dbsdw.minigames.core.api.GameType;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** {@code /stats [player]} and {@code /top <stat> [game]}. */
public final class StatsCommands {
    private final Plugin plugin;
    private final StatsStore store;
    private final GameRegistry games;
    private final Messages messages;

    /** {@code store} is null when the database could not be opened. */
    public StatsCommands(Plugin plugin, StatsStore store, GameRegistry games, Messages messages) {
        this.plugin = plugin;
        this.store = store;
        this.games = games;
        this.messages = messages;
    }

    public void showStats(CommandSender sender, String name) {
        if (store == null) {
            sender.sendMessage(messages.get("stats.unavailable"));
            return;
        }
        store.profile(name).whenComplete((profile, error) -> onMain(() -> {
            if (error != null) {
                plugin.getLogger().severe("[stats] /stats failed: " + error.getMessage());
                sender.sendMessage(messages.get("stats.unavailable"));
                return;
            }
            if (profile.isEmpty()) {
                sender.sendMessage(messages.get("stats.none", Placeholder.unparsed("player", name)));
                return;
            }
            sender.sendMessage(messages.get("stats.header", Placeholder.unparsed("player", profile.get().name())));
            for (GameType game : games.games()) {
                Map<String, Long> values = profile.get().games().get(game.id());
                if (values == null) {
                    continue;
                }
                sender.sendMessage(messages.get("stats.game", Placeholder.component("game", game.displayName())));
                for (String stat : StatNames.of(game)) {
                    sender.sendMessage(messages.get("stats.line",
                            Placeholder.component("stat", StatNames.display(game, stat, messages)),
                            Placeholder.unparsed("value", Long.toString(values.getOrDefault(stat, 0L)))));
                }
            }
        }));
    }

    public void showTop(CommandSender sender, String stat, String gameId) {
        if (store == null) {
            sender.sendMessage(messages.get("stats.unavailable"));
            return;
        }
        Optional<GameType> game = gameId == null ? games.games().stream().findFirst() : games.game(gameId);
        if (game.isEmpty() || !StatNames.of(game.get()).contains(stat)) {
            sender.sendMessage(messages.get("stats.unknown-stat", Placeholder.unparsed("stat", stat)));
            return;
        }
        GameType g = game.get();
        store.top(g.id(), stat, 10).whenComplete((rows, error) -> onMain(() -> {
            if (error != null) {
                plugin.getLogger().severe("[stats] /top failed: " + error.getMessage());
                sender.sendMessage(messages.get("stats.unavailable"));
                return;
            }
            sender.sendMessage(messages.get("stats.top-header",
                    Placeholder.component("stat", StatNames.display(g, stat, messages)),
                    Placeholder.component("game", g.displayName())));
            if (rows.isEmpty()) {
                sender.sendMessage(messages.get("stats.top-empty"));
            }
            int rank = 1;
            for (StatsStore.Row row : rows) {
                sender.sendMessage(messages.get("stats.top-line",
                        Placeholder.unparsed("rank", Integer.toString(rank++)),
                        Placeholder.unparsed("player", row.name()),
                        Placeholder.unparsed("value", Long.toString(row.value()))));
            }
        }));
    }

    public BasicCommand statsCommand() {
        return new BasicCommand() {
            @Override
            public void execute(CommandSourceStack source, String[] args) {
                CommandSender sender = source.getSender();
                if (args.length >= 1) {
                    showStats(sender, args[0]);
                } else if (sender instanceof Player player) {
                    showStats(sender, player.getName());
                } else {
                    sender.sendMessage(messages.get("stats.usage"));
                }
            }

            @Override
            public Collection<String> suggest(CommandSourceStack source, String[] args) {
                String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase();
                return args.length <= 1
                        ? Bukkit.getOnlinePlayers().stream().map(Player::getName)
                                .filter(n -> n.toLowerCase().startsWith(last)).toList()
                        : List.of();
            }
        };
    }

    public BasicCommand topCommand() {
        return new BasicCommand() {
            @Override
            public void execute(CommandSourceStack source, String[] args) {
                if (args.length == 0) {
                    source.getSender().sendMessage(messages.get("stats.top-usage"));
                    return;
                }
                showTop(source.getSender(), args[0].toLowerCase(), args.length >= 2 ? args[1].toLowerCase() : null);
            }

            @Override
            public Collection<String> suggest(CommandSourceStack source, String[] args) {
                String last = args.length == 0 ? "" : args[args.length - 1];
                List<String> options = args.length <= 1
                        ? games.games().stream().flatMap(g -> StatNames.of(g).stream()).distinct().toList()
                        : args.length == 2 ? games.games().stream().map(GameType::id).toList() : List.of();
                return options.stream().filter(o -> o.startsWith(last)).toList();
            }
        };
    }

    private void onMain(Runnable task) {
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /** For the lobby stats item. */
    public Consumer<Player> ownStats() {
        return player -> showStats(player, player.getName());
    }
}
