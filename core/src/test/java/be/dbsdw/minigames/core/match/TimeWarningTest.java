package be.dbsdw.minigames.core.match;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class TimeWarningTest {
    /** Runs a clock from {@code start} ticks down to 0 and collects every warning. */
    private static List<Integer> run(long start) {
        TimeWarning w = new TimeWarning();
        List<Integer> out = new ArrayList<>();
        for (long t = start; t >= 0; t--) {
            w.update(t).ifPresent(out::add);
        }
        return out;
    }

    @Test
    void warnsAtOneMinuteAndEachOfTheLastTenSeconds() {
        assertEquals(List.of(60, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1), run(5 * 60 * 20));
    }

    @Test
    void aMatchThatStartsAtOneMinuteDoesNotWarnAtItsStart() {
        assertEquals(List.of(10, 9, 8, 7, 6, 5, 4, 3, 2, 1), run(60 * 20));
    }

    @Test
    void noClockMeansNoWarningAndAClockThatStartsLateWaitsOneReading() {
        TimeWarning w = new TimeWarning();
        assertEquals(OptionalInt.empty(), w.update(-1));
        assertEquals(OptionalInt.empty(), w.update(5 * 20));
        assertEquals(OptionalInt.of(4), w.update(4 * 20));
        assertEquals(OptionalInt.empty(), w.update(-1));
        assertEquals(OptionalInt.empty(), w.update(3 * 20), "after a pause the clock is read fresh");
    }

    @Test
    void aClockThatJumpsOverTheMinuteStillWarnsOnce() {
        TimeWarning w = new TimeWarning();
        w.update(62 * 20);
        assertEquals(OptionalInt.of(60), w.update(58 * 20));
        assertEquals(OptionalInt.empty(), w.update(57 * 20));
    }
}
