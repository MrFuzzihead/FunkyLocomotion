package com.rwtema.funkylocomotion.rendering;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Compatibility-safe block update entry point for moving tile entities.
 *
 * <p>
 * The old implementation indexed {@link RenderGlobal#worldRenderers} and called
 * {@link net.minecraft.client.renderer.WorldRenderer#updateRenderer} directly.
 * Angelica's Celeritas renderer replaces the vanilla renderer array with a
 * 1x1x1 dummy grid (all coordinates collapse to index 0), so that path rebuilt
 * the wrong chunk and is exactly the kind of direct chunk manipulation Angelica
 * documents as unsafe. {@link RenderGlobal#markBlockForUpdate} is the supported
 * path: vanilla marks the chunk dirty, Celeritas schedules its own section
 * rebuild, and no renderer array is touched.
 */
public final class ChunkRerenderer {

    private ChunkRerenderer() {}

    @SideOnly(Side.CLIENT)
    public static void markBlock(int x, int y, int z) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.renderGlobal == null || mc.theWorld == null) return;

        mc.renderGlobal.markBlockForUpdate(x, y, z);
    }
}
