package be.dbsdw.minigames.paintball.rules;

/**
 * Every paintball rule value, in ticks where it is a duration. Built from config.yml; invalid
 * values are refused with the name of the key.
 */
public record Settings(ModeRules tdm, ModeRules ctf, int hitsToOut, long respawnTicks, long protectionTicks,
                       int shotIntervalTicks, double speed, double spreadDegrees, long ballLifetimeTicks,
                       long flagReturnTicks, PowerupSettings powerups, long splatLifetimeTicks, int splatCap) {
    public Settings {
        require(hitsToOut >= 1, "player.hits-to-out must be >= 1");
        require(respawnTicks >= 0, "player.respawn-seconds must be >= 0");
        require(protectionTicks >= 0, "player.spawn-protection-seconds must be >= 0");
        require(shotIntervalTicks >= 1, "gun.shot-interval-ticks must be >= 1");
        require(speed > 0 && speed <= 10, "gun.speed must be > 0 and <= 10");
        require(spreadDegrees >= 0 && spreadDegrees <= 45, "gun.spread-degrees must be between 0 and 45");
        require(ballLifetimeTicks >= 1, "gun.ball-lifetime-ticks must be >= 1");
        require(flagReturnTicks >= 20, "ctf.flag-return-seconds must be >= 1");
        require(splatLifetimeTicks >= 1, "splats.lifetime-seconds must be > 0");
        require(splatCap >= 0, "splats.max-per-arena must be >= 0");
    }

    /** The spec's defaults. */
    public static Settings defaults() {
        return new Settings(new ModeRules("tdm", 2, 8, 30, 10 * 60 * 20), new ModeRules("ctf", 2, 8, 3, 12 * 60 * 20),
                3, 3 * 20, 2 * 20, 5, 3.0, 1.0, 60, 30 * 20, PowerupSettings.defaults(), 20 * 20, 300);
    }

    static void require(boolean ok, String message) {
        if (!ok) {
            throw new IllegalArgumentException(message);
        }
    }
}
