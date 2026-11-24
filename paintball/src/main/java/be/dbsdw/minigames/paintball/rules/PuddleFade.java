package be.dbsdw.minigames.paintball.rules;

/**
 * The life of a puddle at the foot of a wall: it spreads out right after the paint lands, lies
 * there for a while, then soaks away: it shrinks and fades through the three splat fade stages.
 */
public final class PuddleFade {
    /** Ticks the puddle takes to spread to full size. */
    public static final int SPREAD = 8;
    /** Age at which it starts to soak away. */
    public static final long HOLD = 100;
    /** Ticks the soaking away takes; the puddle is gone after HOLD + DISSOLVE. */
    public static final int DISSOLVE = 60;

    private PuddleFade() {
    }

    /** Fade stage 0..3 (as the splat textures) for the puddle's age. */
    public static int stage(long age) {
        if (age < HOLD) {
            return 0;
        }
        return (int) Math.min(SplatFade.LAST_STAGE, 1 + (age - HOLD) * SplatFade.LAST_STAGE / DISSOLVE);
    }

    public static boolean gone(long age) {
        return age >= HOLD + DISSOLVE;
    }
}
