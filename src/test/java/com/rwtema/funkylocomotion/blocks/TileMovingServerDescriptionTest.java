package com.rwtema.funkylocomotion.blocks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.util.AxisAlignedBB;

import org.junit.Test;

public class TileMovingServerDescriptionTest {

    @Test
    public void descriptionPacketDoesNotMutateOrShareTheLiveDescriptionTag() {
        TileMovingServer tile = new TileMovingServer();
        tile.desc = new NBTTagCompound();
        tile.desc.setString("Block", "minecraft:stone");
        tile.desc.setString("DescID", "");
        tile.time = 5;
        tile.maxTime = 20;
        tile.dir = 3;
        tile.lightLevel = 9;
        tile.lightOpacity = 100;
        tile.collisions = new AxisAlignedBB[] { AxisAlignedBB.getBoundingBox(0, 0, 0, 1, 1, 1) };

        Packet packet = tile.getDescriptionPacket();
        assertTrue(packet instanceof S35PacketUpdateTileEntity);
        NBTTagCompound firstTag = ((S35PacketUpdateTileEntity) packet).func_148857_g();

        assertNotSame(tile.desc, firstTag);
        assertFalse("live desc must not be mutated", tile.desc.hasKey("Time"));
        assertEquals("minecraft:stone", tile.desc.getString("Block"));
        assertEquals(5, firstTag.getInteger("Time"));
        assertEquals(20, firstTag.getInteger("MaxTime"));
        assertEquals(3, firstTag.getByte("Dir"));
        assertEquals(9, firstTag.getByte("Light"));
        assertEquals(100, firstTag.getShort("Opacity"));
        assertEquals(
            1,
            firstTag.getTagList("Collisions", 10)
                .tagCount());

        tile.time = 6;
        Packet secondPacket = tile.getDescriptionPacket();
        NBTTagCompound secondTag = ((S35PacketUpdateTileEntity) secondPacket).func_148857_g();

        assertEquals("first packet must keep its own snapshot", 5, firstTag.getInteger("Time"));
        assertEquals(6, secondTag.getInteger("Time"));
        assertNotSame(firstTag, secondTag);
    }

    @Test
    public void missingDescriptionStillReturnsAnEmptyDoNotRenderPacket() {
        TileMovingServer tile = new TileMovingServer();
        tile.desc = null;

        Packet packet = tile.getDescriptionPacket();
        assertTrue(packet instanceof S35PacketUpdateTileEntity);
        NBTTagCompound tag = ((S35PacketUpdateTileEntity) packet).func_148857_g();
        assertTrue(tag.getBoolean("DNR"));
    }
}
