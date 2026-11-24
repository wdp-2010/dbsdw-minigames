package be.dbsdw.minigames.paintball.rules;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Hits, outs, respawns, spawn protection and eliminations of every player in one match. */
public final class Combat {
    public enum HitResult {
        /** Teammate, a player who is out, or not in this match: the paintball flies on. */
        PASS_THROUGH,
        /** Spawn protection: the paintball breaks without effect. */
        ABSORBED,
        /** A shield power-up took the hit (and is used up). */
        SHIELDED,
        HIT,
        OUT
    }

    private static final class State {
        final int team;
        final HitTracker hits;
        boolean out;
        long respawnAt;
        long protectedUntil;
        int eliminations;
        boolean shield;

        State(int team, int hitsToOut) {
            this.team = team;
            this.hits = new HitTracker(hitsToOut);
        }
    }

    private final Settings settings;
    private final long respawnTicks;
    private final Map<UUID, State> players = new LinkedHashMap<>();

    /** {@code teams}: player to team index, in join order. */
    public Combat(Map<UUID, Integer> teams, Settings settings) {
        this(teams, settings, settings.respawnTicks());
    }

    public Combat(Map<UUID, Integer> teams, Settings settings, long respawnTicks) {
        this.settings = settings;
        this.respawnTicks = respawnTicks;
        teams.forEach((id, team) -> players.put(id, new State(team, settings.hitsToOut())));
    }

    /** Everyone alive with no hits and spawn protection. */
    public void start(long now) {
        for (State s : players.values()) {
            s.out = false;
            s.hits.reset();
            s.protectedUntil = now + settings.protectionTicks();
        }
    }

    public HitResult hit(UUID victim, UUID shooter, int shooterTeam, long now) {
        State v = players.get(victim);
        if (v == null || v.out || v.team == shooterTeam) {
            return HitResult.PASS_THROUGH;
        }
        if (now < v.protectedUntil) {
            return HitResult.ABSORBED;
        }
        if (v.shield) {
            v.shield = false;
            return HitResult.SHIELDED;
        }
        if (!v.hits.hit()) {
            return HitResult.HIT;
        }
        goOut(v, now);
        State s = players.get(shooter);
        if (s != null) {
            s.eliminations++;
        }
        return HitResult.OUT;
    }

    /** Out without a shooter (left the arena). False if the player is unknown or already out. */
    public boolean outOfBounds(UUID victim, long now) {
        State v = players.get(victim);
        if (v == null || v.out) {
            return false;
        }
        goOut(v, now);
        return true;
    }

    /** Shooting ends spawn protection. */
    public void shot(UUID shooter) {
        State s = players.get(shooter);
        if (s != null) {
            s.protectedUntil = Long.MIN_VALUE;
        }
    }

    /** Players whose respawn time has come; they are alive again with no hits and protection. */
    public List<UUID> respawnsDue(long now) {
        List<UUID> due = new ArrayList<>();
        players.forEach((id, s) -> {
            if (s.out && now >= s.respawnAt) {
                s.out = false;
                s.hits.reset();
                s.protectedUntil = now + settings.protectionTicks();
                due.add(id);
            }
        });
        return due;
    }

    public boolean isOut(UUID id) {
        State s = players.get(id);
        return s != null && s.out;
    }

    public boolean isProtected(UUID id, long now) {
        State s = players.get(id);
        return s != null && !s.out && now < s.protectedUntil;
    }

    public int hitsLeft(UUID id) {
        State s = players.get(id);
        return s == null || s.out ? 0 : settings.hitsToOut() - s.hits.hits();
    }

    public long respawnAt(UUID id) {
        State s = players.get(id);
        return s == null ? 0 : s.respawnAt;
    }

    public int eliminations(UUID id) {
        State s = players.get(id);
        return s == null ? 0 : s.eliminations;
    }

    public void remove(UUID id) {
        players.remove(id);
    }

    /** Up to n players by eliminations, ties in join order. */
    public List<UUID> top(int n) {
        return players.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<UUID, State> e) -> e.getValue().eliminations).reversed())
                .limit(n)
                .map(Map.Entry::getKey)
                .toList();
    }

    /** Shield power-up: the next hit is absorbed. */
    public void giveShield(UUID id) {
        State s = players.get(id);
        if (s != null && !s.out) {
            s.shield = true;
        }
    }

    public boolean hasShield(UUID id) {
        State s = players.get(id);
        return s != null && s.shield;
    }

    /** Repair power-up: removes one hit already taken. */
    public boolean repair(UUID id) {
        State s = players.get(id);
        return s != null && !s.out && s.hits.heal();
    }

    public boolean isKnown(UUID id) {
        return players.containsKey(id);
    }

    public int team(UUID id) {
        State s = players.get(id);
        return s == null ? -1 : s.team;
    }

    private void goOut(State s, long now) {
        s.out = true;
        s.shield = false;
        s.respawnAt = now + respawnTicks;
    }
}
