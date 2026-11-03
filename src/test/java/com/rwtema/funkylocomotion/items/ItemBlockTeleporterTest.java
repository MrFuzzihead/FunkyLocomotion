package com.rwtema.funkylocomotion.items;

import static org.junit.Assert.assertTrue;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.Test;

public class ItemBlockTeleporterTest {

    @Test
    public void assignedIdsAreNeverZeroOrOutOfRange() {
        for (int i = 0; i < 1000; i++) {
            ItemStack stack = ItemBlockTeleporter.assignRandomID(new ItemStack(Blocks.stone));
            NBTTagCompound tag = stack.getTagCompound();
            int id = tag.getInteger(ItemBlockTeleporter.NBT_TELEPORTER_ID);
            assertTrue("id " + id, id > 0 && id < 0x100000);
        }
    }
}
