package be.dbsdw.minigames.bridge;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/** {@code /minigames}: go to the minigame server. */
final class MinigamesCommand implements BasicCommand {
    private final BridgeTravel travel;

    MinigamesCommand(BridgeTravel travel) {
        this.travel = travel;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        if (!(source.getSender() instanceof Player player)) {
            source.getSender().sendMessage(Component.text("Only players can use /minigames."));
            return;
        }
        travel.sendToMinigames(player);
    }
}
