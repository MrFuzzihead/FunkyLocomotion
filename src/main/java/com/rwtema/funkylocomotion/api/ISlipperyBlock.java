package com.rwtema.funkylocomotion.api;

import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public interface ISlipperyBlock {

    boolean canStickTo(World world, BlockPos pos, ForgeDirection dir);
}
