package com.rwtema.funkylocomotion.blocks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.rwtema.funkylocomotion.api.BlockPos;
import com.rwtema.funkylocomotion.api.IAdvStickyBlock;
import com.rwtema.funkylocomotion.api.IStickyBlock;
import com.rwtema.funkylocomotion.helper.BlockHelper;
import com.rwtema.funkylocomotion.movepermissions.MovePermissionRegistry;
import com.rwtema.funkylocomotion.movers.IMover;
import com.rwtema.funkylocomotion.movers.MoveManager;
import com.rwtema.funkylocomotion.movers.MoveSourceHelper;
import com.rwtema.funkylocomotion.particles.ObstructionHelper;
import com.rwtema.funkylocomotion.proxydelegates.ProxyRegistry;

import cofh.api.energy.EnergyStorage;
import cofh.api.energy.IEnergyReceiver;

public class TilePusher extends TileEntity implements IEnergyReceiver, IMover {

    public static final int MIN_MAX_TILES = 1;
    public static final int MAX_MAX_TILES = 8192;
    public static final int MIN_POWER_PER_TILE = 0;
    public static final int MAX_POWER_PER_TILE = 1000000;

    public static int maxTiles = 1024;
    public static int powerPerTile = 250;
    public final EnergyStorage energy = new EnergyStorage(energyCapacity(maxTiles, powerPerTile));
    public boolean powered;
    public String ownerName;
    public UUID ownerId;

    public static final int[] moveTime = new int[] { 20, 10, 7, 5, 4, 3 };

    @Override
    public boolean canUpdate() {
        return false;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        energy.readFromNBT(tag);
        // Older saves used "Powered"; current writes use "powered". Read both.
        powered = tag.getBoolean("powered") || tag.getBoolean("Powered");
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        energy.writeToNBT(tag);
        tag.setBoolean("powered", powered);
        if (ownerName != null) tag.setString("OwnerName", ownerName);
        if (ownerId != null) tag.setString("OwnerId", ownerId.toString());
    }

    @Override
    public boolean shouldRefresh(Block oldBlock, Block newBlock, int oldMeta, int newMeta, World world, int x, int y,
        int z) {
        return oldBlock != newBlock;
    }

    public List<BlockPos> getBlocks(World world, BlockPos home, ForgeDirection dir, boolean push) {
        BlockPos advance = home.advance(dir);
        if (push) {
            if (BlockHelper.canStick(world, advance, dir.getOpposite())) return getBlocks(world, home, advance, dir);
        } else {
            if (!world.isAirBlock(advance.x, advance.y, advance.z)) return null;

            advance = advance.advance(dir);
            if (BlockHelper.canStick(world, advance, dir.getOpposite()))
                return getBlocks(world, home, advance, dir.getOpposite());
        }

        return null;
    }

    public List<BlockPos> getBlocks(World world, BlockPos home, BlockPos start, ForgeDirection moveDir) {

        ArrayList<BlockPos> posList = new ArrayList<BlockPos>();
        HashSet<BlockPos> posSet = new HashSet<BlockPos>();
        if (!getBlockPosIterate(world, home, start, posList, posSet)) return null;

        return checkPositions(world, moveDir, posList, posSet);
    }

    public List<BlockPos> checkPositions(World world, ForgeDirection moveDir, ArrayList<BlockPos> posList,
        HashSet<BlockPos> posSet) {
        boolean fail = false;
        for (BlockPos pos : posList) {
            BlockPos adv = pos.advance(moveDir);
            if (!posSet.contains(adv) && !BlockHelper.canReplace(world, adv)) {
                if (!ObstructionHelper.sendObstructionPacket(world, pos, moveDir)) return null;
                fail = true;
            }
        }

        return fail ? null : posList;
    }

    private boolean getBlockPosIterate(World world, BlockPos home, BlockPos start, ArrayList<BlockPos> posList,
        HashSet<BlockPos> posSet) {
        LinkedList<BlockPos> toIterate = new LinkedList<BlockPos>();
        HashSet<BlockPos> toIterateSet = new HashSet<BlockPos>();

        toIterate.add(start);
        toIterateSet.add(start);

        // for (int i = 0; i < toIterate.size(); i++) {
        while (!toIterate.isEmpty()) {
            if (!hasCapacityFor(posList.size())) return false;

            BlockPos pos = toIterate.poll();

            posList.add(pos);
            posSet.add(pos);

            Block b = BlockHelper.getBlock(world, pos);

            IAdvStickyBlock advanced = ProxyRegistry.getInterface(b, IAdvStickyBlock.class);
            if (advanced != null) {
                List<BlockPos> additional = advanced.getBlocksToMove(world, pos);
                if (additional != null) {
                    for (BlockPos newPos : additional) {
                        if (newPos == null || home.equals(newPos) || toIterateSet.contains(newPos)) continue;
                        if (!BlockHelper.canMoveBlock(world, newPos)) continue;
                        toIterate.add(newPos);
                        toIterateSet.add(newPos);
                    }
                }
            }

            IStickyBlock stickyBlock = ProxyRegistry.getInterface(b, IStickyBlock.class);

            if (stickyBlock != null) {
                for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
                    if (stickyBlock.isStickySide(world, pos.x, pos.y, pos.z, side)) {
                        BlockPos newPos = pos.advance(side);

                        if (home.equals(newPos)) continue;

                        if (toIterateSet.contains(newPos)) continue;

                        if (BlockHelper.canStick(world, newPos, side.getOpposite())) {
                            toIterate.add(newPos);
                            toIterateSet.add(newPos);
                        }
                    }
                }
            }
        }
        return true;
    }

    static boolean hasCapacityFor(int currentCount) {
        return currentCount < maxTiles;
    }

    public static int energyCost(int blockCount) {
        long cost = (long) Math.max(0, blockCount) * powerPerTile;
        return cost > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) cost;
    }

    public static int energyCapacity(int maxTiles, int powerPerTile) {
        long capacity = (long) clampConfig(maxTiles, MIN_MAX_TILES, MAX_MAX_TILES)
            * clampConfig(powerPerTile, MIN_POWER_PER_TILE, MAX_POWER_PER_TILE);
        return (int) Math.min(capacity, Integer.MAX_VALUE);
    }

    public static int clampConfig(int value, int min, int max) {
        return value < min ? min : Math.min(value, max);
    }

    public void setOwner(EntityLivingBase entity) {
        if (!(entity instanceof EntityPlayer)) return;

        EntityPlayer player = (EntityPlayer) entity;
        ownerName = player.getCommandSenderName();
        ownerId = player.getGameProfile()
            .getId();
    }

    public boolean canMoveByOwner() {
        return MovePermissionRegistry.canMove(worldObj, xCoord, yCoord, zCoord, ownerName, ownerId);
    }

    public void startMoving() {
        if (!canMoveByOwner()) return;

        int meta = getBlockMetadata();
        ForgeDirection dir = ForgeDirection.getOrientation(meta % 6)
            .getOpposite();
        boolean push = meta < 6;
        if (dir == ForgeDirection.UNKNOWN) return;

        BlockPos pos = new BlockPos(xCoord, yCoord, zCoord);
        List<BlockPos> posList = getBlocks(worldObj, pos, dir, push);
        if (posList == null) return;

        final int energy = energyCost(posList.size());
        if (!MoveSourceHelper.canPay(energy, this.energy)) return;

        List<TileBooster> boosters = MoveSourceHelper.findBoosters(worldObj, pos, dir, energy);
        if (boosters == null) return;

        MoveSourceHelper.drain(energy, boosters, this.energy);
        MoveManager.startMoving(worldObj, posList, getDirection(), MoveSourceHelper.moveTime(boosters.size()));
    }

    @Override
    public boolean stillExists() {
        return !tileEntityInvalid && worldObj != null
            && worldObj.blockExists(xCoord, yCoord, zCoord)
            && worldObj.getTileEntity(xCoord, yCoord, zCoord) == this;
    }

    @Override
    public void onChunkUnload() {
        tileEntityInvalid = true;
    }

    public ForgeDirection getDirection() {
        int meta = getBlockMetadata();
        ForgeDirection dir = ForgeDirection.getOrientation(meta % 6)
            .getOpposite();
        boolean push = meta < 6;
        return push ? dir : dir.getOpposite();
    }

    @Override
    public int receiveEnergy(ForgeDirection from, int maxReceive, boolean simulate) {
        return energy.receiveEnergy(maxReceive, simulate);
    }

    @Override
    public int getEnergyStored(ForgeDirection from) {
        return energy.getEnergyStored();
    }

    @Override
    public int getMaxEnergyStored(ForgeDirection from) {
        return energy.getMaxEnergyStored();
    }

    @Override
    public boolean canConnectEnergy(ForgeDirection from) {
        return true;
    }
}
