package be.dbsdw.minigames.core.lobby;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/** {@code /leave}: leave the queue or the match and go back to the lobby. */
public final class LeaveCommand implements BasicCommand {
    private final Consumer<Player> leave;

    public LeaveCommand(Consumer<Player> leave) {
        this.leave = leave;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        if (!(source.getSender() instanceof Player player)) {
            source.getSender().sendMessage(Component.text("Only players can use /leave."));
            return;
        }
        leave.accept(player);
    }
}
