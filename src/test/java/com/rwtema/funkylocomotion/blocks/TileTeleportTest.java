package com.rwtema.funkylocomotion.blocks;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.rwtema.funkylocomotion.api.BlockPos;

import net.minecraftforge.common.util.ForgeDirection;

public class TileTeleportTest {

    @Test
    public void teleportFacingIsTheOppositeOfTheMetadataFace() {
        for (int meta = 0; meta < 12; meta++) {
            ForgeDirection expected = ForgeDirection.getOrientation(meta % 6)
                .getOpposite();
            assertEquals("meta " + meta, expected, TileTeleport.getTeleportFacing(meta));
        }
    }

    @Test
    public void destinationOffsetUsesTheSameRelativePositionForPushAndPullMetadata() {
        BlockPos srcHome = new BlockPos(10, 64, 10);
        BlockPos dstHome = new BlockPos(100, 70, -5);

        for (int srcMeta = 0; srcMeta < 12; srcMeta++) {
            for (int dstMeta = 0; dstMeta < 12; dstMeta++) {
                BlockPos srcAnchor = srcHome.advance(TileTeleport.getTeleportFacing(srcMeta));
                BlockPos dstAnchor = dstHome.advance(TileTeleport.getTeleportFacing(dstMeta));
                BlockPos moved = new BlockPos(srcAnchor.x + 2, srcAnchor.y - 1, srcAnchor.z + 4);

                BlockPos result = TileTeleport
                    .getDestinationPos(srcHome, srcMeta, dstHome, dstMeta, moved);

                assertEquals(dstAnchor.x + 2, result.x);
                assertEquals(dstAnchor.y - 1, result.y);
                assertEquals(dstAnchor.z + 4, result.z);
            }
        }
    }
}
