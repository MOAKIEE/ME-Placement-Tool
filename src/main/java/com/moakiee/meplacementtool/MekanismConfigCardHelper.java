package com.moakiee.meplacementtool;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Mekanism has no published 26.1.2 Maven artifact yet, so config-card
 * compatibility is intentionally disabled for this port.
 */
public class MekanismConfigCardHelper {
    public static boolean hasConfiguredConfigCard(Player player) {
        return false;
    }

    public static ItemStack getOffHandConfigCard(Player player) {
        return ItemStack.EMPTY;
    }

    public static class ResourceCheckResult {
        public final boolean sufficient;
        public final String message;

        public ResourceCheckResult(boolean sufficient, String message) {
            this.sufficient = sufficient;
            this.message = message;
        }
    }

    public static ResourceCheckResult checkResourcesForMultipleBlocks(Player player, int blockCount) {
        return new ResourceCheckResult(true, "");
    }

    public static boolean applyConfigCardToBlock(Player player, Level level, BlockPos pos, boolean showMessage) {
        return false;
    }
}
