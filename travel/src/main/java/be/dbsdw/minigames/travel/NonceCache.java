package be.dbsdw.minigames.travel;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** Remembers used ticket nonces until their ticket expires, so a ticket works only once. */
public final class NonceCache {
    private final Clock clock;
    private final Map<String, Instant> used = new HashMap<>();

    public NonceCache(Clock clock) {
        this.clock = clock;
    }

    /** Returns true the first time a nonce is seen, false if it was already used and has not expired. */
    public synchronized boolean markUsed(String nonceHex, Instant expiresAt) {
        Instant now = clock.instant();
        used.values().removeIf(expiry -> now.isAfter(expiry));
        return used.putIfAbsent(nonceHex, expiresAt) == null;
    }
}
