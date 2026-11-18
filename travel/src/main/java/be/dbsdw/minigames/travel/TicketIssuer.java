package be.dbsdw.minigames.travel;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

/** Makes fresh signed tickets. */
public final class TicketIssuer {
    private final TicketSigner signer;
    private final Clock clock;
    private final SecureRandom random;

    public TicketIssuer(TicketSigner signer, Clock clock, SecureRandom random) {
        this.signer = signer;
        this.clock = clock;
        this.random = random;
    }

    public byte[] issue(UUID playerId, String playerName, Destination destination) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        byte[] nonce = new byte[16];
        random.nextBytes(nonce);
        Ticket ticket = new Ticket(playerId, playerName, destination, now,
                now.plus(TravelConstants.TICKET_TTL), HexFormat.of().formatHex(nonce));
        return signer.encode(ticket);
    }
}
