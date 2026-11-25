package be.dbsdw.minigames.travel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class QueueStatusTest {
    private static final Instant T0 = Instant.parse("2026-11-25T12:00:00Z");

    private static QueueStatus q(String key, int queued) {
        return new QueueStatus(key, "<red>" + key, queued, 2, 4);
    }

    @Test
    void encodeDecodeRoundTrip() {
        List<QueueStatus> queues = List.of(q("paintball:tdm:1v1", 1), new QueueStatus("chess:classic", "Schaak\tx", 1, 2, 2));
        List<QueueStatus> back = QueueStatus.decode(QueueStatus.encode(queues));
        assertEquals(2, back.size());
        assertEquals(queues.get(0), back.get(0));
        assertEquals("Schaak x", back.get(1).name());
    }

    @Test
    void decodeSkipsBadLines() {
        assertEquals(List.of(q("a:b", 1)), QueueStatus.decode("garbage\na:b\t1\t2\t4\t<red>a:b\nc:d\tx\t2\t4\tn\n"));
    }

    @Test
    void openMeansWaitingAndNotFull() {
        assertTrue(q("a:b", 1).open());
        assertFalse(q("a:b", 0).open());
        assertFalse(q("a:b", 4).open());
    }

    @Test
    void announcesBiggestOpenQueueOncePerGap() {
        QueueAnnouncer a = new QueueAnnouncer(Duration.ofMinutes(10));
        List<QueueStatus> queues = List.of(q("a:b", 1), q("a:c", 2), q("a:d", 4));
        assertEquals(Optional.of(q("a:c", 2)), a.next(queues, T0));
        assertEquals(Optional.empty(), a.next(List.of(q("a:b", 3)), T0.plusSeconds(599)));
        assertEquals(Optional.of(q("a:b", 3)), a.next(List.of(q("a:b", 3)), T0.plusSeconds(600)));
    }

    @Test
    void noRepeatOfTheSameQueueWithNothingNew() {
        QueueAnnouncer a = new QueueAnnouncer(Duration.ofMinutes(10));
        assertTrue(a.next(List.of(q("a:b", 1)), T0).isPresent());
        assertTrue(a.next(List.of(q("a:b", 1)), T0.plusSeconds(3600)).isEmpty());
        assertTrue(a.next(List.of(q("a:b", 2)), T0.plusSeconds(3600)).isPresent());
    }

    @Test
    void firstOfEqualsWinsAndNothingOpenIsSilent() {
        QueueAnnouncer a = new QueueAnnouncer(Duration.ofMinutes(10));
        assertTrue(a.next(List.of(q("a:b", 0)), T0).isEmpty());
        assertEquals("a:b", a.next(List.of(q("a:b", 1), q("a:c", 1)), T0).orElseThrow().key());
    }

    private static QueueStatus leveled(String key, int queued, int level) {
        return new QueueStatus(key, key, queued, 2, 4, "paintball", level);
    }

    @Test
    void levelAndGameRoundTripAndOldLinesStillRead() {
        QueueStatus bomb = leveled("paintball:bomb", 1, 5);
        assertEquals(List.of(bomb), QueueStatus.decode(QueueStatus.encode(List.of(bomb))));
        QueueStatus old = QueueStatus.decode("chess:classic\t1\t2\t2\tSchaak\n").getFirst();
        assertEquals("chess", old.game());
        assertFalse(old.leveled());
    }

    @Test
    void levelsRoundTrip() {
        UUID a = UUID.fromString("00000000-0000-0000-0000-000000000001");
        Map<UUID, Integer> levels = Map.of(a, 7);
        assertEquals(levels, QueueStatus.decodeLevels(QueueStatus.encodeLevels(levels) + "junk\n"));
    }

    @Test
    void eachPlayerGetsTheBiggestQueueTheirLevelAllows() {
        QueueAnnouncer a = new QueueAnnouncer(Duration.ofMinutes(10));
        List<QueueStatus> c = a.candidates(List.of(q("paintball:tdm:1v1", 1), leveled("paintball:bomb", 3, 5)), T0);
        assertEquals("paintball:bomb", QueueAnnouncer.firstAllowed(c, g -> 5).orElseThrow().key());
        assertEquals("paintball:tdm:1v1", QueueAnnouncer.firstAllowed(c, g -> 4).orElseThrow().key());
        assertTrue(QueueAnnouncer.firstAllowed(List.of(leveled("paintball:bomb", 3, 5)), g -> 1).isEmpty());
    }

    @Test
    void candidatesWithoutAnnouncingKeepTheGapOpen() {
        QueueAnnouncer a = new QueueAnnouncer(Duration.ofMinutes(10));
        assertEquals(1, a.candidates(List.of(q("a:b", 1)), T0).size());
        assertEquals(1, a.candidates(List.of(q("a:b", 1)), T0.plusSeconds(1)).size());
        a.announced(q("a:b", 1), T0.plusSeconds(1));
        assertTrue(a.candidates(List.of(q("a:c", 2)), T0.plusSeconds(2)).isEmpty());
    }
}
