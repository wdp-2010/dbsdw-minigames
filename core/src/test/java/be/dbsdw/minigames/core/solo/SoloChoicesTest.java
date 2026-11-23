package be.dbsdw.minigames.core.solo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import be.dbsdw.minigames.core.api.ModeSpec;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SoloChoicesTest {
    private static ModeSpec mode(String game, String id) {
        return new ModeSpec(game, id, id, 2, 1, 1, Set.of());
    }

    @Test
    void groupsModesByGameKeepingOrder() {
        ModeSpec blitz = mode("chess", "blitz");
        ModeSpec tdm = mode("paintball", "tdm");
        ModeSpec classic = mode("chess", "classic");
        var games = SoloChoices.byGame(List.of(blitz, tdm, classic));
        assertEquals(List.of("chess", "paintball"), List.copyOf(games.keySet()));
        assertEquals(List.of(blitz, classic), games.get("chess"));
        assertEquals(List.of(tdm), games.get("paintball"));
    }

    @Test
    void emptyWhenNoGameSupportsSolo() {
        assertEquals(0, SoloChoices.byGame(List.of()).size());
    }
}
