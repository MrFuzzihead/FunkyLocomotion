package com.rwtema.funkylocomotion.compat;

import com.rwtema.funkylocomotion.LogHelper;

/**
 * Optional Angelica integration. Kept reflective so the mod still compiles and
 * runs without Angelica on the classpath.
 */
public final class AngelicaCompat {

    private static final String RENDER_BOUNDS_REGISTRY = "com.gtnewhorizons.angelica.rendering.TileEntityRenderBoundsRegistry";
    private static final String MOVING_TILE = "com.rwtema.funkylocomotion.blocks.TileMovingClient";

    private AngelicaCompat() {}

    public static void registerDynamicTileEntityBounds() {
        try {
            Class<?> registry = Class.forName(RENDER_BOUNDS_REGISTRY);
            registry.getMethod("registerDynamicClass", String.class)
                .invoke(null, MOVING_TILE);
        } catch (ClassNotFoundException ignored) {
            // Angelica is not installed.
        } catch (Throwable t) {
            LogHelper.errorThrowable("Unable to register Funky Locomotion mover bounds with Angelica", t);
        }
    }
}
