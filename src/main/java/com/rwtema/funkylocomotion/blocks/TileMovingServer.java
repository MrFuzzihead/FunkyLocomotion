package com.rwtema.funkylocomotion.blocks;

import java.lang.ref.WeakReference;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;

import com.rwtema.funkylocomotion.movers.MoveManager;
import com.rwtema.funkylocomotion.movers.MovingTileRegistry;

import cpw.mods.fml.relauncher.Side;

public class TileMovingServer extends TileMovingBase {

    public WeakReference<EntityPlayer> activatingPlayer = null;
    public boolean finishing = false;
    public int activatingSide = -1;
    public float activatingHitX, activatingHitY, activatingHitZ;

    public TileMovingServer() {
        super(Side.SERVER);
    }

    @Override
    public Packet getDescriptionPacket() {
        // S35PacketUpdateTileEntity serializes its tag on the Netty thread, while
        // this method runs on the server thread. Never hand out the live desc tag:
        // the next tick mutates it while the previous packet is still being written.
        NBTTagCompound packetTag = desc == null ? new NBTTagCompound() : (NBTTagCompound) desc.copy();
        if (desc == null) packetTag.setBoolean("DNR", true);
        packetTag.setInteger("Time", time);
        packetTag.setInteger("MaxTime", maxTime);
        packetTag.setByte("Dir", (byte) dir);

        if (lightLevel > 0) packetTag.setByte("Light", (byte) lightLevel);
        if (lightOpacity > 0) packetTag.setShort("Opacity", (short) lightOpacity);

        if (collisions != null && collisions.length > 0) {
            packetTag.setTag("Collisions", TagsAxis(collisions));
        }

        return new S35PacketUpdateTileEntity(this.xCoord, this.yCoord, this.zCoord, 0, packetTag);
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        MovingTileRegistry.deregister(this);
    }

    @Override
    public void invalidate() {
        super.invalidate();
        MovingTileRegistry.deregister(this);
    }

    @Override
    public void validate() {
        super.validate();
        MovingTileRegistry.register(this);
    }

    @Override
    public void updateEntity() {
        if (time < maxTime) {

            super.updateEntity();
            // The only changing NBT is the animation time; mark the chunk at a
            // save-friendly interval instead of every single tick.
            if (this.time % 20 == 0) {
                this.worldObj.markTileEntityChunkModified(this.xCoord, this.yCoord, this.zCoord, this);
            }
        } else if (!finishing) MoveManager.finishMoving();
    }

    public void cacheActivate(EntityPlayer player, int side, float hitX, float hitY, float hitZ) {
        if (this.activatingPlayer == null || this.activatingPlayer.get() == null) {
            activatingPlayer = new WeakReference<EntityPlayer>(player);
            activatingSide = side;
            activatingHitX = hitX;
            activatingHitY = hitY;
            activatingHitZ = hitZ;
        }
    }
}
