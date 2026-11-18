package be.dbsdw.minigames.travel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class TicketSecretsTest {
    @Test
    void generatedSecretIs32Bytes() {
        assertEquals(32, TicketSecrets.generate(new SecureRandom()).length);
    }

    @Test
    void base64RoundTrip() {
        byte[] secret = TicketSecrets.generate(new SecureRandom());
        assertArrayEquals(secret, TicketSecrets.fromBase64(TicketSecrets.toBase64(secret)));
    }

    @Test
    void wrongLengthIsRejected() {
        String shortSecret = Base64.getEncoder().encodeToString(new byte[31]);
        assertThrows(IllegalArgumentException.class, () -> TicketSecrets.fromBase64(shortSecret));
    }

    @Test
    void garbageIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> TicketSecrets.fromBase64("not base64!!"));
    }

    @Test
    void blankIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> TicketSecrets.fromBase64(""));
    }
}
