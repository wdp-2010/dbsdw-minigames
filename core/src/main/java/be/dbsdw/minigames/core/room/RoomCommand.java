package be.dbsdw.minigames.core.room;

import be.dbsdw.minigames.core.Messages;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** {@code /room [invite|join|leave|kick|start] [speler]}: private rooms. */
public final class RoomCommand implements BasicCommand {
    private static final List<String> SUBS = List.of("invite", "join", "leave", "kick", "start");

    private final RoomService rooms;
    private final Messages messages;

    public RoomCommand(RoomService rooms, Messages messages) {
        this.rooms = rooms;
        this.messages = messages;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        if (!(source.getSender() instanceof Player player)) {
            source.getSender().sendMessage(Component.text("Only players can use /room."));
            return;
        }
        if (args.length == 0) {
            rooms.openOrCreate(player);
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "invite" -> withTarget(player, args, rooms::invite);
            case "join" -> withTarget(player, args, rooms::join);
            case "kick" -> withTarget(player, args, rooms::kick);
            case "leave" -> rooms.leave(player);
            case "start" -> rooms.start(player);
            default -> player.sendMessage(messages.get("room.usage"));
        }
    }

    private void withTarget(Player player, String[] args, BiConsumer<Player, Player> action) {
        if (args.length < 2) {
            player.sendMessage(messages.get("room.usage"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage(messages.get("room.no-player", Placeholder.unparsed("player", args[1])));
            return;
        }
        action.accept(player, target);
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        if (!(source.getSender() instanceof Player player)) {
            return List.of();
        }
        if (args.length <= 1) {
            String typed = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return SUBS.stream().filter(s -> s.startsWith(typed)).toList();
        }
        if (args.length == 2) {
            List<String> names = switch (args[0].toLowerCase(Locale.ROOT)) {
                case "invite" -> rooms.invitableNames(player);
                case "join" -> rooms.inviterNames(player);
                case "kick" -> rooms.memberNames(player);
                default -> List.of();
            };
            String typed = args[1].toLowerCase(Locale.ROOT);
            return names.stream().filter(n -> n.toLowerCase(Locale.ROOT).startsWith(typed)).toList();
        }
        return List.of();
    }
}
