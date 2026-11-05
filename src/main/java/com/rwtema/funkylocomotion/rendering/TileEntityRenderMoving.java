package com.rwtema.funkylocomotion.rendering;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.shader.TesselatorVertexState;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Facing;
import net.minecraft.world.World;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.opengl.GL11;

import com.rwtema.funkylocomotion.blocks.BlockMoving;
import com.rwtema.funkylocomotion.blocks.TileMovingClient;
import com.rwtema.funkylocomotion.fakes.FakeWorldClient;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class TileEntityRenderMoving extends TileEntitySpecialRenderer {

    private RenderBlocks renderBlocks;
    private World world;
    private FakeWorldClient fakeWorldClient;

    @Override
    public void renderTileEntityAt(TileEntity tile, double x, double y, double z, float f) {
        if (!(tile instanceof TileMovingClient)) return;

        TileMovingClient mover = (TileMovingClient) tile;
        if (!mover.init) return;

        if (mover.maxTime == 0 || mover.block == Blocks.air) return;

        double h = mover.offset(true);

        int dir = mover.dir;
        if (dir == -1) {
            return;
        }

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            this.bindTexture(TextureMap.locationBlocksTexture);
            boolean flag = false;

            Tessellator tessellator = Tessellator.instance;

            int pass = MinecraftForgeClient.getRenderPass();
            if (mover.render && mover.block.canRenderInPass(pass)
                && mover.block.getRenderType() >= 0
                && (renderBlocks != null || createCache())) {
                GL11.glPushMatrix();

                setupTranslations(x, y, z, mover, h, dir);

                flag = renderStatic(mover, pass);

                GL11.glPopMatrix();
                GL11.glEnable(GL11.GL_CULL_FACE);
            }

            flag = flag | renderDynamic(x, y, z, f, mover, h, dir, pass);

            if (pass == 0) {
                mover.failedToRenderInFirstPass = !flag;
            } else if (!flag && mover.failedToRenderInFirstPass) {
                GL11.glPushMatrix();

                setupTranslations(x, y, z, mover, h, dir);

                RenderHelper.disableStandardItemLighting();
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GL11.glEnable(GL11.GL_BLEND);

                if (Minecraft.isAmbientOcclusionEnabled()) {
                    GL11.glShadeModel(GL11.GL_SMOOTH);
                } else {
                    GL11.glShadeModel(GL11.GL_FLAT);
                }

                tessellator.setColorOpaque_F(1.0F, 1.0F, 1.0F);
                tessellator.startDrawingQuads();
                renderBlocks.setOverrideBlockTexture(mover.error ? BlockMoving.crate_error : BlockMoving.crate);
                renderBlocks.renderBlockByRenderType(Blocks.stone, mover.xCoord, mover.yCoord, mover.zCoord);
                renderBlocks.clearOverrideBlockTexture();

                mover.cachedState[0] = leadVertexState();
                mover.skipPass[0] = false;
                mover.cachedState[1] = null;
                mover.skipPass[1] = true;

                tessellator.draw();
                GL11.glPopMatrix();

                RenderHelper.enableStandardItemLighting();
            }
        } finally {
            GL11.glPopAttrib();
        }
    }

    private void setupTranslations(double x, double y, double z, TileMovingClient mover, double h, int dir) {
        if (dir < 6) {
            GL11.glTranslated(x, y, z);
            GL11.glTranslated(-mover.xCoord, -mover.yCoord, -mover.zCoord);
            GL11.glTranslated(
                Facing.offsetsXForSide[dir] * h,
                Facing.offsetsYForSide[dir] * h,
                Facing.offsetsZForSide[dir] * h);
        } else {
            GL11.glTranslated(x, y, z);
            GL11.glTranslated(0.5, 0.5, 0.5);
            double dh = dir == 6 ? h + 1 : -h;
            GL11.glScaled(dh, dh, dh);
            GL11.glTranslated(-0.5, -0.5, -0.5);
            GL11.glTranslated(-mover.xCoord, -mover.yCoord, -mover.zCoord);
        }
    }

    protected boolean renderDynamic(double x, double y, double z, float f, TileMovingClient mover, double h, int dir,
        int pass) {
        if (!mover.render || mover.tile == null || !mover.tile.shouldRenderInPass(pass)) return false;

        if (fakeWorldClient == null && !createCache()) return false;

        TileEntitySpecialRenderer specialRenderer = TileEntityRendererDispatcher.instance
            .getSpecialRenderer(mover.tile);
        if (specialRenderer == null) return false;

        GL11.glPushMatrix();
        setupTranslations(x, y, z, mover, h, dir);
        try {
            specialRenderer.func_147496_a(fakeWorldClient);
            WorldClient prevWorld1 = Minecraft.getMinecraft().theWorld;
            World prevWorld2 = Minecraft.getMinecraft().thePlayer.worldObj;

            try {
                Minecraft.getMinecraft().theWorld = fakeWorldClient;
                Minecraft.getMinecraft().thePlayer.worldObj = fakeWorldClient;
                specialRenderer.renderTileEntityAt(mover.tile, mover.xCoord, mover.yCoord, mover.zCoord, f);
            } finally {
                Minecraft.getMinecraft().theWorld = prevWorld1;
                Minecraft.getMinecraft().thePlayer.worldObj = prevWorld2;
            }
            specialRenderer.func_147496_a(world);
        } catch (Exception | LinkageError e) {
            FLRenderHelper.clearTessellator();

            (new RuntimeException(
                "Unable to render TSER " + mover.tile.getClass()
                    .getName()
                    + " for "
                    + Block.blockRegistry.getNameForObject(mover.block)
                    + " with meta "
                    + mover.meta
                    + " at ("
                    + mover.xCoord
                    + ","
                    + mover.yCoord
                    + mover.zCoord
                    + "). Disabling Rendering.",
                e)).printStackTrace();

            TileMovingClient.renderErrorList.add(mover.tile.getClass());
            mover.error = true;
            mover.tile = null;
            mover.render = false;
        }

        GL11.glPopMatrix();
        return true;
    }

    /**
     * Drops cached geometry when anything it depends on changed. Block data,
     * neighbouring movers and icon UVs all contribute to the cached vertex
     * state, so a cache built for one arrangement must never be reused for
     * another.
     */
    static void refreshCachedGeometry(TileMovingClient mover) {
        int revision = MoverRenderState.revision();
        if (mover.renderCacheGeneration == revision) return;

        mover.cachedState[0] = null;
        mover.cachedState[1] = null;
        mover.skipPass[0] = false;
        mover.skipPass[1] = false;
        mover.failedToRenderInFirstPass = false;
        mover.renderCacheGeneration = revision;
    }

    protected boolean renderStatic(TileMovingClient mover, int pass) {
        refreshCachedGeometry(mover);

        if (mover.skipPass[pass]) return true;

        RenderHelper.disableStandardItemLighting();

        if (pass != 0) {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        }

        // Frame and glass are non-opaque. Culling their back faces makes the
        // structure look hollow when the camera crosses into or looks through
        // it. Opaque blocks keep normal back-face culling.
        if (mover.block.isOpaqueCube()) {
            GL11.glEnable(GL11.GL_CULL_FACE);
        } else {
            GL11.glDisable(GL11.GL_CULL_FACE);
        }

        // A mover can be flush with a static block (or with another mover of a
        // different type). Nudge the depth of the moving geometry so a coplanar
        // face cannot flicker against the other one. Non-opaque movers win the
        // tie (they are drawn from both sides); opaque movers lose it.
        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        if (mover.block.isOpaqueCube()) {
            GL11.glPolygonOffset(1.0F, 1.0F);
        } else {
            GL11.glPolygonOffset(-1.0F, -1.0F);
        }

        if (Minecraft.isAmbientOcclusionEnabled()) {
            GL11.glShadeModel(GL11.GL_SMOOTH);
        } else {
            GL11.glShadeModel(GL11.GL_FLAT);
        }

        Tessellator tessellator = Tessellator.instance;

        if (mover.cachedState[pass] != null) {
            tessellator.startDrawingQuads();
            tessellator.setVertexState(mover.cachedState[pass]);
            tessellator.draw();
        } else {

            tessellator.startDrawingQuads();
            tessellator.setColorOpaque_F(1.0F, 1.0F, 1.0F);

            mover.cachedState[pass] = null;
            mover.skipPass[pass] = true;

            boolean rendered = false;
            boolean failed = false;

            // While the mover is between its source and destination it is not
            // touching static neighbours along the move axis, so do not cull
            // those faces. Faces perpendicular to the move keep their static
            // neighbour, because their adjacency does not change.
            // Movers still report their moved block, so the structure's own
            // internal faces stay culled. Non-opaque movers also drop coplanar
            // frame/glass faces (back-face culling is off for them).
            boolean previousIgnore = fakeWorldClient.ignoreStaticNeighbours;
            int previousX = fakeWorldClient.renderX;
            int previousY = fakeWorldClient.renderY;
            int previousZ = fakeWorldClient.renderZ;
            ForgeDirection previousDir = fakeWorldClient.movingDir;
            Block previousRenderBlock = fakeWorldClient.renderBlock;
            int previousRenderMeta = fakeWorldClient.renderMeta;

            fakeWorldClient.renderX = mover.xCoord;
            fakeWorldClient.renderY = mover.yCoord;
            fakeWorldClient.renderZ = mover.zCoord;
            fakeWorldClient.movingDir = mover.dir >= 0 && mover.dir < 6 ? ForgeDirection.getOrientation(mover.dir)
                : ForgeDirection.UNKNOWN;
            fakeWorldClient.ignoreStaticNeighbours = mover.offset(false) != 0;
            fakeWorldClient.renderBlock = mover.block;
            fakeWorldClient.renderMeta = mover.meta;
            try {
                rendered = renderBlocks.renderBlockByRenderType(mover.block, mover.xCoord, mover.yCoord, mover.zCoord);
            } catch (Exception | LinkageError e) {
                failed = true;
                (new RuntimeException(
                    "Unable to render block " + Block.blockRegistry.getNameForObject(mover.block)
                        + " with meta "
                        + mover.meta
                        + " at ("
                        + mover.xCoord
                        + ","
                        + mover.yCoord
                        + ","
                        + mover.zCoord
                        + "). Disabling Rendering.",
                    e)).printStackTrace();

                TileMovingClient.renderErrorList.add(mover.block.getClass());
                mover.tile = null;
                mover.render = false;
                mover.error = true;

                mover.cachedState[pass] = null;
                mover.skipPass[pass] = true;
            } finally {
                fakeWorldClient.ignoreStaticNeighbours = previousIgnore;
                fakeWorldClient.renderX = previousX;
                fakeWorldClient.renderY = previousY;
                fakeWorldClient.renderZ = previousZ;
                fakeWorldClient.movingDir = previousDir;
                fakeWorldClient.renderBlock = previousRenderBlock;
                fakeWorldClient.renderMeta = previousRenderMeta;
            }

            MoverRenderResult result = MoverRenderResult.classify(failed, rendered, tessellator.rawBufferIndex);
            if (result == MoverRenderResult.CACHE) {
                mover.cachedState[pass] = leadVertexState();
                mover.skipPass[pass] = false;
            } else if (result == MoverRenderResult.FAILED || result == MoverRenderResult.SKIP) {
                mover.cachedState[pass] = null;
                mover.skipPass[pass] = true;
            } else {
                // The renderer was not ready (for example an ISBRH that returns
                // false until its data is available). Do not blacklist the pass.
                mover.cachedState[pass] = null;
                mover.skipPass[pass] = false;
            }

            tessellator.draw();
        }
        GL11.glPolygonOffset(0.0F, 0.0F);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        RenderHelper.enableStandardItemLighting();
        return true;
    }

    @SubscribeEvent
    public void onTextureStitch(TextureStitchEvent.Post event) {
        // Block icons/UVs changed; drop every mover's cached vertex state.
        MoverRenderState.markDirty();
    }

    private static TesselatorVertexState leadVertexState() {

        return Tessellator.instance.getVertexState(
            (float) Minecraft.getMinecraft().thePlayer.posX,
            (float) Minecraft.getMinecraft().thePlayer.posY,
            (float) Minecraft.getMinecraft().thePlayer.posZ);

    }

    public void func_147496_a(World world) {
        this.world = world;
        // Reset cache without calling createCache() here.
        // createCache() constructs a FakeWorldClient (extends WorldClient), which fires
        // WorldEvent.Load. If this method is called during TileEntityRendererDispatcher
        // func_147543_a's iteration of mapSpecialRenderers (world-change path), any
        // ClientRegistry.bindTileEntitySpecialRenderer call triggered by that event
        // would put() into mapSpecialRenderers mid-iteration, causing a
        // ConcurrentModificationException. The cache is instead created lazily on the
        // first render, outside of any mapSpecialRenderers iteration.
        fakeWorldClient = null;
        renderBlocks = null;
    }

    private boolean createCache() {
        if (!FakeWorldClient.isValid(world)) {
            fakeWorldClient = null;
            renderBlocks = null;
            return false;
        } else {
            this.fakeWorldClient = FakeWorldClient.getFakeWorldWrapper(this.world);
            this.renderBlocks = new RenderBlocks(fakeWorldClient);
            return true;
        }
    }
}
