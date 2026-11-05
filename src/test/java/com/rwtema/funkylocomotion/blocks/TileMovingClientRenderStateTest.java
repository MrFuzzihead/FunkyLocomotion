package com.rwtema.funkylocomotion.blocks;

import static org.junit.Assert.assertTrue;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;

import org.junit.Test;

import com.rwtema.funkylocomotion.rendering.MoverRenderState;

public class TileMovingClientRenderStateTest {

    /**
     * A client mover tile entity can be reused by a later move at the same
     * position. The data packet may replace the block (for example a chest where
     * an earlier move rendered a furnace), so any cached geometry must be
     * invalidated or the chest is drawn with the furnace's cached model.
     */
    @Test
    public void dataPacketInvalidatesCachedRenderState() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("BlockName", "minecraft:chest");
        tag.setByte("Meta", (byte) 4);
        tag.setInteger("Time", 0);
        tag.setInteger("MaxTime", 20);
        tag.setByte("Dir", (byte) 3);
        tag.setBoolean("DNR", true);

        TileMovingClient mover = new TileMovingClient();

        int before = MoverRenderState.revision();
        mover.onDataPacket(null, new S35PacketUpdateTileEntity(0, 64, 0, 0, tag));

        assertTrue(
            "receiving new mover data must invalidate cached render geometry",
            MoverRenderState.revision() > before);
    }
}
