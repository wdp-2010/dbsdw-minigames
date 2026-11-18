package be.dbsdw.minigames.core.arena;

import static be.dbsdw.minigames.core.arena.ArenaFixtures.CTF;
import static be.dbsdw.minigames.core.arena.ArenaFixtures.TDM;
import static be.dbsdw.minigames.core.arena.ArenaFixtures.bounded;
import static be.dbsdw.minigames.core.arena.ArenaFixtures.pos;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class ArenaDefinitionTest {
    @Test
    void containsIsInclusiveOfCornerBlocks() {
        ArenaDefinition a = bounded("a");
        assertTrue(a.contains(pos(59.9, 80.5, 39.9)));
        assertFalse(a.contains(pos(60.0, 70, 10)));
        assertFalse(a.contains(pos(-0.1, 70, 10)));
    }

    @Test
    void fitsNeedsSpawnForEveryTeam() {
        ArenaDefinition a = bounded("a").withModes(Set.of(TDM.key())).withSpawn(0, pos(5, 65, 5));
        assertFalse(a.fits(TDM));
        assertTrue(a.withSpawn(1, pos(50, 65, 5)).fits(TDM));
    }

    @Test
    void fitsNeedsListedMode() {
        assertFalse(ArenaFixtures.tdmArena("a").fits(CTF));
    }

    @Test
    void fitsNeedsFlagsWhenRequired() {
        ArenaDefinition a = bounded("a").withModes(Set.of(CTF.key()))
                .withSpawn(0, pos(5, 65, 5)).withSpawn(1, pos(50, 65, 5)).withFlag(0, pos(2, 65, 20));
        assertFalse(a.fits(CTF));
        assertTrue(a.withFlag(1, pos(57, 65, 20)).fits(CTF));
    }

    @Test
    void fitsNeedsBounds() {
        ArenaDefinition a = ArenaDefinition.empty("a", "world").withModes(Set.of(TDM.key()))
                .withSpawn(0, pos(5, 65, 5)).withSpawn(1, pos(50, 65, 5));
        assertFalse(a.fits(TDM));
    }
}
