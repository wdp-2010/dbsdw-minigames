package be.dbsdw.minigames.travel;

/** Why a ticket was not accepted. */
public final class TicketException extends Exception {
    private static final long serialVersionUID = 1L;

    public enum Reason {
        MISSING, MALFORMED, BAD_SIGNATURE, WRONG_DESTINATION, EXPIRED, NOT_YET_VALID, WRONG_PLAYER, REUSED
    }

    private final Reason reason;

    public TicketException(Reason reason, String detail) {
        super(reason + ": " + detail);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    /** One-line reason for server logs. Never contains ticket bytes or the secret. */
    public String logText() {
        if (reason == Reason.BAD_SIGNATURE) {
            return "ticket signature invalid — check that ticket-secret is the same on both servers";
        }
        return reason.name();
    }
}
