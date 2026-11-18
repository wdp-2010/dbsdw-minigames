package be.dbsdw.minigames.travel;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Encodes tickets to signed bytes and back.
 *
 * <p>Wire format, version 1, big-endian: version u8 | destination u8 | uuid msb i64 | uuid lsb i64 |
 * issuedAt epoch-seconds i64 | expiresAt epoch-seconds i64 | nonce 16 bytes | nameLength u8 |
 * name UTF-8 | HMAC-SHA256 over everything before it (32 bytes).
 */
public final class TicketSigner {
    private static final byte VERSION = 1;
    private static final int HEADER = 1 + 1 + 8 + 8 + 8 + 8 + 16 + 1;
    private static final int MAC_LENGTH = 32;
    private static final String ALGORITHM = "HmacSHA256";
    private static final HexFormat HEX = HexFormat.of();

    private final byte[] secret;

    public TicketSigner(byte[] secret) {
        if (secret.length != 32) {
            throw new IllegalArgumentException("ticket secret must be 32 bytes");
        }
        this.secret = secret.clone();
    }

    public byte[] encode(Ticket ticket) {
        byte[] name = ticket.playerName().getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(HEADER + name.length + MAC_LENGTH);
        buffer.put(VERSION)
                .put((byte) ticket.destination().ordinal())
                .putLong(ticket.playerId().getMostSignificantBits())
                .putLong(ticket.playerId().getLeastSignificantBits())
                .putLong(ticket.issuedAt().getEpochSecond())
                .putLong(ticket.expiresAt().getEpochSecond())
                .put(HEX.parseHex(ticket.nonceHex()))
                .put((byte) name.length)
                .put(name);
        buffer.put(mac(buffer.array(), buffer.position()));
        return buffer.array();
    }

    public Ticket decode(byte[] data) throws TicketException {
        if (data.length < HEADER + 1 + MAC_LENGTH) {
            throw new TicketException(TicketException.Reason.MALFORMED, "too short (" + data.length + " bytes)");
        }
        if (data[0] != VERSION) {
            throw new TicketException(TicketException.Reason.MALFORMED, "unknown version " + data[0]);
        }
        int signedLength = data.length - MAC_LENGTH;
        byte[] expected = mac(data, signedLength);
        if (!MessageDigest.isEqual(expected, Arrays.copyOfRange(data, signedLength, data.length))) {
            throw new TicketException(TicketException.Reason.BAD_SIGNATURE, "signature does not match");
        }
        ByteBuffer buffer = ByteBuffer.wrap(data, 1, signedLength - 1);
        Destination[] destinations = Destination.values();
        int destination = buffer.get();
        if (destination < 0 || destination >= destinations.length) {
            throw new TicketException(TicketException.Reason.MALFORMED, "unknown destination " + destination);
        }
        UUID playerId = new UUID(buffer.getLong(), buffer.getLong());
        Instant issuedAt = Instant.ofEpochSecond(buffer.getLong());
        Instant expiresAt = Instant.ofEpochSecond(buffer.getLong());
        byte[] nonce = new byte[16];
        buffer.get(nonce);
        int nameLength = Byte.toUnsignedInt(buffer.get());
        if (nameLength != buffer.remaining()) {
            throw new TicketException(TicketException.Reason.MALFORMED, "name length does not match");
        }
        try {
            String name = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(buffer)
                    .toString();
            return new Ticket(playerId, name, destinations[destination], issuedAt, expiresAt, HEX.formatHex(nonce));
        } catch (CharacterCodingException | IllegalArgumentException e) {
            throw new TicketException(TicketException.Reason.MALFORMED, e.getMessage());
        }
    }

    private byte[] mac(byte[] data, int length) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            mac.update(data, 0, length);
            return mac.doFinal();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }
}
