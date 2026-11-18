package be.dbsdw.minigames.travel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TicketExceptionTest {
    @Test
    void badSignatureLogTextNamesTheSecret() {
        TicketException e = new TicketException(TicketException.Reason.BAD_SIGNATURE, "signature does not match");
        assertEquals("ticket signature invalid — check that ticket-secret is the same on both servers", e.logText());
    }

    @Test
    void otherReasonsLogTheirName() {
        assertEquals("MISSING", new TicketException(TicketException.Reason.MISSING, "no ticket").logText());
    }
}
