package com.rwtema.funkylocomotion.movers;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Versioned, backwards-compatible block snapshot helpers.
 *
 * <p>
 * Server move data historically stored {@code "Block"} as a registry name while
 * the client description stored it as a numeric id. Both forms are still read;
 * new writes include a registry name plus a numeric fallback and a format
 * version.
 */
public final class MoveSnapshot {

    public static final String VERSION_KEY = "FLVersion";
    public static final int VERSION = 2;

    private MoveSnapshot() {}

    public static NBTTagCompound createBlockTag(Block block, int meta) {
        NBTTagCompound tag = new NBTTagCompound();
        if (block == null) block = Blocks.air;

        String name = Block.blockRegistry.getNameForObject(block);
        if (name != null) tag.setString("Block", name);
        tag.setInteger("BlockId", Block.getIdFromBlock(block));
        tag.setByte("Meta", (byte) meta);
        tag.setInteger(VERSION_KEY, VERSION);
        return tag;
    }

    public static Block readBlock(NBTTagCompound tag) {
        if (tag == null) return Blocks.air;

        if (tag.hasKey("BlockName", 8)) {
            Block block = Block.getBlockFromName(tag.getString("BlockName"));
            if (block != null) return block;
        }

        if (tag.hasKey("Block", 8)) {
            Block block = Block.getBlockFromName(tag.getString("Block"));
            if (block != null) return block;
        }

        if (tag.hasKey("Block", 3)) {
            Block block = Block.getBlockById(tag.getInteger("Block"));
            if (block != null) return block;
        }

        if (tag.hasKey("BlockId", 3)) {
            Block block = Block.getBlockById(tag.getInteger("BlockId"));
            if (block != null) return block;
        }

        return Blocks.air;
    }

    public static int readMeta(NBTTagCompound tag) {
        return tag == null ? 0 : tag.getByte("Meta") & 0xFF;
    }
}
