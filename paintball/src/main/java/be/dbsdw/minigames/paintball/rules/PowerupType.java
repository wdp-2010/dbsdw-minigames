package be.dbsdw.minigames.paintball.rules;

/** Power-ups that spawn in the arena. */
public enum PowerupType {
    RAPID_FIRE, TRIPLE_SHOT, SPEED, SHIELD, REPAIR;

    /** Config name, e.g. "rapid_fire". */
    public String key() {
        return name().toLowerCase();
    }
}
