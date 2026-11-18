package be.dbsdw.minigames.travel;

import static be.dbsdw.minigames.travel.Fixtures.NAME;
import static be.dbsdw.minigames.travel.Fixtures.SECRET_A;
import static be.dbsdw.minigames.travel.Fixtures.SECRET_B;
import static be.dbsdw.minigames.travel.Fixtures.START;
import static be.dbsdw.minigames.travel.Fixtures.STEVE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TicketVerifierTest {
    private final MutableClock clock = new MutableClock(START);
    private final TicketVerifier verifier =
            new TicketVerifier(new TicketSigner(SECRET_A), Destination.MINIGAMES, clock, new NonceCache(clock));

    private byte[] issue(byte[] secret, Destination destination, MutableClock issuerClock) {
        return new TicketIssuer(new TicketSigner(secret), issuerClock, new SecureRandom())
                .issue(STEVE, NAME, destination);
    }

    private byte[] issue() {
        return issue(SECRET_A, Destination.MINIGAMES, clock);
    }

    private TicketException.Reason failure(byte[] data, UUID id, String name) {
        return assertThrows(TicketException.class, () -> verifier.verify(data, id, name)).reason();
    }

    @Test
    void freshTicketIsAccepted() throws TicketException {
        assertEquals(NAME, verifier.verify(issue(), STEVE, NAME).playerName());
    }

    @Test
    void nullAndEmptyAreMissing() {
        assertEquals(TicketException.Reason.MISSING, failure(null, STEVE, NAME));
        assertEquals(TicketException.Reason.MISSING, failure(new byte[0], STEVE, NAME));
    }

    @Test
    void otherSecretIsBadSignature() {
        byte[] data = issue(SECRET_B, Destination.MINIGAMES, clock);
        assertEquals(TicketException.Reason.BAD_SIGNATURE, failure(data, STEVE, NAME));
    }

    @Test
    void survivalTicketIsWrongDestination() {
        byte[] data = issue(SECRET_A, Destination.SURVIVAL, clock);
        assertEquals(TicketException.Reason.WRONG_DESTINATION, failure(data, STEVE, NAME));
    }

    @Test
    void ticketIsExpiredAfter60Seconds() throws TicketException {
        byte[] late = issue();
        byte[] onTime = issue();
        clock.advance(Duration.ofSeconds(60));
        verifier.verify(onTime, STEVE, NAME);
        clock.advance(Duration.ofSeconds(1));
        assertEquals(TicketException.Reason.EXPIRED, failure(late, STEVE, NAME));
    }

    @Test
    void futureTicketWithinSkewIsAccepted() throws TicketException {
        MutableClock ahead = new MutableClock(START.plusSeconds(5));
        verifier.verify(issue(SECRET_A, Destination.MINIGAMES, ahead), STEVE, NAME);
        ahead.set(START.plusSeconds(6));
        byte[] tooFar = issue(SECRET_A, Destination.MINIGAMES, ahead);
        assertEquals(TicketException.Reason.NOT_YET_VALID, failure(tooFar, STEVE, NAME));
    }

    @Test
    void otherUuidIsWrongPlayer() {
        assertEquals(TicketException.Reason.WRONG_PLAYER, failure(issue(), Fixtures.offlineUuid("Alex"), NAME));
    }

    @Test
    void nameCaseMismatchIsWrongPlayer() {
        assertEquals(TicketException.Reason.WRONG_PLAYER, failure(issue(), STEVE, "steve"));
    }

    @Test
    void reusedNonceIsRefused() throws TicketException {
        byte[] data = issue();
        verifier.verify(data, STEVE, NAME);
        assertEquals(TicketException.Reason.REUSED, failure(data, STEVE, NAME));
    }
}
