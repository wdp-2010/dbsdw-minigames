package be.dbsdw.minigames.verstoppertje.rules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;

/** How many seekers a match starts with, and who they are. */
public final class Roles {
    private Roles() {}

    /** One seeker per {@code perSeeker} players, rounded; at least one seeker and one hider. */
    public static int seekers(int players, int perSeeker) {
        if (players < 2) {
            return players;
        }
        int n = Math.round((float) players / Math.max(1, perSeeker));
        return Math.max(1, Math.min(players - 1, n));
    }

    /**
     * Picks the seekers: players the core put in the seekers' team first (in a room they chose it),
     * then random hiders when more are needed; random ones of the seekers' team stay when fewer are.
     */
    public static Set<UUID> pick(List<UUID> hiders, List<UUID> seekers, int count, RandomGenerator rnd) {
        List<UUID> first = new ArrayList<>(seekers);
        List<UUID> rest = new ArrayList<>(hiders);
        Collections.shuffle(first, Random.from(rnd));
        Collections.shuffle(rest, Random.from(rnd));
        first.addAll(rest);
        return new LinkedHashSet<>(first.subList(0, Math.min(count, first.size())));
    }
}
