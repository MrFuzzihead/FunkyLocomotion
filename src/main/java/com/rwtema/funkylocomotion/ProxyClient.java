package com.rwtema.funkylocomotion;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.world.World;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.common.MinecraftForge;

import com.rwtema.funkylocomotion.blocks.TileMovingClient;
import com.rwtema.funkylocomotion.compat.AngelicaCompat;
import com.rwtema.funkylocomotion.eventhandler.ClientTimer;
import com.rwtema.funkylocomotion.fakes.FakeWorldClient;
import com.rwtema.funkylocomotion.rendering.RenderBlockPusher;
import com.rwtema.funkylocomotion.rendering.RenderBlockSlider;
import com.rwtema.funkylocomotion.rendering.RenderItemWrench;
import com.rwtema.funkylocomotion.rendering.TileEntityRenderMoving;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ProxyClient extends Proxy {

    @Override
    public void registerRendering() {
        pusherRendererId = RenderingRegistry.getNextAvailableRenderId();
        sliderRendererId = RenderingRegistry.getNextAvailableRenderId();

        RenderingRegistry.registerBlockHandler(new RenderBlockPusher());
        RenderingRegistry.registerBlockHandler(new RenderBlockSlider());
        MinecraftForgeClient.registerItemRenderer(FunkyLocomotion.wrench, new RenderItemWrench());
        TileEntityRenderMoving moverRenderer = new TileEntityRenderMoving();
        ClientRegistry.bindTileEntitySpecialRenderer(TileMovingClient.class, moverRenderer);
        MinecraftForge.EVENT_BUS.register(moverRenderer);
        FMLCommonHandler.instance()
            .bus()
            .register(new ClientTimer());

        // Angelica classifies tile entity render bounds on worker threads during
        // chunk meshing. Register before any world is loaded so it never calls
        // TileMovingClient.getRenderBoundingBox() off the client thread.
        AngelicaCompat.registerDynamicTileEntityBounds();

        FakeWorldClient.register();
    }

    @Override
    public World getClientWorld() {
        return Minecraft.getMinecraft().theWorld;
    }

    @Override
    public void sendUsePacket(int x, int y, int z, int face, ItemStack item, float hitX, float hitY, float hitZ) {
        Minecraft.getMinecraft()
            .getNetHandler()
            .addToSendQueue(new C08PacketPlayerBlockPlacement(x, y, z, face, item, hitX, hitY, hitZ));
    }
}
