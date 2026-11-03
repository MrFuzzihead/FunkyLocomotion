package com.rwtema.funkylocomotion.movepermissions;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.UUID;

import org.junit.After;
import org.junit.Test;

public class MovePermissionRegistryTest {

    @After
    public void clear() {
        MovePermissionRegistry.clear();
    }

    @Test
    public void permissionsCanVetoAMove() {
        MovePermissionRegistry.register((world, x, y, z, username, id) -> false);

        assertFalse(MovePermissionRegistry.canMove(null, 0, 0, 0, "player", UUID.randomUUID()));
    }

    @Test
    public void noPermissionsMeansAllowed() {
        assertTrue(MovePermissionRegistry.canMove(null, 0, 0, 0, null, null));
    }
}
