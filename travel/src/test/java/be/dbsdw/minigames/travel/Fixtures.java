package be.dbsdw.minigames.travel;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

/** Shared test values. */
final class Fixtures {
    static final Instant START = Instant.parse("2026-11-18T12:00:00Z");
    static final byte[] SECRET_A = filled(0x01);
    static final byte[] SECRET_B = filled(0x02);
    static final String NAME = "Steve";
    static final UUID STEVE = offlineUuid(NAME);

    private Fixtures() {}

    static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] filled(int value) {
        byte[] secret = new byte[32];
        Arrays.fill(secret, (byte) value);
        return secret;
    }
}
