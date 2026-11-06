package com.rwtema.funkylocomotion.blocks;

import static org.junit.Assert.assertTrue;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.Test;

public class TilePusherNbtTest {

    @Test
    public void readsTheCurrentLowercasePoweredKey() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean("powered", true);

        TilePusher pusher = new TilePusher();
        pusher.readFromNBT(tag);

        assertTrue(pusher.powered);
    }

    @Test
    public void readsTheLegacyCapitalizedPoweredKey() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean("Powered", true);

        TilePusher pusher = new TilePusher();
        pusher.readFromNBT(tag);

        assertTrue(pusher.powered);
    }
}
