package com.rwtema.funkylocomotion;

import java.util.WeakHashMap;

import net.minecraft.entity.Entity;
import net.minecraft.util.Vec3;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;

public class EntityMovingEventHandler {

    public static final WeakHashMap<Entity, Vec3> client = new WeakHashMap<Entity, Vec3>();
    public static final WeakHashMap<Entity, Vec3> server = new WeakHashMap<Entity, Vec3>();

    private EntityMovingEventHandler() {

    }

    public static void init() {
        FMLCommonHandler.instance()
            .bus()
            .register(new EntityMovingEventHandler());
    }

    public static WeakHashMap<Entity, Vec3> getMovementMap(Side side) {
        return side == Side.CLIENT ? client : server;
    }

    /**
     * Delegate to vanilla movement. The moving block supplies its transformed
     * collision boxes through BlockMoving.addCollisionBoxesToList, so vanilla
     * handles collision flags, onGround, fall state, webs and step sounds
     * consistently. The old copy omitted several of those side effects.
     */
    public static void moveEntity(Entity entity, double dx, double dy, double dz) {
        entity.moveEntity(dx, dy, dz);
    }

    @SubscribeEvent
    public void moveEntities(TickEvent.ServerTickEvent event) {
        move(event);
    }

    @SubscribeEvent
    public void moveEntities(TickEvent.ClientTickEvent event) {
        move(event);
    }

    public void move(TickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        getMovementMap(event.side)
            .clear();
    }

}
