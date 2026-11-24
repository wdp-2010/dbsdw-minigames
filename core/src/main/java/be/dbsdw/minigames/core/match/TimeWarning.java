package be.dbsdw.minigames.core.match;

import java.util.OptionalInt;

/**
 * When to warn that a match's time is nearly up: once at one minute left and every second of the
 * last ten. Fed the game's ticks left every tick; a warning fires when the whole seconds left
 * (rounded up) drop to one of those values. Never at the very first reading, so a match with a
 * one-minute limit does not warn as it starts.
 */
final class TimeWarning {
    static final int MINUTE = 60;
    static final int COUNTDOWN = 10;

    private int lastSeconds = -1;

    /** The seconds to announce now, if any. {@code ticksLeft} below 0 means the match has no clock right now. */
    OptionalInt update(long ticksLeft) {
        if (ticksLeft < 0) {
            lastSeconds = -1;
            return OptionalInt.empty();
        }
        int seconds = (int) Math.min(Integer.MAX_VALUE, (ticksLeft + 19) / 20);
        int before = lastSeconds;
        lastSeconds = seconds;
        if (before < 0 || seconds >= before || seconds == 0) {
            return OptionalInt.empty();
        }
        for (int s = before - 1; s >= seconds; s--) {
            if (s == MINUTE || s <= COUNTDOWN) {
                return OptionalInt.of(seconds <= COUNTDOWN ? seconds : s);
            }
        }
        return OptionalInt.empty();
    }
}
