package com.moakiee.meplacementtool;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import mekanism.api.IConfigCardAccess;
import mekanism.api.NBTConstants;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.item.ItemConfigurationCard;
import mekanism.common.registries.MekanismItems;
import mekanism.common.util.CapabilityUtils;
import mekanism.common.util.ItemDataUtils;
import mekanism.common.util.NBTUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Optional;

/**
 * Helper class for applying Mekanism Configuration Card settings to placed blocks
 */
public class MekanismConfigCardHelper {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Check if the player has a configured Mekanism configuration card in their off-hand
     *
     * @param player the player to check
     * @return true if off-hand contains a configured configuration card
     */
    public static boolean hasConfiguredConfigCard(Player player) {
        ItemStack offHandStack = player.getItemInHand(InteractionHand.OFF_HAND);
        if (offHandStack.isEmpty()) {
            return false;
        }
        if (!(offHandStack.getItem() instanceof ItemConfigurationCard configCard)) {
            return false;
        }
        return configCard.hasData(offHandStack);
    }

    /**
     * Get the configuration card from player's off-hand
     *
     * @param player the player
     * @return the configuration card item stack, or empty if not present
     */
    public static ItemStack getOffHandConfigCard(Player player) {
        ItemStack offHandStack = player.getItemInHand(InteractionHand.OFF_HAND);
        if (!offHandStack.isEmpty() && offHandStack.getItem() instanceof ItemConfigurationCard) {
            return offHandStack;
        }
        return ItemStack.EMPTY;
    }

    /**
     * Get the stored data from a configuration card
     */
    private static CompoundTag getData(ItemStack stack) {
        CompoundTag data = ItemDataUtils.getCompound(stack, NBTConstants.DATA);
        return data.isEmpty() ? null : data;
    }

    /**
     * Get the stored tile type from configuration card data
     */
    private static BlockEntityType<?> getStoredTileType(CompoundTag data) {
        if (data == null || !data.contains(NBTConstants.DATA_TYPE, Tag.TAG_STRING)) {
            return null;
        }
        ResourceLocation tileRegistryName = ResourceLocation.tryParse(data.getString(NBTConstants.DATA_TYPE));
        return tileRegistryName == null ? null : ForgeRegistries.BLOCK_ENTITY_TYPES.getValue(tileRegistryName);
    }

    /**
     * Get the stored name from configuration card data
     */
    private static String getStoredName(CompoundTag data) {
        if (data == null || !data.contains(NBTConstants.DATA_NAME, Tag.TAG_STRING)) {
            return null;
        }
        return data.getString(NBTConstants.DATA_NAME);
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
        // Mekanism configuration cards don't consume any resources when applying settings
        return new ResourceCheckResult(true, "");
    }

    /**
     * Apply configuration card settings to a placed block entity
     *
     * @param player      the player who placed the block
     * @param level       the world
     * @param pos         the position of the placed block
     * @param showMessage whether to show a message to the player
     * @return true if settings were applied successfully
     */
    public static boolean applyConfigCardToBlock(Player player, Level level, BlockPos pos, boolean showMessage) {
        ItemStack configCardStack = getOffHandConfigCard(player);
        if (configCardStack.isEmpty()) {
            return false;
        }

        CompoundTag data = getData(configCardStack);
        if (data == null) {
            return false;
        }

        BlockEntityType<?> storedType = getStoredTileType(data);
        if (storedType == null) {
            return false;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            return false;
        }

        // Try to get IConfigCardAccess capability
        Optional<IConfigCardAccess> configCardSupport = CapabilityUtils.getCapability(be, Capabilities.CONFIG_CARD, null).resolve();
        if (configCardSupport.isEmpty()) {
            return false;
        }

        IConfigCardAccess configCardAccess = configCardSupport.get();
        String storedName = getStoredName(data);

        // Check if the configuration data is compatible
        if (!configCardAccess.isConfigurationDataCompatible(storedType)) {
            if (showMessage) {
                // Incompatible - show error message
                player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("message.meplacementtool.mek_config_incompatible"),
                    true
                );
            }
            return false;
        }

        try {
            // Apply the configuration data
            configCardAccess.setConfigurationData(player, data);
            configCardAccess.configurationDataSet();
            
            if (showMessage) {
                player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("message.meplacementtool.mek_config_loaded"),
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
