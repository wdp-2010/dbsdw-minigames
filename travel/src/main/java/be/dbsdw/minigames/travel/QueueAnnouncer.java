package be.dbsdw.minigames.travel;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Decides when survival hears about a minigame queue with players waiting: at most once per
 * {@code gap}, and not again for the same queue with the same number of players (nothing new).
 * The queue picked is the open one with the most players waiting (the first of equals).
 */
public final class QueueAnnouncer {
    private final Duration gap;
    private Instant last;
    private String lastKey;
    private int lastQueued;

    public QueueAnnouncer(Duration gap) {
        this.gap = gap;
    }

    /** The queue to announce now, if any; announcing it is recorded. */
    public Optional<QueueStatus> next(List<QueueStatus> queues, Instant now) {
        if (last != null && now.isBefore(last.plus(gap))) {
            return Optional.empty();
        }
        Optional<QueueStatus> best = queues.stream()
                .filter(QueueStatus::open)
                .max(Comparator.comparingInt(QueueStatus::queued).thenComparing(q -> -queues.indexOf(q)));
        if (best.isEmpty() || (best.get().key().equals(lastKey) && best.get().queued() == lastQueued)) {
            return Optional.empty();
        }
        last = now;
        lastKey = best.get().key();
        lastQueued = best.get().queued();
        return best;
    }
}
