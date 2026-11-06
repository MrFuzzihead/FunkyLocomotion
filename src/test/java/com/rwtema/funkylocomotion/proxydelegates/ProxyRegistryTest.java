package com.rwtema.funkylocomotion.proxydelegates;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.After;
import org.junit.Test;

public class ProxyRegistryTest {

    interface Marker {
    }

    interface Sticky {

        boolean sticky();
    }

    static class Base implements Marker {
    }

    static class Child extends Base {
    }

    static class Unrelated {
    }

    static class StickyProxy implements Sticky {

        @Override
        public boolean sticky() {
            return true;
        }
    }

    @After
    public void clear() {
        ProxyRegistry.proxies.clear();
    }

    @Test
    public void findsAProxyThroughAnInterfaceOnASuperclass() {
        StickyProxy proxy = new StickyProxy();
        ProxyRegistry.register(Marker.class, proxy, Sticky.class);

        assertSame(proxy, ProxyRegistry.getInterface(new Base(), Sticky.class));
    }

    @Test
    public void returnsTheObjectItselfWhenItImplementsTheInterface() {
        StickyProxy proxy = new StickyProxy();
        assertSame(proxy, ProxyRegistry.getInterface(proxy, Sticky.class));
    }

    @Test
    public void returnsNullForUnrelatedObjects() {
        assertNull(ProxyRegistry.getInterface(new Unrelated(), Sticky.class));
        assertNotNull(new Unrelated());
    }
}
