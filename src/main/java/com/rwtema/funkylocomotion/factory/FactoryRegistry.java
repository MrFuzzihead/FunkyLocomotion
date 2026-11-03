package com.rwtema.funkylocomotion.factory;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.rwtema.funkylocomotion.api.BlockPos;
import com.rwtema.funkylocomotion.api.IMoveFactory;
import com.rwtema.funkylocomotion.helper.BlockHelper;

public class FactoryRegistry {

    public static final Map<Block, IMoveFactory> moveFactoryMapBlock = new HashMap<Block, IMoveFactory>();
    public static final Map<Class<? extends Block>, IMoveFactory> moveFactoryMapBlockClass = new HashMap<Class<? extends Block>, IMoveFactory>();
    public static final Map<Class<?>, IMoveFactory> moveFactoryMapInheritanceClass = new HashMap<Class<?>, IMoveFactory>();
    private static final DefaultMoveFactory defaultFactory = new DefaultMoveFactory();
    public static final ChunkUnloadFactory chunkUnloadFactory = new ChunkUnloadFactory();

    public static IMoveFactory getDefaultFactory() {
        return defaultFactory;
    }

    public static IMoveFactory getFactory(TileEntity tile) {
        if (tile == null) return null;

        if (tile instanceof IMoveFactory) return (IMoveFactory) tile;

        for (Class<?> c = tile.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            IMoveFactory factory = moveFactoryMapInheritanceClass.get(c);
            if (factory != null) return factory;
        }

        return null;
    }

    public static IMoveFactory getFactory(Block b) {
        if (b == null) return getDefaultFactory();
        if (b instanceof IMoveFactory) return (IMoveFactory) b;

        IMoveFactory f = moveFactoryMapBlock.get(b);
        if (f != null) return f;

        for (Class<?> c = b.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            f = moveFactoryMapBlockClass.get(c);
            if (f != null) return f;
        }

        return getDefaultFactory();
    }

    public static IMoveFactory getFactory(World world, BlockPos pos) {
        IMoveFactory f = getFactory(BlockHelper.getTile(world, pos));
        if (f != null) return f;
        return getFactory(BlockHelper.getBlock(world, pos));
    }
}
