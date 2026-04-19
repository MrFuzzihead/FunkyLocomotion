package com.rwtema.funkylocomotion.api;

import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public interface IDescriptionProxy {

    String getID();

    boolean canHandleTile(TileEntity tile);

    void addDescriptionToTags(NBTTagCompound descriptor, TileEntity tile);

    @SideOnly(Side.CLIENT)
    TileEntity recreateTileEntity(NetworkManager net, NBTTagCompound tag, Block block, int meta, BlockPos pos,
        World world);

}
