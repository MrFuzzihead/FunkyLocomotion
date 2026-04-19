package com.rwtema.funkylocomotion.factory;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.chunk.Chunk;

import com.rwtema.funkylocomotion.api.BlockPos;

public class ChunkUnloadFactory extends DefaultMoveFactory {

    @Override
    protected NBTTagCompound saveTile(BlockPos pos, Chunk chunk, NBTTagCompound tag) {
        TileEntity tile = chunk.getTileEntityUnsafe(pos.x & 15, pos.y, pos.z & 15);
        if (tile != null) {
            NBTTagCompound tileTag = new NBTTagCompound();
            tile.writeToNBT(tileTag);
            tag.setTag("Tile", tileTag);
            tile.onChunkUnload();
            chunk.chunkTileEntityMap.remove(new ChunkPosition(pos.x & 15, pos.y, pos.z & 15));
            tile.getWorldObj().loadedTileEntityList.remove(tile);
            return tileTag;
        }
        return null;
    }
}
