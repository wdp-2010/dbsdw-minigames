package be.dbsdw.minigames.core.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;

import be.dbsdw.minigames.core.api.ModeSpec;
import be.dbsdw.minigames.core.progress.UnlockLadder.Entry;
import be.dbsdw.minigames.core.progress.UnlockLadder.State;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UnlockLadderTest {
    private final LevelCurve curve = new LevelCurve(100, 1.5, 50);
    private final ModeSpec tdm = mode("tdm");
    private final ModeSpec ctf = mode("ctf");
    private final ModeSpec shop = mode("shop");
    private final ModeSpec bomb = mode("bomb");
    private final Map<ModeSpec, Integer> need = Map.of(tdm, 0, ctf, 1, shop, 5, bomb, 10);

    private static ModeSpec mode(String id) {
        return new ModeSpec("g", id, id, 2, 1, 4, Set.of());
    }

    private List<Entry> ladder(long xp) {
        return UnlockLadder.build(List.of(bomb, shop, tdm, ctf), need::get, curve, xp);
    }

    @Test
    void sortedByLevelWithOpenModesFirst() {
        assertEquals(List.of(tdm, ctf, shop, bomb), ladder(0).stream().map(Entry::mode).toList());
    }

    @Test
    void modesWithoutLevelAreOpen() {
        List<Entry> l = ladder(0);
        assertEquals(State.OPEN, l.get(0).state());
        assertEquals(State.OPEN, l.get(1).state());
    }

    @Test
    void theNearestLockedModeIsNextAndTheRestLocked() {
        List<Entry> l = ladder(0);
        assertEquals(State.NEXT, l.get(2).state());
        assertEquals(State.LOCKED, l.get(3).state());
    }

    @Test
    void missingXpIsWhatTheLevelStillNeeds() {
        long have = curve.xpFor(5) - 30;
        assertEquals(30, ladder(have).get(2).xpMissing());
        assertEquals(curve.xpFor(10) - have, ladder(have).get(3).xpMissing());
    }

    @Test
    void reachingALevelUnlocksAndMovesTheGoal() {
        List<Entry> l = ladder(curve.xpFor(5));
        assertEquals(State.UNLOCKED, l.get(2).state());
        assertEquals(0, l.get(2).xpMissing());
        assertEquals(State.NEXT, l.get(3).state());
        assertEquals(3, UnlockLadder.playable(l));
    }

    @Test
    void everythingUnlockedLeavesNoNext() {
        List<Entry> l = ladder(curve.xpFor(10));
        assertEquals(4, UnlockLadder.playable(l));
        assertEquals(0, l.stream().filter(e -> e.state() == State.NEXT).count());
    }

    @Test
    void modesOnTheSameLevelAreAllNext() {
        Map<ModeSpec, Integer> same = Map.of(tdm, 0, ctf, 3, shop, 3, bomb, 8);
        List<Entry> l = UnlockLadder.build(List.of(tdm, ctf, shop, bomb), same::get, curve, 0);
        assertEquals(State.NEXT, l.get(1).state());
        assertEquals(State.NEXT, l.get(2).state());
        assertEquals(State.LOCKED, l.get(3).state());
    }
}
