package com.rwtema.funkylocomotion.blocks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TilePusherLimitsTest {

    @Test
    public void configValuesAreClampedToSafeRanges() {
        assertEquals(TilePusher.MIN_MAX_TILES,
            TilePusher.clampConfig(-1, TilePusher.MIN_MAX_TILES, TilePusher.MAX_MAX_TILES));
        assertEquals(TilePusher.MAX_MAX_TILES,
            TilePusher.clampConfig(1_000_000, TilePusher.MIN_MAX_TILES, TilePusher.MAX_MAX_TILES));
        assertEquals(1024, TilePusher.clampConfig(1024, TilePusher.MIN_MAX_TILES, TilePusher.MAX_MAX_TILES));
        assertEquals(TilePusher.MIN_POWER_PER_TILE,
            TilePusher.clampConfig(0, TilePusher.MIN_POWER_PER_TILE, TilePusher.MAX_POWER_PER_TILE));
    }

    @Test
    public void capacityDoesNotOverflowForLargeConfigs() {
        assertEquals(1024 * 250, TilePusher.energyCapacity(1024, 250));
        assertEquals(Integer.MAX_VALUE, TilePusher.energyCapacity(8192, 1_000_000));
        assertEquals(TilePusher.MIN_MAX_TILES * TilePusher.MIN_POWER_PER_TILE,
            TilePusher.energyCapacity(-5, -5));
    }

    @Test
    public void energyCostDoesNotOverflowForLargeStructures() {
        assertEquals(1024 * TilePusher.powerPerTile, TilePusher.energyCost(1024));
        assertEquals(Integer.MAX_VALUE, TilePusher.energyCost(100_000_000));
        assertEquals(0, TilePusher.energyCost(-1));
    }

    @Test
    public void theLimitIsInclusiveAndRejectsTheNextBlock() {
        int previousLimit = TilePusher.maxTiles;
        try {
            TilePusher.maxTiles = 4;
            assertTrue(TilePusher.hasCapacityFor(0));
            assertTrue(TilePusher.hasCapacityFor(3));
            assertFalse(TilePusher.hasCapacityFor(4));
            assertFalse(TilePusher.hasCapacityFor(5));
        } finally {
            TilePusher.maxTiles = previousLimit;
        }
    }
}
