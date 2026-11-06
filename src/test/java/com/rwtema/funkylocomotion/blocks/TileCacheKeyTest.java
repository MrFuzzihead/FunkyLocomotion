package com.rwtema.funkylocomotion.blocks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

public class TileCacheKeyTest {

    @Test
    public void sameDimensionAndCoordinatesAreEqual() {
        assertEquals(new TileCacheKey(0, 10, 64, -3), new TileCacheKey(0, 10, 64, -3));
        assertEquals(new TileCacheKey(0, 10, 64, -3).hashCode(), new TileCacheKey(0, 10, 64, -3).hashCode());
    }

    @Test
    public void differentDimensionsAreNotEqual() {
        assertNotEquals(new TileCacheKey(0, 10, 64, -3), new TileCacheKey(-1, 10, 64, -3));
    }

    @Test
    public void differentCoordinatesAreNotEqual() {
        assertNotEquals(new TileCacheKey(0, 10, 64, -3), new TileCacheKey(0, 10, 64, -2));
    }
}
