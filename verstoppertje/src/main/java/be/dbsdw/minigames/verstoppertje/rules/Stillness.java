package be.dbsdw.minigames.verstoppertje.rules;

/** How long a hider has stood on the same block without looking around. */
public final class Stillness {
    /** Turning further than this (degrees, either way) counts as moving. */
    public static final float TURN = 10f;
    private long block = Long.MIN_VALUE;
    private long since;
    private boolean looked;
    private float yaw;
    private float pitch;

    /** Updates with the block the hider stands in now; true when they moved to another block. */
    public boolean update(long blockKey, long now) {
        if (blockKey == block) {
            return false;
        }
        block = blockKey;
        since = now;
        return true;
    }

    /** Updates with where the hider looks; true when they turned more than {@link #TURN} since the last reset. */
    public boolean look(float yawNow, float pitchNow, long now) {
        if (!looked) {
            looked = true;
            yaw = yawNow;
            pitch = pitchNow;
            return false;
        }
        float dy = Math.abs(((yawNow - yaw) % 360 + 540) % 360 - 180);
        if (dy <= TURN && Math.abs(pitchNow - pitch) <= TURN) {
            return false;
        }
        yaw = yawNow;
        pitch = pitchNow;
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
