package be.dbsdw.minigames.paintball.bot;

import java.util.Locale;
import java.util.Optional;

/**
 * How well a paintball bot plays: how far off its aim is, how long it takes to react to an enemy it
 * sees, how much slower than the gun allows it shoots, how far it sees, how close it gets before it
 * fights, how fast it walks, and whether it leads moving targets and dodges sideways while fighting.
 */
public enum BotLevel {
    MAKKELIJK(3.0, 24, 2.6, 26, 12, 0.16, false, false),
    NORMAAL(1.6, 14, 1.7, 36, 16, 0.19, true, false),
    MOEILIJK(0.8, 8, 1.2, 48, 20, 0.22, true, true),
    MEESTER(0.35, 4, 1.0, 64, 24, 0.25, true, true);

    private final double aimErrorDegrees;
    private final int reactionTicks;
    private final double fireSlowdown;
    private final double sightRange;
    private final double fightRange;
    private final double walkSpeed;
    private final boolean leads;
    private final boolean strafes;

    BotLevel(double aimErrorDegrees, int reactionTicks, double fireSlowdown, double sightRange, double fightRange,
             double walkSpeed,
             boolean leads, boolean strafes) {
        this.aimErrorDegrees = aimErrorDegrees;
        this.reactionTicks = reactionTicks;
        this.fireSlowdown = fireSlowdown;
        this.sightRange = sightRange;
        this.fightRange = fightRange;
        this.walkSpeed = walkSpeed;
        this.leads = leads;
        this.strafes = strafes;
    }

    /**
     * Typical aim error in degrees: each shot is off by a random angle that is usually smaller than
     * this and at most three times it (on top of the gun's own spread).
     */
    public double aimErrorDegrees() {
        return aimErrorDegrees;
    }

    /** Ticks between first seeing an enemy and the first shot at it. */
    public int reactionTicks() {
        return reactionTicks;
    }

    /** Ticks between two shots, given the gun's shot interval. */
    public int shotInterval(int gunInterval) {
        return Math.max(gunInterval, (int) Math.round(gunInterval * fireSlowdown));
    }

    /** Blocks the bot sees and shoots enemies at. */
    public double sightRange() {
        return sightRange;
    }

    /**
     * Blocks from an enemy at which the bot stops closing in and fights; it only shoots within one and
     * a half times this, where its shots can land.
     */
    public double fightRange() {
        return fightRange;
    }

    /** Blocks per tick (a player walks about 0.22, sprints about 0.28). */
    public double walkSpeed() {
        return walkSpeed;
    }

    /** Aims where a moving enemy will be when the paintball arrives. */
    public boolean leads() {
        return leads;
    }

    /** Steps sideways while shooting, which makes it harder to hit. */
    public boolean strafes() {
        return strafes;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<BotLevel> parse(String id) {
        for (BotLevel l : values()) {
            if (l.id().equalsIgnoreCase(id)) {
                return Optional.of(l);
            }
        }
        return Optional.empty();
    }
}
