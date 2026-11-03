package com.rwtema.funkylocomotion.movepermissions;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.world.World;

import com.rwtema.funkylocomotion.api.IMovePermissions;

public final class MovePermissionRegistry {

    private static final List<IMovePermissions> permissions = new CopyOnWriteArrayList<IMovePermissions>();

    private MovePermissionRegistry() {}

    public static void register(IMovePermissions permission) {
        if (permission == null) throw new IllegalArgumentException("permission must not be null");
        permissions.add(permission);
    }

    public static boolean canMove(World world, int x, int y, int z, String username, UUID id) {
        for (IMovePermissions permission : permissions) {
            if (!permission.canMove(world, x, y, z, username, id)) return false;
        }
        return true;
    }

    static void clear() {
        permissions.clear();
    }
}
