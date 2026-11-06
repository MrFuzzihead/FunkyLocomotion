package com.rwtema.funkylocomotion.rendering;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MoverRenderResultTest {

    @Test
    public void aFailedRenderIsRetried() {
        assertEquals(MoverRenderResult.RETRY, MoverRenderResult.classify(false, false, 0));
        assertEquals(MoverRenderResult.RETRY, MoverRenderResult.classify(false, false, 12));
    }

    @Test
    public void anExceptionDisablesThePassInsteadOfRetryingForever() {
        assertEquals(MoverRenderResult.FAILED, MoverRenderResult.classify(true, false, 0));
        assertEquals(MoverRenderResult.FAILED, MoverRenderResult.classify(true, true, 12));
    }

    @Test
    public void aSuccessfulEmptyRenderIsSkipped() {
        assertEquals(MoverRenderResult.SKIP, MoverRenderResult.classify(false, true, 0));
    }

    @Test
    public void aSuccessfulRenderWithGeometryIsCached() {
        assertEquals(MoverRenderResult.CACHE, MoverRenderResult.classify(false, true, 1));
        assertEquals(MoverRenderResult.CACHE, MoverRenderResult.classify(false, true, 4096));
    }
}
