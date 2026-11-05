package com.rwtema.funkylocomotion.blocks;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.rwtema.funkylocomotion.api.IStickyBlock;
import com.rwtema.funkylocomotion.helper.IMoverRenderWorld;

public class BlockFrame extends Block implements IStickyBlock {

    public BlockFrame() {
        super(Material.rock);
        this.setBlockName("funkylocomotion:frame");
        this.setBlockTextureName("funkylocomotion:frame");
        this.setHardness(1);
    }

    @Override
    public boolean shouldSideBeRendered(IBlockAccess world, int x, int y, int z, int side) {
        Block neighbor = world.getBlock(x, y, z);
        if (neighbor instanceof BlockFrame) return false;

        // While a mover has slid away from an opaque static block, that block is
        // ignored for culling. At rest it would hide this face, so only keep the
        // face if it is a solid (closed) frame side; an open lattice face would
        // show the world behind it and flicker as the structure moves.
        if (world instanceof IMoverRenderWorld && ((IMoverRenderWorld) world).isIgnoringOpaqueStatic(x, y, z)
            && !isFaceSolid(world, x, y, z, side)) {
            return false;
        }

        return super.shouldSideBeRendered(world, x, y, z, side);
    }

    /**
     * Whether this block's face on the given side uses the solid (closed) icon.
     * The base frame is always the open lattice.
     */
    protected boolean isFaceSolid(IBlockAccess world, int x, int y, int z, int side) {
        return false;
    }

    @Override
    public boolean isStickySide(World world, int x, int y, int z, ForgeDirection side) {
        return true;
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean isBlockNormalCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public boolean isSideSolid(IBlockAccess world, int x, int y, int z, ForgeDirection side) {
        return true;
    }
}
