package be.dbsdw.minigames.core.arena;

/** A position in an arena's world, without Bukkit types. */
public record Pos(double x, double y, double z, float yaw, float pitch) {
    public int blockX() {
        return (int) Math.floor(x);
    }

    public int blockY() {
        return (int) Math.floor(y);
    }

    public int blockZ() {
        return (int) Math.floor(z);
    }

    /** Moved by whole blocks along x and z. */
    public Pos plus(int dx, int dz) {
        return new Pos(x + dx, y, z + dz, yaw, pitch);
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.ROOT, "%.1f,%.1f,%.1f", x, y, z);
    }
}
