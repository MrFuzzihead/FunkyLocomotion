package com.rwtema.funkylocomotion.blocks;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TileMovingProgressTest {

    @Test
    public void zeroDurationDoesNotProduceNanOrInfinity() {
        TileMovingServer tile = new TileMovingServer();
        tile.maxTime = 0;
        tile.time = 0;
        tile.dir = 2;

        assertEquals(0.0, tile.offset(true), 0.0);
        assertEquals(1.0, tile.progress(), 0.0);
        assertEquals(0.0, tile.getMovVec().xCoord, 0.0);
        assertEquals(0.0, tile.getMovVec().yCoord, 0.0);
        assertEquals(0.0, tile.getMovVec().zCoord, 0.0);
    }

    @Test
    public void halfwayThroughANormalMoveInterpolates() {
        TileMovingServer tile = new TileMovingServer();
        tile.maxTime = 10;
        tile.time = 5;
        tile.dir = 2;

        assertEquals(-0.5, tile.offset(false), 0.0001);
        assertEquals(0.5, tile.progress(), 0.0001);
        assertEquals(0.0, tile.getMovVec().xCoord, 0.0001);
        assertEquals(-0.1, tile.getMovVec().zCoord, 0.0001);
    }
}
