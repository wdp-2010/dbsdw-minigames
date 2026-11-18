package be.dbsdw.minigames.core.queue;

/** Countdown lengths: the normal one, and the shorter one used once the queue is full. */
public record QueueTimings(int countdownSeconds, int fullCountdownSeconds) {
    public QueueTimings {
        if (fullCountdownSeconds < 1 || fullCountdownSeconds > countdownSeconds) {
            throw new IllegalArgumentException("need 1 <= fullCountdownSeconds <= countdownSeconds");
        }
    }
}
