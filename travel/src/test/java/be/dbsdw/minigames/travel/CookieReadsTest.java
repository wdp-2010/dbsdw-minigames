package be.dbsdw.minigames.travel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class CookieReadsTest {
    private static final Duration SHORT = Duration.ofMillis(50);

    @Test
    void completedValueIsReturned() {
        byte[] value = {1, 2, 3};
        assertArrayEquals(value, CookieReads.await(CompletableFuture.completedFuture(value), SHORT));
    }

    @Test
    void timeoutReturnsNull() {
        assertNull(CookieReads.await(new CompletableFuture<>(), SHORT));
    }

    @Test
    void failureReturnsNull() {
        CompletableFuture<byte[]> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("client gone"));
        assertNull(CookieReads.await(failed, SHORT));
    }
}
