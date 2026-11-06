package com.rwtema.funkylocomotion.blocks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.util.Facing;

import org.junit.Test;

public class BlockSliderRenderSideTest {

    @Test
    public void unsetRenderSideAllowsAllFaces() {
        for (int side = 0; side < 6; side++) {
            assertTrue(BlockSlider.isRenderSideVisible(-1, side));
        }
    }

    @Test
    public void onlyTheTwoFacesOrthogonalToTheArrowAreRendered() {
        for (int renderSide = 0; renderSide < 6; renderSide++) {
            for (int side = 0; side < 6; side++) {
                boolean expected = side == renderSide || side == Facing.oppositeSide[renderSide];
                assertEquals(
                    "renderSide=" + renderSide + ", side=" + side,
                    expected,
                    BlockSlider.isRenderSideVisible(renderSide, side));
            }
        }
    }

    @Test
    public void unknownOrInvalidRenderSideNeverHidesFacesByAccident() {
        for (int side = 0; side < 6; side++) {
            assertFalse(BlockSlider.isRenderSideVisible(6, side));
            assertTrue(BlockSlider.isRenderSideVisible(7, side));
            assertTrue(BlockSlider.isRenderSideVisible(99, side));
        }
    }

    @Test
    public void renderSideIsIsolatedPerThread() throws Exception {
        BlockSlider.setRenderSide(2);
        AtomicInteger otherThreadValue = new AtomicInteger();
        Thread worker = new Thread(() -> otherThreadValue.set(BlockSlider.getRenderSide()));
        try {
            worker.start();
            worker.join();
            assertEquals(2, BlockSlider.getRenderSide());
            assertEquals(-1, otherThreadValue.get());
        } finally {
            BlockSlider.setRenderSide(-1);
        }
    }
}
