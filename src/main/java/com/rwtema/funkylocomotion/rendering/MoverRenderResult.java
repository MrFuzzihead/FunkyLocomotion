package com.rwtema.funkylocomotion.rendering;

/**
 * Decision for a cached mover render pass.
 *
 * <p>
 * A renderer returning {@code false} is treated as "not ready yet" and retried on
 * the next frame. Only a successful render with no emitted vertices is cached as
 * empty. Treating the first {@code false} as a permanent skip used to make blocks
 * disappear for the rest of the mover's lifetime.
 */
enum MoverRenderResult {

    CACHE,
    RETRY,
    SKIP;

    static MoverRenderResult classify(boolean rendered, int vertexCount) {
        if (!rendered) return RETRY;
        return vertexCount > 0 ? CACHE : SKIP;
    }
}
