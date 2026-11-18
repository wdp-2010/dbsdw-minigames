package be.dbsdw.minigames.bridge;

/** Whether a survival player may travel to the minigame server. The first failing rule wins. */
public enum Eligibility {
    OK,
    NOT_LOGGED_IN,
    BEDROCK,
    WRONG_VERSION;

    public static Eligibility check(boolean loggedIn, boolean bedrock, int clientProtocol, int serverProtocol) {
        if (!loggedIn) {
            return NOT_LOGGED_IN;
        }
        if (bedrock) {
            return BEDROCK;
        }
        if (clientProtocol != serverProtocol) {
            return WRONG_VERSION;
        }
        return OK;
    }
}
