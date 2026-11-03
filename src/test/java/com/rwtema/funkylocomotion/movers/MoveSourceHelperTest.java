package com.rwtema.funkylocomotion.movers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import cofh.api.energy.EnergyStorage;

public class MoveSourceHelperTest {

    @Test
    public void canPayChecksEveryStorage() {
        EnergyStorage first = new EnergyStorage(1000);
        EnergyStorage second = new EnergyStorage(1000);
        first.receiveEnergy(500, false);
        second.receiveEnergy(500, false);

        assertTrue(MoveSourceHelper.canPay(500, first, second));
        assertFalse(MoveSourceHelper.canPay(600, first, second));
    }

    @Test
    public void drainPaysAllStorages() {
        EnergyStorage first = new EnergyStorage(1000);
        EnergyStorage second = new EnergyStorage(1000);
        first.receiveEnergy(500, false);
        second.receiveEnergy(500, false);

        MoveSourceHelper.drain(500, null, first, second);

        assertEquals(0, first.getEnergyStored());
        assertEquals(0, second.getEnergyStored());
    }

    @Test
    public void moveTimeIsClamped() {
        assertEquals(20, MoveSourceHelper.moveTime(0));
        assertEquals(10, MoveSourceHelper.moveTime(1));
        assertEquals(3, MoveSourceHelper.moveTime(99));
    }
}
