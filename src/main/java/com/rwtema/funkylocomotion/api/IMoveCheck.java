package com.rwtema.funkylocomotion.api;

import net.minecraft.world.World;

public interface IMoveCheck {

    boolean canMove(World worldObj, int x, int y, int z);
}
