package be.dbsdw.minigames.travel;

import java.security.SecureRandom;
import java.util.Base64;

/** Creates and parses the 32-byte secret both servers share. */
public final class TicketSecrets {
    private static final int LENGTH = 32;

    private TicketSecrets() {}

    public static byte[] generate(SecureRandom random) {
        byte[] secret = new byte[LENGTH];
        random.nextBytes(secret);
        return secret;
    }

    public static String toBase64(byte[] secret) {
        return Base64.getEncoder().encodeToString(secret);
    }

    /** Parses a base64 secret; throws IllegalArgumentException unless it is exactly 32 bytes. */
    public static byte[] fromBase64(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("ticket secret is empty");
        }
        byte[] secret = Base64.getDecoder().decode(text.strip());
        if (secret.length != LENGTH) {
            throw new IllegalArgumentException("ticket secret must be 32 bytes, got " + secret.length);
        }
        return secret;
    }
}
