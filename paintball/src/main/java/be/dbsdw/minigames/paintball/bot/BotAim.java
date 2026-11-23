package be.dbsdw.minigames.paintball.bot;

import java.util.Optional;

/**
 * Where a bot points its gun so a paintball lands on a target: the paintball flies like a snowball
 * (drag 0.99 per tick) with part of its gravity given back (see the paintball's {@code gun.gravity}),
 * so the bot aims a little above far targets. With {@code lead} it aims where a moving target will be
 * when the paintball gets there.
 */
public final class BotAim {
    /** Vanilla snowball gravity per tick. */
    static final double GRAVITY = 0.03;
    static final double DRAG = 0.99;

    /** A unit aim direction and the paintball's flight time in ticks. */
    public record Solution(double x, double y, double z, int ticks) {}

    private BotAim() {
    }

    /**
     * @param from      muzzle position {x, y, z}
     * @param target    point to hit {x, y, z}
     * @param velocity  the target's velocity in blocks per tick {x, y, z}
     * @param speed     paintball launch speed in blocks per tick
     * @param gravity   share of snowball gravity the paintball keeps (0..1)
     * @param lifetime  ticks before a paintball is removed
     * @return empty when the target is out of reach
     */
    public static Optional<Solution> solve(double[] from, double[] target, double[] velocity, double speed,
                                           double gravity, int lifetime, boolean lead) {
        double[] aimAt = target.clone();
        Optional<Solution> out = Optional.empty();
        for (int round = 0; round < (lead ? 3 : 1); round++) {
            out = solveStill(from, aimAt, speed, gravity, lifetime);
            if (out.isEmpty() || !lead) {
                return out;
            }
            int t = out.get().ticks();
            aimAt = new double[] {target[0] + velocity[0] * t, target[1], target[2] + velocity[2] * t};
        }
        return out;
    }

    private static Optional<Solution> solveStill(double[] from, double[] to, double speed, double gravity,
                                                 int lifetime) {
        double dx = to[0] - from[0];
        double dz = to[2] - from[2];
        double dy = to[1] - from[1];
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double yaw = Math.atan2(dz, dx);
        if (horizontal < 0.5) {
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            return len == 0 ? Optional.empty() : Optional.of(new Solution(dx / len, dy / len, dz / len, 1));
        }
        // Height at the target's distance rises with the pitch (for pitches up to 45 degrees): bisect.
        double lo = Math.toRadians(-60);
        double hi = Math.toRadians(45);
        Flight top = fly(hi, speed, gravity, horizontal, lifetime);
        if (top == null || top.height < dy) {
            return Optional.empty();
        }
        Flight best = top;
        double pitch = hi;
        for (int i = 0; i < 40; i++) {
            double mid = (lo + hi) / 2;
            Flight f = fly(mid, speed, gravity, horizontal, lifetime);
            if (f == null || f.height < dy) {
                lo = mid;
            } else {
                hi = mid;
                pitch = mid;
                best = f;
            }
        }
        double c = Math.cos(pitch);
        return Optional.of(new Solution(Math.cos(yaw) * c, Math.sin(pitch), Math.sin(yaw) * c, best.ticks));
    }

    record Flight(double height, int ticks) {}

    /** The paintball's height when it has flown {@code horizontal} blocks, or null if it never gets there. */
    static Flight fly(double pitch, double speed, double gravity, double horizontal, int lifetime) {
        double vh = Math.cos(pitch) * speed;
        double vy = Math.sin(pitch) * speed;
        double h = 0;
        double y = 0;
        double giveBack = GRAVITY * (1 - gravity);
        for (int t = 1; t <= lifetime; t++) {
            vy -= GRAVITY;
            double nh = h + vh;
            double ny = y + vy;
            if (nh >= horizontal) {
                double f = (horizontal - h) / vh;
                return new Flight(y + (ny - y) * f, t);
            }
            h = nh;
            y = ny;
            vh *= DRAG;
            vy *= DRAG;
            vy += giveBack;
        }
        return null;
    }
}
