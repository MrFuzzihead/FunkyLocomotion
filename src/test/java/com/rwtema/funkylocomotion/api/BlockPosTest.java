package com.rwtema.funkylocomotion.api;

import static org.junit.Assert.assertEquals;

import net.minecraftforge.common.util.ForgeDirection;

import org.junit.Test;

public class BlockPosTest {

    @Test
    public void advanceByForgeDirectionMatchesExpectedOffset() {
        BlockPos origin = new BlockPos(10, 64, -3);

        for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
            BlockPos advanced = origin.advance(dir.ordinal());
            assertEquals(origin.x + dir.offsetX, advanced.x);
            assertEquals(origin.y + dir.offsetY, advanced.y);
            assertEquals(origin.z + dir.offsetZ, advanced.z);
        }
    }

    @Test
    public void advanceByUnknownSideIsANoOp() {
        BlockPos origin = new BlockPos(10, 64, -3);

        assertEquals(origin, origin.advance(6));
        assertEquals(origin, origin.advance(7));
        assertEquals(origin, origin.advance(99));
    }
}
