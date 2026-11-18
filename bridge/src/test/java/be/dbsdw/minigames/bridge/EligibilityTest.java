package be.dbsdw.minigames.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EligibilityTest {
    @Test
    void loggedInJavaPlayerOnServerVersionIsOk() {
        assertEquals(Eligibility.OK, Eligibility.check(true, false, 777, 777));
    }

    @Test
    void notLoggedInWinsOverEverything() {
        assertEquals(Eligibility.NOT_LOGGED_IN, Eligibility.check(false, true, 1, 777));
    }

    @Test
    void bedrockPlayerIsRefused() {
        assertEquals(Eligibility.BEDROCK, Eligibility.check(true, true, 777, 777));
    }

    @Test
    void otherProtocolIsRefused() {
        assertEquals(Eligibility.WRONG_VERSION, Eligibility.check(true, false, 774, 777));
    }
}
