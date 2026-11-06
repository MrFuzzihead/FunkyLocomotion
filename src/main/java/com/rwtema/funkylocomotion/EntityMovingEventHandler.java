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
        moveEntity(entity, dx, dy, dz, false);
    }

    /**
     * Move an entity that is standing on a moving block. Vanilla only reports
     * onGround when downward motion is blocked, which is never true for an
     * entity carried down flush with the platform. Keeping the entity grounded
     * and its vertical motion cancelled stops gravity from accumulating between
     * carries (the downward-ride bounce).
     */
    public static void moveEntity(Entity entity, double dx, double dy, double dz, boolean onMovingPlatform) {
        // Let vanilla's sneaking edge protection see the support during this move.
        if (onMovingPlatform) entity.onGround = true;

        entity.moveEntity(dx, dy, dz);

        if (onMovingPlatform) {
            entity.onGround = true;
            entity.motionY = 0;
            entity.fallDistance = 0;
        }
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
