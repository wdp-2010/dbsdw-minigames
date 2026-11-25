package be.dbsdw.minigames.travel;

import java.time.Duration;

/** Fixed values shared by both servers. */
public final class TravelConstants {
    /** Cookie key {@code dbsdw:ticket}. */
    public static final String COOKIE_NAMESPACE = "dbsdw";
    public static final String COOKIE_PATH = "ticket";
    /**
     * Cookie key {@code dbsdw:queue}: the public queue a player travels to the minigame server for
     * (UTF-8 queue key, empty for none). Not signed: the minigame server checks it like a menu click.
     */
    public static final String QUEUE_COOKIE_PATH = "queue";
    public static final Duration TICKET_TTL = Duration.ofSeconds(60);
    public static final Duration CLOCK_SKEW = Duration.ofSeconds(5);
    public static final Duration COOKIE_TIMEOUT = Duration.ofSeconds(5);
    public static final Duration PROBE_TIMEOUT = Duration.ofSeconds(1);

    private TravelConstants() {}
}
