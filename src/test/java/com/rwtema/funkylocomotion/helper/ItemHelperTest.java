package com.rwtema.funkylocomotion.helper;

import static org.junit.Assert.assertFalse;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.Test;

public class ItemHelperTest {

    @Test
    public void nullAndOrdinaryItemsAreNotWrenches() {
        assertFalse(ItemHelper.isWrench(null));
        assertFalse(ItemHelper.isWrench(new ItemStack(new Item())));
    }
}
