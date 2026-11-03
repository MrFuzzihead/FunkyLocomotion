package com.rwtema.funkylocomotion;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.entity.Entity;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;

public class EntityMovingEventHandler {

    public static final Set<Entity> client = Collections.newSetFromMap(new WeakHashMap<Entity, Boolean>());
    public static final Set<Entity> server = Collections.newSetFromMap(new WeakHashMap<Entity, Boolean>());

    private EntityMovingEventHandler() {}

    public static void init() {
        FMLCommonHandler.instance()
            .bus()
            .register(new EntityMovingEventHandler());
    }

    public static Set<Entity> getMovementSet(Side side) {
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

        getMovementSet(event.side).clear();
    }
}
