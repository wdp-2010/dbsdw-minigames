package be.dbsdw.minigames.verstoppertje.rules;

/** Where a ray first enters an axis-aligned box. */
public final class RayBox {
    private RayBox() {}

    /**
     * Distance along the ray (direction need not be normalised: the result is in units of its
     * length) from the origin to the box, 0 when the origin is inside, or -1 when it misses.
     */
    public static double distance(double ox, double oy, double oz, double dx, double dy, double dz,
                                  double x1, double y1, double z1, double x2, double y2, double z2) {
        double near = Double.NEGATIVE_INFINITY;
        double far = Double.POSITIVE_INFINITY;
        double[] o = {ox, oy, oz};
        double[] d = {dx, dy, dz};
        double[] lo = {x1, y1, z1};
        double[] hi = {x2, y2, z2};
        for (int i = 0; i < 3; i++) {
            if (Math.abs(d[i]) < 1e-12) {
                if (o[i] < lo[i] || o[i] > hi[i]) {
                    return -1;
                }
                continue;
            }
            double a = (lo[i] - o[i]) / d[i];
            double b = (hi[i] - o[i]) / d[i];
            near = Math.max(near, Math.min(a, b));
            far = Math.min(far, Math.max(a, b));
        }
        if (far < 0 || near > far) {
            return -1;
        }
        return Math.max(0, near);
    }
}
