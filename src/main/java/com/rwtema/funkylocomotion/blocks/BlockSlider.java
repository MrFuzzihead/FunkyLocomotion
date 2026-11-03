package com.rwtema.funkylocomotion.blocks;

import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Facing;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.rwtema.funkylocomotion.FunkyLocomotion;
import com.rwtema.funkylocomotion.helper.ItemHelper;

public class BlockSlider extends BlockPusher {

    public static IIcon iconSlider0;
    public static IIcon iconSlider1;
    public static IIcon iconSlider;
    public static IIcon iconSliderPush;
    public static IIcon iconSliderFront;

    /**
     * The side currently being rendered by {@link com.rwtema.funkylocomotion.rendering.RenderBlockSlider}.
     * This used to be a plain static field, which is unsafe under Angelica because
     * block sections are meshed on multiple worker threads. Keep it thread-local so
     * each meshing task sees only its own render pass.
     */
    private static final ThreadLocal<Integer> RENDER_SIDE = new ThreadLocal<Integer>() {

        @Override
        protected Integer initialValue() {
            return -1;
        }
    };

    public static int getRenderSide() {
        return RENDER_SIDE.get();
    }

    public static void setRenderSide(int side) {
        RENDER_SIDE.set(side);
    }

    static boolean isRenderSideVisible(int renderSide, int side) {
        if (renderSide == 6) return false;
        if (renderSide < 0 || renderSide > 5) return true;
        return side == renderSide || side == Facing.oppositeSide[renderSide];
    }

    public BlockSlider() {
        super();
        this.setBlockName("funkylocomotion:slider");
    }

    @Override
    public void registerBlockIcons(IIconRegister register) {
        iconSlider = register.registerIcon("funkylocomotion:sliderSide");
        iconSliderPush = register.registerIcon("funkylocomotion:sliderSideFront");
        iconSlider0 = register.registerIcon("funkylocomotion:sliderSide0");
        iconSlider1 = register.registerIcon("funkylocomotion:sliderSide1");
        iconSliderFront = register.registerIcon("funkylocomotion:sliderFront");
        super.registerBlockIcons(register);
    }

    @Override
    public int damageDropped(int meta) {
        return 0;
    }

    @Override
    public boolean shouldSideBeRendered(IBlockAccess world, int x, int y, int z, int side) {
        return isRenderSideVisible(getRenderSide(), side) && super.shouldSideBeRendered(world, x, y, z, side);
    }

    @Override
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (tile != null && tile.getClass() == TileSlider.class) {
            ForgeDirection slideDir = ((TileSlider) tile).getSlideDir();

            if (slideDir.ordinal() == side) return iconSliderPush;
            else if (slideDir.getOpposite()
                .ordinal() == side) return iconSlider;
        }

        return super.getIcon(world, x, y, z, side);

    }

    @SuppressWarnings("unchecked")
    @Override
    public void getSubBlocks(Item item, CreativeTabs tab, List list) {
        list.add(new ItemStack(item, 1, 0));
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (!world.isRemote) {
            ItemStack item = player.getHeldItem();
            if (!(ItemHelper.isWrench(item))) return false;

            final int meta = world.getBlockMetadata(x, y, z);
            TileEntity tile = world.getTileEntity(x, y, z);
            if (player.isSneaking()) {
                if (tile != null && tile.getClass() == TileSlider.class) {
                    ((TileSlider) tile).rotateAboutAxis();
                    world.markBlockForUpdate(x, y, z);
                }
            } else {

                world.setBlockMetadataWithNotify(x, y, z, toggleFacing(meta, side), 3);

                if (tile != null && tile.getClass() == TileSlider.class) {
                    TileSlider slider = (TileSlider) tile;
                    slider.setSlideDir(ForgeDirection.UNKNOWN);
                    slider.getSlideDir();
                    world.markBlockForUpdate(x, y, z);
                }
            }

        }
        return true;
    }

    static int toggleFacing(int meta, int side) {
        if (side == meta % 6) side = Facing.oppositeSide[side];
        return (meta < 6 ? 0 : 6) + side;
    }

    @Override
    public IIcon getIcon(int side, int meta) {
        int dir = Facing.oppositeSide[meta % 6];
        return (side == dir) ? iconSliderFront : ((side == Facing.oppositeSide[dir]) ? blockIcon : iconSlider0);
    }

    @Override
    public int getRenderType() {
        return FunkyLocomotion.proxy.sliderRendererId;
    }

    @Override
    public TileEntity createTileEntity(World world, int metadata) {
        return new TileSlider();
    }

}
