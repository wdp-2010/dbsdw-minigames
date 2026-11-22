package be.dbsdw.minigames.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EligibilityTest {
    @Test
    void loggedInJavaPlayerOnServerVersionIsOk() {
        assertEquals(Eligibility.OK, Eligibility.check(true, false, 777));
    }

    @Test
    void olderClientDownToTheFloorIsOk() {
        assertEquals(Eligibility.OK, Eligibility.check(true, false, 775));
        assertEquals(Eligibility.OK, Eligibility.check(true, false, Eligibility.MIN_PROTOCOL));
    }

    @Test
    void newerClientIsOk() {
        assertEquals(Eligibility.OK, Eligibility.check(true, false, 780));
    }

    @Test
    void unknownProtocolIsOk() {
        assertEquals(Eligibility.OK, Eligibility.check(true, false, 0));
    }

    @Test
    void clientBelowTheFloorIsRefused() {
        assertEquals(Eligibility.WRONG_VERSION, Eligibility.check(true, false, Eligibility.MIN_PROTOCOL - 1));
        assertEquals(Eligibility.WRONG_VERSION, Eligibility.check(true, false, 767));
    }

    @Test
    void notLoggedInWinsOverEverything() {
        assertEquals(Eligibility.NOT_LOGGED_IN, Eligibility.check(false, true, 1));
    }

    @Test
    void bedrockPlayerIsRefused() {
        assertEquals(Eligibility.BEDROCK, Eligibility.check(true, true, 777));
    }
}
