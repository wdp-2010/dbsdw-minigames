package be.dbsdw.minigames.valvloer.rules;

/** A block position packed into one long (x and z: 26 bits, y: 12 bits, all signed). */
public final class BlockKey {
    private BlockKey() {}

    public static long of(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    public static int x(long key) {
        return (int) (key >> 38);
    }

    public static int y(long key) {
        return (int) (key << 52 >> 52);
    }

    public static int z(long key) {
        return (int) (key << 26 >> 38);
    }
}
