package be.dbsdw.minigames.paintball.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CtfAndPowerupTest {
    private final UUID red = UUID.randomUUID();
    private final UUID blue = UUID.randomUUID();
    private final UUID blue2 = UUID.randomUUID();

    @Test
    void pickupCaptureOnlyWithOwnFlagHome() {
        Flags flags = new Flags(2, 600);
        assertFalse(flags.pickup(0, red, 0), "own flag cannot be picked up");
        assertTrue(flags.pickup(1, red, 0));
        assertFalse(flags.pickup(1, blue2, 1));
        assertTrue(flags.pickup(0, blue, 1));
        assertTrue(flags.capture(red, 0).isEmpty(), "own flag is away");
        flags.drop(blue, 1, 2, 3, 100);
        assertTrue(flags.returnHome(0, 0));
        assertEquals(OptionalInt.of(1), flags.capture(red, 0));
        assertEquals(Flags.State.HOME, flags.state(1));
    }

    @Test
    void droppedFlagReturnsAfterTimeOrCanBeRetaken() {
        Flags flags = new Flags(2, 600);
        flags.pickup(1, red, 0);
        flags.drop(red, 5, 6, 7, 100);
        assertEquals(Flags.State.DROPPED, flags.state(1));
        assertFalse(flags.returnHome(1, 0), "enemy cannot return it");
        assertTrue(flags.autoReturns(699).isEmpty());
        assertEquals(List.of(1), flags.autoReturns(700));
        flags.pickup(1, red, 0);
        flags.drop(red, 5, 6, 7, 800);
        assertTrue(flags.pickup(1, red, 0), "enemy can pick a dropped flag up again");
    }

    @Test
    void shieldTakesOneHitAndRepairHeals() {
        Combat c = new Combat(new java.util.LinkedHashMap<>(Map.of(red, 0, blue, 1)), Settings.defaults());
        c.start(-1000);
        c.giveShield(blue);
        assertEquals(Combat.HitResult.SHIELDED, c.hit(blue, red, 0, 0));
        assertEquals(Combat.HitResult.HIT, c.hit(blue, red, 0, 0));
        assertEquals(2, c.hitsLeft(blue));
        assertTrue(c.repair(blue));
        assertEquals(3, c.hitsLeft(blue));
        assertFalse(c.repair(blue));
    }

    @Test
    void powerupPointRefillsAfterRespawn() {
        PowerupSpawns s = new PowerupSpawns(2, 600);
        assertEquals(List.of(0, 1), s.due(0));
        assertTrue(s.due(1).isEmpty());
        s.taken(1, 100);
        assertTrue(s.due(699).isEmpty());
        assertEquals(List.of(1), s.due(700));
    }

    @Test
    void buffsExpire() {
        Buffs b = new Buffs();
        b.give(red, PowerupType.RAPID_FIRE, 0, 200);
        assertTrue(b.active(red, PowerupType.RAPID_FIRE, 199));
        assertFalse(b.active(red, PowerupType.RAPID_FIRE, 200));
    }

    @Test
    void splatsCapAndExpire() {
        SplatQueue<Integer> q = new SplatQueue<>(2, 400);
        assertTrue(q.add(1, 0).isEmpty());
        assertTrue(q.add(2, 10).isEmpty());
        assertEquals(List.of(1), q.add(3, 20));
        assertEquals(List.of(2), q.expired(410));
        assertEquals(List.of(3), q.clear());
    }
}
