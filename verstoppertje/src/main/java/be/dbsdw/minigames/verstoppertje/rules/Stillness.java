package be.dbsdw.minigames.verstoppertje.rules;

/** How long a hider has stood on the same block. */
public final class Stillness {
    private long block = Long.MIN_VALUE;
    private long since;

    /** Updates with the block the hider stands in now; true when they moved to another block. */
    public boolean update(long blockKey, long now) {
        if (blockKey == block) {
            return false;
        }
        block = blockKey;
        since = now;
        return true;
    }

    public long stillFor(long now) {
        return now - since;
    }

    public static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }
}
