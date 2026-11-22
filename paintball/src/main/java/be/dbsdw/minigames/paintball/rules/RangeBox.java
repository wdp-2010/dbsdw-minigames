package be.dbsdw.minigames.paintball.rules;

/** A box of whole blocks, both corner blocks included: the practice range zone. */
public record RangeBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    /** From two corner blocks in any order. */
    public static RangeBox of(int x1, int y1, int z1, int x2, int y2, int z2) {
        return new RangeBox(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
    }

    public boolean contains(double x, double y, double z) {
        return x >= minX && x < maxX + 1 && y >= minY && y < maxY + 1 && z >= minZ && z < maxZ + 1;
    }
}
