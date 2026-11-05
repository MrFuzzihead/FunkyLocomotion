package com.rwtema.funkylocomotion.rendering;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MoverRenderStateTest {

    @Test
    public void markDirtyAdvancesTheRevision() {
        int before = MoverRenderState.revision();
        MoverRenderState.markDirty();
        assertTrue("markDirty must invalidate cached mover geometry", MoverRenderState.revision() > before);
    }
}
