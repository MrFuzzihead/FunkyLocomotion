package com.rwtema.funkylocomotion.blocks;

import static org.junit.Assert.assertEquals;

import net.minecraft.util.Facing;

import org.junit.Test;

public class BlockSliderFacingTest {

    @Test
    public void clickingTheCurrentFaceFlipsIt() {
        for (int meta = 0; meta < 12; meta++) {
            int side = meta % 6;
            int expected = (meta < 6 ? 0 : 6) + Facing.oppositeSide[side];
            assertEquals("meta=" + meta, expected, BlockSlider.toggleFacing(meta, side));
        }
    }

    @Test
    public void clickingAnotherFaceSelectsThatFace() {
        for (int meta = 0; meta < 12; meta++) {
            for (int side = 0; side < 6; side++) {
                if (side == meta % 6) continue;
                assertEquals((meta < 6 ? 0 : 6) + side, BlockSlider.toggleFacing(meta, side));
            }
        }
    }
}
