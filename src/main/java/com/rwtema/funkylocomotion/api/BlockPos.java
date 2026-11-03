package com.rwtema.funkylocomotion.api;

import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

public final class BlockPos {

    public int x, y, z;

    public BlockPos(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BlockPos blockPos = (BlockPos) o;

        return x == blockPos.x && y == blockPos.y && z == blockPos.z;

    }

    @Override
    public int hashCode() {
        return (x * 31 + z) * 31 + y;
    }

    public BlockPos(TileEntity tile) {
        this(tile.xCoord, tile.yCoord, tile.zCoord);
    }

    public BlockPos advance(ForgeDirection dir) {
        return new BlockPos(x + dir.offsetX, y + dir.offsetY, z + dir.offsetZ);
    }

    @Override
    public String toString() {
        return "Pos{" + "" + x + ", " + y + ", " + z + '}';
    }

    /**
     * Returns a copy of this position advanced one block in the given direction.
     * <p>
     * Side ids use the vanilla {@link ForgeDirection} numbering ({@code 0..5});
     * {@code 6} ({@link ForgeDirection#UNKNOWN}) and out-of-range values are treated
     * as "no movement" instead of indexing the raw {@code Facing} offset arrays.
     */
    public BlockPos advance(int i) {
        return advance(ForgeDirection.getOrientation(i));
    }
}
