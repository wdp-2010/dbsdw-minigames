package be.dbsdw.minigames.core.travel;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/** {@code /survival}: go back to the survival server. */
public final class SurvivalCommand implements BasicCommand {
    private final CoreTravel travel;

    public SurvivalCommand(CoreTravel travel) {
        this.travel = travel;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        if (!(source.getSender() instanceof Player player)) {
            source.getSender().sendMessage(Component.text("Only players can use /survival."));
            return;
        }
        travel.sendToSurvival(player);
    }
}
