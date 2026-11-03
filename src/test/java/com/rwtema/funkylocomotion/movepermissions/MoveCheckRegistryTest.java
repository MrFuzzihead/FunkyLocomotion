package com.rwtema.funkylocomotion.movepermissions;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

public class MoveCheckRegistryTest {

    @After
    public void clear() {
        MoveCheckRegistry.clear();
    }

    @Test
    public void registeredCheckerCanVetoAMove() {
        MoveCheckRegistry.register((world, x, y, z, block, meta, tile) -> true);

        assertTrue(MoveCheckRegistry.preventMovement(null, 0, 0, 0, null, 0, null));
    }

    @Test
    public void noCheckerMeansNoVeto() {
        assertFalse(MoveCheckRegistry.preventMovement(null, 0, 0, 0, null, 0, null));
    }
}
