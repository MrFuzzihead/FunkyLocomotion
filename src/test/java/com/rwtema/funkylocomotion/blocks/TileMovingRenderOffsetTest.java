package com.rwtema.funkylocomotion.blocks;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Test;

import com.rwtema.funkylocomotion.Proxy;

public class TileMovingRenderOffsetTest {

    @After
    public void resetPartialTick() {
        Proxy.renderTimeOffset = 0;
    }

    private static TileMovingServer tile(int time, int maxTime) {
        TileMovingServer tile = new TileMovingServer();
        tile.time = time;
        tile.maxTime = maxTime;
        tile.dir = 2;
        return tile;
    }

    @Test
    public void renderOffsetInterpolatesFromThePreviousTick() {
        TileMovingServer tile = tile(5, 10);

        Proxy.renderTimeOffset = 0;
        assertEquals(-0.6, tile.offset(true), 1e-9);

        Proxy.renderTimeOffset = 0.5F;
        assertEquals(-0.55, tile.offset(true), 1e-9);

        Proxy.renderTimeOffset = 1;
        assertEquals(-0.5, tile.offset(true), 1e-9);
    }

    @Test
    public void renderOffsetStartsAtTheSource() {
        TileMovingServer tile = tile(0, 10);
        Proxy.renderTimeOffset = 0.75F;
        assertEquals(-1.0, tile.offset(true), 1e-9);

        tile.time = 1;
        Proxy.renderTimeOffset = 0;
        assertEquals(-1.0, tile.offset(true), 1e-9);

        Proxy.renderTimeOffset = 1;
        assertEquals(-0.9, tile.offset(true), 1e-9);
    }

    @Test
    public void finalStepInterpolatesThenPinsAtTheDestination() {
        TileMovingServer tile = tile(10, 10);

        Proxy.renderTimeOffset = 0;
        assertEquals(-0.1, tile.offset(true), 1e-9);

        Proxy.renderTimeOffset = 1;
        assertEquals(0.0, tile.offset(true), 1e-9);

        tile.finishRenderTicks = 1;
        Proxy.renderTimeOffset = 0;
        assertEquals(0.0, tile.offset(true), 1e-9);
    }

    @Test
    public void collisionOffsetUsesTheCompletedTick() {
        TileMovingServer tile = tile(5, 10);
        assertEquals(-0.5, tile.offset(false), 1e-9);

        tile.time = 10;
        assertEquals(0.0, tile.offset(false), 1e-9);
    }
}
