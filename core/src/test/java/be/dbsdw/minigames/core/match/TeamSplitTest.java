package be.dbsdw.minigames.core.match;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class TeamSplitTest {
    private final List<UUID> seven = IntStream.range(0, 7).mapToObj(i -> new UUID(0, i)).toList();

    @Test
    void sizesDifferByAtMostOne() {
        List<List<UUID>> teams = TeamSplit.split(seven, 2, new Random(3));
        assertEquals(Set.of(4, 3), Set.of(teams.get(0).size(), teams.get(1).size()));
    }

    @Test
    void everyPlayerOnce() {
        List<List<UUID>> teams = TeamSplit.split(seven, 3, new Random(3));
        Set<UUID> all = new HashSet<>();
        teams.forEach(all::addAll);
        assertEquals(7, teams.stream().mapToInt(List::size).sum());
        assertEquals(Set.copyOf(seven), all);
    }

    @Test
    void sameSeedSameSplit() {
        assertEquals(TeamSplit.split(seven, 2, new Random(9)), TeamSplit.split(seven, 2, new Random(9)));
    }
}
