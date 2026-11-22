package be.dbsdw.minigames.core.room;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RoomTeamsTest {
    private final List<UUID> p = IntStream.range(0, 4).mapToObj(i -> new UUID(0, i)).toList();

    private static List<Integer> sizes(List<List<UUID>> teams) {
        return teams.stream().map(List::size).sorted().toList();
    }

    @Test
    void twoUnpickedBecomeOneVsOne() {
        List<List<UUID>> teams = RoomTeams.build(p.subList(0, 2), Map.of(), RoomTeams.Rule.PICK, 2, new Random(1));
        assertEquals(1, teams.get(0).size());
        assertEquals(1, teams.get(1).size());
    }

    @Test
    void threeUnpickedBecomeTwoVsOne() {
        List<List<UUID>> teams = RoomTeams.build(p.subList(0, 3), Map.of(), RoomTeams.Rule.HOST, 2, new Random(1));
        assertEquals(List.of(1, 2), sizes(teams));
    }

    @Test
    void picksAreKept() {
        List<List<UUID>> teams = RoomTeams.build(p.subList(0, 3), Map.of(p.get(0), 0, p.get(1), 0),
                RoomTeams.Rule.PICK, 2, new Random(1));
        assertEquals(List.of(p.get(0), p.get(1)), teams.get(0));
        assertEquals(List.of(p.get(2)), teams.get(1));
    }

    @Test
    void picksCanLeaveATeamEmpty() {
        List<List<UUID>> teams = RoomTeams.build(p.subList(0, 2), Map.of(p.get(0), 0, p.get(1), 0),
                RoomTeams.Rule.PICK, 2, new Random(1));
        assertTrue(teams.get(1).isEmpty());
    }

    @Test
    void randomIgnoresPicks() {
        List<List<UUID>> teams = RoomTeams.build(p.subList(0, 2), Map.of(p.get(0), 0, p.get(1), 0),
                RoomTeams.Rule.RANDOM, 2, new Random(1));
        assertEquals(List.of(1, 1), sizes(teams));
    }

    @Test
    void rulesCycle() {
        assertEquals(RoomTeams.Rule.HOST, RoomTeams.Rule.PICK.next());
        assertEquals(RoomTeams.Rule.RANDOM, RoomTeams.Rule.HOST.next());
        assertEquals(RoomTeams.Rule.PICK, RoomTeams.Rule.RANDOM.next());
    }
}
