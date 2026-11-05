package com.rwtema.funkylocomotion.network;

import java.lang.ref.WeakReference;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.rwtema.funkylocomotion.FunkyLocomotion;
import com.rwtema.funkylocomotion.api.BlockPos;
import com.rwtema.funkylocomotion.blocks.TileCacheKey;
import com.rwtema.funkylocomotion.blocks.TileMovingClient;
import com.rwtema.funkylocomotion.fakes.FakeWorldClient;
import com.rwtema.funkylocomotion.rendering.MoverRenderState;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

public class MessageClearTile implements IMessage {

    int x, y, z;

    public MessageClearTile() {

    }

    public MessageClearTile(BlockPos pos) {
        this(pos.x, pos.y, pos.z);
    }

    public MessageClearTile(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
    }

    @SideOnly(Side.CLIENT)
    public void handlePacket(MessageContext ctx) {
        World clientWorld = FunkyLocomotion.proxy.getClientWorld();

        Block b = clientWorld.getBlock(x, y, z);
        if (b == Blocks.air || b == FunkyLocomotion.moving) return;

        TileEntity tile = clientWorld.getTileEntity(x, y, z);

        if (tile == null) return;

        // Keep the tile so a moving tile entity can reuse it as its render source.
        // Cache before removal: removeTileEntity invalidates the tile and some mods
        // clear state in invalidate().
        if (FakeWorldClient.isValid(clientWorld)) {
            TileMovingClient.cachedTiles
                .put(new TileCacheKey(clientWorld, x, y, z), new WeakReference<TileEntity>(tile));
        }

        // Use the canonical removal path (correct ChunkPosition key, invalidate, and
        // light/neighbour notification) instead of poking the chunk map directly.
        // Also drop it from the loaded list immediately so it cannot be ticked as a
        // ghost tile before World.updateEntities gets a chance to purge it.
        clientWorld.loadedTileEntityList.remove(tile);
        clientWorld.removeTileEntity(x, y, z);

        // A nearby mover may have culled a face against this block.
        MoverRenderState.markDirty();
    }

    public static class Handler implements IMessageHandler<MessageClearTile, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(MessageClearTile message, MessageContext ctx) {
            message.handlePacket(ctx);
            return null;
        }
    }
}
