package be.dbsdw.minigames.paintball.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import be.dbsdw.minigames.paintball.rules.Loadout.Check;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ShopTest {
    private final UUID p = UUID.randomUUID();

    @Test
    void walletStartsSpendsAndCaps() {
        Wallet w = new Wallet(800, 1000);
        assertEquals(800, w.cash(p));
        assertFalse(w.spend(p, 900));
        assertTrue(w.spend(p, 300));
        assertEquals(500, w.cash(p));
        assertEquals(500, w.earn(p, 3000));
        assertEquals(1000, w.cash(p));
        w.reset();
        assertEquals(800, w.cash(p));
    }

    @Test
    void loadoutLimitsAndRules() {
        Loadout l = new Loadout();
        assertEquals(Check.OK, l.check(p, ShopItem.PAINT_GRENADE, 800, true, false, false, false));
        l.add(p, ShopItem.PAINT_GRENADE);
        l.add(p, ShopItem.PAINT_GRENADE);
        assertEquals(Check.MAXED, l.check(p, ShopItem.PAINT_GRENADE, 800, true, false, false, false));
        assertTrue(l.take(p, ShopItem.PAINT_GRENADE));
        assertEquals(1, l.count(p, ShopItem.PAINT_GRENADE));
        assertEquals(Check.CLOSED, l.check(p, ShopItem.VEST, 800, false, true, false, false));
        assertEquals(Check.TOO_POOR, l.check(p, ShopItem.TRIPLE_CARTRIDGE, 500, true, false, false, false));
        assertEquals(Check.NOT_NEEDED, l.check(p, ShopItem.REPAIR, 800, true, false, false, false));
        assertEquals(Check.OK, l.check(p, ShopItem.REPAIR, 800, true, false, false, true));
        l.add(p, ShopItem.REPAIR); // instant: not kept, so it can be bought again
        assertEquals(0, l.count(p, ShopItem.REPAIR));
        l.clear(p);
        assertFalse(l.has(p, ShopItem.PAINT_GRENADE));
    }

    @Test
    void defuseKitOnlyForBombDefenders() {
        Loadout l = new Loadout();
        assertEquals(Check.NOT_FOR_YOU, l.check(p, ShopItem.DEFUSE_KIT, 800, true, false, false, false));
        assertEquals(Check.NOT_FOR_YOU, l.check(p, ShopItem.DEFUSE_KIT, 800, true, true, false, false));
        assertEquals(Check.OK, l.check(p, ShopItem.DEFUSE_KIT, 800, true, true, true, false));
    }

    @Test
    void smokeBlocksLinesThroughIt() {
        Smokes s = new Smokes();
        s.add(0, 0, 0, 2, 100);
        assertTrue(s.blocks(new double[] {-5, 0, 0}, new double[] {5, 0, 0}, 10));
        assertFalse(s.blocks(new double[] {-5, 3, 0}, new double[] {5, 3, 0}, 10));
        assertFalse(s.blocks(new double[] {-5, 0, 0}, new double[] {-3, 0, 0}, 10));
        assertFalse(s.blocks(new double[] {-5, 0, 0}, new double[] {5, 0, 0}, 100));
        assertTrue(s.active(100).isEmpty());
    }
}
