package com.rwtema.funkylocomotion.movers;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.rwtema.funkylocomotion.FunkyLocomotion;
import com.rwtema.funkylocomotion.api.BlockPos;
import com.rwtema.funkylocomotion.blocks.TileBooster;
import com.rwtema.funkylocomotion.blocks.TilePusher;
import com.rwtema.funkylocomotion.helper.BlockHelper;

import cofh.api.energy.EnergyStorage;

public final class MoveSourceHelper {

    private MoveSourceHelper() {}

    public static int energyCost(List<BlockPos> positions) {
        return TilePusher.energyCost(positions == null ? 0 : positions.size());
    }

    public static boolean canPay(int energy, EnergyStorage... storages) {
        for (EnergyStorage storage : storages) {
            if (storage == null || storage.extractEnergy(energy, true) != energy) return false;
        }
        return true;
    }

    /**
     * @return the adjacent boosters, or {@code null} if one cannot pay the cost.
     */
    public static List<TileBooster> findBoosters(World world, BlockPos pos, ForgeDirection dir, int energy) {
        List<TileBooster> boosters = new ArrayList<TileBooster>(6);

        for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
            if (d == dir) continue;

            BlockPos p = pos.advance(d);
            if (BlockHelper.getBlock(world, p) != FunkyLocomotion.booster) continue;
            if (ForgeDirection.getOrientation(BlockHelper.getMeta(world, p) % 6) != d) continue;

            TileEntity tile = BlockHelper.getTile(world, p);
            if (!(tile instanceof TileBooster)) continue;

            TileBooster booster = (TileBooster) tile;
            if (booster.energy.extractEnergy(energy, true) != energy) return null;
            boosters.add(booster);
        }

        return boosters;
    }

    public static void drain(int energy, List<TileBooster> boosters, EnergyStorage... storages) {
        if (boosters != null) {
            for (TileBooster booster : boosters) {
                booster.energy.extractEnergy(energy, false);
            }
        }

        for (EnergyStorage storage : storages) {
            if (storage != null) storage.extractEnergy(energy, false);
        }
    }

    public static int moveTime(int boosterCount) {
        int index = Math.min(Math.max(0, boosterCount), TilePusher.moveTime.length - 1);
        return TilePusher.moveTime[index];
    }
}
