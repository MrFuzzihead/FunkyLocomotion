package com.rwtema.funkylocomotion.proxydelegates;

import java.util.HashMap;
import java.util.Map;

public class ProxyRegistry {

    public static final Map<Class<?>, Map<Object, Object>> proxies = new HashMap<Class<?>, Map<Object, Object>>();

    public static <T> T register(Object a, T proxy, Class<? extends T> iface) {
        if (a == null) throw new IllegalArgumentException("proxy key must not be null");
        if (proxy == null) throw new IllegalArgumentException("proxy must not be null");
        if (iface == null) throw new IllegalArgumentException("proxy interface must not be null");
        if (!iface.isAssignableFrom(proxy.getClass())) {
            throw new IllegalArgumentException(
                proxy.getClass()
                    .getName() + " does not implement "
                    + iface.getName());
        }

        if (iface.isAssignableFrom(a.getClass())) return iface.cast(a);

        Map<Object, Object> h = proxies.computeIfAbsent(iface, k -> new HashMap<Object, Object>());
        h.put(a, iface.cast(proxy));

        return iface.cast(proxy);
    }

    @SuppressWarnings("unchecked")
    public static <T> T getInterface(Object a, Class<? extends T> iface) {
        if (a == null || iface == null) return null;

        Class<?> aClass = a.getClass();
        if (iface.isAssignableFrom(aClass)) {
            return (T) a;
        }

        Map<Object, Object> h = proxies.get(iface);
        if (h == null) return null;

        Object obj = h.get(a);
        if (obj == null) {
            if (h.containsKey(aClass)) {
                obj = h.get(aClass);
            } else {
                // Walk superclasses as well as directly implemented interfaces.
                for (Class<?> c = aClass; c != null && c != Object.class; c = c.getSuperclass()) {
                    obj = h.get(c);
                    if (obj != null) break;

                    for (Class<?> iface2 : c.getInterfaces()) {
                        obj = h.get(iface2);
                        if (obj != null) break;
                    }
                    if (obj != null) break;
                }
                if (obj == null) h.put(aClass, null);
            }
        }

        return (T) obj;
    }
}
