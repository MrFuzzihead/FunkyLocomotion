package com.rwtema.funkylocomotion.harness;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ForgeDirection;

import com.rwtema.funkylocomotion.FunkyLocomotion;
import com.rwtema.funkylocomotion.api.BlockPos;
import com.rwtema.funkylocomotion.blocks.TileMovingServer;
import com.rwtema.funkylocomotion.blocks.TilePusher;
import com.rwtema.funkylocomotion.blocks.TileTeleport;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Test-only dedicated-server harness. It is loaded only by
 * {@code ./gradlew runServer -PflHarness=true} (the harness source set is not
 * packaged into the mod jar).
 *
 * <p>
 * It exercises the parts that JUnit cannot reach: a real {@link WorldServer},
 * real chunks/tile entities, the move manager's tick lifecycle, pusher BFS,
 * teleporter puller metadata, and the client/server tile choice. A pass prints
 * {@code [FL-HARNESS] PASS} and shuts the server down; a failure prints
 * {@code [FL-HARNESS] FAIL} and exits non-zero.
 */
@Mod(modid = "flharness", name = "Funky Locomotion Harness", version = "1.0", acceptableRemoteVersions = "*")
public class FunkyLocomotionHarness {

    private static final String TAG = "[FL-HARNESS]";
    private static final int Y = 100;
    private static final int PUSH_X = 0;
    private static final int TELEPORT_SRC_X = 20;
    private static final int TELEPORT_DST_X = 30;

    private WorldServer world;
    private int ticks;
    private boolean pusherStarted;
    private boolean teleporterStarted;

    @Mod.EventHandler
    public void serverStarted(FMLServerStartedEvent event) {
        if (!Boolean.getBoolean("fl.harness")) return;

        world = MinecraftServer.getServer()
            .worldServerForDimension(0);
        check(world != null, "overworld did not load");

        FMLCommonHandler.instance()
            .bus()
            .register(this);

        checkMovingTileSide();
        setupPusherTest();
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        ticks++;
        if (ticks == 50) {
            verifyPusherTest();
            setupTeleporterTest();
        } else if (ticks == 120) {
            verifyTeleporterTest();
            checkPusherLimit();
            passAndShutdown();
        }
    }

    private void checkMovingTileSide() {
        clearArea(-2, 2, -2, 2);
        check(world.setBlock(0, Y, 2, FunkyLocomotion.moving, 0, 3), "could not place the moving block");

        TileEntity tile = world.getTileEntity(0, Y, 2);
        check(
            tile instanceof TileMovingServer,
            "server world created " + (tile == null ? "no tile"
                : tile.getClass()
                    .getName())
                + " instead of TileMovingServer");

        world.setBlock(0, Y, 2, Blocks.air, 0, 2);
    }

    private void setupPusherTest() {
        clearArea(-2, 6, -2, 2);

        check(world.setBlock(PUSH_X, Y, 0, FunkyLocomotion.pusher, 4, 3), "could not place the pusher");
        for (int x = 1; x <= 3; x++) {
            check(world.setBlock(x, Y, 0, FunkyLocomotion.frame[0], 0, 3), "could not place frame at x=" + x);
        }

        TileEntity tile = world.getTileEntity(PUSH_X, Y, 0);
        check(tile instanceof TilePusher, "pusher tile entity is missing");
        TilePusher pusher = (TilePusher) tile;

        int cost = TilePusher.energyCost(3);
        check(
            pusher.energy.receiveEnergy(cost * 2, false) >= cost,
            "could not charge the pusher (needed " + cost + ")");

        pusher.startMoving();
        pusherStarted = true;
    }

    private void verifyPusherTest() {
        check(pusherStarted, "pusher move was not started");
        check(world.getBlock(PUSH_X, Y, 0) == FunkyLocomotion.pusher, "the pusher itself moved");
        check(world.isAirBlock(1, Y, 0), "source frame at x=1 is still present: " + describeBlock(1, Y, 0));
        for (int x = 2; x <= 4; x++) {
            check(isFrame(x, Y, 0), "expected a moved frame at x=" + x + " but found " + describeBlock(x, Y, 0));
        }
    }

    private void setupTeleporterTest() {
        clearArea(TELEPORT_SRC_X - 2, TELEPORT_DST_X + 2, -2, 2);

        // Metadata 6 is a puller teleporter. This used to call
        // BlockPos.advance(meta ^ 1) and throw ArrayIndexOutOfBoundsException.
        check(
            world.setBlock(TELEPORT_SRC_X, Y, 0, FunkyLocomotion.teleporter, 6, 3),
            "could not place the source teleporter");
        check(
            world.setBlock(TELEPORT_DST_X, Y, 0, FunkyLocomotion.teleporter, 6, 3),
            "could not place the destination teleporter");

        TileEntity src = world.getTileEntity(TELEPORT_SRC_X, Y, 0);
        TileEntity dst = world.getTileEntity(TELEPORT_DST_X, Y, 0);
        check(src instanceof TileTeleport, "source teleporter tile is missing");
        check(dst instanceof TileTeleport, "destination teleporter tile is missing");

        int id = 424242;
        configureTeleport((TileTeleport) src, TELEPORT_SRC_X, id);
        configureTeleport((TileTeleport) dst, TELEPORT_DST_X, id);

        int cost = TilePusher.energyCost(1);
        check(
            ((TileTeleport) src).energy.receiveEnergy(cost * 2, false) >= cost,
            "could not charge the source teleporter");
        check(
            ((TileTeleport) dst).energy.receiveEnergy(cost * 2, false) >= cost,
            "could not charge the destination teleporter");

        // Pullers look at the block on their facing side; metadata 6 faces up.
        check(
            world.setBlock(TELEPORT_SRC_X, Y + 1, 0, FunkyLocomotion.frame[0], 0, 3),
            "could not place the teleporter payload");

        ((TileTeleport) src).startMoving();
        teleporterStarted = true;
    }

    private void configureTeleport(TileTeleport teleporter, int x, int id) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("x", x);
        tag.setInteger("y", Y);
        tag.setInteger("z", 0);
        tag.setInteger("ID", id);
        teleporter.readFromNBT(tag);
        teleporter.validate();
    }

    private void verifyTeleporterTest() {
        check(teleporterStarted, "teleporter move was not started");
        check(isFrame(TELEPORT_DST_X, Y + 1, 0), "payload did not arrive at the destination");
        check(world.isAirBlock(TELEPORT_SRC_X, Y + 1, 0), "source payload is still present");
        check(world.getBlock(TELEPORT_DST_X, Y, 0) == FunkyLocomotion.teleporter, "destination teleporter was removed");
    }

    private void checkPusherLimit() {
        int previousLimit = TilePusher.maxTiles;
        try {
            TilePusher.maxTiles = 4;
            clearArea(38, 52, -2, 2);

            check(world.setBlock(40, Y, 0, FunkyLocomotion.pusher, 4, 3), "could not place the limit-test pusher");
            for (int x = 41; x <= 47; x++) {
                check(
                    world.setBlock(x, Y, 0, FunkyLocomotion.frame[0], 0, 3),
                    "could not place limit-test frame at x=" + x);
            }

            TileEntity tile = world.getTileEntity(40, Y, 0);
            check(tile instanceof TilePusher, "limit-test pusher tile is missing");
            TilePusher pusher = (TilePusher) tile;

            check(
                pusher.getBlocks(world, new BlockPos(pusher), ForgeDirection.EAST, true) == null,
                "pusher accepted a structure larger than maxTiles");
        } finally {
            TilePusher.maxTiles = previousLimit;
        }
    }

    private String describeBlock(int x, int y, int z) {
        Block block = world.getBlock(x, y, z);
        String name = block == null ? "null" : String.valueOf(Block.blockRegistry.getNameForObject(block));
        return name + " meta=" + world.getBlockMetadata(x, y, z);
    }

    private boolean isFrame(int x, int y, int z) {
        Block block = world.getBlock(x, y, z);
        return block == FunkyLocomotion.frame[0] || block == FunkyLocomotion.frame[1]
            || block == FunkyLocomotion.frame[2]
            || block == FunkyLocomotion.frame[3];
    }

    private void clearArea(int minX, int maxX, int minZ, int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = Y - 2; y <= Y + 3; y++) {
                    world.setBlock(x, y, z, Blocks.air, 0, 2);
                }
            }
        }
    }

    private void passAndShutdown() {
        System.out.println(TAG + " PASS");
        MinecraftServer.getServer()
            .initiateShutdown();
    }

    private static void check(boolean condition, String message) {
        if (condition) return;

        System.err.println(TAG + " FAIL: " + message);
        MinecraftServer server = MinecraftServer.getServer();
        if (server != null) server.initiateShutdown();
        System.exit(1);
    }
}
