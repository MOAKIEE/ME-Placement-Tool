package com.moakiee.meplacementtool;

import net.neoforged.fml.ModList;

/**
 * Helper class for checking mod compatibility
 */
public class ModCompat {
    private static Boolean mekanismLoaded = null;
    private static Boolean jeiLoaded = null;

    /**
     * Check if Mekanism mod is loaded
     */
    public static boolean isMekanismLoaded() {
        if (mekanismLoaded == null) {
            mekanismLoaded = ModList.get().isLoaded("mekanism");
        }
        return mekanismLoaded;
    }

    /**
     * Check if JEI mod is loaded
     */
    public static boolean isJeiLoaded() {
        if (jeiLoaded == null) {
            jeiLoaded = ModList.get().isLoaded("jei");
        }
        return jeiLoaded;
    }
}
