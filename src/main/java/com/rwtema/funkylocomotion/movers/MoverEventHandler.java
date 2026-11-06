package com.rwtema.funkylocomotion.movers;

import com.rwtema.funkylocomotion.LogHelper;
import com.rwtema.funkylocomotion.helper.WeakSet;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;

public class MoverEventHandler {

    public static final WeakSet<IMover> movers = new WeakSet<IMover>();

    public static void init() {
        FMLCommonHandler.instance()
            .bus()
            .register(new MoverEventHandler());
    }

    public static void registerMover(IMover mover) {
        movers.add(mover);
    }

    private MoverEventHandler() {}

    @SubscribeEvent
    public void onPostWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side != Side.SERVER || movers.isEmpty()) return;

        IMover[] iMovers = movers.toArray(new IMover[0]);
        movers.clear();

        for (IMover mover : iMovers) {
            try {
                if (mover.stillExists()) {
                    mover.startMoving();
                }
            } catch (Throwable t) {
                LogHelper.errorThrowable("Unable to start a registered mover", t);
            }
        }

        // Do not clear again: a mover registered by a startMoving callback (for
        // example a neighbouring pusher) should be handled on the next tick.
    }
}
