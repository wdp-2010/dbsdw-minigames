package be.dbsdw.minigames.paintball.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SplatFadeTest {
    @Test
    void fullForSeventyPercentThenThreeEqualSteps() {
        assertEquals(0, SplatFade.stage(0, 400));
        assertEquals(0, SplatFade.stage(279, 400));
        assertEquals(1, SplatFade.stage(280, 400));
        assertEquals(2, SplatFade.stage(320, 400));
        assertEquals(3, SplatFade.stage(360, 400));
        assertEquals(3, SplatFade.stage(399, 400));
    }

    @Test
    void outOfRangeAgesClamp() {
        assertEquals(3, SplatFade.stage(5000, 400));
        assertEquals(0, SplatFade.stage(-1, 400));
    }

    @Test
    void tinyOrZeroLifetimeDoesNotDivideByZero() {
        assertEquals(0, SplatFade.stage(0, 0));
        assertEquals(3, SplatFade.stage(5, 5));
    }

    @Test
    void agesListsEveryLiveSplatOldestFirst() {
        SplatQueue<String> q = new SplatQueue<>(10, 400);
        q.add("a", 0);
        q.add("b", 10);
        q.add("c", 20);
        assertEquals(List.of(Map.entry("a", 30L), Map.entry("b", 20L), Map.entry("c", 10L)), q.ages(30));
    }

    @Test
    void agesSkipsSplatsAlreadyEvictedOrExpired() {
        SplatQueue<String> q = new SplatQueue<>(2, 400);
        q.add("a", 0);
        q.add("b", 10);
        q.add("c", 20);
        q.expired(410);
        assertEquals(List.of(Map.entry("c", 400L)), q.ages(420));
    }
}
