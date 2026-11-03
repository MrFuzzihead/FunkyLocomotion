package com.rwtema.funkylocomotion.fmp;

import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.rwtema.funkylocomotion.api.BlockPos;
import com.rwtema.funkylocomotion.api.IDescriptionProxy;

import codechicken.multipart.MultipartHelper;
import codechicken.multipart.TileMultipart;

public class FMPDescriber implements IDescriptionProxy {

    @Override
    public String getID() {
        return "FMP";
    }

    @Override
    public boolean canHandleTile(TileEntity tile) {
        return tile instanceof TileMultipart;
    }

    @Override
    public void addDescriptionToTags(NBTTagCompound descriptor, TileEntity tile) {
        if (!(tile instanceof TileMultipart)) return;

        NBTTagCompound tileTag = new NBTTagCompound();
        tile.writeToNBT(tileTag);
        descriptor.setTag("Tile", tileTag);
    }

    @Override
    public TileEntity recreateTileEntity(NetworkManager net, NBTTagCompound tag, Block block, int meta, BlockPos pos,
        World world) {
        if (!tag.hasKey("Tile", 10)) return block.createTileEntity(world, meta);

        TileEntity tile = MultipartHelper.createTileFromNBT(world, tag.getCompoundTag("Tile"));
        if (tile instanceof TileMultipart) {
            tile.xCoord = pos.x;
            tile.yCoord = pos.y;
            tile.zCoord = pos.z;
        }
        return tile;
    }
}
