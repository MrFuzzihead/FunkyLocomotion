package com.rwtema.funkylocomotion.description;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.rwtema.funkylocomotion.api.IDescriptionProxy;

public class DescriptorRegistry {

    private static final Map<String, IDescriptionProxy> proxyMap = new HashMap<String, IDescriptionProxy>();
    private static final List<IDescriptionProxy> proxyList = new ArrayList<IDescriptionProxy>();

    static {
        register(new DescribeVanilla(), false);
        register(new DescribeVanillaSign(), false);
    }

    /**
     * @param fallback {@code true} to append this descriptor as a low-priority
     *                 fallback, {@code false} to give it high priority. The old
     *                 parameter was named {@code priority}, but behaved as the
     *                 opposite of what most callers expected.
     */
    public static void register(IDescriptionProxy d, boolean fallback) {
        if (proxyMap.containsKey(d.getID())) throw new RuntimeException(d.getID() + " already registered");

        proxyMap.put(d.getID(), d);
        if (fallback || proxyList.isEmpty()) proxyList.add(d);
        else proxyList.add(0, d);
    }

    public static List<IDescriptionProxy> getProxyList() {
        return Collections.unmodifiableList(new ArrayList<IDescriptionProxy>(proxyList));
    }

    public static IDescriptionProxy getDescriptor(String s) {
        IDescriptionProxy descriptor = proxyMap.get(s == null ? "" : s);
        return descriptor != null ? descriptor : proxyMap.get("");
    }

}
