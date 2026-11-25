package be.dbsdw.minigames.core.queue;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/** {@code /queue <key>}: join a public queue, e.g. {@code /queue paintball:tdm:2v2}; without a key the games menu. */
public final class QueueCommand implements BasicCommand {
    private final BiConsumer<Player, String> join;
    private final Consumer<Player> openGames;
    private final Supplier<List<String>> keys;

    public QueueCommand(BiConsumer<Player, String> join, Consumer<Player> openGames, Supplier<List<String>> keys) {
        this.join = join;
        this.openGames = openGames;
        this.keys = keys;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        if (!(source.getSender() instanceof Player player)) {
            source.getSender().sendMessage(Component.text("Only players can use /queue."));
            return;
        }
        if (args.length == 0) {
            openGames.accept(player);
            return;
        }
        join.accept(player, args[0].toLowerCase(Locale.ROOT));
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        return args.length <= 1 ? keys.get().stream().filter(k -> k.startsWith(prefix)).toList() : List.of();
    }
}
