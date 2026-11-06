package com.rwtema.funkylocomotion.movepermissions;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

public class MoveBlacklistTest {

    @After
    public void clear() {
        MoveBlacklist.clear();
    }

    @Test
    public void matchesModAndRegistryEntries() {
        MoveBlacklist.add("gregtech");
        MoveBlacklist.add("block:foo:bar");

        assertTrue(MoveBlacklist.matches("gregtech:machine", "gregtech"));
        assertTrue(MoveBlacklist.matches("foo:bar", "foo"));
        assertFalse(MoveBlacklist.matches("other:block", "other"));
    }

    @Test
    public void explicitModPrefixWorks() {
        MoveBlacklist.add("mod:thaumcraft");

        assertTrue(MoveBlacklist.matches("thaumcraft:blockAiry", "thaumcraft"));
    }
}
