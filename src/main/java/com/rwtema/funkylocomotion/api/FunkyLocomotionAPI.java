package com.rwtema.funkylocomotion.api;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;

import com.rwtema.funkylocomotion.description.DescriptorRegistry;
import com.rwtema.funkylocomotion.factory.FactoryRegistry;
import com.rwtema.funkylocomotion.movepermissions.IMoveChecker;
import com.rwtema.funkylocomotion.movepermissions.MoveBlacklist;
import com.rwtema.funkylocomotion.movepermissions.MoveCheckRegistry;
import com.rwtema.funkylocomotion.movepermissions.MovePermissionRegistry;

/**
 * Small stable facade for the Funky Locomotion registries.
 */
public final class FunkyLocomotionAPI {

    private FunkyLocomotionAPI() {}

    public static void registerMoveFactory(Block block, IMoveFactory factory) {
        if (block == null || factory == null) throw new IllegalArgumentException("block and factory are required");
        FactoryRegistry.moveFactoryMapBlock.put(block, factory);
    }

    public static void registerMoveFactory(Class<? extends Block> blockClass, IMoveFactory factory) {
        if (blockClass == null || factory == null) throw new IllegalArgumentException("block class and factory are required");
        FactoryRegistry.moveFactoryMapBlockClass.put(blockClass, factory);
    }

    public static void registerMoveFactoryForTile(Class<? extends TileEntity> tileClass, IMoveFactory factory) {
        if (tileClass == null || factory == null) throw new IllegalArgumentException("tile class and factory are required");
        FactoryRegistry.moveFactoryMapInheritanceClass.put(tileClass, factory);
    }

    public static void registerDescription(IDescriptionProxy proxy, boolean fallback) {
        if (proxy == null) throw new IllegalArgumentException("proxy is required");
        DescriptorRegistry.register(proxy, fallback);
    }

    public static void registerMoveChecker(IMoveChecker checker) {
        MoveCheckRegistry.register(checker);
    }

    public static void registerMovePermissions(IMovePermissions permissions) {
        MovePermissionRegistry.register(permissions);
    }

    public static void addBlacklist(String entry) {
        MoveBlacklist.add(entry);
    }
}
