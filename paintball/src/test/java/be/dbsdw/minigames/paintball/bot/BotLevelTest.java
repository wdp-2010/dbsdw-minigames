package be.dbsdw.minigames.paintball.bot;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BotLevelTest {
    /** A bot closes in to where its shots land before it fights; stronger bots are steadier and fight from further. */
    @Test
    void levelsFightWithinSightAndGetSteadier() {
        BotLevel prev = null;
        for (BotLevel l : BotLevel.values()) {
            assertTrue(l.fightRange() * 1.5 <= l.sightRange() + 1e-9, l.id());
            // Typical miss at fight range stays under a block: the bot can hit what it fights.
            assertTrue(Math.tan(Math.toRadians(l.aimErrorDegrees())) * l.fightRange() < 0.7, l.id());
            if (prev != null) {
                assertTrue(l.aimErrorDegrees() < prev.aimErrorDegrees(), l.id());
                assertTrue(l.fightRange() > prev.fightRange(), l.id());
            }
            prev = l;
        }
    }
}
