package be.dbsdw.minigames.core.match;

import be.dbsdw.minigames.core.TeamBalancer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.random.RandomGenerator;

/** Splits the players of a starting match into teams whose sizes differ by at most one. */
public final class TeamSplit {
    private TeamSplit() {}

    public static List<List<UUID>> split(List<UUID> players, int teams, RandomGenerator rnd) {
        List<UUID> shuffled = new ArrayList<>(players);
        Collections.shuffle(shuffled, Random.from(rnd));
        List<List<UUID>> result = new ArrayList<>();
        for (int i = 0; i < teams; i++) {
            result.add(new ArrayList<>());
        }
        int[] sizes = new int[teams];
        for (UUID player : shuffled) {
            int team = TeamBalancer.pickTeam(sizes);
            result.get(team).add(player);
            sizes[team]++;
        }
        return result.stream().map(List::copyOf).toList();
    }
}
