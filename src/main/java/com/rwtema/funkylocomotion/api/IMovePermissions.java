package com.rwtema.funkylocomotion.api;

import java.util.UUID;

import net.minecraft.world.World;

public interface IMovePermissions {

    boolean canMove(World worldObj, int x, int y, int z, String username, UUID id);
}
