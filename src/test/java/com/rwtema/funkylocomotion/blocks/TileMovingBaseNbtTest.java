package com.rwtema.funkylocomotion.blocks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;

import org.junit.Test;

public class TileMovingBaseNbtTest {

    @Test
    public void roundTripsCollisionBoxesAndScheduledTicks() {
        TileMovingServer source = new TileMovingServer();
        source.block = new NBTTagCompound();
        source.block.setString("Block", "minecraft:stone");
        source.desc = new NBTTagCompound();
        source.desc.setString("Block", "1");
        source.time = 3;
        source.maxTime = 10;
        source.dir = 4;
        source.lightLevel = 7;
        source.lightOpacity = 200;
        source.collisions = new AxisAlignedBB[] {
            AxisAlignedBB.getBoundingBox(0.0, 0.0, 0.0, 1.0, 0.5, 1.0),
            AxisAlignedBB.getBoundingBox(0.25, 0.5, 0.25, 0.75, 1.0, 0.75) };
        source.scheduledTickTime = 12;
        source.scheduledTickPriority = 2;

        NBTTagCompound tag = new NBTTagCompound();
        source.writeMoveDataToNBT(tag);

        TileMovingServer loaded = new TileMovingServer();
        loaded.readMoveDataFromNBT(tag);

        assertEquals(source.block.toString(), loaded.block.toString());
        assertEquals(source.desc.toString(), loaded.desc.toString());
        assertEquals(3, loaded.time);
        assertEquals(10, loaded.maxTime);
        assertEquals(4, loaded.dir);
        assertEquals(7, loaded.lightLevel);
        assertEquals(200, loaded.lightOpacity);
        assertEquals(2, loaded.collisions.length);
        assertBoxEquals(source.collisions[0], loaded.collisions[0]);
        assertBoxEquals(source.collisions[1], loaded.collisions[1]);
        assertEquals(12, loaded.scheduledTickTime);
        assertEquals(2, loaded.scheduledTickPriority);
        assertFalse(loaded.isAir);
    }

    @Test
    public void missingOptionalKeysUseSafeDefaults() {
        TileMovingServer loaded = new TileMovingServer();
        loaded.readMoveDataFromNBT(new NBTTagCompound());

        assertTrue(loaded.block.hasNoTags());
        assertTrue(loaded.desc.hasNoTags());
        assertTrue(loaded.isAir);
        assertEquals(-1, loaded.dir);
        assertEquals(0, loaded.collisions.length);
        assertEquals(-1, loaded.scheduledTickTime);
        assertEquals(0, loaded.lightOpacity);
    }

    @Test
    public void lightValuesAreClampedToVanillaRanges() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setByte("Light", (byte) 99);
        tag.setShort("Opacity", (short) -20);

        TileMovingServer loaded = new TileMovingServer();
        loaded.readMoveDataFromNBT(tag);

        assertEquals(15, loaded.lightLevel);
        assertEquals(0, loaded.lightOpacity);
    }

    @Test
    public void legacyTimeTimeKeyIsNotTreatedAsTickTime() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("TimeTime", 40);

        TileMovingServer loaded = new TileMovingServer();
        loaded.readMoveDataFromNBT(tag);

        assertEquals(-1, loaded.scheduledTickTime);
    }

    private static void assertBoxEquals(AxisAlignedBB expected, AxisAlignedBB actual) {
        assertEquals(expected.minX, actual.minX, 0.0001);
        assertEquals(expected.minY, actual.minY, 0.0001);
        assertEquals(expected.minZ, actual.minZ, 0.0001);
        assertEquals(expected.maxX, actual.maxX, 0.0001);
        assertEquals(expected.maxY, actual.maxY, 0.0001);
        assertEquals(expected.maxZ, actual.maxZ, 0.0001);
    }
}
