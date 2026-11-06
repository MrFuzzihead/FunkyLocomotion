package com.rwtema.funkylocomotion.rendering;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Client-side revision counter for cached mover geometry.
 *
 * <p>
 * A mover's static geometry is cached in a {@code TesselatorVertexState} and only
 * rebuilt when this revision changes. The geometry depends on more than the
 * mover's own block:
 *
 * <ul>
 * <li>the block and metadata themselves (a client tile entity can be reused by a
 * later move at the same position);</li>
 * <li>neighbouring movers, because {@code Block.shouldSideBeRendered} culls faces
 * against frames, glass of the same type and opaque blocks;</li>
 * <li>the resolved {@link net.minecraft.client.renderer.texture.TextureAtlasSprite}
 * UVs after a resource reload.</li>
 * </ul>
 *
 * <p>
 * Data packets arrive one at a time, so a mover can be rendered while only some
 * of its neighbours have their current block. Bumping this revision for every
 * mover data change makes each mover rebuild once more after the packet burst,
 * with the complete neighbour set. Without this, a cached frame/glass cage could
 * keep the faces culled for the previous arrangement (missing faces) and a reused
 * mover could keep rendering the previous block's model (for example a chest
 * drawn with the furnace geometry cached at that position by an earlier move).
 *
 * <p>
 * This class deliberately has no client or GL imports: it is bumped from tile
 * entity code that is also loaded on a dedicated server.
 */
public final class MoverRenderState {

    private static final AtomicInteger REVISION = new AtomicInteger();

    private MoverRenderState() {}

    public static int revision() {
        return REVISION.get();
    }

    public static void markDirty() {
        REVISION.incrementAndGet();
    }
}
