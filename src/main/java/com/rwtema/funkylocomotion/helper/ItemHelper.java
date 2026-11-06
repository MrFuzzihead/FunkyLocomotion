package com.rwtema.funkylocomotion.helper;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.rwtema.funkylocomotion.FunkyLocomotion;

public class ItemHelper {

    public static final String[] wrenchClassNames = new String[] { "buildcraft.api.tools.IToolWrench",
        "cofh.api.item.IToolHammer", "powercrystals.minefactoryreloaded.api.IMFRHammer",
        "appeng.api.implementations.items.IAEWrench", "crazypants.enderio.api.tool.ITool" };

    public static final Class<?>[] wrenchClasses;

    private static final ClassValue<Boolean> WRENCH_CACHE = new ClassValue<Boolean>() {

        @Override
        protected Boolean computeValue(Class<?> type) {
            for (Class<?> wrenchClass : wrenchClasses) {
                if (wrenchClass != null && wrenchClass.isAssignableFrom(type)) return true;
            }
            return false;
        }
    };

    static {
        String[] wrenchInterfaces = wrenchClassNames;
        wrenchClasses = new Class[wrenchInterfaces.length];
        for (int i = 0; i < wrenchClasses.length; i++) {
            try {
                // Do not initialize optional third-party API classes during mod construction.
                wrenchClasses[i] = Class.forName(wrenchInterfaces[i], false, ItemHelper.class.getClassLoader());
            } catch (ClassNotFoundException | LinkageError ignore) {
                wrenchClasses[i] = null;
            }
        }
    }

    public static boolean isWrench(ItemStack item) {
        if (item == null || item.getItem() == null) return false;

        if (item.getItem() == Items.stick || item.getItem() == FunkyLocomotion.wrench) return true;

        return WRENCH_CACHE.get(
            item.getItem()
                .getClass());
    }
}
