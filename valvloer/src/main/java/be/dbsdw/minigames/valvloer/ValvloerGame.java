package be.dbsdw.minigames.valvloer;

import be.dbsdw.minigames.core.Messages;
import be.dbsdw.minigames.core.api.GameType;
import be.dbsdw.minigames.core.api.MatchContext;
import be.dbsdw.minigames.core.api.MatchHandler;
import be.dbsdw.minigames.core.api.ModeSpec;
import be.dbsdw.minigames.valvloer.match.Restorer;
import be.dbsdw.minigames.valvloer.match.ValvloerMatch;
import be.dbsdw.minigames.valvloer.rules.Settings;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;

/** Valvloer: two teams on stacked floors that drop away underfoot; last team standing wins. */
final class ValvloerGame implements GameType {
    static final String ID = "valvloer";

    private final Settings settings;
    private final Set<Material> floorTypes;
    private final Messages messages;
    private final Restorer restorer;
    private final Logger log;
    private final ModeSpec mode;

    ValvloerGame(Settings settings, Set<Material> floorTypes, Messages messages, Restorer restorer, Logger log) {
        this.settings = settings;
        this.floorTypes = Set.copyOf(floorTypes);
        this.messages = messages;
        this.restorer = restorer;
        this.log = log;
        this.mode = new ModeSpec(ID, "teams", messages.raw("mode.teams"), 2, 1, settings.maxPerTeam(), Set.of());
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Component displayName() {
        return messages.get("game.name");
    }

    @Override
    public Material icon() {
        return Material.SCAFFOLDING;
    }

    @Override
    public List<ModeSpec> modes() {
        return List.of(mode);
    }

    @Override
    public List<String> stats() {
        return List.of("survived", "drops");
    }

    @Override
    public Component statName(String stat) {
        return messages.get("stat." + stat);
    }

    @Override
    public MatchHandler newMatch(MatchContext context) {
        return new ValvloerMatch(context, settings, floorTypes, messages, restorer, log);
    }
}
