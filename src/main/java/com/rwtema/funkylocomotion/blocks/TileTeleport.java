package com.rwtema.funkylocomotion.blocks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.ForgeDirection;

import com.rwtema.funkylocomotion.api.BlockPos;
import com.rwtema.funkylocomotion.helper.BlockHelper;
import com.rwtema.funkylocomotion.helper.WeakSet;
import com.rwtema.funkylocomotion.movers.MoveManager;
import com.rwtema.funkylocomotion.movers.MoveSourceHelper;
import com.rwtema.funkylocomotion.particles.ObstructionHelper;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gnu.trove.map.hash.TLongObjectHashMap;

public class TileTeleport extends TilePusher {

    int teleportId;

    static TLongObjectHashMap<WeakSet<TileTeleport>> cache = new TLongObjectHashMap<WeakSet<TileTeleport>>();

    @Override
    public List<BlockPos> getBlocks(World world, BlockPos home, ForgeDirection dir, boolean push) {
        BlockPos advance = home.advance(dir);
        if (BlockHelper.canStick(world, advance, dir.getOpposite()))
            return getBlocks(world, home, advance, ForgeDirection.UNKNOWN);

        return null;
    }

    @Override
    public List<BlockPos> checkPositions(World srcWorld, ForgeDirection moveDir, ArrayList<BlockPos> posList,
        HashSet<BlockPos> posSet) {
        TileTeleport tile = getTileTeleport();
        if (tile == null) return null;

        World dstWorld = tile.worldObj;

        boolean fail = false;
        for (BlockPos pos : posList) {

            BlockPos adv = getDestinationPos(tile, pos);

            if ((dstWorld == srcWorld && posSet.contains(adv)) || !BlockHelper.canReplace(dstWorld, adv)) {
                if (!ObstructionHelper.sendObstructionPacket(srcWorld, pos, moveDir)) return null;
                fail = true;
            }
        }

        return fail ? null : posList;
    }

    private TileTeleport getTileTeleport() {
        if (teleportId == 0) return null;

        WeakSet<TileTeleport> tileTeleports = cache.get(teleportId);

        if (tileTeleports == null) return null; // should never happen

        for (Iterator<TileTeleport> iterator = tileTeleports.iterator(); iterator.hasNext();) {
            TileTeleport tile = iterator.next();

            if (tile.isInvalid()) {
                iterator.remove();
                continue;
            }

            if (tile == this || !tile.hasWorldObj()) continue;

            World world = tile.getWorldObj();

            if (world == null || world.isRemote || world.provider == null) {
                iterator.remove();
                continue;
            }

            if (DimensionManager.getWorld(world.provider.dimensionId) != world) {
                iterator.remove();
                continue;
            }

            if (!world.blockExists(tile.xCoord, tile.yCoord, tile.zCoord)) continue;

            return tile;
        }

        if (tileTeleports.isEmpty()) cache.remove(teleportId);
        return null;
    }

    static ForgeDirection getTeleportFacing(int meta) {
        return ForgeDirection.getOrientation(meta % 6)
            .getOpposite();
    }

    static BlockPos getDestinationPos(BlockPos srcHome, int srcMeta, BlockPos dstHome, int dstMeta, BlockPos pos) {
        BlockPos srcPos = srcHome.advance(getTeleportFacing(srcMeta));
        BlockPos dstPos = dstHome.advance(getTeleportFacing(dstMeta));

        return new BlockPos(pos.x - srcPos.x + dstPos.x, pos.y - srcPos.y + dstPos.y, pos.z - srcPos.z + dstPos.z);
    }

    private BlockPos getDestinationPos(TileTeleport tile, BlockPos pos) {
        return getDestinationPos(
            new BlockPos(this),
            getBlockMetadata(),
            new BlockPos(tile),
            tile.getBlockMetadata(),
            pos);
    }

    public void startMoving() {
        if (!canMoveByOwner()) return;

        TileTeleport tileTeleport = getTileTeleport();
        if (tileTeleport == null) return;

        int meta = getBlockMetadata();
        ForgeDirection dir = ForgeDirection.getOrientation(meta % 6)
            .getOpposite();
        boolean push = meta < 6;
        if (dir == ForgeDirection.UNKNOWN) return;

        BlockPos pos = new BlockPos(xCoord, yCoord, zCoord);
        List<BlockPos> posList = getBlocks(worldObj, pos, dir, push);
        if (posList == null) return;

        final int energy = MoveSourceHelper.energyCost(posList);
        if (!MoveSourceHelper.canPay(energy, this.energy, tileTeleport.energy)) return;

        List<TileBooster> boosters = MoveSourceHelper.findBoosters(worldObj, pos, dir, energy);
        if (boosters == null) return;

        MoveSourceHelper.drain(energy, boosters, this.energy, tileTeleport.energy);

        ArrayList<MoveManager.BlockLink> links = new ArrayList<MoveManager.BlockLink>(posList.size());
        for (BlockPos blockPos : posList) {
            links.add(new MoveManager.BlockLink(blockPos, getDestinationPos(tileTeleport, blockPos)));
        }

        MoveManager.startMoving(
            worldObj,
            tileTeleport.worldObj,
            links,
            ForgeDirection.UNKNOWN,
            MoveSourceHelper.moveTime(boosters.size()) * 2);
    }

    @Override
    public void invalidate() {
        super.invalidate();
        unCache();
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        unCache();
    }

    private void unCache() {
        if (teleportId != 0 && (worldObj == null || !worldObj.isRemote)) {
            WeakSet<TileTeleport> tileTeleports = cache.get(teleportId);
            if (tileTeleports != null) {
                tileTeleports.remove(this);
                if (tileTeleports.isEmpty()) cache.remove(teleportId);
            }
        }
    }

    @Override
    public void validate() {
        super.validate();
        if (teleportId != 0 && worldObj != null && !worldObj.isRemote) {

            WeakSet<TileTeleport> tileTeleports = cache.get(teleportId);
            if (tileTeleports == null) {
                tileTeleports = new WeakSet<TileTeleport>();
                cache.put(teleportId, tileTeleports);
            }

            tileTeleports.add(this);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        teleportId = tag.getInteger("ID");
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setInteger("ID", teleportId);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        teleportId = pkt.func_148857_g()
            .getInteger("ID");
        if (worldObj.blockExists(xCoord, yCoord, zCoord)) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("ID", teleportId);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, tag);
    }
}
