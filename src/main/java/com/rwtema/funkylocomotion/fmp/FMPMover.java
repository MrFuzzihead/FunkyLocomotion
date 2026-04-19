package com.rwtema.funkylocomotion.fmp;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.chunk.Chunk;

import com.rwtema.funkylocomotion.factory.DefaultMoveFactory;

import codechicken.multipart.MultipartHelper;
import codechicken.multipart.TileMultipart;
import framesapi.BlockPos;

public class FMPMover extends DefaultMoveFactory {

    @Override
    protected TileEntity loadTile(BlockPos pos, NBTTagCompound tag, Chunk chunk) {
        if (tag.hasKey("Tile", 10)) {
            NBTTagCompound tileTag = tag.getCompoundTag("Tile");
            tileTag.setInteger("x", pos.x);
            tileTag.setInteger("y", pos.y);
            tileTag.setInteger("z", pos.z);

            TileEntity tile = MultipartHelper.createTileFromNBT(chunk.worldObj, tileTag);
            if (tile != null) {
                chunk.addTileEntity(tile);
                MultipartHelper.sendDescPacket(chunk.worldObj, tile);
                ((TileMultipart) tile).onMoved();
                return tile;
            }
        }
        return null;
    }
}
