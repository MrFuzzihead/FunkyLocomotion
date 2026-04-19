package com.rwtema.funkylocomotion.api;

import java.util.List;

import net.minecraft.world.World;

public interface IAdvStickyBlock {

    List<BlockPos> getBlocksToMove(World world, BlockPos pos);
}
