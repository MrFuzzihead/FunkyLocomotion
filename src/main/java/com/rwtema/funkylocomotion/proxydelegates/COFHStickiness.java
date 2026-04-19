package com.rwtema.funkylocomotion.proxydelegates;

import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.rwtema.funkylocomotion.blocks.BlockStickyFrame;

import cofh.api.block.IBlockAppearance;
import framesapi.IStickyBlock;

public class COFHStickiness implements IStickyBlock {

    @Override
    public boolean isStickySide(World world, int x, int y, int z, ForgeDirection side) {
        return isStickySide_do(world, x, y, z, side);
    }

    private static boolean isStickySide_do(World world, int x, int y, int z, ForgeDirection side) {
        IBlockAppearance block = (IBlockAppearance) world.getBlock(x, y, z);

        Block visualBlock = block.getVisualBlock(world, x, y, z, side);
        if (!(visualBlock instanceof BlockStickyFrame)) return false;

        int visualMeta = block.getVisualMeta(world, x, y, z, side);

        return ((BlockStickyFrame) visualBlock).isMetaSticky(visualMeta, side);
    }

    public static void register() {
        register_do();
    }

    private static void register_do() {
        ProxyRegistry.register(IBlockAppearance.class, new COFHStickiness(), IStickyBlock.class);
    }
}
