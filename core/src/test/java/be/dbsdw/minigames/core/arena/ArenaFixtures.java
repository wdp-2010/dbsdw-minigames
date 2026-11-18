package be.dbsdw.minigames.core.arena;

import be.dbsdw.minigames.core.api.ArenaFeature;
import be.dbsdw.minigames.core.api.ModeSpec;
import java.util.Set;

final class ArenaFixtures {
    static final ModeSpec TDM = new ModeSpec("paintball", "tdm", "TDM", 2, 2, 8, Set.of());
    static final ModeSpec CTF = new ModeSpec("paintball", "ctf", "CTF", 2, 2, 8, Set.of(ArenaFeature.FLAGS));

    private ArenaFixtures() {}

    static Pos pos(double x, double y, double z) {
        return new Pos(x, y, z, 0, 0);
    }

    /** Bounds (0,60,0)..(59,80,39), no modes, no points. */
    static ArenaDefinition bounded(String name) {
        return ArenaDefinition.empty(name, "world").withCorner(1, pos(0, 60, 0)).withCorner(2, pos(59, 80, 39));
    }

    /** Bounded, lists TDM, one spawn per team. */
    static ArenaDefinition tdmArena(String name) {
        return bounded(name).withModes(Set.of(TDM.key()))
                .withSpawn(0, pos(5, 65, 20)).withSpawn(1, pos(54, 65, 20));
    }
}
