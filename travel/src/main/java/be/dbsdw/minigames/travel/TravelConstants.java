package be.dbsdw.minigames.travel;

import java.time.Duration;

/** Fixed values shared by both servers. */
public final class TravelConstants {
    /** Cookie key {@code dbsdw:ticket}. */
    public static final String COOKIE_NAMESPACE = "dbsdw";
    public static final String COOKIE_PATH = "ticket";
    public static final Duration TICKET_TTL = Duration.ofSeconds(60);
    public static final Duration CLOCK_SKEW = Duration.ofSeconds(5);
    public static final Duration COOKIE_TIMEOUT = Duration.ofSeconds(5);
    public static final Duration PROBE_TIMEOUT = Duration.ofSeconds(1);

    private TravelConstants() {}
}
