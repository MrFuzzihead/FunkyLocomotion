package com.rwtema.funkylocomotion.fakes;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.world.WorldEvent;

import com.rwtema.funkylocomotion.blocks.BlockFrame;
import com.rwtema.funkylocomotion.blocks.TileMovingClient;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class FakeWorldClient extends WorldClient {

    private static final Map<World, FakeWorldClient> cache = new ConcurrentHashMap<World, FakeWorldClient>();
    public double offset = 0;
    public ForgeDirection dir = ForgeDirection.UNKNOWN;
    /**
     * When set, non-mover positions along the move axis are reported as air.
     * Used while building a mover's static geometry: the mover is translated
     * between its source and destination, so a face that will eventually touch
     * a static block is still visible during the animation. Faces perpendicular
     * to the move axis keep their static neighbour, because their adjacency does
     * not change. Movers always report their moved block, so culling between
     * blocks of the moving structure stays exact.
     */
    public boolean ignoreStaticNeighbours = false;
    public int renderX, renderY, renderZ;
    public ForgeDirection movingDir = ForgeDirection.UNKNOWN;
    /**
     * Block currently being drawn by the mover renderer. When it is a
     * transparent cube (glass/ice), a neighbouring frame is reported as this
     * block so the glass's own side check hides the coplanar glass face instead
     * of z-fighting with the frame face.
     */
    public Block renderBlock = null;
    public int renderMeta = 0;
    final World world;
    final WorldClient worldClient;

    private FakeWorldClient(World world) {
        super(
            new NetHandlerPlayClient(Minecraft.getMinecraft(), null, new NetworkManager(true)),
            new WorldSettings(world.getWorldInfo()),
            world.provider.dimensionId,
            world.difficultySetting,
            world.theProfiler);
        this.world = world;
        this.worldClient = world instanceof WorldClient ? ((WorldClient) world) : null;
    }

    public static boolean isValid(World world) {
        return world != null && world.provider != null;
    }

    public static FakeWorldClient getFakeWorldWrapper(World world) {
        FakeWorldClient fakeWorldClient = cache.get(world);
        if (fakeWorldClient == null) {
            // Construction fires WorldEvent.Load and allocates a WorldClient, so
            // avoid creating duplicate wrappers when the first render and a packet
            // arrive concurrently. This should still only be called from the
            // client/render thread, but the cache itself must not corrupt.
            synchronized (cache) {
                fakeWorldClient = cache.get(world);
                if (fakeWorldClient == null) {
                    fakeWorldClient = new FakeWorldClient(world);
                    cache.put(world, fakeWorldClient);
                }
            }
        }

        return fakeWorldClient;
    }

    public static void clear() {
        cache.clear();
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new FakeWorldManager());
    }

    @Override
    public boolean isBlockNormalCubeDefault(int x, int y, int z, boolean defaultValue) {
        Block block = this.getBlock(x, y, z);
        return block.isNormalCube(this, x, y, z);
    }

    @Override
    protected boolean chunkExists(int x, int z) {
        return world.blockExists(x << 4, 100, z << 4);
    }

    @Override
    public Chunk getChunkFromChunkCoords(int x, int z) {
        return world.getChunkFromChunkCoords(x, z);
    }

    @Override
    protected IChunkProvider createChunkProvider() {
        return null;
    }

    @Override
    protected int func_152379_p() {
        return Minecraft.getMinecraft().gameSettings.renderDistanceChunks;
    }

    @Override
    public Entity getEntityByID(int id) {
        return world.getEntityByID(id);
    }

    public TileMovingClient getTile(int x, int y, int z) {
        TileEntity tile = world.getTileEntity(x, y, z);
        return (tile != null && tile.getClass() == TileMovingClient.class) ? (TileMovingClient) tile : null;
    }

    @Override
    public Block getBlock(int x, int y, int z) {
        TileMovingClient tile = getTile(x, y, z);
        boolean mover = tile != null;
        Block block;

        if (mover) {
            block = tile.block;
        } else if (ignoreStaticNeighbours && differsAlongMoveAxis(x, y, z)) {
            return Blocks.air;
        } else {
            block = world.getBlock(x, y, z);
        }

        if (mover && block instanceof BlockFrame && isTransparentCube(renderBlock)) {
            // Two movers are both drawn with back-face culling disabled, so
            // coplanar frame/glass faces would z-fight from either side. Report
            // the frame as the glass block so the glass side check hides its
            // duplicate; the frame's face is drawn double-sided and stays.
            return renderBlock;
        }

        return block;
    }

    private static boolean isTransparentCube(Block block) {
        return block != null && (block.getMaterial() == Material.glass || block.getMaterial() == Material.ice);
    }

    private boolean differsAlongMoveAxis(int x, int y, int z) {
        ForgeDirection dir = movingDir;
        if (dir == null) return false;
        return (dir.offsetX != 0 && x != renderX) || (dir.offsetY != 0 && y != renderY)
            || (dir.offsetZ != 0 && z != renderZ);
    }

    @Override
    public TileEntity getTileEntity(int x, int y, int z) {
        TileMovingClient tile = getTile(x, y, z);
        return tile == null ? world.getTileEntity(x, y, z) : tile.tile;
    }

    @Override
    public boolean setBlock(int x, int y, int z, Block block, int meta, int flag) {
        return false;
    }

    @Override
    public boolean updateLightByType(EnumSkyBlock skyBlock, int x, int y, int z) {
        return false;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getLightBrightnessForSkyBlocks(int x, int y, int z, int minBrightness) {
        return world.getLightBrightnessForSkyBlocks(x, y, z, minBrightness);
    }

    @Override
    public int getBlockLightValue(int x, int y, int z) {
        return world.getBlockLightValue(x, y, z);
    }

    @Override
    public int getSavedLightValue(EnumSkyBlock type, int x, int y, int z) {
        return world.getSavedLightValue(type, x, y, z);
    }

    @Override
    public int getBlockMetadata(int x, int y, int z) {
        TileMovingClient tile = getTile(x, y, z);
        if (tile == null) return world.getBlockMetadata(x, y, z);

        // Keep the substituted frame neighbour's metadata in sync with its
        // substituted block, or vanilla glass's same-block metadata check would
        // decide the faces differ and render the coplanar glass face anyway.
        if (tile.block instanceof BlockFrame && isTransparentCube(renderBlock)) return renderMeta;

        return tile.meta;
    }

    @Override
    public int isBlockProvidingPowerTo(int x, int y, int z, int side) {
        return world.isBlockProvidingPowerTo(x, y, z, side);
    }

    @Override
    public boolean isAirBlock(int x, int y, int z) {
        TileMovingClient tile = getTile(x, y, z);
        return tile == null ? world.isAirBlock(x, y, z) : tile.block == Blocks.air;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public BiomeGenBase getBiomeGenForCoords(int x, int z) {
        return world.getBiomeGenForCoords(x, z);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getHeight() {
        return world.getHeight();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean extendedLevelsInChunkCache() {
        return world.extendedLevelsInChunkCache();
    }

    @Override
    public boolean isSideSolid(int x, int y, int z, ForgeDirection side, boolean _default) {
        TileMovingClient tile = getTile(x, y, z);
        return tile == null ? world.isSideSolid(x, y, z, side, _default) : tile.block.isSideSolid(this, x, y, z, side);
    }

    @Override
    public boolean spawnEntityInWorld(Entity entity) {
        return false;
    }

    @Override
    public void spawnParticle(String type, double x, double y, double z, double r, double g, double b) {
        world
            .spawnParticle(type, x + offset * dir.offsetX, y + offset * dir.offsetY, z + offset * dir.offsetZ, r, g, b);
    }

    @Override
    public void tick() {

    }

    @Override
    public CrashReportCategory addWorldInfoToCrashReport(CrashReport crash) {
        CrashReportCategory crashReportCategory = world.addWorldInfoToCrashReport(crash);
        crashReportCategory.addCrashSection("Fake World", "This world is a fake wrapper used by Funky Locomotion");
        return crashReportCategory;
    }

    @Override
    public void doVoidFogParticles(int x, int y, int z) {
        if (worldClient != null) worldClient.doVoidFogParticles(x, y, z);
    }

    @Override
    public void makeFireworks(double x, double y, double z, double vx, double vy, double vz, NBTTagCompound tag) {
        if (worldClient != null) worldClient.makeFireworks(x, y, z, vx, vy, vz, tag);
    }

    @Override
    public boolean func_147492_c(int x, int y, int z, Block block, int meta) {
        return false;
    }

    @Override
    public void removeEntity(Entity entity) {

    }

    @Override
    public void addEntityToWorld(int id, Entity entity) {

    }

    @Override
    public Entity removeEntityFromWorld(int entity) {
        return null;
    }

    @Override
    public void playSound(double x, double y, double z, String sound, float volume, float pitch, boolean positioned) {
        if (worldClient != null) worldClient.playSound(x, y, z, sound, volume, pitch, positioned);
    }

    @Override
    public void removeAllEntities() {

    }

    @Override
    public void doPreChunk(int x, int z, boolean load) {

    }

    @Override
    protected void func_147456_g() {

    }

    @Override
    public void sendQuittingDisconnectingPacket() {
        world.sendQuittingDisconnectingPacket();
    }

    @SideOnly(Side.CLIENT)
    public static class FakeWorldManager {

        @SubscribeEvent
        public void onDimensionUnload(WorldEvent.Unload event) {
            cache.remove(event.world);
            TileMovingClient.clearCachedTiles(event.world);

            MinecraftServer server = MinecraftServer.getServer();
            if (server != null && !server.isServerRunning()) {
                clear();
            }
        }

    }
}
