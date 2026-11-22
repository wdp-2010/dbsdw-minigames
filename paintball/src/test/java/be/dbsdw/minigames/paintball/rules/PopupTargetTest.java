package be.dbsdw.minigames.paintball.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PopupTargetTest {
    private static final int HITS = 3;
    private static final long DOWN = 60;

    @Test
    void goesDownOnTheLastHit() {
        PopupTarget t = new PopupTarget(HITS, DOWN);
        assertEquals(PopupTarget.Hit.HIT, t.hit(10));
        assertEquals(PopupTarget.Hit.HIT, t.hit(11));
        assertEquals(PopupTarget.Hit.DOWN, t.hit(12));
        assertTrue(t.isDown(12));
    }

    @Test
    void ignoresHitsWhileDown() {
        PopupTarget t = new PopupTarget(1, DOWN);
        t.hit(0);
        assertEquals(PopupTarget.Hit.IGNORED, t.hit(5));
    }

    @Test
    void sinksStaysDownAndRisesAgain() {
        PopupTarget t = new PopupTarget(1, DOWN);
        assertEquals(0.0, t.sink(0), 1e-9);
        t.hit(100);
        assertEquals(0.0, t.sink(100), 1e-9);
        assertEquals(PopupTarget.DEPTH / 2, t.sink(100 + PopupTarget.MOVE_TICKS / 2), 1e-9);
        assertEquals(PopupTarget.DEPTH, t.sink(100 + PopupTarget.MOVE_TICKS), 1e-9);
        assertEquals(PopupTarget.DEPTH, t.sink(100 + DOWN), 1e-9);
        assertEquals(PopupTarget.DEPTH / 2, t.sink(100 + DOWN + PopupTarget.MOVE_TICKS / 2), 1e-9);
        assertEquals(0.0, t.sink(100 + DOWN + PopupTarget.MOVE_TICKS), 1e-9);
    }

    @Test
    void takesHitsAgainOnceFullyUpWithFreshHits() {
        PopupTarget t = new PopupTarget(2, DOWN);
        t.hit(0);
        t.hit(1);
        long up = 1 + DOWN + PopupTarget.MOVE_TICKS;
        assertEquals(PopupTarget.Hit.IGNORED, t.hit(up - 1));
        assertEquals(PopupTarget.Hit.HIT, t.hit(up));
        assertEquals(PopupTarget.Hit.DOWN, t.hit(up + 1));
    }
}
