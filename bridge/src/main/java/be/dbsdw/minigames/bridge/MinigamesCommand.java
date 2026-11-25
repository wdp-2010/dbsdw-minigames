package be.dbsdw.minigames.bridge;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /minigames} goes to the minigame server; {@code /minigames queue <key>} goes there into a
 * public queue (the queue notification's click); {@code /minigames npc set|remove} manages the NPC (admins).
 */
final class MinigamesCommand implements BasicCommand {
    static final String ADMIN_PERMISSION = "minigames.admin";

    private final BridgeTravel travel;
    private final MinigamesNpc npc;
    private final Messages messages;

    MinigamesCommand(BridgeTravel travel, MinigamesNpc npc, Messages messages) {
        this.travel = travel;
        this.npc = npc;
        this.messages = messages;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use /minigames."));
            return;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("npc") && player.hasPermission(ADMIN_PERMISSION)) {
            switch (args[1].toLowerCase(Locale.ROOT)) {
                case "set" -> {
                    npc.spawn(player);
                    player.sendMessage(messages.get("npc-set"));
                }
                case "remove" -> player.sendMessage(messages.get("npc-removed",
                        Placeholder.unparsed("count", Integer.toString(npc.removeNear(player)))));
                default -> player.sendMessage(Component.text("/minigames npc set|remove"));
            }
            return;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("queue")) {
            String key = args[1].toLowerCase(Locale.ROOT);
            // Same shape as MinigameCore's queue keys (game:mode or game:mode:2v2); the minigame server checks the rest.
            if (key.length() <= 64 && key.matches("[a-z0-9_-]+:[a-z0-9_-]+(:[0-9]+(v[0-9]+)+)?")) {
                travel.sendToMinigames(player, key);
                return;
            }
        }
        travel.sendToMinigames(player);
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        if (!source.getSender().hasPermission(ADMIN_PERMISSION)) {
            return List.of();
        }
        if (args.length <= 1) {
            return List.of("npc");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("npc")) {
            return List.of("set", "remove");
        }
        return List.of();
    }
}
