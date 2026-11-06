package com.rwtema.funkylocomotion.movepermissions;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public final class MoveCheckRegistry {

    private static final List<IMoveChecker> checkers = new CopyOnWriteArrayList<IMoveChecker>();
    private static final IMoveChecker DEFAULT = new IMoveChecker() {

        @Override
        public boolean preventMovement(World world, int x, int y, int z, Block block, int meta, TileEntity tile) {
            if (block == null) return false;
            return !MoveCheckReflector.canMoveClass(block.getClass())
                || tile != null && !MoveCheckReflector.canMoveClass(tile.getClass());
        }
    };

    static {
        register(DEFAULT);
    }

    private MoveCheckRegistry() {}

    public static void register(IMoveChecker checker) {
        if (checker == null) throw new IllegalArgumentException("checker must not be null");
        checkers.add(checker);
    }

    public static boolean preventMovement(World world, int x, int y, int z, Block block, int meta, TileEntity tile) {
        for (IMoveChecker checker : checkers) {
            if (checker.preventMovement(world, x, y, z, block, meta, tile)) return true;
        }
        return false;
    }

    static void clear() {
        checkers.clear();
        checkers.add(DEFAULT);
    }
}
