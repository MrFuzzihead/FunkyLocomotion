package com.rwtema.funkylocomotion.network;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.rwtema.funkylocomotion.FunkyLocomotion;
import com.rwtema.funkylocomotion.Proxy;

public class MessageClearTileHandlerTest {

    private Proxy previousProxy;

    @Before
    public void installClientWorldProxy() {
        previousProxy = FunkyLocomotion.proxy;
    }

    @After
    public void restoreProxy() {
        FunkyLocomotion.proxy = previousProxy;
    }

    @Test
    public void removesTheSourceTileThroughTheWorldApi() {
        World world = mock(World.class);
        Block block = mock(Block.class);
        TileEntity tile = mock(TileEntity.class);
        world.loadedTileEntityList = new ArrayList<TileEntity>();
        world.loadedTileEntityList.add(tile);

        when(world.getBlock(1, 64, -3)).thenReturn(block);
        when(world.getTileEntity(1, 64, -3)).thenReturn(tile);

        FunkyLocomotion.proxy = new Proxy() {

            @Override
            public World getClientWorld() {
                return world;
            }
        };

        new MessageClearTile(1, 64, -3).handlePacket(null);

        verify(world).removeTileEntity(1, 64, -3);
        assertTrue(world.loadedTileEntityList.isEmpty());
    }
}
