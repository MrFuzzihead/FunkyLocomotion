package com.rwtema.funkylocomotion.blocks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.Test;

public class TeleportIdDataTest {

    @Test
    public void aRequestedIdIsKeptForAPairAndThenReplaced() {
        TeleportIdData data = new TeleportIdData("test");

        assertEquals(123, data.allocate(123));
        assertEquals(123, data.allocate(123));
        assertNotEquals(123, data.allocate(123));
    }

    @Test
    public void releasedIdsStayReservedAcrossReloads() {
        TeleportIdData data = new TeleportIdData("test");
        int id = data.allocate(456);
        data.release(id);
        data.release(id);

        NBTTagCompound tag = new NBTTagCompound();
        data.writeToNBT(tag);
        TeleportIdData loaded = new TeleportIdData("test");
        loaded.readFromNBT(tag);

        assertNotEquals(id, loaded.allocate(id));
    }

    @Test
    public void zeroRequestAlwaysReceivesAFreshId() {
        TeleportIdData data = new TeleportIdData("test");

        int first = data.allocate(0);
        int second = data.allocate(0);

        assertEquals(true, first > 0);
        assertNotEquals(first, second);
    }
}
