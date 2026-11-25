package be.dbsdw.minigames.paintball.rules;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Smoke clouds in one match: spheres that block the bots' view until they clear. */
public final class Smokes {
    public record Cloud(double x, double y, double z, double radius, long until) {}

    private final List<Cloud> clouds = new ArrayList<>();

    public void add(double x, double y, double z, double radius, long until) {
        clouds.add(new Cloud(x, y, z, radius, until));
    }

    /** Clouds still up; drops the ones that cleared. */
    public List<Cloud> active(long now) {
        for (Iterator<Cloud> it = clouds.iterator(); it.hasNext(); ) {
            if (it.next().until() <= now) {
                it.remove();
            }
        }
        return List.copyOf(clouds);
    }

    public void clear() {
        clouds.clear();
    }

    /** Whether the line from a to b passes through a cloud (or starts or ends in one). */
    public boolean blocks(double[] a, double[] b, long now) {
        for (Cloud c : clouds) {
            if (c.until() > now && segmentHits(a, b, c)) {
                return true;
            }
        }
        return false;
    }

    static boolean segmentHits(double[] a, double[] b, Cloud c) {
        double dx = b[0] - a[0];
        double dy = b[1] - a[1];
        double dz = b[2] - a[2];
        double len2 = dx * dx + dy * dy + dz * dz;
        double t = len2 == 0 ? 0 : ((c.x() - a[0]) * dx + (c.y() - a[1]) * dy + (c.z() - a[2]) * dz) / len2;
        t = Math.clamp(t, 0, 1);
        double px = a[0] + dx * t - c.x();
        double py = a[1] + dy * t - c.y();
        double pz = a[2] + dz * t - c.z();
        return px * px + py * py + pz * pz <= c.radius() * c.radius();
    }
}
