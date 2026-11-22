package be.dbsdw.minigames.core.room;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import be.dbsdw.minigames.core.api.ModeSpec;
import be.dbsdw.minigames.core.arena.ArenaDefinition;
import be.dbsdw.minigames.core.arena.ArenaPool;
import be.dbsdw.minigames.core.arena.Pos;
import be.dbsdw.minigames.core.room.RoomBook.Outcome;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RoomBookTest {
    /** Public minimum 4, maximum 4: rooms start from 2 anyway. */
    private static final ModeSpec M = new ModeSpec("t", "m", "M", 2, 2, 2, Set.of());
    private static final ModeSpec N = new ModeSpec("t", "n", "N", 2, 1, 1, Set.of());
    private final List<UUID> p = IntStream.range(0, 6).mapToObj(i -> new UUID(0, i)).toList();
    private ArenaPool pool;
    private RoomBook book;

    private static ArenaDefinition arena(String name) {
        return ArenaDefinition.empty(name, "world")
                .withCorner(1, new Pos(0, 0, 0, 0, 0)).withCorner(2, new Pos(10, 10, 10, 0, 0))
                .withModes(Set.of(M.key(), N.key()))
                .withSpawn(0, new Pos(1, 1, 1, 0, 0)).withSpawn(1, new Pos(9, 1, 9, 0, 0));
    }

    @BeforeEach
    void setUp() {
        pool = new ArenaPool(List.of(arena("a"), arena("b")));
        book = new RoomBook(pool, new Random(1));
    }

    private List<RoomEvent> ticks(int n) {
        List<RoomEvent> events = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            events.addAll(book.tick());
        }
        return events;
    }

    /** p0 hosts, the given players join. */
    private void room(int... joiners) {
        assertEquals(Outcome.OK, book.create(p.get(0), M));
        for (int j : joiners) {
            assertEquals(Outcome.OK, book.invite(p.get(0), p.get(j)));
            assertEquals(Outcome.OK, book.join(p.get(j), p.get(0)));
        }
    }

    private RoomView view() {
        return book.roomOf(p.get(0)).orElseThrow();
    }

    @Test
    void createMakesHost() {
        room();
        assertEquals(p.get(0), view().host());
        assertEquals(List.of(p.get(0)), view().members());
        assertEquals(RoomTeams.Rule.PICK, view().rule());
        assertEquals(Outcome.IN_ROOM, book.create(p.get(0), M));
    }

    @Test
    void joinNeedsInvite() {
        room();
        assertEquals(Outcome.NO_INVITE, book.join(p.get(1), p.get(0)));
        assertEquals(Outcome.NO_ROOM, book.join(p.get(1), p.get(2)));
        book.invite(p.get(0), p.get(1));
        assertTrue(book.invited(p.get(1), p.get(0)));
        assertEquals(Outcome.OK, book.join(p.get(1), p.get(0)));
        assertFalse(book.invited(p.get(1), p.get(0)));
    }

    @Test
    void joinRefusedWhenInAnotherRoom() {
        room();
        book.create(p.get(1), M);
        book.invite(p.get(0), p.get(1));
        assertEquals(Outcome.IN_ROOM, book.join(p.get(1), p.get(0)));
        assertEquals(p.get(1), book.roomOf(p.get(1)).orElseThrow().host());
    }

    @Test
    void inviteOnlyByHost() {
        room(1);
        assertEquals(Outcome.NOT_HOST, book.invite(p.get(1), p.get(2)));
        assertEquals(Outcome.IN_ROOM, book.invite(p.get(0), p.get(1)));
    }

    @Test
    void inviteExpiresAfter60s() {
        room();
        book.invite(p.get(0), p.get(1));
        ticks(1199);
        assertTrue(book.invited(p.get(1), p.get(0)));
        ticks(1);
        assertFalse(book.invited(p.get(1), p.get(0)));
        assertEquals(Outcome.NO_INVITE, book.join(p.get(1), p.get(0)));
    }

    @Test
    void hostLeavingHandsToOldestMember() {
        room(1, 2);
        RoomBook.Left left = book.leave(p.get(0)).orElseThrow();
        assertFalse(left.removed());
        assertEquals(Optional.of(p.get(1)), left.newHost());
        assertEquals(p.get(1), book.roomOf(p.get(2)).orElseThrow().host());
        assertTrue(book.roomOf(p.get(0)).isEmpty());
    }

    @Test
    void lastLeaveRemovesRoom() {
        room(1);
        RoomBook.Left first = book.leave(p.get(1)).orElseThrow();
        assertEquals(Optional.empty(), first.newHost());
        RoomBook.Left last = book.leave(p.get(0)).orElseThrow();
        assertTrue(last.removed());
        assertTrue(book.room(last.roomId()).isEmpty());
        assertTrue(book.leave(p.get(0)).isEmpty());
    }

    @Test
    void kickOnlyByHost() {
        room(1, 2);
        assertEquals(Outcome.NOT_HOST, book.kick(p.get(1), p.get(2)));
        assertEquals(Outcome.NOT_MEMBER, book.kick(p.get(0), p.get(4)));
        assertEquals(Outcome.OK, book.kick(p.get(0), p.get(2)));
        assertTrue(book.roomOf(p.get(2)).isEmpty());
    }

    @Test
    void fullRoomRefusesJoin() {
        room(1, 2, 3);
        book.invite(p.get(0), p.get(4));
        assertEquals(Outcome.FULL, book.join(p.get(4), p.get(0)));
        assertEquals(Outcome.FULL, book.setMode(p.get(0), N));
    }

    @Test
    void pickOnlyUnderPickRule() {
        room(1);
        assertEquals(Outcome.OK, book.pick(p.get(1), OptionalInt.of(1)));
        assertEquals(1, view().picks().get(p.get(1)));
        assertEquals(Outcome.OK, book.pick(p.get(0), OptionalInt.of(1)));
        book.invite(p.get(0), p.get(2));
        book.join(p.get(2), p.get(0));
        assertEquals(Outcome.TEAM_FULL, book.pick(p.get(2), OptionalInt.of(1)));
        assertEquals(Outcome.OK, book.pick(p.get(1), OptionalInt.empty()));
        assertFalse(view().picks().containsKey(p.get(1)));
        assertEquals(Outcome.NOT_HOST, book.cycleRule(p.get(1)));
        assertEquals(Outcome.OK, book.cycleRule(p.get(0)));
        assertEquals(Outcome.NOT_ALLOWED, book.pick(p.get(1), OptionalInt.of(0)));
    }

    @Test
    void moveCyclesUnderHostRule() {
        room(1);
        assertEquals(Outcome.NOT_ALLOWED, book.move(p.get(0), p.get(1)));
        book.cycleRule(p.get(0));
        assertEquals(RoomTeams.Rule.HOST, view().rule());
        assertEquals(Outcome.NOT_HOST, book.move(p.get(1), p.get(1)));
        book.move(p.get(0), p.get(1));
        assertEquals(0, view().picks().get(p.get(1)));
        book.move(p.get(0), p.get(1));
        assertEquals(1, view().picks().get(p.get(1)));
        book.move(p.get(0), p.get(1));
        assertFalse(view().picks().containsKey(p.get(1)));
        book.move(p.get(0), p.get(1));
        assertEquals(0, view().picks().get(p.get(1)));
    }

    @Test
    void startNeedsTwo() {
        room();
        assertEquals(Outcome.TOO_FEW, book.start(p.get(0)));
        assertFalse(pool.isBusy("a") || pool.isBusy("b"));
    }

    @Test
    void startRefusedWhenATeamWouldBeEmpty() {
        room(1);
        book.pick(p.get(0), OptionalInt.of(0));
        book.pick(p.get(1), OptionalInt.of(0));
        assertEquals(Outcome.EMPTY_TEAM, book.start(p.get(0)));
        book.pick(p.get(1), OptionalInt.empty());
        assertEquals(Outcome.OK, book.start(p.get(0)));
    }

    @Test
    void startBusyArena() {
        room(1);
        pool.reserve("a", M);
        assertEquals(Outcome.OK, book.setArena(p.get(0), Optional.of("a")));
        assertEquals(Outcome.BUSY, book.start(p.get(0)));
        assertEquals(Outcome.NO_ARENA, book.setArena(p.get(0), Optional.of("zzz")));
        pool.reserve("b", M);
        book.setArena(p.get(0), Optional.empty());
        assertEquals(Outcome.NO_ARENA, book.start(p.get(0)));
    }

    @Test
    void countdownEmitsStartWithTeamsAfter100Ticks() {
        room(1, 2);
        book.setArena(p.get(0), Optional.of("b"));
        assertEquals(Outcome.OK, book.start(p.get(0)));
        assertTrue(pool.isBusy("b"));
        assertEquals(OptionalInt.of(5), view().secondsLeft());
        List<RoomEvent> early = ticks(99);
        assertTrue(early.stream().noneMatch(e -> e instanceof RoomEvent.Start));
        assertEquals(List.of(5, 4, 3, 2, 1), early.stream()
                .filter(e -> e instanceof RoomEvent.CountdownTick)
                .map(e -> ((RoomEvent.CountdownTick) e).seconds()).toList());
        List<RoomEvent> last = ticks(1);
        RoomEvent.Start start = assertInstanceOf(RoomEvent.Start.class, last.get(0));
        assertEquals("b", start.arena().name());
        assertEquals(M, start.mode());
        assertEquals(List.of(1, 2), start.teams().stream().map(List::size).sorted().toList());
        assertTrue(view().playing());
        assertTrue(pool.isBusy("b"));
        assertEquals(Outcome.PLAYING, book.start(p.get(0)));
    }

    @Test
    void settingsLockedDuringCountdown() {
        room(1);
        book.start(p.get(0));
        assertEquals(Outcome.COUNTING, book.setMode(p.get(0), N));
        assertEquals(Outcome.COUNTING, book.cycleRule(p.get(0)));
        assertEquals(Outcome.COUNTING, book.pick(p.get(1), OptionalInt.of(0)));
        assertEquals(Outcome.COUNTING, book.start(p.get(0)));
    }

    @Test
    void cancelReleasesArena() {
        room(1);
        book.setArena(p.get(0), Optional.of("a"));
        book.start(p.get(0));
        assertEquals(Outcome.NOT_HOST, book.cancel(p.get(1)));
        assertEquals(Outcome.OK, book.cancel(p.get(0)));
        assertFalse(pool.isBusy("a"));
        assertTrue(view().secondsLeft().isEmpty());
        assertTrue(ticks(200).stream().noneMatch(e -> e instanceof RoomEvent.Start));
    }

    @Test
    void countdownCancelledWhenTooFewLeft() {
        room(1);
        book.setArena(p.get(0), Optional.of("a"));
        book.start(p.get(0));
        book.leave(p.get(1));
        List<RoomEvent> events = ticks(1);
        assertTrue(events.stream().anyMatch(e -> e instanceof RoomEvent.CountdownCancelled));
        assertFalse(pool.isBusy("a"));
    }

    @Test
    void hostLeavingDuringCountdownHandsOver() {
        room(1, 2);
        book.setArena(p.get(0), Optional.of("a"));
        book.start(p.get(0));
        book.leave(p.get(0));
        List<RoomEvent> events = ticks(100);
        RoomEvent.Start start = (RoomEvent.Start) events.stream()
                .filter(e -> e instanceof RoomEvent.Start).findFirst().orElseThrow();
        assertEquals(2, start.teams().stream().mapToInt(List::size).sum());
        assertEquals(p.get(1), book.roomOf(p.get(1)).orElseThrow().host());
    }

    @Test
    void lastLeaveDuringCountdownReleasesArena() {
        room();
        book.invite(p.get(0), p.get(1));
        book.join(p.get(1), p.get(0));
        book.setArena(p.get(0), Optional.of("a"));
        book.start(p.get(0));
        book.leave(p.get(1));
        book.leave(p.get(0));
        assertFalse(pool.isBusy("a"));
    }

    @Test
    void leaveWhilePlayingRemovesMember() {
        room(1, 2);
        book.start(p.get(0));
        ticks(100);
        assertEquals(Outcome.PLAYING, book.kick(p.get(0), p.get(2)));
        book.leave(p.get(2));
        int id = view().id();
        book.matchEnded(id);
        assertFalse(view().playing());
        assertEquals(List.of(p.get(0), p.get(1)), view().members());
    }

    @Test
    void matchEndedAllowsNewStart() {
        room(1);
        book.start(p.get(0));
        ticks(100);
        assertEquals(Outcome.PLAYING, book.start(p.get(0)));
        book.matchEnded(view().id());
        pool.release("a");
        pool.release("b");
        assertEquals(Outcome.OK, book.start(p.get(0)));
    }

    @Test
    void joinAllowedWhilePlaying() {
        room(1);
        book.start(p.get(0));
        ticks(100);
        assertEquals(Outcome.OK, book.invite(p.get(0), p.get(2)));
        assertEquals(Outcome.OK, book.join(p.get(2), p.get(0)));
    }
}
