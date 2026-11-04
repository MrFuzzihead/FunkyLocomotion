package com.rwtema.funkylocomotion.compat;

import com.rwtema.funkylocomotion.LogHelper;

import cpw.mods.fml.common.Loader;

/**
 * Optional Angelica integration. The Forge mod check avoids classloading the
 * optional registry when Angelica is absent; the call itself stays reflective
 * because the registry is an internal Angelica API that this mod does not
 * compile against.
 */
public final class AngelicaCompat {

    private static final String ANGELICA_MOD_ID = "angelica";
    private static final String RENDER_BOUNDS_REGISTRY = "com.gtnewhorizons.angelica.rendering.TileEntityRenderBoundsRegistry";
    private static final String MOVING_TILE = "com.rwtema.funkylocomotion.blocks.TileMovingClient";

    private AngelicaCompat() {}

    public static void registerDynamicTileEntityBounds() {
        if (!isAngelicaLoaded()) return;

        try {
            Class<?> registry = Class.forName(RENDER_BOUNDS_REGISTRY);
            registry.getMethod("registerDynamicClass", String.class)
                .invoke(null, MOVING_TILE);
        } catch (ClassNotFoundException ignored) {
            // Angelica is installed but this version does not provide the registry.
        } catch (Throwable t) {
            LogHelper.errorThrowable("Unable to register Funky Locomotion mover bounds with Angelica", t);
        }
    }

    private static boolean isAngelicaLoaded() {
        try {
            return Loader.isModLoaded(ANGELICA_MOD_ID);
        } catch (Throwable ignored) {
            // Plain class-loader environments (unit tests) do not have FML bootstrapped.
            return false;
        }
    }
}
