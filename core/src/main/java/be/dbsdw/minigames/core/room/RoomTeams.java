package be.dbsdw.minigames.core.room;

import be.dbsdw.minigames.core.match.TeamSplit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.random.RandomGenerator;

/** Turns a room's members and team picks into the teams of a starting match. */
public final class RoomTeams {
    /** How a room's teams are made; the host chooses. */
    public enum Rule {
        /** Members pick their own team. */
        PICK,
        /** The host moves members between teams. */
        HOST,
        /** Everyone is shuffled into balanced teams. */
        RANDOM;

        public Rule next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private RoomTeams() {}

    /**
     * The teams for a match. Under {@link Rule#RANDOM} picks are ignored; otherwise picks are kept and
     * each member without one joins the smallest team (ties random). A team can end up empty when
     * members picked that way; the caller checks.
     */
    public static List<List<UUID>> build(List<UUID> members, Map<UUID, Integer> picks, Rule rule, int teams,
                                         RandomGenerator rnd) {
        if (rule == Rule.RANDOM) {
            return TeamSplit.split(members, teams, rnd);
        }
        List<List<UUID>> result = new ArrayList<>();
        for (int i = 0; i < teams; i++) {
            result.add(new ArrayList<>());
        }
        List<UUID> unpicked = new ArrayList<>();
        for (UUID member : members) {
            Integer team = picks.get(member);
            if (team != null && team >= 0 && team < teams) {
                result.get(team).add(member);
            } else {
                unpicked.add(member);
            }
        }
        Collections.shuffle(unpicked, Random.from(rnd));
        for (UUID member : unpicked) {
            int min = result.stream().mapToInt(List::size).min().orElseThrow();
            List<Integer> smallest = new ArrayList<>();
            for (int i = 0; i < teams; i++) {
                if (result.get(i).size() == min) {
                    smallest.add(i);
                }
            }
            result.get(smallest.get(rnd.nextInt(smallest.size()))).add(member);
        }
        return result.stream().map(List::copyOf).toList();
    }
}
