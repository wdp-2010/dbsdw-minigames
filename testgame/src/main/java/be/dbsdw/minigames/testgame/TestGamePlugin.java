package be.dbsdw.minigames.testgame;

import be.dbsdw.minigames.core.api.GameRegistry;
import be.dbsdw.minigames.core.api.GameType;
import be.dbsdw.minigames.core.api.MatchContext;
import be.dbsdw.minigames.core.api.MatchHandler;
import be.dbsdw.minigames.core.api.ModeSpec;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Test-only game used by the e2e checks. Never deploy. */
public final class TestGamePlugin extends JavaPlugin {
    private final Map<UUID, PointsMatch> matches = new HashMap<>();

    @Override
    public void onEnable() {
        GameRegistry registry = getServer().getServicesManager().load(GameRegistry.class);
        if (registry == null) {
            throw new IllegalStateException("MinigameCore's GameRegistry is not available");
        }
        registry.register(this, new GameType() {
            @Override
            public String id() {
                return "testgame";
            }

            @Override
            public Component displayName() {
                return Component.text("Testspel");
            }

            @Override
            public Material icon() {
                return Material.SLIME_BALL;
            }

            @Override
            public List<ModeSpec> modes() {
                return List.of(new ModeSpec("testgame", "points", "Punten", 2, 1, 1, Set.of()));
            }

            @Override
            public MatchHandler newMatch(MatchContext context) {
                return new PointsMatch(context, matches);
            }
        });
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register("testgame", "Test-only: score a point", new BasicCommand() {
                    @Override
                    public void execute(CommandSourceStack source, String[] args) {
                        if (source.getSender() instanceof Player player && args.length == 1 && args[0].equals("score")) {
                            PointsMatch match = matches.get(player.getUniqueId());
                            if (match != null) {
                                match.score(player);
                            }
                        }
                    }
                }));
        getLogger().warning("MgTestGame is a test-only plugin. Do not run it on a real server.");
    }
}
