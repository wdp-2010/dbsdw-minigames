package be.dbsdw.minigames.paintball.rules;

/**
 * Rules of the bomb mode, durations in ticks. Built from the {@code bomb:} section of config.yml;
 * invalid values are refused with the name of the key.
 *
 * @param winRounds    rounds a team needs to win the match
 * @param maxRounds    rounds at most; sides swap after half of them (an even number)
 * @param siteRadius   blocks around the defenders' flag spot that count as the bomb site
 */
public record BombSettings(int unlockLevel, int minPerTeam, int maxPerTeam, int winRounds, int maxRounds,
                           long buyTicks, long roundTicks, long postTicks, long plantTicks, long fuseTicks,
                           long defuseTicks, long kitDefuseTicks, double siteRadius) {
    public BombSettings {
        Settings.require(minPerTeam >= 1, "bomb.min-per-team must be >= 1");
        Settings.require(maxPerTeam >= minPerTeam, "bomb.max-per-team must be >= bomb.min-per-team");
        Settings.require(winRounds >= 1, "bomb.win-rounds must be >= 1");
        Settings.require(maxRounds >= 2 && maxRounds % 2 == 0, "bomb.max-rounds must be an even number >= 2");
        Settings.require(winRounds <= maxRounds, "bomb.win-rounds must be <= bomb.max-rounds");
        Settings.require(buyTicks >= 0, "bomb.buy-seconds must be >= 0");
        Settings.require(roundTicks >= 20, "bomb.round-seconds must be >= 1");
        Settings.require(postTicks >= 0, "bomb.round-end-seconds must be >= 0");
        Settings.require(plantTicks >= 1, "bomb.plant-seconds must be > 0");
        Settings.require(fuseTicks >= 20, "bomb.fuse-seconds must be >= 1");
        Settings.require(defuseTicks >= 1, "bomb.defuse-seconds must be > 0");
        Settings.require(kitDefuseTicks >= 1 && kitDefuseTicks <= defuseTicks,
                "bomb.kit-defuse-seconds must be > 0 and <= bomb.defuse-seconds");
        Settings.require(siteRadius >= 1 && siteRadius <= 16, "bomb.site-radius must be between 1 and 16");
    }

    public static BombSettings defaults() {
        return new BombSettings(10, 2, 5, 5, 8, 10 * 20, 90 * 20, 5 * 20, 4 * 20, 35 * 20, 6 * 20, 3 * 20, 4);
    }

    /** Rounds before the sides swap. */
    public int half() {
        return maxRounds / 2;
    }

    /** The longest a match can take: every round to its end, with a planted bomb going off late. */
    public long longestMatchTicks() {
        return maxRounds * (buyTicks + roundTicks + fuseTicks + postTicks) + 60 * 20;
    }
}
