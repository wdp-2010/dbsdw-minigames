package be.dbsdw.minigames.paintball.rules;

/**
 * Every paintball rule value, in ticks where it is a duration. Built from config.yml; invalid
 * values are refused with the name of the field.
 */
public record Settings(int minPerTeam, int maxPerTeam, int winEliminations, long timeLimitTicks, int hitsToOut,
                       long respawnTicks, long protectionTicks, int shotIntervalTicks, double speed,
                       double spreadDegrees, long ballLifetimeTicks) {
    public Settings {
        require(minPerTeam >= 1, "tdm.min-per-team must be >= 1");
        require(maxPerTeam >= minPerTeam, "tdm.max-per-team must be >= tdm.min-per-team");
        require(winEliminations >= 1, "tdm.win-eliminations must be >= 1");
        require(timeLimitTicks >= 20, "tdm.time-limit-minutes must be > 0");
        require(hitsToOut >= 1, "player.hits-to-out must be >= 1");
        require(respawnTicks >= 0, "player.respawn-seconds must be >= 0");
        require(protectionTicks >= 0, "player.spawn-protection-seconds must be >= 0");
        require(shotIntervalTicks >= 1, "gun.shot-interval-ticks must be >= 1");
        require(speed > 0 && speed <= 10, "gun.speed must be > 0 and <= 10");
        require(spreadDegrees >= 0 && spreadDegrees <= 45, "gun.spread-degrees must be between 0 and 45");
        require(ballLifetimeTicks >= 1, "gun.ball-lifetime-ticks must be >= 1");
    }

    /** The spec's defaults. */
    public static Settings defaults() {
        return new Settings(2, 8, 30, 10 * 60 * 20, 3, 3 * 20, 2 * 20, 5, 3.0, 1.0, 60);
    }

    private static void require(boolean ok, String message) {
        if (!ok) {
            throw new IllegalArgumentException(message);
        }
    }
}
