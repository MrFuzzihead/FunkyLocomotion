package com.rwtema.funkylocomotion.movers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.server.management.PlayerManager;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ReportedException;
import net.minecraft.world.NextTickListEntry;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.util.ForgeDirection;

import com.rwtema.funkylocomotion.LogHelper;
import com.rwtema.funkylocomotion.api.BlockPos;
import com.rwtema.funkylocomotion.api.IDescriptionProxy;
import com.rwtema.funkylocomotion.api.IMoveFactory;
import com.rwtema.funkylocomotion.blocks.BlockMoving;
import com.rwtema.funkylocomotion.blocks.TileMovingServer;
import com.rwtema.funkylocomotion.description.DescriptorRegistry;
import com.rwtema.funkylocomotion.factory.FactoryRegistry;
import com.rwtema.funkylocomotion.helper.BlockHelper;
import com.rwtema.funkylocomotion.network.FLNetwork;
import com.rwtema.funkylocomotion.network.MessageClearTile;

public class MoveManager {

    public static final NBTTagCompound airBlockTag;
    public static final NBTTagCompound airDescTag;

    static {
        airBlockTag = new NBTTagCompound();
        airDescTag = new NBTTagCompound();
        if (Block.getIdFromBlock(Blocks.air) != 0) airDescTag.setInteger("Block", Block.getIdFromBlock(Blocks.air));
    }

    public static void startMoving(World world, List<BlockPos> posList, ForgeDirection dir, int maxTime) {
        ArrayList<BlockLink> links = new ArrayList<BlockLink>(posList.size());
        for (BlockPos blockPos : posList) {
            links.add(new BlockLink(blockPos, blockPos.advance(dir)));
        }

        startMoving(world, world, links, dir, maxTime);
    }

    final static LinkedHashMap<String, Object> vars = new LinkedHashMap<String, Object>(10, 0.2F);

    @SuppressWarnings("unchecked")
    public synchronized static void startMoving(World srcWorld, World dstWorld, List<BlockLink> links,
        ForgeDirection dir, int maxTime) {
        String section = "Start";

        clearVars();

        try {
            vars.put("srcWorld", srcWorld);
            vars.put("dstWorld", dstWorld);
            vars.put("links", links);
            vars.put("dir", dir);
            vars.put("maxTime", maxTime);

            if (dir != ForgeDirection.UNKNOWN && srcWorld != dstWorld) {
                throw new IllegalArgumentException("Trying to regular shift between worlds");
            }

            HashSet<BlockPos> srcBlocks = new HashSet<BlockPos>();
            HashSet<BlockPos> dstBlocks = new HashSet<BlockPos>();

            vars.put("srcBlocks", srcBlocks);
            vars.put("dstBlocks", dstBlocks);

            for (BlockLink link : links) {
                srcBlocks.add(link.srcPos);
                dstBlocks.add(link.dstPos);
            }

            HashSet<BlockPos> dstBlocksToBeDestroyed = new HashSet<BlockPos>();
            HashSet<BlockPos> srcBlocksToBecomeAir = new HashSet<BlockPos>();

            vars.put("dstBlocksToBeDestroyed", dstBlocksToBeDestroyed);
            vars.put("srcBlocksToBecomeAir", srcBlocksToBecomeAir);

            srcBlocksToBecomeAir.addAll(srcBlocks);
            dstBlocksToBeDestroyed.addAll(dstBlocks);

            if (srcWorld == dstWorld) {
                srcBlocksToBecomeAir.removeAll(dstBlocks);
                dstBlocksToBeDestroyed.removeAll(srcBlocks);
            }

            Map<BlockPos, Entry> dstTileEntries = new HashMap<BlockPos, Entry>();
            Map<BlockPos, Integer> srcLightOpacity = new HashMap<BlockPos, Integer>();

            vars.put("dstTileEntries", dstTileEntries);
            vars.put("srcLightOpacity", srcLightOpacity);

            Set<Chunk> srcChunks = new HashSet<Chunk>();
            HashSet<EntityPlayer> srcWatchingPlayers = new HashSet<EntityPlayer>();
            HashSet<EntityPlayer> dstWatchingPlayers = new HashSet<EntityPlayer>();
            HashSet inventories = new HashSet();

            vars.put("srcChunks", srcChunks);
            vars.put("srcWatchingPlayers", srcWatchingPlayers);
            vars.put("dstWatchingPlayers", dstWatchingPlayers);
            vars.put("inventories", inventories);

            section = "BreakBlockWithDrop";

            for (BlockPos pos : dstBlocksToBeDestroyed) {
                Chunk dstChunk = BlockHelper.getChunk(dstWorld, pos);
                if (dstChunk != null) {
                    PlayerManager.PlayerInstance watcher = FLNetwork.getChunkWatcher(dstChunk, dstWorld);
                    if (watcher != null) dstWatchingPlayers.addAll(watcher.playersWatchingChunk);
                }

                TileEntity dstTile = dstWorld.getTileEntity(pos.x, pos.y, pos.z);
                if (dstTile instanceof IInventory) inventories.add(dstTile);

                BlockHelper.breakBlockWithDrop(dstWorld, pos);
            }

            section = "Read Data";

            for (BlockLink link : links) {
                vars.put("Iterator", link);
                BlockPos dstPos = link.dstPos;
                BlockPos srcPos = link.srcPos;

                Entry e = new Entry(dstPos);

                e.block = srcWorld.getBlock(srcPos.x, srcPos.y, srcPos.z);
                e.meta = srcWorld.getBlockMetadata(srcPos.x, srcPos.y, srcPos.z);

                e.lightopacity = e.block.getLightOpacity(srcWorld, srcPos.x, srcPos.y, srcPos.z);

                e.lightlevel = e.block.getLightValue(srcWorld, srcPos.x, srcPos.y, srcPos.z);

                List<AxisAlignedBB> axes = new ArrayList<AxisAlignedBB>();
                e.block.addCollisionBoxesToList(
                    srcWorld,
                    srcPos.x,
                    srcPos.y,
                    srcPos.z,
                    TileEntity.INFINITE_EXTENT_AABB,
                    axes,
                    null);

                if (!axes.isEmpty()) {
                    e.bb = new ArrayList<AxisAlignedBB>();
                    for (AxisAlignedBB bb : axes) {
                        e.bb.add(
                            AxisAlignedBB.getBoundingBox(
                                bb.minX - srcPos.x,
                                bb.minY - srcPos.y,
                                bb.minZ - srcPos.z,
                                bb.maxX - srcPos.x,
                                bb.maxY - srcPos.y,
                                bb.maxZ - srcPos.z));
                    }
                }

                NBTTagCompound descriptor = new NBTTagCompound();

                descriptor.setInteger("Block", Block.getIdFromBlock(e.block));
                if (e.meta != 0) descriptor.setByte("Meta", (byte) e.meta);

                TileEntity tile = srcWorld.getTileEntity(srcPos.x, srcPos.y, srcPos.z);
                if (tile != null) {
                    boolean flag = false;
                    for (IDescriptionProxy d : DescriptorRegistry.getProxyList()) {
                        if (d.canHandleTile(tile)) {
                            d.addDescriptionToTags(descriptor, tile);
                            if (!d.getID()
                                .isEmpty()) descriptor.setString("DescID", d.getID());
                            flag = true;
                            break;
                        }
                    }
                    if (!flag) {
                        descriptor.setBoolean("DNR", true);
                    }

                    if (tile instanceof IInventory) {
                        inventories.add(tile);
                    }
                }

                e.description = descriptor;
                srcLightOpacity.put(srcPos, e.lightopacity);

                srcChunks.add(BlockHelper.getChunk(srcWorld, srcPos));

                dstTileEntries.put(dstPos, e);
            }
            vars.put("Iterator", BLANK);

            section = "LoadTicks";
            for (Chunk c : srcChunks) {
                vars.put("Iterator", c);
                List<NextTickListEntry> ticks = srcWorld.getPendingBlockUpdates(c, false);
                if (ticks != null) {
                    long k = srcWorld.getTotalWorldTime();
                    for (NextTickListEntry tick : ticks) {
                        vars.put("Iterator2", tick);
                        BlockPos p = (new BlockPos(tick.xCoord, tick.yCoord, tick.zCoord));

                        if (BlockHelper.getBlock(c, p) != tick.func_151351_a()) continue;

                        p = p.advance(dir);

                        if (!dstTileEntries.containsKey(p)) continue;

                        Entry e = dstTileEntries.get(p);

                        e.scheduledTickTime = (int) (tick.scheduledTime - k);
                        e.scheduledTickPriority = tick.priority;
                    }
                }

                PlayerManager.PlayerInstance chunkWatcher = FLNetwork.getChunkWatcher(c, srcWorld);
                if (chunkWatcher != null) srcWatchingPlayers.addAll(chunkWatcher.playersWatchingChunk);
            }
            vars.put("Iterator", BLANK);
            vars.put("Iterator2", BLANK);

            // from now on - NO BLOCK UPDATES
            section = "destroyBlock";
            for (BlockLink link : links) {
                vars.put("Iterator", link);
                IMoveFactory factory = FactoryRegistry.getFactory(srcWorld, link.srcPos);
                dstTileEntries.get(link.dstPos).blockTag = factory.destroyBlock(srcWorld, link.srcPos);
            }
            vars.put("Iterator", BLANK);

            // let there be updates;
            section = "closeInventories";
            srcWatchingPlayers.addAll(dstWatchingPlayers);
            for (EntityPlayer watchingPlayer : srcWatchingPlayers) {
                vars.put("Iterator", watchingPlayer);
                if (watchingPlayer.openContainer != watchingPlayer.inventoryContainer
                    && watchingPlayer.openContainer != null) {
                    for (Object o : watchingPlayer.openContainer.inventorySlots) {
                        Slot s = (Slot) o;
                        if (inventories.contains(s.inventory)) {
                            watchingPlayer.closeScreen();
                            break;
                        }
                    }
                }
            }
            vars.put("Iterator", BLANK);

            section = "clearTilesSilent";
            for (BlockLink link : links) {
                vars.put("Iterator", link);
                BlockPos dstPos = link.dstPos;
                BlockPos srcPos = link.srcPos;
                BlockHelper.silentClear(BlockHelper.getChunk(dstWorld, dstPos), dstPos);
                if (dir != ForgeDirection.UNKNOWN) FLNetwork
                    .sendToAllWatchingChunk(srcWorld, srcPos.x, srcPos.y, srcPos.z, new MessageClearTile(srcPos));
                dstWorld.removeTileEntity(dstPos.x, dstPos.y, dstPos.z);
            }
            vars.put("Iterator", BLANK);

            section = "postUpdateBlock";
            for (BlockPos pos : srcBlocksToBecomeAir) {
                vars.put("Iterator", pos);
                // These positions are in the source world. For a cross-dimension
                // teleport, posting them to dstWorld updated unrelated blocks at the
                // same coordinates in the destination dimension.
                Integer oldOpacity = srcLightOpacity.get(pos);
                BlockHelper.postUpdateBlock(srcWorld, pos, oldOpacity != null ? oldOpacity : -1);
            }
            vars.put("Iterator", BLANK);

            ArrayList<TileMovingServer> tiles = new ArrayList<TileMovingServer>();

            vars.put("tiles", tiles);

            section = "createTiles";
            for (Entry e : dstTileEntries.values()) {
                vars.put("Iterator", e);
                dstWorld.setBlock(e.pos.x, e.pos.y, e.pos.z, BlockMoving.instance, 0, 0);
                TileMovingServer tile = (TileMovingServer) dstWorld.getTileEntity(e.pos.x, e.pos.y, e.pos.z);
                vars.put("Iterator2", tile);
                tile.block = e.blockTag;
                tile.desc = e.description;
                tile.dir = dir.ordinal();
                tile.maxTime = maxTime;

                tile.lightLevel = e.lightlevel;
                tile.lightOpacity = e.lightopacity;

                tile.scheduledTickTime = e.scheduledTickTime;
                tile.scheduledTickPriority = e.scheduledTickPriority;
                if (e.bb != null) tile.collisions = e.bb.toArray(new AxisAlignedBB[0]);

                tile.isAir = false;

                tiles.add(tile);
            }
            vars.put("Iterator", BLANK);
            vars.put("Iterator2", BLANK);

            section = "createBlankTiles";
            for (BlockLink link : links) {
                vars.put("Iterator", BLANK);
                BlockPos srcPos = link.srcPos;
                if (srcWorld != dstWorld || !dstTileEntries.containsKey(srcPos)) {
                    srcWorld.setBlock(srcPos.x, srcPos.y, srcPos.z, BlockMoving.instance, 0, 0);
                    TileMovingServer tile = (TileMovingServer) srcWorld.getTileEntity(srcPos.x, srcPos.y, srcPos.z);
                    vars.put("Iterator2", tile);

                    if (dir != ForgeDirection.UNKNOWN) {
                        tile.block = (NBTTagCompound) airBlockTag.copy();
                        tile.desc = (NBTTagCompound) airDescTag.copy();
                        tile.dir = dir.ordinal();
                        tile.maxTime = maxTime;

                        tile.lightLevel = 0;
                        tile.lightOpacity = 0;
                    } else {
                        FLNetwork.sendToAllWatchingChunk(
                            srcWorld,
                            srcPos.x,
                            srcPos.y,
                            srcPos.z,
                            new MessageClearTile(srcPos));
                        Entry e = dstTileEntries.get(link.dstPos);
                        tile.block = (NBTTagCompound) airBlockTag.copy();
                        if (e.description != null) tile.desc = (NBTTagCompound) e.description.copy();
                        else tile.desc = (NBTTagCompound) airDescTag.copy();
                        tile.dir = 7;
                        tile.maxTime = maxTime;

                        tile.lightLevel = e.lightlevel;
                        tile.lightOpacity = e.lightopacity;

                        if (e.bb != null) tile.collisions = e.bb.toArray(new AxisAlignedBB[0]);

                    }
                    tile.isAir = true;

                    tiles.add(tile);
                }
            }
            vars.put("Iterator", BLANK);

            section = "networkUpdateBlocks";
            for (TileMovingServer tile : tiles) {
                vars.put("Iterator", tile);
                PlayerManager.PlayerInstance watcher = FLNetwork
                    .getChunkWatcher(tile.getWorldObj(), tile.xCoord, tile.zCoord);
                if (watcher != null) {
                    S23PacketBlockChange pkt = new S23PacketBlockChange(
                        tile.xCoord,
                        tile.yCoord,
                        tile.zCoord,
                        tile.getWorldObj());
                    watcher.sendToAllPlayersWatchingChunk(pkt);
                }
            }
            vars.put("Iterator", BLANK);

            section = "networkUpdateTile";
            for (TileMovingServer tile : tiles) {
                vars.put("Iterator", tile);
                PlayerManager.PlayerInstance watcher = FLNetwork
                    .getChunkWatcher(tile.getWorldObj(), tile.xCoord, tile.zCoord);
                if (watcher != null) {
                    Packet packet = tile.getDescriptionPacket();
                    vars.put("Iterator2", packet);
                    if (packet != null) watcher.sendToAllPlayersWatchingChunk(packet);
                }
            }
            vars.put("Iterator", BLANK);
            vars.put("Iterator2", BLANK);

            section = "DoneIfWeCrashNowThenWhatTheHell";

            clearVars();
        } catch (Throwable err) {
            try {
                CrashReport crashReport = buildCrashReport(section, err);
                throw new ReportedException(crashReport);
            } finally {
                clearVars();
            }
        }
    }

    private static CrashReport buildCrashReport(String section, Throwable err) {
        CrashReport crashReport = CrashReport.makeCrashReport(err, "FunkyLocomotionMoveCrash");
        CrashReportCategory moveCode = crashReport.makeCategory("MoveCode");

        moveCode.addCrashSection("Section", "\"" + section + "\"");

        for (Map.Entry<String, Object> e : vars.entrySet()) {
            Object value = e.getValue();
            if (value != BLANK) moveCode.addCrashSection("var_" + e.getKey(), makeString(value, 0));
        }
        return crashReport;
    }

    private static final Object BLANK = new Object();

    private static void clearVars() {
        for (Map.Entry<String, Object> entry : vars.entrySet()) {
            entry.setValue(BLANK);
        }
    }

    public static void finishMoving() {
        clearVars();
        String section = "start";

        List<TileMovingServer> tiles = MovingTileRegistry.getTilesFinishedMoving();
        Set<TileMovingServer> failed = new HashSet<TileMovingServer>();
        Map<BlockPos, Integer> oldOpacities = new HashMap<BlockPos, Integer>();

        for (TileMovingServer tile : tiles) tile.finishing = true;

        try {
            vars.put("tiles", tiles);

            // Clear Tiles. Do not use setBlock(air) + setBlock(stone) here:
            // that ran the full block-change/light path twice per tile and left a
            // stone block in the world if the final recreation failed.
            section = "Clear Tiles";
            for (TileMovingServer tile : tiles) {
                if (tile.isInvalid()) continue;
                vars.put("tile", tile);
                try {
                    World world = tile.getWorldObj();
                    if (world == null) {
                        markFailed(tile, new IllegalStateException("tile has no world"), failed);
                        continue;
                    }

                    BlockPos pos = new BlockPos(tile);
                    Chunk chunk = BlockHelper.getChunk(world, pos);
                    if (chunk == null) {
                        markFailed(tile, new IllegalStateException("chunk is not loaded"), failed);
                        continue;
                    }

                    oldOpacities.put(
                        pos,
                        BlockHelper.getBlock(world, pos)
                            .getLightOpacity(world, pos.x, pos.y, pos.z));
                    world.removeTileEntity(pos.x, pos.y, pos.z);
                    BlockHelper.silentClear(chunk, pos);
                } catch (Throwable t) {
                    markFailed(tile, t, failed);
                }
            }
            vars.put("tile", BLANK);

            // Set Block/Tile
            section = "Set Block/Tile";
            for (TileMovingServer tile : tiles) {
                if (failed.contains(tile)) continue;
                vars.put("tile", tile);
                try {
                    World world = tile.getWorldObj();
                    BlockPos pos = new BlockPos(tile);
                    if (tile.block != null && tile.block.hasKey("Block")) {
                        Block block = Block.getBlockFromName(tile.block.getString("Block"));
                        vars.put("block", block);
                        if (block == null) block = Blocks.air;
                        IMoveFactory factory = FactoryRegistry.getFactory(block);
                        vars.put("factory", factory);
                        factory.recreateBlock(world, pos, tile.block);
                    }
                } catch (Throwable t) {
                    markFailed(tile, t, failed);
                }
            }
            vars.put("tile", BLANK);
            vars.put("block", BLANK);
            vars.put("factory", BLANK);

            // Update Blocks
            section = "Update Blocks";
            for (TileMovingServer tile : tiles) {
                if (failed.contains(tile)) continue;
                vars.put("tile", tile);
                try {
                    World world = tile.getWorldObj();
                    BlockPos pos = new BlockPos(tile);
                    Integer oldOpacity = oldOpacities.get(pos);
                    BlockHelper.postUpdateBlock(world, pos, oldOpacity != null ? oldOpacity : -1);
                    if (tile.scheduledTickTime != -1) world.scheduleBlockUpdateWithPriority(
                        tile.xCoord,
                        tile.yCoord,
                        tile.zCoord,
                        BlockHelper.getBlock(world, pos),
                        tile.scheduledTickTime - tile.maxTime,
                        tile.scheduledTickPriority);
                } catch (Throwable t) {
                    markFailed(tile, t, failed);
                }
            }
            vars.put("tile", BLANK);

            // Redo cached Activation
            section = "Redo Activation";
            for (TileMovingServer tile : tiles) {
                if (failed.contains(tile)) continue;
                vars.put("tile", tile);
                try {
                    if (tile.activatingPlayer != null) {
                        EntityPlayer player = tile.activatingPlayer.get();
                        if (player != null && player.worldObj == tile.getWorldObj()) {
                            Block b = BlockHelper.getBlock(tile.getWorldObj(), new BlockPos(tile));
                            b.onBlockActivated(
                                tile.getWorldObj(),
                                tile.xCoord,
                                tile.yCoord,
                                tile.zCoord,
                                player,
                                tile.activatingSide,
                                tile.activatingHitX,
                                tile.activatingHitY,
                                tile.activatingHitZ);
                        }
                    }
                } catch (Throwable t) {
                    markFailed(tile, t, failed);
                }
            }
            vars.put("tile", BLANK);

            section = "Fin";
        } catch (Throwable err) {
            try {
                CrashReport crashReport = buildCrashReport(section, err);
                throw new ReportedException(crashReport);
            } finally {
                clearVars();
            }
        } finally {
            for (TileMovingServer tile : tiles) {
                if (!tile.isInvalid()) tile.finishing = false;
            }
            clearVars();
        }
    }

    private static void markFailed(TileMovingServer tile, Throwable t, Set<TileMovingServer> failed) {
        failed.add(tile);
        LogHelper.errorThrowable(
            "Unable to finish moving tile at (" + tile.xCoord + "," + tile.yCoord + "," + tile.zCoord + ")",
            t);
        tile.invalidate();
        MovingTileRegistry.deregister(tile);
    }

    private static String makeString(Object o, int n) {
        if (o == null) return "null";
        if (o instanceof String) return (String) o;

        StringBuilder builder = new StringBuilder();
        tabs(n, builder);
        builder.append(
            o.getClass()
                .getSimpleName());
        builder.append("{");
        if (o instanceof Block) {
            String nameForObject = "" + Block.blockRegistry.getNameForObject(o);
            builder.append(nameForObject);
            builder.append(",");
            builder.append(o);
        } else if (o instanceof TileEntity) {
            try {
                builder.append("tag=");
                NBTTagCompound tag = new NBTTagCompound();
                ((TileEntity) o).writeToNBT(tag);
                builder.append(tag);
            } catch (Exception err) {
                builder.append("TE WriteToNBT Crash\n");
                builder.append(err);
            }
        } else if (o instanceof Collection) {
            int i = 0;
            Iterator iterator = ((Iterable) o).iterator();
            builder.append('\n');
            tabs(n, builder);
            while (iterator.hasNext()) {
                builder.append("\t\t")
                    .append(i)
                    .append("=");
                i++;
                builder.append(makeString(iterator.next(), n + 1));
                builder.append("\n");
                tabs(n, builder);
            }
        } else {
            builder.append(o);
        }
        builder.append("}");
        return builder.toString();
    }

    private static void tabs(int n, StringBuilder builder) {
        for (int j = 0; j < n; j++) {
            builder.append('\t');
        }
    }

    private static class Entry {

        public int scheduledTickTime = -1;
        public int scheduledTickPriority;
        NBTTagCompound blockTag;
        NBTTagCompound description;
        final BlockPos pos;
        Block block;
        int meta;
        List<AxisAlignedBB> bb = null;
        int lightlevel;
        int lightopacity;

        public Entry(BlockPos pos) {
            this.pos = pos;
        }

        @Override
        public String toString() {
            return "Entry{" + "scheduledTickTime="
                + scheduledTickTime
                + ", scheduledTickPriority="
                + scheduledTickPriority
                + ", blockTag="
                + blockTag
                + ", description="
                + description
                + ", pos="
                + pos
                + ", block="
                + makeString(block, 0)
                + ", meta="
                + meta
                + ", bb="
                + makeString(bb, 0)
                + ", lightlevel="
                + lightlevel
                + ", lightopacity="
                + lightopacity
                + '}';
        }
    }

    public static class BlockLink {

        BlockPos srcPos;
        BlockPos dstPos;

        public BlockLink(BlockPos srcPos, BlockPos dstPos) {
            this.srcPos = srcPos;
            this.dstPos = dstPos;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            BlockLink blockLink = (BlockLink) o;

            if (!dstPos.equals(blockLink.dstPos)) return false;
            return srcPos.equals(blockLink.srcPos);
        }

        @Override
        public int hashCode() {
            int result = srcPos.hashCode();
            result = 31 * result + dstPos.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "BlockLink{" + srcPos.toString() + ", " + dstPos.toString() + '}';
        }
    }

}
