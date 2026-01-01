package com.moakiee.meplacementtool;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.network.chat.Component;

import mekanism.api.IConfigCardAccess;
import mekanism.api.SerializationConstants;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.registries.MekanismDataComponents;

/**
 * Helper class for applying Mekanism Configuration Card settings to placed blocks
 */
public class MekanismConfigCardHelper {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Check if the player has a configured Mekanism configuration card in their off-hand
     */
    public static boolean hasConfiguredConfigCard(Player player) {
        if (!ModCompat.isMekanismLoaded()) {
            return false;
        }
        
        try {
            return hasConfiguredConfigCardInternal(player);
        } catch (Throwable t) {
            // Mekanism classes not available
            return false;
        }
    }

    private static boolean hasConfiguredConfigCardInternal(Player player) {
        ItemStack offHandStack = player.getItemInHand(InteractionHand.OFF_HAND);
        if (offHandStack.isEmpty()) {
            return false;
        }
        
        // Check if it's a Mekanism configuration card with data
        var itemId = BuiltInRegistries.ITEM.getKey(offHandStack.getItem());
        if (itemId == null || !itemId.getNamespace().equals("mekanism")) {
            return false;
        }
        
        // ItemConfigurationCard check
        if (!itemId.getPath().equals("configuration_card")) {
            return false;
        }

        // Check if the card has configuration data stored using Mekanism's Data Component
        CompoundTag data = offHandStack.get(MekanismDataComponents.CONFIGURATION_DATA);
        return data != null && !data.isEmpty() && data.contains(SerializationConstants.DATA_NAME, Tag.TAG_STRING);
    }

    /**
     * Get the configuration card from player's off-hand
     */
    public static ItemStack getOffHandConfigCard(Player player) {
        if (!ModCompat.isMekanismLoaded()) {
            return ItemStack.EMPTY;
        }
        
        try {
            return getOffHandConfigCardInternal(player);
        } catch (Throwable t) {
            return ItemStack.EMPTY;
        }
    }

    private static ItemStack getOffHandConfigCardInternal(Player player) {
        ItemStack offHandStack = player.getItemInHand(InteractionHand.OFF_HAND);
        if (!offHandStack.isEmpty()) {
            var itemId = BuiltInRegistries.ITEM.getKey(offHandStack.getItem());
            if (itemId != null && itemId.getNamespace().equals("mekanism") && 
                    itemId.getPath().equals("configuration_card")) {
                return offHandStack;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * Result of resource check - Mekanism config cards don't need additional resources
     */
    public static class ResourceCheckResult {
        public final boolean sufficient;
        public final String message;

        public ResourceCheckResult(boolean sufficient, String message) {
            this.sufficient = sufficient;
            this.message = message;
        }
    }

    /**
     * Check if there are enough resources - Mekanism config cards don't consume resources
     */
    public static ResourceCheckResult checkResourcesForMultipleBlocks(Player player, int blockCount) {
        return new ResourceCheckResult(true, "");
    }

    /**
     * Apply configuration card settings to a placed block entity
     */
    public static boolean applyConfigCardToBlock(Player player, Level level, BlockPos pos, boolean showMessage) {
        if (!ModCompat.isMekanismLoaded()) {
            return false;
        }
        
        try {
            return applyConfigCardToBlockInternal(player, level, pos, showMessage);
        } catch (Throwable t) {
            LOGGER.warn("Failed to apply Mekanism config card (API error)", t);
            return false;
        }
    }

    private static boolean applyConfigCardToBlockInternal(Player player, Level level, BlockPos pos, boolean showMessage) {
        ItemStack configCardStack = getOffHandConfigCardInternal(player);
        if (configCardStack.isEmpty()) {
            return false;
        }

        // Get config card access capability from the block
        IConfigCardAccess configCardAccess = level.getCapability(Capabilities.CONFIG_CARD, pos, null);
        if (configCardAccess == null) {
            return false;
        }

        // Get the stored configuration data from the card using Mekanism's Data Component
        CompoundTag cardTag = configCardStack.get(MekanismDataComponents.CONFIGURATION_DATA);
        if (cardTag == null || cardTag.isEmpty()) {
            return false;
        }
        
        if (!cardTag.contains(SerializationConstants.DATA_TYPE, Tag.TAG_STRING)) {
            return false;
        }

        String storedTypeName = cardTag.getString(SerializationConstants.DATA_TYPE);
        ResourceLocation storedTypeId = ResourceLocation.tryParse(storedTypeName);
        if (storedTypeId == null) {
            return false;
        }

        Block storedType = BuiltInRegistries.BLOCK.get(storedTypeId);
        if (storedType == null) {
            return false;
        }

        // Check compatibility
        if (!configCardAccess.isConfigurationDataCompatible(storedType)) {
            if (showMessage) {
                player.displayClientMessage(
                        Component.translatable("message.meplacementtool.mek_config_incompatible"),
                        true
                );
            }
            return false;
        }

        try {
            // Apply the configuration data - the cardTag IS the data, not a sub-compound
            configCardAccess.setConfigurationData(level.registryAccess(), player, cardTag);
            configCardAccess.configurationDataSet();

            if (showMessage) {
                player.displayClientMessage(
                        Component.translatable("message.meplacementtool.mek_config_loaded"),
                        true
                );
            }
            return true;
        } catch (Exception e) {
            LOGGER.warn("Failed to apply Mekanism configuration card settings to block at {}", pos, e);
            return false;
        }
    }
}
