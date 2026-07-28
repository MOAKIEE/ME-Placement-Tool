package com.moakiee.meplacementtool;

import net.neoforged.fml.ModList;

/**
 * Helper class for checking mod compatibility
 */
public class ModCompat {
    private static Boolean mekanismLoaded = null;
    private static Boolean jeiLoaded = null;
    private static Boolean ae2ltLoaded = null;

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

    /**
     * Check if AE2 Lightning Tech is loaded
     */
    public static boolean isAe2ltLoaded() {
        if (ae2ltLoaded == null) {
            ae2ltLoaded = ModList.get().isLoaded("ae2lt");
        }
        return ae2ltLoaded;
    }
}
