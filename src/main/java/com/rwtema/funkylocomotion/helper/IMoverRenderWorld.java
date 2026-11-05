package com.rwtema.funkylocomotion.helper;

/**
 * Implemented by the fake world used while a mover's static geometry is built.
 *
 * <p>
 * Kept in common code (no client imports) so {@code BlockFrame} can query it
 * without a dedicated-server class-loading hazard.
 */
public interface IMoverRenderWorld {

    /**
     * True when the given position holds a static opaque block that the mover is
     * currently ignoring for face culling (because the mover has slid away from
     * it along the move axis). The block culls the face at rest, so the mover
     * should only keep the face if it is a solid (closed) frame side.
     */
    boolean isIgnoringOpaqueStatic(int x, int y, int z);
}
