package be.dbsdw.minigames.travel;

import static be.dbsdw.minigames.travel.Fixtures.NAME;
import static be.dbsdw.minigames.travel.Fixtures.SECRET_A;
import static be.dbsdw.minigames.travel.Fixtures.SECRET_B;
import static be.dbsdw.minigames.travel.Fixtures.START;
import static be.dbsdw.minigames.travel.Fixtures.STEVE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class TicketSignerTest {
    private static final String NONCE = "0123456789abcdef0123456789abcdef";

    private static Ticket ticket(String name) {
        return new Ticket(STEVE, name, Destination.MINIGAMES, START, START.plusSeconds(60), NONCE);
    }

    private static TicketException.Reason failure(TicketSigner signer, byte[] data) {
        return assertThrows(TicketException.class, () -> signer.decode(data)).reason();
    }

    @Test
    void roundTripKeepsAllFields() throws TicketException {
        TicketSigner signer = new TicketSigner(SECRET_A);
        Ticket original = ticket(NAME);
        assertEquals(original, signer.decode(signer.encode(original)));
    }

    @Test
    void flippedPayloadByteIsBadSignature() {
        TicketSigner signer = new TicketSigner(SECRET_A);
        byte[] data = signer.encode(ticket(NAME));
        data[20] ^= 0x01;
        assertEquals(TicketException.Reason.BAD_SIGNATURE, failure(signer, data));
    }

    @Test
    void otherSecretIsBadSignature() {
        byte[] data = new TicketSigner(SECRET_A).encode(ticket(NAME));
        assertEquals(TicketException.Reason.BAD_SIGNATURE, failure(new TicketSigner(SECRET_B), data));
    }

    @Test
    void truncatedDataIsMalformed() {
        TicketSigner signer = new TicketSigner(SECRET_A);
        byte[] data = Arrays.copyOf(signer.encode(ticket(NAME)), 10);
        assertEquals(TicketException.Reason.MALFORMED, failure(signer, data));
    }

    @Test
    void unknownVersionIsMalformed() {
        TicketSigner signer = new TicketSigner(SECRET_A);
        byte[] data = signer.encode(ticket(NAME));
        data[0] = 2;
        assertEquals(TicketException.Reason.MALFORMED, failure(signer, data));
    }

    @Test
    void sixteenCharNameFitsInACookie() {
        byte[] data = new TicketSigner(SECRET_A).encode(ticket("A".repeat(16)));
        assertTrue(data.length <= 5120, "cookie payload is " + data.length + " bytes");
    }

    @Test
    void seventeenCharNameIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ticket("A".repeat(17)));
    }
}
