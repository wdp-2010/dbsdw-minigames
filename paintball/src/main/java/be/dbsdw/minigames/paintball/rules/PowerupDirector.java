package be.dbsdw.minigames.paintball.rules;

import be.dbsdw.minigames.paintball.rules.WalkableArea.Cell;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * Picks where the next power-up appears and which one it is.
 *
 * <p>Spots are random walkable floor, away from players, team spawns and other power-ups. When one
 * team is ahead, a quiet catch-up leans both choices towards the team that is behind: spots nearer
 * its players weigh more, and near them repair and shield come up more often. The lean grows with
 * the score gap and is capped, so at the biggest gap still roughly a third of the power-ups land on
 * the leading team's side: it helps, but it never looks rigged.
 */
public final class PowerupDirector {
    /** A player's position and team. */
    public record Mover(double x, double y, double z, int team) {
    }

    static final double SPOT_LEAN = 0.8;
    static final double TYPE_LEAN = 1.5;
    static final int SAMPLE = 64;
    static final double MIN_PLAYER = 4;
    static final double MIN_OTHER = 7;
    static final double MIN_SPAWN = 5;

    private final List<Cell> cells;
    private final List<Cell> spawns;
    private final double catchUp;
    private final RandomGenerator rng;

    /**
     * @param cells walkable floor (from {@link WalkableArea})
     * @param spawns team spawn positions; power-ups keep away from them
     * @param catchUp 0 = no lean, 1 = default, up to 2
     */
    public PowerupDirector(List<Cell> cells, List<Cell> spawns, double catchUp, RandomGenerator rng) {
        this.cells = List.copyOf(cells);
        this.spawns = List.copyOf(spawns);
        this.catchUp = catchUp;
        this.rng = rng;
    }

    public boolean hasSpots() {
        return !cells.isEmpty();
    }

    /** How strongly to help the team that is behind, 0..catchUp: 0 when level. */
    double strength(int[] score, int winScore) {
        int gap = Math.abs(score[0] - score[1]);
        double full = Math.max(1, winScore * 0.2);
        return catchUp * Math.min(1, gap / full);
    }

    /** The team that is behind, or -1 when level. */
    static int trailing(int[] score) {
        return score[0] == score[1] ? -1 : score[0] < score[1] ? 0 : 1;
    }

    /** -1..1: positive when the spot is nearer the trailing team's players than the leading team's. */
    static double lean(Cell spot, List<Mover> players, int trailing) {
        if (trailing < 0) {
            return 0;
        }
        double near = nearest(spot, players, trailing);
        double far = nearest(spot, players, 1 - trailing);
        if (Double.isInfinite(near) || Double.isInfinite(far)) {
            return 0;
        }
        return (far - near) / (far + near + 1);
    }

    private static double nearest(Cell spot, List<Mover> players, int team) {
        double best = Double.POSITIVE_INFINITY;
        for (Mover m : players) {
            if (m.team() == team) {
                best = Math.min(best, Math.sqrt(spot.distanceSquared(m.x(), m.y(), m.z())));
            }
        }
        return best;
    }

    public Optional<Cell> pickSpot(List<Mover> players, List<Cell> occupied, int[] score, int winScore) {
        if (cells.isEmpty()) {
            return Optional.empty();
        }
        int trailing = trailing(score);
        double s = strength(score, winScore);
        List<Cell> options = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        for (int pass = 0; pass < 2 && options.isEmpty(); pass++) {
            boolean strict = pass == 0;
            for (int i = 0; i < SAMPLE * 4 && options.size() < SAMPLE; i++) {
                Cell c = cells.get(rng.nextInt(cells.size()));
                if (strict && !clear(c, players, occupied)) {
                    continue;
                }
                options.add(c);
                weights.add(Math.exp(SPOT_LEAN * s * lean(c, players, trailing)));
            }
        }
        return Optional.of(options.get(weighted(weights)));
    }

    private boolean clear(Cell c, List<Mover> players, List<Cell> occupied) {
        for (Mover m : players) {
            if (c.distanceSquared(m.x(), m.y(), m.z()) < MIN_PLAYER * MIN_PLAYER) {
                return false;
            }
        }
        for (Cell o : occupied) {
            if (c.distanceSquared(o.x() + 0.5, o.y(), o.z() + 0.5) < MIN_OTHER * MIN_OTHER) {
                return false;
            }
        }
        for (Cell sp : spawns) {
            if (c.distanceSquared(sp.x() + 0.5, sp.y(), sp.z() + 0.5) < MIN_SPAWN * MIN_SPAWN) {
                return false;
            }
        }
        return true;
    }

    public PowerupType pickType(Cell spot, List<Mover> players, List<PowerupType> enabled, int[] score, int winScore) {
        double s = strength(score, winScore);
        double lean = Math.max(0, lean(spot, players, trailing(score)));
        List<Double> weights = new ArrayList<>();
        for (PowerupType t : enabled) {
            boolean helps = t == PowerupType.REPAIR || t == PowerupType.SHIELD;
            weights.add(helps ? 1 + TYPE_LEAN * s * lean : 1.0);
        }
        return enabled.get(weighted(weights));
    }

    private int weighted(List<Double> weights) {
        double total = 0;
        for (double w : weights) {
            total += w;
        }
        double r = rng.nextDouble() * total;
        for (int i = 0; i < weights.size(); i++) {
            r -= weights.get(i);
            if (r < 0) {
                return i;
            }
        }
        return weights.size() - 1;
    }
}
