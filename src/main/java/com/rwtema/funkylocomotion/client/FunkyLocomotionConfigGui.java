package com.rwtema.funkylocomotion.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Configuration;

import com.rwtema.funkylocomotion.FunkyLocomotion;

import cpw.mods.fml.client.config.GuiConfig;
import cpw.mods.fml.client.config.IConfigElement;

public class FunkyLocomotionConfigGui extends GuiConfig {

    public FunkyLocomotionConfigGui(GuiScreen parent) {
        super(parent, elements(), FunkyLocomotion.MODID, FunkyLocomotion.MODID, false, false, "Funky Locomotion");
    }

    @SuppressWarnings("rawtypes")
    private static List<IConfigElement> elements() {
        List<IConfigElement> list = new ArrayList<IConfigElement>();
        Configuration config = FunkyLocomotion.getConfig();
        if (config != null) {
            list.add(new ConfigElement(config.getCategory(Configuration.CATEGORY_GENERAL)));
            if (config.hasCategory("client")) list.add(new ConfigElement(config.getCategory("client")));
        }
        return list;
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        FunkyLocomotion.reloadConfig();
    }
}
