package com.rwtema.funkylocomotion;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;

public class CreativeTabFrames extends CreativeTabs {

    public CreativeTabFrames() {
        super(FunkyLocomotion.MODID);
    }

    @Override
    public Item getTabIconItem() {
        Item item = Item.getItemFromBlock(FunkyLocomotion.pusher);
        if (item == null) item = Item.getItemFromBlock(FunkyLocomotion.frame[0]);
        if (item == null) item = Item.getItemFromBlock(Blocks.piston);
        return item;
    }
}
