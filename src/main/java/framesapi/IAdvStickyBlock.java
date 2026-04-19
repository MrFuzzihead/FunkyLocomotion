package framesapi;

import java.util.List;

import net.minecraft.world.World;

public interface IAdvStickyBlock {

    public List<BlockPos> getBlocksToMove(World world, BlockPos pos);
}
