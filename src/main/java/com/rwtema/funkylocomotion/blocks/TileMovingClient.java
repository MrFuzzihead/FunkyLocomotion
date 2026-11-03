package com.rwtema.funkylocomotion.blocks;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.shader.TesselatorVertexState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.rwtema.funkylocomotion.api.BlockPos;
import com.rwtema.funkylocomotion.api.IDescriptionProxy;
import com.rwtema.funkylocomotion.description.DescriptorRegistry;
import com.rwtema.funkylocomotion.fakes.FakeWorldClient;
import com.rwtema.funkylocomotion.rendering.ChunkRerenderer;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class TileMovingClient extends TileMovingBase {

    public static final Map<TileCacheKey, WeakReference<TileEntity>> cachedTiles = new ConcurrentHashMap<TileCacheKey, WeakReference<TileEntity>>();
    public static final HashSet<Class> renderBlackList = new HashSet<Class>();
    public static final HashSet<Class> renderErrorList = new HashSet<Class>();
    public Block block = Blocks.air;
    public int meta = 0;
    public TileEntity tile = null;
    public boolean render;
    public boolean error = false;
    public boolean rawTile = false;
    public boolean init = false;
    public boolean failedToRenderInFirstPass = false;

    public TileMovingClient() {
        super(Side.CLIENT);
    }

    @Override
    public void updateEntity() {
        if (time < maxTime) {
            super.updateEntity();
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (rawTile && tile != null) tile.invalidate();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {

        NBTTagCompound tag = pkt.func_148857_g();
        Block packetBlock = Block.getBlockById(tag.getInteger("Block"));
        if (packetBlock == null) {
            packetBlock = Blocks.air;
            error = true;
            render = false;
        }
        block = packetBlock;
        meta = tag.getInteger("Meta");

        time = tag.getInteger("Time");
        maxTime = tag.getInteger("MaxTime");

        lightLevel = tag.getInteger("Light");
        lightOpacity = tag.getShort("Opacity");

        isAir = block == Blocks.air;

        if (tag.hasKey("Collisions", 9)) {
            collisions = AxisTags(tag.getTagList("Collisions", 10));
        }

        // The client only needs a render update; postUpdateBlock's light
        // recalculation is for direct server-side writes and is very expensive
        // for every block in a large moving frame.
        if (worldObj != null && worldObj.blockExists(xCoord, yCoord, zCoord)) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }

        dir = tag.getByte("Dir");

        TileEntity tile = null;

        if (this.dir >= 0 && this.dir < 8 && this.dir != 6) {
            ForgeDirection d = ForgeDirection.getOrientation(this.dir);
            WeakReference<TileEntity> ref = cachedTiles
                .remove(new TileCacheKey(worldObj, xCoord - d.offsetX, yCoord - d.offsetY, zCoord - d.offsetZ));

            if (ref != null) tile = ref.get();
        }

        if (tile != null && FakeWorldClient.isValid(worldObj) && tile.getWorldObj() == this.worldObj) {
            rawTile = true;
            tile.xCoord = this.xCoord;
            tile.yCoord = this.yCoord;
            tile.zCoord = this.zCoord;
            tile.blockType = this.block;
            tile.blockMetadata = this.meta;

            tile.setWorldObj(FakeWorldClient.getFakeWorldWrapper(this.worldObj));
            // The tile was invalidated when it was removed from the source chunk.
            // Clear that flag now that it has been repurposed, otherwise TESRs and
            // mods that skip invalid tile entities will skip it too.
            tile.validate();
            this.tile = tile;
            render = true;
        } else {
            render = !tag.getBoolean("DNR");

            if (render) {
                IDescriptionProxy d = DescriptorRegistry.getDescriptor(tag.getString("DescID"));
                if (d != null)
                    this.tile = d.recreateTileEntity(net, tag, block, meta, new BlockPos(this), getWorldObj());
            }
        }

        if (checkClass(this.block) || checkClass(this.tile)) this.tile = null;

        // Pre-register the tile entity's special renderer outside the render loop to prevent
        // a ConcurrentModificationException caused by lazy class-hierarchy caching in
        // TileEntityRendererDispatcher.getSpecialRenderer modifying its mapSpecialRenderers
        // HashMap while it is being iterated during the normal tile entity render pass.
        if (this.tile != null) {
            TileEntityRendererDispatcher.instance.getSpecialRenderer(this.tile);
        }

        if (render && !init) ChunkRerenderer.markBlock(xCoord, yCoord, zCoord);

        init = true;
    }

    public boolean checkClass(Object o) {
        if (o == null) return false;

        Class<?> clazz = o.getClass();
        for (Class<?> blocked : renderBlackList) {
            if (blocked.isAssignableFrom(clazz)) {
                this.render = false;
                return true;
            }
        }

        for (Class<?> errored : renderErrorList) {
            if (errored.isAssignableFrom(clazz)) {
                this.render = false;
                this.error = true;
                return true;
            }
        }

        return false;
    }

    public static void clearCachedTiles(World world) {
        int dimension = world == null || world.provider == null ? Integer.MIN_VALUE : world.provider.dimensionId;
        cachedTiles.keySet()
            .removeIf(key -> key.dimension == dimension);
    }

    @SideOnly(Side.CLIENT)
    public AxisAlignedBB getRenderBoundingBox() {
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(xCoord, yCoord, zCoord, xCoord + 1, yCoord + 1, zCoord + 1);

        AxisAlignedBB other = null;
        if (tile != null) {
            try {
                other = tile.getRenderBoundingBox();
            } catch (Throwable ignored) {
                // A broken optional TESR should not break culling for every mover.
            }
        } else if (block != null && block != Blocks.air && worldObj != null) {
            try {
                // Use the real world; getRenderBoundingBox can be called on a
                // Celeritas worker thread, and constructing a FakeWorldClient there
                // fires WorldEvent.Load off the client thread.
                other = block.getCollisionBoundingBoxFromPool(worldObj, xCoord, yCoord, zCoord);
            } catch (Throwable ignored) {
                // Fall back to the full block box below.
            }
        }

        if (other != null) box = box.func_111270_a(other);

        if (maxTime <= 0) return box;

        double h = offset(true);
        if (dir == 6 || dir == 7) {
            // Teleport movers scale around the block center instead of translating.
            double scale = dir == 6 ? h + 1.0 : -h;
            if (scale < 0.0) scale = 0.0;
            double cx = (box.minX + box.maxX) * 0.5;
            double cy = (box.minY + box.maxY) * 0.5;
            double cz = (box.minZ + box.maxZ) * 0.5;
            return AxisAlignedBB.getBoundingBox(
                cx + (box.minX - cx) * scale,
                cy + (box.minY - cy) * scale,
                cz + (box.minZ - cz) * scale,
                cx + (box.maxX - cx) * scale,
                cy + (box.maxY - cy) * scale,
                cz + (box.maxZ - cz) * scale);
        }

        if (dir < 0 || dir >= 6) return box;

        ForgeDirection d = ForgeDirection.getOrientation(dir);
        return box.getOffsetBoundingBox(h * d.offsetX, h * d.offsetY, h * d.offsetZ);
    }

    @Override
    public boolean shouldRenderInPass(int pass) {
        if (maxTime == 0) return false;

        if (pass == 1 && failedToRenderInFirstPass) return true;

        if (!render || error) return pass == 0;

        if (block == Blocks.air || (tile == null && block.getRenderType() == -1)) return false;

        if (tile != null) {
            if (tile.shouldRenderInPass(pass)) return true;
        }

        return block.canRenderInPass(pass);
    }

    public final boolean[] skipPass = new boolean[2];
    public final TesselatorVertexState[] cachedState = new TesselatorVertexState[2];
}
