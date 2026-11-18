package be.dbsdw.minigames.core.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import be.dbsdw.minigames.core.api.ModeSpec;
import be.dbsdw.minigames.core.arena.ArenaDefinition;
import be.dbsdw.minigames.core.arena.ArenaPool;
import be.dbsdw.minigames.core.arena.Pos;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QueueBookTest {
    private static final ModeSpec M = new ModeSpec("t", "m", "M", 2, 1, 2, Set.of());
    private static final ModeSpec N = new ModeSpec("t", "n", "N", 2, 1, 2, Set.of());
    private final List<UUID> p = IntStream.range(0, 6).mapToObj(i -> new UUID(0, i)).toList();
    private ArenaPool pool;
    private QueueBook book;

    @BeforeEach
    void setUp() {
        ArenaDefinition a = ArenaDefinition.empty("a", "world")
                .withCorner(1, new Pos(0, 0, 0, 0, 0)).withCorner(2, new Pos(10, 10, 10, 0, 0))
                .withModes(Set.of(M.key(), N.key()))
                .withSpawn(0, new Pos(1, 1, 1, 0, 0)).withSpawn(1, new Pos(9, 1, 9, 0, 0));
        pool = new ArenaPool(List.of(a));
        book = new QueueBook(pool, new QueueTimings(30, 10), new Random(1));
        book.addMode(M);
        book.addMode(N);
    }

    private List<QueueEvent> ticks(int n) {
        List<QueueEvent> events = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            events.addAll(book.tick());
        }
        return events;
    }

    private void join(int... players) {
        for (int i : players) {
            book.toggle(p.get(i), M.key());
        }
    }

    @Test
    void toggleJoinsThenLeaves() {
        assertEquals(QueueBook.Toggle.JOINED, book.toggle(p.get(0), M.key()));
        assertEquals(QueueBook.Toggle.LEFT, book.toggle(p.get(0), M.key()));
        assertEquals(Optional.empty(), book.queueOf(p.get(0)));
    }

    @Test
    void toggleOtherModeMoves() {
        join(0);
        assertEquals(QueueBook.Toggle.MOVED, book.toggle(p.get(0), N.key()));
        assertEquals(0, book.size(M.key()));
        assertEquals(1, book.size(N.key()));
    }

    @Test
    void unknownModeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> book.toggle(p.get(0), "t:x"));
    }

    @Test
    void belowMinimumNoCountdown() {
        join(0);
        assertTrue(ticks(100).isEmpty());
    }

    @Test
    void countdownStartsAtMinimumAndReservesArena() {
        join(0, 1);
        List<QueueEvent> events = book.tick();
        assertEquals(List.of(new QueueEvent.CountdownStarted(M.key(), "a", 30)), events);
        assertTrue(pool.isBusy("a"));
    }

    @Test
    void fullQueueShortensTo10() {
        join(0, 1);
        book.tick();
        join(2, 3);
        List<QueueEvent> events = book.tick();
        assertEquals(1, events.size());
        QueueEvent.CountdownTick tick = assertInstanceOf(QueueEvent.CountdownTick.class, events.getFirst());
        assertEquals(10, tick.seconds());
        assertEquals(10, book.secondsLeft(M.key()).orElseThrow());
    }

    @Test
    void fullFromTheStartUses10() {
        join(0, 1, 2, 3);
        assertEquals(List.of(new QueueEvent.CountdownStarted(M.key(), "a", 10)), book.tick());
    }

    @Test
    void startsAfterCountdownWithFirstMaximumPlayers() {
        join(0, 1, 2, 3, 4);
        List<QueueEvent> events = ticks(1 + 200);
        QueueEvent.Start start = assertInstanceOf(QueueEvent.Start.class, events.getLast());
        assertEquals(p.subList(0, 4), start.players());
        assertEquals("a", start.arena().name());
        assertEquals(Optional.of(M.key()), book.queueOf(p.get(4)));
        assertEquals(Optional.empty(), book.queueOf(p.get(0)));
        assertTrue(pool.isBusy("a"));
    }

    @Test
    void noStartBeforeCountdownEnds() {
        join(0, 1, 2, 3);
        assertTrue(ticks(200).stream().noneMatch(e -> e instanceof QueueEvent.Start));
    }

    @Test
    void leaveBelowMinimumCancelsAndReleasesArena() {
        join(0, 1);
        book.tick();
        assertTrue(book.remove(p.get(1)));
        List<QueueEvent> events = book.tick();
        assertEquals(List.of(new QueueEvent.CountdownCancelled(M.key(), List.of(p.get(0)))), events);
        assertFalse(pool.isBusy("a"));
        assertTrue(book.secondsLeft(M.key()).isEmpty());
    }

    @Test
    void waitsForFreeArenaThenStarts() {
        pool.reserve(M, new Random(1));
        join(0, 1);
        List<QueueEvent> events = ticks(100);
        assertEquals(List.of(new QueueEvent.WaitingForArena(M.key(), List.of(p.get(0), p.get(1)))), events);
        pool.release("a");
        assertEquals(List.of(new QueueEvent.CountdownStarted(M.key(), "a", 30)), book.tick());
    }

    @Test
    void announcesOnlyAtFixedSeconds() {
        join(0, 1);
        List<Integer> seconds = ticks(1 + 600).stream()
                .filter(e -> e instanceof QueueEvent.CountdownTick)
                .map(e -> ((QueueEvent.CountdownTick) e).seconds())
                .toList();
        assertEquals(List.of(20, 10, 5, 4, 3, 2, 1), seconds);
    }

    @Test
    void removeModeReturnsQueuedAndReleasesArena() {
        join(0, 1);
        book.tick();
        assertEquals(List.of(p.get(0), p.get(1)), book.removeMode(M.key()));
        assertFalse(pool.isBusy("a"));
        assertEquals(Optional.empty(), book.queueOf(p.get(0)));
    }
}
