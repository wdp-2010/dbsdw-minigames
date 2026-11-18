package be.dbsdw.minigames.travel;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** The facts a transfer ticket vouches for. Times have whole-second precision on the wire. */
public record Ticket(UUID playerId, String playerName, Destination destination,
                     Instant issuedAt, Instant expiresAt, String nonceHex) {
    private static final Pattern NONCE = Pattern.compile("[0-9a-f]{32}");

    public Ticket {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(playerName, "playerName");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(nonceHex, "nonceHex");
        if (playerName.isEmpty() || playerName.length() > 16) {
            throw new IllegalArgumentException("player name must be 1 to 16 characters");
        }
        if (!NONCE.matcher(nonceHex).matches()) {
            throw new IllegalArgumentException("nonce must be 32 lowercase hex characters");
        }
    }
}
