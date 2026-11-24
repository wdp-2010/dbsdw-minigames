package be.dbsdw.minigames.paintball.rules;

import java.util.random.RandomGenerator;

/** A random direction inside a cone around an aim direction, uniform over the cone. */
public final class Spread {
    private Spread() {
    }

    /** Returns a unit vector at most {@code maxDegrees} away from (x, y, z). */
    public static double[] apply(double x, double y, double z, double maxDegrees, RandomGenerator rng) {
        double len = Math.sqrt(x * x + y * y + z * z);
        if (len == 0) {
            throw new IllegalArgumentException("direction must not be zero");
        }
        double dx = x / len;
        double dy = y / len;
        double dz = z / len;
        if (maxDegrees <= 0) {
            return new double[] {dx, dy, dz};
        }
        double cosT = 1 - rng.nextDouble() * (1 - Math.cos(Math.toRadians(maxDegrees)));
        return turn(dx, dy, dz, cosT, rng);
    }

    /** Returns a unit vector exactly {@code degrees} away from (x, y, z), in a random direction. */
    public static double[] tilt(double x, double y, double z, double degrees, RandomGenerator rng) {
        double len = Math.sqrt(x * x + y * y + z * z);
        if (len == 0) {
            throw new IllegalArgumentException("direction must not be zero");
        }
        return turn(x / len, y / len, z / len, Math.cos(Math.toRadians(degrees)), rng);
    }

    /** Turns the unit vector d by the angle whose cosine is {@code cosT}, around d at a random angle. */
    private static double[] turn(double dx, double dy, double dz, double cosT, RandomGenerator rng) {
        double sinT = Math.sqrt(Math.max(0, 1 - cosT * cosT));
        double phi = 2 * Math.PI * rng.nextDouble();
        // Helper axis not parallel to the direction.
        double hx = Math.abs(dx) < 0.9 ? 1 : 0;
        double hy = Math.abs(dx) < 0.9 ? 0 : 1;
        // a = normalize(d x h) with h = (hx, hy, 0), b = d x a
        double ax = -dz * hy;
        double ay = dz * hx;
        double az = dx * hy - dy * hx;
        double alen = Math.sqrt(ax * ax + ay * ay + az * az);
        ax /= alen;
        ay /= alen;
        az /= alen;
        double bx = dy * az - dz * ay;
        double by = dz * ax - dx * az;
        double bz = dx * ay - dy * ax;
        double c = sinT * Math.cos(phi);
        double s = sinT * Math.sin(phi);
        return new double[] {
            dx * cosT + ax * c + bx * s,
            dy * cosT + ay * c + by * s,
            dz * cosT + az * c + bz * s,
        };
    }
}
