package be.dbsdw.minigames.verstoppertje.rules;

/** Verstoppertje's config.yml. Times in ticks. */
public record Settings(int maxPerTeam, int playersPerSeeker, long hideTicks, long seekTicks, long solidTicks,
                       long hintTicks, double reach) {
    public Settings {
        if (maxPerTeam < 1 || playersPerSeeker < 1) {
            throw new IllegalArgumentException("max-per-team and players-per-seeker must be 1 or more");
        }
        if (hideTicks < 0 || seekTicks <= 0 || solidTicks < 0) {
            throw new IllegalArgumentException("hide-seconds and solid-seconds must be 0 or more, seek-minutes more than 0");
        }
        if (reach <= 0 || reach > 6) {
            throw new IllegalArgumentException("reach must be between 0 and 6");
        }
    }

    /** No hints when hint-seconds is 0. */
    public boolean hints() {
        return hintTicks > 0;
    }
}
