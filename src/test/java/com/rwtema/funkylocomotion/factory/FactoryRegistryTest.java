package com.rwtema.funkylocomotion.factory;

import static org.junit.Assert.assertSame;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import org.junit.After;
import org.junit.Test;

import com.rwtema.funkylocomotion.api.BlockPos;
import com.rwtema.funkylocomotion.api.IMoveFactory;

public class FactoryRegistryTest {

    static class BaseBlock extends Block {

        BaseBlock() {
            super(Material.rock);
        }
    }

    static class ChildBlock extends BaseBlock {
    }

    static class TestFactory implements IMoveFactory {

        @Override
        public NBTTagCompound destroyBlock(World world, BlockPos pos) {
            return new NBTTagCompound();
        }

        @Override
        public boolean recreateBlock(World world, BlockPos pos, NBTTagCompound tag) {
            return true;
        }
    }

    @After
    public void clear() {
        FactoryRegistry.moveFactoryMapBlockClass.clear();
        FactoryRegistry.moveFactoryMapBlock.clear();
        FactoryRegistry.moveFactoryMapInheritanceClass.clear();
    }

    @Test
    public void blockSubclassesInheritTheirParentsFactory() {
        TestFactory factory = new TestFactory();
        FactoryRegistry.moveFactoryMapBlockClass.put(BaseBlock.class, factory);

        assertSame(factory, FactoryRegistry.getFactory(new ChildBlock()));
    }
}
