package framesapi;

import java.util.UUID;

import net.minecraft.world.World;

public interface IMovePermissions {

    public boolean canMove(World worldObj, int x, int y, int z, String username, UUID id);
}
