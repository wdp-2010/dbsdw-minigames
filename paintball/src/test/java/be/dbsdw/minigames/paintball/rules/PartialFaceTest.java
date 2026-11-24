package be.dbsdw.minigames.paintball.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import be.dbsdw.minigames.paintball.rules.PartialFace.Part;
import java.util.List;
import org.junit.jupiter.api.Test;

class PartialFaceTest {
    // a stair facing north: a bottom slab and the upper step at the back (south half)
    private static final List<double[]> STAIR = List.of(
            new double[] {0, 0, 0, 1, 0.5, 1},
            new double[] {0, 0.5, 0.5, 1, 1, 1});

    @Test
    void stairSideHitHighOnTheStepStaysOnTheStep() {
        // east side (axis x, +), u = z, v = y; the ball hits at z 0.8, y 0.8: the upper step
        Part p = PartialFace.pick(STAIR, 0, 1, 2, 1, plane -> new double[] {0.8, 0.8});
        assertEquals(1, p.plane(), 1e-9);
        assertEquals(0.5, p.loU(), 1e-9);
        assertEquals(0.5, p.loV(), 1e-9);
        assertEquals(1, p.hiV(), 1e-9);
    }

    @Test
    void stairSideHitInTheEmptyCornerGoesToTheNearestPart() {
        // z 0.2, y 0.9 is the open corner of the L: nearest is the slab (0.4 below) vs step (0.3 across)
        Part p = PartialFace.pick(STAIR, 0, 1, 2, 1, plane -> new double[] {0.2, 0.9});
        assertEquals(0.5, p.loU(), 1e-9);
        assertEquals(0.5, p.loV(), 1e-9);
    }

    @Test
    void stairFrontPicksTheRiserOrTheSlabByHeight() {
        // north side (axis z, -), u = x, v = y; the path crosses each plane at the same (u, v) here
        Part high = PartialFace.pick(STAIR, 2, -1, 0, 1, plane -> new double[] {0.5, 0.75});
        assertEquals(0.5, high.plane(), 1e-9);
        Part low = PartialFace.pick(STAIR, 2, -1, 0, 1, plane -> new double[] {0.5, 0.25});
        assertEquals(0, low.plane(), 1e-9);
        assertEquals(0.5, low.hiV(), 1e-9);
    }

    @Test
    void topOfASlabAndStepPicksTheOutermostFaceTheBallReaches() {
        // from above (axis y, +), u = x, v = z: at z 0.8 the step top (y 1) is hit first
        Part p = PartialFace.pick(STAIR, 1, 1, 0, 2, plane -> new double[] {0.5, 0.8});
        assertEquals(1, p.plane(), 1e-9);
    }

    @Test
    void collisionAboveOneBlockIsCutOff() {
        // a wall's collision box is 1.5 high
        Part p = PartialFace.pick(List.of(new double[] {0.25, 0, 0.25, 0.75, 1.5, 0.75}), 0, 1, 2, 1,
                plane -> new double[] {0.5, 0.5});
        assertEquals(1, p.hiV(), 1e-9);
        assertEquals(0.5, p.room(), 1e-9);
    }

    @Test
    void emptyShapeHasNoFace() {
        assertNull(PartialFace.pick(List.of(), 0, 1, 2, 1, plane -> new double[] {0.5, 0.5}));
    }
}
