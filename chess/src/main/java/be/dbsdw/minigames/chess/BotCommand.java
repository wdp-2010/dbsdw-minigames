package be.dbsdw.minigames.chess;

import be.dbsdw.minigames.chess.bot.BotLevel;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * {@code /schaakbot}: the menu to play against the bot (difficulty, colour, time control).
 * {@code /schaakbot <niveau> [wit|zwart|willekeurig] [modus]} starts at once.
 */
final class BotCommand implements BasicCommand {
    private static final List<String> COLOURS = List.of("wit", "zwart", "willekeurig");

    private final ChessGame game;

    BotCommand(ChessGame game) {
        this.game = game;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        if (!(source.getSender() instanceof Player player)) {
            source.getSender().sendMessage(Component.text("Only players can use /schaakbot."));
            return;
        }
        if (game.matchOf(player.getUniqueId()).isPresent()) {
            player.sendMessage(game.messages().get("bot.busy"));
            return;
        }
        if (args.length == 0) {
            game.openBotMenu(player);
            return;
        }
        Optional<BotLevel> level = BotLevel.parse(args[0]);
        String colour = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "wit";
        String mode = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : game.modes().getFirst().id();
        if (level.isEmpty() || !COLOURS.contains(colour) || args.length > 3
                || game.modes().stream().noneMatch(m -> m.id().equals(mode))) {
            player.sendMessage(game.messages().get("bot.usage"));
            return;
        }
        boolean white = switch (colour) {
            case "wit" -> true;
            case "zwart" -> false;
            default -> ThreadLocalRandom.current().nextBoolean();
        };
        game.startBot(player, level.get(), white, mode);
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        String typed = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> options = switch (Math.max(1, args.length)) {
            case 1 -> Arrays.stream(BotLevel.values()).map(BotLevel::id).toList();
            case 2 -> COLOURS;
            case 3 -> game.modes().stream().map(m -> m.id()).toList();
            default -> List.of();
        };
        return options.stream().filter(s -> s.startsWith(typed)).toList();
    }
}
