package be.dbsdw.minigames.paintball.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class BombSitesTest {
    private final List<double[]> sites = List.of(new double[] {10, 5, 10}, new double[] {40, 5, 10});

    @Test
    void flagArenasKeepTheirSpawnsWhenSidesSwap() {
        assertEquals(0, BombSites.spawnSide(0, 0, false));
        assertEquals(0, BombSites.spawnSide(0, 1, false));
        assertEquals(1, BombSites.spawnSide(1, 0, false));
    }

    @Test
    void bombMapsPutTheAttackersOnSpawnZero() {
        assertEquals(0, BombSites.spawnSide(0, 0, true));
        assertEquals(1, BombSites.spawnSide(1, 0, true));
        assertEquals(1, BombSites.spawnSide(0, 1, true));
        assertEquals(0, BombSites.spawnSide(1, 1, true));
    }

    @Test
    void findsTheSiteWithinItsRadius() {
        assertEquals(0, BombSites.siteAt(sites, 4, 13, 5, 11));
        assertEquals(1, BombSites.siteAt(sites, 4, 40, 7.5, 6.5));
        assertEquals(-1, BombSites.siteAt(sites, 4, 25, 5, 10));
        assertEquals(-1, BombSites.siteAt(sites, 4, 14.1, 5, 10));
    }

    @Test
    void tooHighOrLowIsNotOnTheSite() {
        assertEquals(-1, BombSites.siteAt(sites, 4, 10, 9, 10));
        assertEquals(-1, BombSites.siteAt(sites, 4, 10, 1.5, 10));
    }

    @Test
    void labels() {
        assertEquals("A", BombSites.label(0));
        assertEquals("B", BombSites.label(1));
    }
}
