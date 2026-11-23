package be.dbsdw.minigames.valvloer.rules;

import java.util.Set;

/** Valvloer's config.yml. Times in ticks. */
public record Settings(int maxPerTeam, long graceTicks, long timeLimitTicks, Set<String> floorBlocks,
                       long dropDelayTicks, long campTicks) {
    public Settings {
        floorBlocks = Set.copyOf(floorBlocks);
        if (maxPerTeam < 1) {
            throw new IllegalArgumentException("match.max-per-team must be 1 or more");
        }
        if (graceTicks < 0 || dropDelayTicks < 0) {
            throw new IllegalArgumentException("grace-seconds and drop-delay-ticks must be 0 or more");
        }
        if (timeLimitTicks <= 0 || campTicks <= 0) {
            throw new IllegalArgumentException("time-limit-minutes and camp-seconds must be more than 0");
        }
        if (floorBlocks.isEmpty()) {
            throw new IllegalArgumentException("floor.blocks is empty");
        }
    }
}
