package com.rwtema.funkylocomotion.rendering;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.rwtema.funkylocomotion.blocks.TileMovingClient;

public class MoverRenderCacheTest {

    @Test
    public void cacheSurvivesWhenNothingChanged() {
        TileMovingClient mover = new TileMovingClient();
        mover.renderCacheGeneration = MoverRenderState.revision();
        mover.skipPass[0] = true;

        TileEntityRenderMoving.refreshCachedGeometry(mover);

        assertTrue("an unchanged revision must keep the cached pass state", mover.skipPass[0]);
    }

    @Test
    public void anyRenderStateChangeDropsCachedGeometry() {
        TileMovingClient mover = new TileMovingClient();
        mover.renderCacheGeneration = MoverRenderState.revision();
        mover.skipPass[0] = true;
        mover.skipPass[1] = true;
        mover.failedToRenderInFirstPass = true;

        // A block/neighbour change (for example a new data packet) bumps this.
        MoverRenderState.markDirty();
        TileEntityRenderMoving.refreshCachedGeometry(mover);

        assertFalse("cached geometry must be rebuilt after a data change", mover.skipPass[0]);
        assertFalse("cached geometry must be rebuilt after a data change", mover.skipPass[1]);
        assertFalse("the first-pass failure flag must be re-evaluated", mover.failedToRenderInFirstPass);
        assertEquals(MoverRenderState.revision(), mover.renderCacheGeneration);
    }
}
