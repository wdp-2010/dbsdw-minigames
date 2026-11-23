package be.dbsdw.minigames.chess;

import be.dbsdw.minigames.chess.match.ChessMatch;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BiConsumer;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * {@code /schaak}: the menu of unfinished games; {@code doorgaan <id>} accepts an invite to continue
 * one; {@code remise}, {@code pauze}, {@code claim} and {@code bewaar} are the clickable chat answers
 * during a game (the hotbar has the same buttons).
 */
final class ChessCommand implements BasicCommand {
    private static final List<String> SUBS = List.of("partijen", "doorgaan", "remise", "pauze", "claim", "bewaar");

    private final ChessGame game;

    ChessCommand(ChessGame game) {
        this.game = game;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        if (!(source.getSender() instanceof Player player)) {
            source.getSender().sendMessage(Component.text("Only players can use /schaak."));
            return;
        }
        String sub = args.length == 0 ? "partijen" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "partijen" -> game.openGames(player);
            case "doorgaan" -> {
                if (args.length != 2) {
                    player.sendMessage(game.messages().get("command.usage"));
                } else {
                    game.requestResume(player, args[1]);
                }
            }
            case "remise" -> inMatch(player, ChessMatch::draw);
            case "pauze" -> inMatch(player, ChessMatch::pause);
            case "claim" -> inMatch(player, ChessMatch::claim);
            case "bewaar" -> inMatch(player, ChessMatch::keep);
            default -> player.sendMessage(game.messages().get("command.usage"));
        }
    }

    private void inMatch(Player player, BiConsumer<ChessMatch, Player> action) {
        Optional<ChessMatch> match = game.matchOf(player.getUniqueId());
        if (match.isEmpty()) {
            player.sendMessage(game.messages().get("command.no-game"));
            return;
        }
        action.accept(match.get(), player);
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        if (args.length <= 1) {
            String typed = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return SUBS.stream().filter(s -> s.startsWith(typed)).toList();
        }
        return List.of();
    }
}
