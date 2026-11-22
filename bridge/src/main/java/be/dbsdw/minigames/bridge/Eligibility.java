package be.dbsdw.minigames.bridge;

/** Whether a survival player may travel to the minigame server. The first failing rule wins. */
public enum Eligibility {
    OK,
    NOT_LOGGED_IN,
    BEDROCK,
    WRONG_VERSION;

    /**
     * Lowest client protocol let through: 773, Minecraft 1.21.9. The resource pack's
     * {@code pack.mcmeta} uses {@code min_format}/{@code max_format}, which older clients cannot
     * read (its models and fonts need 1.21.4 or newer). ViaVersion and ViaBackwards on the minigame
     * server translate everything else, also for clients newer than the server.
     */
    public static final int MIN_PROTOCOL = 773;

    /** {@code clientProtocol} is 0 when it is unknown (no ViaVersion): the client then speaks the server's version. */
    public static Eligibility check(boolean loggedIn, boolean bedrock, int clientProtocol) {
        if (!loggedIn) {
            return NOT_LOGGED_IN;
        }
        if (bedrock) {
            return BEDROCK;
        }
        if (clientProtocol != 0 && clientProtocol < MIN_PROTOCOL) {
            return WRONG_VERSION;
        }
        return OK;
    }
}
