package be.dbsdw.minigames.paintball.rules;

/**
 * Fade stage of a paint splat for its age: fully visible for the first 70 % of its lifetime, then
 * three equal steps of fading (stages 1 to 3). Matches the splat textures in the resource pack.
 */
public final class SplatFade {
    public static final int LAST_STAGE = 3;

    private SplatFade() {
    }

    public static int stage(long ageTicks, long lifetimeTicks) {
        if (ageTicks <= 0 || lifetimeTicks <= 0) {
            return 0;
        }
        long full = lifetimeTicks * 7 / 10;
        if (ageTicks < full) {
            return 0;
        }
        long step = Math.max(1, lifetimeTicks / 10);
        return (int) Math.min(LAST_STAGE, 1 + (ageTicks - full) / step);
    }
}
