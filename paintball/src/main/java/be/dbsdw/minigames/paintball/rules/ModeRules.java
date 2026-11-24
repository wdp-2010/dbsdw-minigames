package be.dbsdw.minigames.paintball.rules;

/**
 * Team sizes, win score and time limit of one mode (tdm or ctf). With {@code winScorePerPlayer} above 0
 * the win score grows with the larger team (players and bots) up to {@code winScore}: a 1v1 to 30 takes
 * far too long.
 */
public record ModeRules(String key, int minPerTeam, int maxPerTeam, int winScore, int winScorePerPlayer,
                        long timeLimitTicks) {
    public ModeRules {
        Settings.require(winScorePerPlayer >= 0, key + " win score per player must be >= 0");
        Settings.require(minPerTeam >= 1, key + ".min-per-team must be >= 1");
        Settings.require(maxPerTeam >= minPerTeam, key + ".max-per-team must be >= " + key + ".min-per-team");
        Settings.require(winScore >= 1, key + " win score must be >= 1");
        Settings.require(timeLimitTicks >= 20, key + ".time-limit-minutes must be > 0");
    }

    /** The score to win with {@code largestTeam} on the bigger team. */
    public int winScoreFor(int largestTeam) {
        if (winScorePerPlayer <= 0) {
            return winScore;
        }
        return Math.min(winScore, winScorePerPlayer * Math.max(1, largestTeam));
    }
}
