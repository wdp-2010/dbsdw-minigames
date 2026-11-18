package be.dbsdw.minigames.paintball.rules;

/**
 * Fire rate for one player. A trigger pull during the cooldown is kept (one at most) and fires as
 * soon as the cooldown ends, so holding the button (the client repeats every 4 ticks) gives a
 * steady shot every {@code interval} ticks.
 */
public final class FireGate {
    public enum Decision { FIRE, QUEUED, IGNORED }

    private long lastShot = Long.MIN_VALUE;
    private long lastRequest = Long.MIN_VALUE;
    private boolean queued;

    public Decision request(long now, int interval) {
        if (now == lastRequest) {
            // A click on a block sends two packets in the same tick; it is one pull.
            return Decision.IGNORED;
        }
        lastRequest = now;
        if (lastShot == Long.MIN_VALUE || now - lastShot >= interval) {
            lastShot = now;
            queued = false;
            return Decision.FIRE;
        }
        if (queued) {
            return Decision.IGNORED;
        }
        queued = true;
        return Decision.QUEUED;
    }

    /** True (once) when a queued shot may fire now. */
    public boolean pollQueued(long now, int interval) {
        if (queued && now - lastShot >= interval) {
            queued = false;
            lastShot = now;
            return true;
        }
        return false;
    }

    public void clear() {
        queued = false;
    }
}
