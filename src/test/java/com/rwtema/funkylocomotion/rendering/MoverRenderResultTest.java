package com.rwtema.funkylocomotion.rendering;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MoverRenderResultTest {

    @Test
    public void aFailedRenderIsRetried() {
        assertEquals(MoverRenderResult.RETRY, MoverRenderResult.classify(false, 0));
        assertEquals(MoverRenderResult.RETRY, MoverRenderResult.classify(false, 12));
    }

    @Test
    public void aSuccessfulEmptyRenderIsSkipped() {
        assertEquals(MoverRenderResult.SKIP, MoverRenderResult.classify(true, 0));
    }

    @Test
    public void aSuccessfulRenderWithGeometryIsCached() {
        assertEquals(MoverRenderResult.CACHE, MoverRenderResult.classify(true, 1));
        assertEquals(MoverRenderResult.CACHE, MoverRenderResult.classify(true, 4096));
    }
}
