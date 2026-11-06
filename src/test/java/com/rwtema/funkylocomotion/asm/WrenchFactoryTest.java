package com.rwtema.funkylocomotion.asm;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.objectweb.asm.Opcodes.ACC_ABSTRACT;
import static org.objectweb.asm.Opcodes.ACC_BRIDGE;
import static org.objectweb.asm.Opcodes.ACC_NATIVE;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_PROTECTED;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACC_SYNTHETIC;

import org.junit.Test;

public class WrenchFactoryTest {

    @Test
    public void copiesOnlyPublicAbstractInterfaceMethods() {
        assertTrue(WrenchFactory.shouldCopyMethod(ACC_PUBLIC | ACC_ABSTRACT, "canWrench"));
        assertTrue(WrenchFactory.shouldCopyMethod(ACC_PUBLIC | ACC_ABSTRACT, "isUsable"));
    }

    @Test
    public void skipsInitializersAndNonAbstractMethods() {
        assertFalse(WrenchFactory.shouldCopyMethod(ACC_PUBLIC | ACC_STATIC, "<clinit>"));
        assertFalse(WrenchFactory.shouldCopyMethod(ACC_PUBLIC, "<init>"));
        assertFalse(WrenchFactory.shouldCopyMethod(ACC_PUBLIC, "defaultMethod"));
        assertFalse(WrenchFactory.shouldCopyMethod(ACC_PUBLIC | ACC_STATIC | ACC_ABSTRACT, "staticHelper"));
    }

    @Test
    public void skipsNonPublicAndNonCopyableMethods() {
        assertFalse(WrenchFactory.shouldCopyMethod(ACC_PRIVATE | ACC_ABSTRACT, "privateHelper"));
        assertFalse(WrenchFactory.shouldCopyMethod(ACC_PROTECTED | ACC_ABSTRACT, "protectedHelper"));
        assertFalse(WrenchFactory.shouldCopyMethod(ACC_PUBLIC | ACC_ABSTRACT | ACC_NATIVE, "nativeMethod"));
        assertFalse(WrenchFactory.shouldCopyMethod(ACC_PUBLIC | ACC_ABSTRACT | ACC_SYNTHETIC, "syntheticMethod"));
        assertFalse(WrenchFactory.shouldCopyMethod(ACC_PUBLIC | ACC_BRIDGE | ACC_ABSTRACT, "bridgeMethod"));
    }
}
