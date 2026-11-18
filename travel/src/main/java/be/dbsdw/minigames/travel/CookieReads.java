package be.dbsdw.minigames.travel;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Waits for a cookie read from the client. */
public final class CookieReads {
    private CookieReads() {}

    /** Returns the cookie bytes, or null if the client did not answer in time or the read failed. */
    public static byte[] await(CompletableFuture<byte[]> read, Duration timeout) {
        try {
            return read.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException | TimeoutException e) {
            return null;
        }
    }
}
