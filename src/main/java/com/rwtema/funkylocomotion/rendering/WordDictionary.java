package com.rwtema.funkylocomotion.rendering;

import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.client.resources.SimpleReloadableResourceManager;
import net.minecraft.util.StringTranslate;

import cpw.mods.fml.common.ObfuscationReflectionHelper;

public class WordDictionary implements IResourceManagerReloadListener {

    static {
        try {
            Minecraft minecraft = Minecraft.getMinecraft();
            if (minecraft != null && minecraft.getResourceManager() instanceof SimpleReloadableResourceManager) {
                ((SimpleReloadableResourceManager) minecraft.getResourceManager())
                    .registerReloadListener(new WordDictionary());
            }
        } catch (Throwable ignored) {
            // The dictionary has a deterministic fallback; a missing client resource
            // manager must not break item tooltips.
        }
    }

    private static final String[] FALLBACK = new String[] { "A", "B", "C", "D", "E", "F", "G", "H", "I", "J",
        "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z", "Block", "Cloud", "Craft",
        "Dark", "Dust", "Ender", "Engine", "Frame", "Gear", "Gem", "Gold", "Ingot", "Iron", "Light", "Machine",
        "Move", "Nugget", "Pearl", "Piston", "Plate", "Power", "Red", "Rod", "Signal", "Sky", "Slime", "Stone",
        "Wire", "World" };

    private static String[] words;

    public static String[] getWords() {
        if (words != null) return words;

        TreeSet<String> wordSet = new TreeSet<String>();
        for (String fallback : FALLBACK) wordSet.add(fallback);

        try {
            StringTranslate translator = ObfuscationReflectionHelper
                .getPrivateValue(StringTranslate.class, null, "field_74817_a", "instance");
            Map<String, String> languageList = ObfuscationReflectionHelper
                .getPrivateValue(StringTranslate.class, translator, "field_74816_c", "languageList");

            if (languageList != null) {
                for (String s : languageList.values()) {
                    String s1 = s.toLowerCase(Locale.US);
                    for (String word : s1.split("[^A-Za-z]")) {
                        int n = word.length();
                        if (n <= 1) continue;

                        wordSet.add(
                            word.substring(0, 1)
                                .toUpperCase(Locale.ENGLISH)
                                + word.substring(1)
                                    .toLowerCase(Locale.ENGLISH));
                    }
                }
            }
        } catch (Throwable ignored) {
            // Reflection is best-effort; the fallback list is always valid.
        }

        words = wordSet.toArray(new String[0]);
        return words;
    }

    @Override
    public void onResourceManagerReload(IResourceManager p_110549_1_) {
        words = null;
    }
}
