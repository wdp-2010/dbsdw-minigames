package be.dbsdw.minigames.paintball.rules;

/** Team sizes, win score and time limit of one mode (tdm or ctf). */
public record ModeRules(String key, int minPerTeam, int maxPerTeam, int winScore, long timeLimitTicks) {
    public ModeRules {
        Settings.require(minPerTeam >= 1, key + ".min-per-team must be >= 1");
        Settings.require(maxPerTeam >= minPerTeam, key + ".max-per-team must be >= " + key + ".min-per-team");
        Settings.require(winScore >= 1, key + " win score must be >= 1");
        Settings.require(timeLimitTicks >= 20, key + ".time-limit-minutes must be > 0");
    }
}
