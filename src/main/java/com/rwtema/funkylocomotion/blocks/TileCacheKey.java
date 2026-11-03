package com.rwtema.funkylocomotion.blocks;

import net.minecraft.world.World;

/**
 * Dimension-aware key for the client-side copy of a source tile entity.
 * The old {@code ChunkCoordinates} key could return a tile from another
 * dimension when the player visited a second world with the same coordinates.
 */
public final class TileCacheKey {

    final int dimension;
    final int x;
    final int y;
    final int z;

    public TileCacheKey(World world, int x, int y, int z) {
        this(world == null || world.provider == null ? Integer.MIN_VALUE : world.provider.dimensionId, x, y, z);
    }

    public TileCacheKey(int dimension, int x, int y, int z) {
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TileCacheKey)) return false;

        TileCacheKey that = (TileCacheKey) o;

        return dimension == that.dimension && x == that.x && y == that.y && z == that.z;
    }

    @Override
    public int hashCode() {
        int result = dimension;
        result = 31 * result + x;
        result = 31 * result + y;
        result = 31 * result + z;
        return result;
    }

    @Override
    public String toString() {
        return "TileCacheKey{" + dimension + "," + x + "," + y + "," + z + '}';
    }
}
