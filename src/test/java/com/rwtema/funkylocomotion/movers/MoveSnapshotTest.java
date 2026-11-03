package com.rwtema.funkylocomotion.movers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.Test;

public class MoveSnapshotTest {

    @Test
    public void roundTripsRegistryNameAndMeta() {
        NBTTagCompound tag = MoveSnapshot.createBlockTag(Blocks.stone, 3);

        assertSame(Blocks.stone, MoveSnapshot.readBlock(tag));
        assertEquals(3, MoveSnapshot.readMeta(tag));
        assertEquals(MoveSnapshot.VERSION, tag.getInteger(MoveSnapshot.VERSION_KEY));
    }

    @Test
    public void readsLegacyNumericBlockId() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("Block", 1);

        assertSame(Blocks.stone, MoveSnapshot.readBlock(tag));
    }

    @Test
    public void readsNumericBlockIdFallback() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("BlockId", 1);

        assertSame(Blocks.stone, MoveSnapshot.readBlock(tag));
    }

    @Test
    public void unknownOrMissingBlockFallsBackToAir() {
        assertSame(Blocks.air, MoveSnapshot.readBlock(new NBTTagCompound()));

        NBTTagCompound unknown = new NBTTagCompound();
        unknown.setString("Block", "not_a_real_block");
        assertSame(Blocks.air, MoveSnapshot.readBlock(unknown));
    }
}
