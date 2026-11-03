package com.rwtema.funkylocomotion.blocks;

import static org.junit.Assert.assertTrue;

import net.minecraft.tileentity.TileEntity;

import org.junit.Test;

public class BlockMovingSideTest {

    @Test
    public void serverWorldCreatesAServerMover() {
        TileEntity tile = BlockMoving.createTileEntity(false);
        assertTrue(tile instanceof TileMovingServer);
    }
}
