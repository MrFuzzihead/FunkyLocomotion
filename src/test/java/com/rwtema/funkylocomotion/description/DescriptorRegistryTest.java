package com.rwtema.funkylocomotion.description;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

public class DescriptorRegistryTest {

    @Test
    public void missingOrEmptyDescriptionIdsFallBackToVanilla() {
        assertNotNull(DescriptorRegistry.getDescriptor(""));
        assertSame(DescriptorRegistry.getDescriptor(""), DescriptorRegistry.getDescriptor(null));
        assertSame(DescriptorRegistry.getDescriptor(""), DescriptorRegistry.getDescriptor("unknown-descriptor"));
    }
}
