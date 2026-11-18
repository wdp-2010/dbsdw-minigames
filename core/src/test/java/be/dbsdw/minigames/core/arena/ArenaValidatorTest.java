package be.dbsdw.minigames.core.arena;

import static be.dbsdw.minigames.core.arena.ArenaFixtures.TDM;
import static be.dbsdw.minigames.core.arena.ArenaFixtures.bounded;
import static be.dbsdw.minigames.core.arena.ArenaFixtures.pos;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import be.dbsdw.minigames.core.api.ModeSpec;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class ArenaValidatorTest {
    private static final Function<String, Optional<ModeSpec>> MODES =
            key -> key.equals(TDM.key()) ? Optional.of(TDM) : Optional.empty();

    @Test
    void missingBoundsIsError() {
        ArenaDefinition a = ArenaDefinition.empty("a", "world").withModes(Set.of(TDM.key()));
        assertTrue(ArenaValidator.validate(a, MODES).errors().stream().anyMatch(e -> e.contains("bounds")));
    }

    @Test
    void noModesIsError() {
        assertTrue(ArenaValidator.validate(bounded("a"), MODES).errors().stream().anyMatch(e -> e.contains("mode")));
    }

    @Test
    void pointOutsideBoundsIsError() {
        ArenaDefinition a = ArenaFixtures.tdmArena("a").withSpawn(0, pos(100, 70, 10));
        assertTrue(ArenaValidator.validate(a, MODES).errors().stream()
                .anyMatch(e -> e.contains("spawn") && e.contains("team 0")));
    }

    @Test
    void registeredModeThatDoesNotFitIsError() {
        ArenaDefinition a = bounded("a").withModes(Set.of(TDM.key())).withSpawn(0, pos(5, 65, 5));
        assertTrue(ArenaValidator.validate(a, MODES).errors().stream().anyMatch(e -> e.contains(TDM.key())));
    }

    @Test
    void unregisteredModeIsWarningOnly() {
        ArenaDefinition a = ArenaFixtures.tdmArena("a").withModes(Set.of(TDM.key(), "paintball:tmd"));
        ArenaValidator.Validation v = ArenaValidator.validate(a, MODES);
        assertEquals(0, v.errors().size(), v.errors().toString());
        assertEquals(1, v.warnings().size());
        assertTrue(v.warnings().getFirst().contains("paintball:tmd"));
    }

    @Test
    void validArenaHasNoErrors() {
        ArenaValidator.Validation v = ArenaValidator.validate(ArenaFixtures.tdmArena("a"), MODES);
        assertTrue(v.errors().isEmpty(), v.errors().toString());
        assertTrue(v.warnings().isEmpty());
    }
}
