package be.dbsdw.minigames.core.solo;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/** {@code /solo}: opens the menu of games you can play alone against a bot. */
public final class SoloCommand implements BasicCommand {
    private final Consumer<Player> open;

    public SoloCommand(Consumer<Player> open) {
        this.open = open;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        if (source.getSender() instanceof Player player) {
            open.accept(player);
        } else {
            source.getSender().sendMessage(Component.text("Only players can use /solo."));
        }
    }
}
