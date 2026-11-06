package com.rwtema.funkylocomotion.movepermissions;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MoveCheckReflectorTest {

    @Test
    public void immovableMarkerPreventsMovement() {
        assertFalse(MoveCheckReflector.canMoveClass(Immovable.class));
    }

    @Test
    public void movableClassIsAllowed() {
        assertTrue(MoveCheckReflector.canMoveClass(Movable.class));
    }

    @Test
    public void classWithoutMarkerIsAllowed() {
        assertTrue(MoveCheckReflector.canMoveClass(NoMarker.class));
    }

    public static class Immovable {

        public static boolean _Immovable() {
            return true;
        }
    }

    public static class Movable {

        public static boolean _Immovable() {
            return false;
        }
    }

    public static class NoMarker {
    }
}
