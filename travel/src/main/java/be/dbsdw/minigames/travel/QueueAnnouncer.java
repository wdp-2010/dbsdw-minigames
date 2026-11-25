package be.dbsdw.minigames.travel;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.ToIntFunction;

/**
 * Decides when survival hears about a minigame queue with players waiting: at most once per
 * {@code gap}, and not again for the same queue with the same number of players (nothing new).
 * Each player hears of the open queue with the most players waiting (the first of equals) that
 * their level lets them join; a queue nobody may join is not announced and does not use up the gap.
 */
public final class QueueAnnouncer {
    private final Duration gap;
    private Instant last;
    private String lastKey;
    private int lastQueued;

    public QueueAnnouncer(Duration gap) {
        this.gap = gap;
    }

    /**
     * The queues that may be announced now, most players waiting first (the first of equals first):
     * empty within the gap; the queue announced last, with no one new in it, left out.
     */
    public List<QueueStatus> candidates(List<QueueStatus> queues, Instant now) {
        if (last != null && now.isBefore(last.plus(gap))) {
            return List.of();
        }
        return queues.stream()
                .filter(QueueStatus::open)
                .filter(q -> !(q.key().equals(lastKey) && q.queued() == lastQueued))
                .sorted(Comparator.comparingInt(QueueStatus::queued).reversed())
                .toList();
    }

    /** Records that {@code queue} was announced (to at least one player) at {@code now}. */
    public void announced(QueueStatus queue, Instant now) {
        last = now;
        lastKey = queue.key();
        lastQueued = queue.queued();
    }

    /**
     * The first candidate a player may join, given their level per game ({@code levelIn} gets a
     * game id; it is only asked for queues that take a level).
     */
    public static Optional<QueueStatus> firstAllowed(List<QueueStatus> candidates, ToIntFunction<String> levelIn) {
        return candidates.stream().filter(q -> q.allows(q.leveled() ? levelIn.applyAsInt(q.game()) : 1)).findFirst();
    }

    /** The queue to announce now to a player of any level, if any; announcing it is recorded. */
    public Optional<QueueStatus> next(List<QueueStatus> queues, Instant now) {
        Optional<QueueStatus> best = candidates(queues, now).stream().findFirst();
        best.ifPresent(q -> announced(q, now));
        return best;
    }
}
