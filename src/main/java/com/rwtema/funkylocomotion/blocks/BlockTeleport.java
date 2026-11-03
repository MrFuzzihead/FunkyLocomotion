package com.rwtema.funkylocomotion.blocks;

import java.util.ArrayList;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.stats.StatList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Facing;
import net.minecraft.util.IIcon;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;

import com.rwtema.funkylocomotion.FunkyLocomotion;
import com.rwtema.funkylocomotion.items.ItemBlockTeleporter;
import com.rwtema.funkylocomotion.items.ItemWrench;

public class BlockTeleport extends BlockPusher {

    public static IIcon iconFront;
    public static IIcon iconSide;

    public BlockTeleport() {
        super();
        this.setBlockName("funkylocomotion:teleporter");
    }

    @Override
    public void registerBlockIcons(IIconRegister register) {
        iconFront = register.registerIcon("funkylocomotion:teleporterFront");
        iconSide = register.registerIcon("funkylocomotion:teleporterSide");
        super.registerBlockIcons(register);
    }

    @Override
    public IIcon getIcon(int side, int meta) {
        final int dir = Facing.oppositeSide[meta % 6];
        return side == dir ? iconFront : side == Facing.oppositeSide[dir] ? blockIcon : iconSide;
    }

    @Override
    public ItemStack getPickBlock(MovingObjectPosition target, World world, int x, int y, int z, EntityPlayer player) {
        ArrayList<ItemStack> drops = getDrops(world, x, y, z, 0, 0);
        return drops.isEmpty() ? null : drops.get(0);
    }

    @Override
    public int damageDropped(int meta) {
        return 0;
    }

    @Override
    public TileEntity createTileEntity(World world, int metadata) {
        return new TileTeleport();
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (world.isRemote) return true;

        ItemStack held = player.getHeldItem();
        if (held == null || held.getItem() != FunkyLocomotion.wrench
            || held.getItemDamage() != ItemWrench.metaWrenchEye) {
            return super.onBlockActivated(world, x, y, z, player, side, hitX, hitY, hitZ);
        }

        TileEntity tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof TileTeleport)) return super.onBlockActivated(world, x, y, z, player, side, hitX, hitY, hitZ);

        TileTeleport teleporter = (TileTeleport) tile;
        NBTTagCompound tag = held.getTagCompound();
        if (tag == null || !tag.hasKey(ItemWrench.NBT_TELEPORT_ID)) {
            int id = teleporter.teleportId;
            if (id == 0) {
                player.addChatComponentMessage(new ChatComponentTranslation("frame.teleport.no_id.0"));
                return true;
            }

            if (tag == null) tag = new NBTTagCompound();
            tag.setInteger(ItemWrench.NBT_TELEPORT_ID, id);
            held.setTagCompound(tag);
            player.addChatComponentMessage(new ChatComponentTranslation("frame.teleport.pair.pick", id));
            return true;
        }

        int id = tag.getInteger(ItemWrench.NBT_TELEPORT_ID);
        teleporter.setTeleportId(id);
        player.addChatComponentMessage(new ChatComponentTranslation("frame.teleport.pair.apply", id));
        return true;
    }

    @Override
    public boolean removedByPlayer(World world, EntityPlayer player, int x, int y, int z, boolean willHarvest) {
        return super.removedByPlayer(world, player, x, y, z, willHarvest);
    }

    @Override
    public void harvestBlock(World world, EntityPlayer player, int x, int y, int z, int meta) {
        if (world.isRemote) return;

        for (ItemStack item : getDrops(world, x, y, z, meta, 0)) {
            dropBlockAsItem(world, x, y, z, item);
        }

        int stat = getIdFromBlock(this);
        if (stat >= 0 && stat < StatList.mineBlockStatArray.length && StatList.mineBlockStatArray[stat] != null) {
            player.addStat(StatList.mineBlockStatArray[stat], 1);
        }
        player.addExhaustion(0.025F);
    }

    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int metadata, int fortune) {
        ArrayList<ItemStack> ret = new ArrayList<ItemStack>();
        ItemStack item = new ItemStack(this, 1, damageDropped(metadata));

        TileEntity tile = world.getTileEntity(x, y, z);
        if (tile instanceof TileTeleport) {
            int teleportId = ((TileTeleport) tile).teleportId;

            if (teleportId != 0) {
                NBTTagCompound tag = new NBTTagCompound();
                tag.setInteger(ItemBlockTeleporter.NBT_TELEPORTER_ID, teleportId);
                item.setTagCompound(tag);
            }
        }

        ret.add(item);
        return ret;
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase entity, ItemStack item) {
        super.onBlockPlacedBy(world, x, y, z, entity, item);
        if (item == null || !item.hasTagCompound()) return;

        TileEntity tile = world.getTileEntity(x, y, z);
        if (tile instanceof TileTeleport) {
            tile.invalidate();
            ((TileTeleport) tile).teleportId = item.getTagCompound()
                .getInteger(ItemBlockTeleporter.NBT_TELEPORTER_ID);
            tile.validate();
        }
    }
}
