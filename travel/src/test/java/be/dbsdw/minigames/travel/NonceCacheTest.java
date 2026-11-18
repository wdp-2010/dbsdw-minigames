package be.dbsdw.minigames.travel;

import static be.dbsdw.minigames.travel.Fixtures.START;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class NonceCacheTest {
    private static final String NONCE = "0123456789abcdef0123456789abcdef";
    private final MutableClock clock = new MutableClock(START);
    private final NonceCache cache = new NonceCache(clock);

    @Test
    void firstUseIsTrueSecondIsFalse() {
        assertTrue(cache.markUsed(NONCE, START.plusSeconds(60)));
        assertFalse(cache.markUsed(NONCE, START.plusSeconds(60)));
    }

    @Test
    void entryIsForgottenAfterExpiry() {
        cache.markUsed(NONCE, START.plusSeconds(60));
        clock.advance(Duration.ofSeconds(61));
        assertTrue(cache.markUsed(NONCE, clock.instant().plusSeconds(60)));
    }
}
