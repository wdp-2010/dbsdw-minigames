package be.dbsdw.minigames.paintball.rules;

import java.util.List;

/** Power-up timings; {@code enabled} is the list a spawn point picks from. */
public record PowerupSettings(List<PowerupType> enabled, long respawnTicks, long rapidFireTicks, long tripleShotTicks,
                              long speedTicks, double tripleFanDegrees) {
    public PowerupSettings {
        enabled = List.copyOf(enabled);
        Settings.require(respawnTicks >= 20, "powerups.respawn-seconds must be >= 1");
        Settings.require(rapidFireTicks >= 1 && tripleShotTicks >= 1 && speedTicks >= 1,
                "powerups durations must be > 0");
        Settings.require(tripleFanDegrees >= 0 && tripleFanDegrees <= 30, "powerups.triple-shot-fan-degrees must be 0-30");
    }

    public static PowerupSettings defaults() {
        return new PowerupSettings(List.of(PowerupType.values()), 30 * 20, 10 * 20, 8 * 20, 10 * 20, 4.0);
    }

    public long duration(PowerupType type) {
        return switch (type) {
            case RAPID_FIRE -> rapidFireTicks;
            case TRIPLE_SHOT -> tripleShotTicks;
            case SPEED -> speedTicks;
            case SHIELD, REPAIR -> 0;
        };
    }
}
