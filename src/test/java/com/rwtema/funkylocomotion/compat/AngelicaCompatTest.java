package com.rwtema.funkylocomotion.compat;

import static org.junit.Assert.fail;

import org.junit.Test;

public class AngelicaCompatTest {

    @Test
    public void missingAngelicaIsNotFatal() {
        try {
            AngelicaCompat.registerDynamicTileEntityBounds();
        } catch (Throwable t) {
            fail("Angelica compatibility registration must fail silently when Angelica is absent: " + t);
        }
    }
}
