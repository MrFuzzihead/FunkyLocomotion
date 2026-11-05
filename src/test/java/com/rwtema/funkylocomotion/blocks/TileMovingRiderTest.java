package com.rwtema.funkylocomotion.blocks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import net.minecraft.util.AxisAlignedBB;

import org.junit.Test;

public class TileMovingRiderTest {

    private static AxisAlignedBB box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return AxisAlignedBB.getBoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Test
    public void downwardCarryIsAppliedExactlyOnce() {
        // The platform has already moved down for this tick; its previous top was 1.0.
        AxisAlignedBB rider = box(0, 1.0, 0, 0.6, 2.8, 0.6);
        AxisAlignedBB platform = box(0, 0, 0, 1, 0.95, 1);

        double adjustment = TileMovingBase.riderTopAdjustment(rider, platform, -0.05, -0.0784);

        assertEquals(0.0, adjustment, 1e-9);
        assertEquals(0.95, rider.minY + adjustment - 0.05, 1e-9);
    }

    @Test
    public void fallingRiderSnapsToPreviousTopBeforeTheMove() {
        AxisAlignedBB rider = box(0, 1.1, 0, 0.6, 2.9, 0.6);
        AxisAlignedBB platform = box(0, 0, 0, 1, 0.95, 1);

        double adjustment = TileMovingBase.riderTopAdjustment(rider, platform, -0.05, -0.0784);

        assertEquals(-0.1, adjustment, 1e-9);
        assertEquals(0.95, rider.minY + adjustment - 0.05, 1e-9);
    }

    @Test
    public void fastDownwardCarryStillCatchesTheRider() {
        // A one-block-per-tick move: the old top was 1.0, the new top is 0.0.
        AxisAlignedBB rider = box(0, 1.0, 0, 0.6, 2.8, 0.6);
        AxisAlignedBB platform = box(0, -1, 0, 1, 0.0, 1);

        double adjustment = TileMovingBase.riderTopAdjustment(rider, platform, -1.0, -0.0784);

        assertEquals(0.0, adjustment, 1e-9);
        assertEquals(0.0, rider.minY + adjustment - 1.0, 1e-9);
    }

    @Test
    public void fastUpwardCarryStillCatchesTheRider() {
        // A one-block-per-tick rise: the old top was 1.0, the new top is 2.0.
        AxisAlignedBB rider = box(0, 1.0, 0, 0.6, 2.8, 0.6);
        AxisAlignedBB platform = box(0, 1, 0, 1, 2.0, 1);

        double adjustment = TileMovingBase.riderTopAdjustment(rider, platform, 1.0, -0.0784);

        assertEquals(0.0, adjustment, 1e-9);
        assertEquals(2.0, rider.minY + adjustment + 1.0, 1e-9);
    }

    @Test
    public void upwardCarryIsAppliedExactlyOnce() {
        AxisAlignedBB rider = box(0, 1.0, 0, 0.6, 2.8, 0.6);
        AxisAlignedBB platform = box(0, 0, 0, 1, 1.05, 1);

        double adjustment = TileMovingBase.riderTopAdjustment(rider, platform, 0.05, 0.0);

        assertEquals(0.0, adjustment, 1e-9);
        assertEquals(1.05, rider.minY + adjustment + 0.05, 1e-9);
    }

    @Test
    public void entityBelowThePlatformIsNotCarried() {
        AxisAlignedBB rider = box(0, 0.3, 0, 0.6, 2.1, 0.6);
        AxisAlignedBB platform = box(0, 0, 0, 1, 0.95, 1);

        assertTrue(Double.isNaN(TileMovingBase.riderTopAdjustment(rider, platform, -0.05, -0.0784)));
    }

    @Test
    public void jumpingEntityAboveThePlatformIsNotSnappedBack() {
        AxisAlignedBB rider = box(0, 1.1, 0, 0.6, 2.9, 0.6);
        AxisAlignedBB platform = box(0, 0, 0, 1, 0.95, 1);

        assertTrue(Double.isNaN(TileMovingBase.riderTopAdjustment(rider, platform, -0.05, 0.1)));
    }

    @Test
    public void entityBesideThePlatformIsNotCarried() {
        AxisAlignedBB rider = box(2, 1.0, 0, 2.6, 2.8, 0.6);
        AxisAlignedBB platform = box(0, 0, 0, 1, 0.95, 1);

        assertTrue(Double.isNaN(TileMovingBase.riderTopAdjustment(rider, platform, -0.05, -0.0784)));
    }
}
