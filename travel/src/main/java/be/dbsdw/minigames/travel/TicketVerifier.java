package be.dbsdw.minigames.travel;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Checks a ticket presented by an arriving player. */
public final class TicketVerifier {
    private final TicketSigner signer;
    private final Destination self;
    private final Clock clock;
    private final NonceCache nonces;

    public TicketVerifier(TicketSigner signer, Destination self, Clock clock, NonceCache nonces) {
        this.signer = signer;
        this.self = self;
        this.clock = clock;
        this.nonces = nonces;
    }

    public Ticket verify(byte[] data, UUID playerId, String playerName) throws TicketException {
        if (data == null || data.length == 0) {
            throw new TicketException(TicketException.Reason.MISSING, "no ticket");
        }
        Ticket ticket = signer.decode(data);
        if (ticket.destination() != self) {
            throw new TicketException(TicketException.Reason.WRONG_DESTINATION, "ticket is for " + ticket.destination());
        }
        Instant now = clock.instant();
        if (now.isAfter(ticket.expiresAt())) {
            throw new TicketException(TicketException.Reason.EXPIRED, "expired at " + ticket.expiresAt());
        }
        if (ticket.issuedAt().isAfter(now.plus(TravelConstants.CLOCK_SKEW))) {
            throw new TicketException(TicketException.Reason.NOT_YET_VALID, "issued at " + ticket.issuedAt());
        }
        if (!ticket.playerId().equals(playerId) || !ticket.playerName().equals(playerName)) {
            throw new TicketException(TicketException.Reason.WRONG_PLAYER, "ticket is for " + ticket.playerName());
        }
        if (!nonces.markUsed(ticket.nonceHex(), ticket.expiresAt())) {
            throw new TicketException(TicketException.Reason.REUSED, "ticket already used");
        }
        return ticket;
    }
}
