package be.dbsdw.minigames.paintball.bot;

import java.util.Locale;
import java.util.Optional;

/**
 * How well a paintball bot plays: how far off its aim is, how long it takes to react to an enemy it
 * sees, how much slower than the gun allows it shoots, how far it sees, how fast it walks, and
 * whether it leads moving targets and dodges sideways while fighting.
 */
public enum BotLevel {
    MAKKELIJK(7.0, 24, 2.6, 26, 0.16, false, false),
    NORMAAL(3.5, 14, 1.7, 36, 0.19, true, false),
    MOEILIJK(1.8, 8, 1.2, 48, 0.22, true, true),
    MEESTER(0.7, 4, 1.0, 64, 0.25, true, true);

    private final double aimErrorDegrees;
    private final int reactionTicks;
    private final double fireSlowdown;
    private final double sightRange;
    private final double walkSpeed;
    private final boolean leads;
    private final boolean strafes;

    BotLevel(double aimErrorDegrees, int reactionTicks, double fireSlowdown, double sightRange, double walkSpeed,
             boolean leads, boolean strafes) {
        this.aimErrorDegrees = aimErrorDegrees;
        this.reactionTicks = reactionTicks;
        this.fireSlowdown = fireSlowdown;
        this.sightRange = sightRange;
        this.walkSpeed = walkSpeed;
        this.leads = leads;
        this.strafes = strafes;
    }

    /** Random aim error, in degrees (a cone around the perfect shot). */
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
