package com.moakiee.meplacementtool;

import net.minecraftforge.fml.ModList;

/**
 * Helper class for checking mod compatibility
 */
public class ModCompat {
    private static boolean mekanismLoaded = false;
    private static boolean checkedMekanism = false;

    /**
     * Check if Mekanism mod is loaded
     */
    public static boolean isMekanismLoaded() {
        if (!checkedMekanism) {
            mekanismLoaded = ModList.get().isLoaded("mekanism");
            checkedMekanism = true;
        }
        return mekanismLoaded;
    }
}
