package com.moakiee.meplacementtool;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.config.Actionable;
import appeng.api.ids.AEComponents;
import appeng.api.implementations.items.IMemoryCard;
import appeng.api.implementations.items.MemoryCardMessages;
import appeng.api.networking.IGrid;
import appeng.api.parts.IPart;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.core.definitions.AEItems;
import appeng.items.tools.MemoryCardItem;
import appeng.items.tools.NetworkToolItem;
import appeng.me.helpers.PlayerSource;
import appeng.parts.AEBasePart;
import appeng.util.SettingsFrom;

import java.util.HashMap;
import java.util.Map;

/**
 * Helper class for applying AE2 Memory Card settings to placed blocks/parts
 */
public class MemoryCardHelper {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Check if the player has a configured memory card in their off-hand
     */
    public static boolean hasConfiguredMemoryCard(Player player) {
        ItemStack offHandStack = player.getItemInHand(InteractionHand.OFF_HAND);
        if (offHandStack.isEmpty()) {
            return false;
        }
        if (!(offHandStack.getItem() instanceof IMemoryCard)) {
            return false;
        }
        
        // Check if the memory card has stored settings using Data Components
        var settingsSource = offHandStack.get(AEComponents.EXPORTED_SETTINGS_SOURCE);
        return settingsSource != null;
    }

    /**
     * Get the memory card from player's off-hand
     */
    public static ItemStack getOffHandMemoryCard(Player player) {
        ItemStack offHandStack = player.getItemInHand(InteractionHand.OFF_HAND);
        if (!offHandStack.isEmpty() && offHandStack.getItem() instanceof IMemoryCard) {
            return offHandStack;
        }
        return ItemStack.EMPTY;
    }

    /**
     * Count how many of a specific item the player has in their inventory and network tool
     */
    private static int countItemInPlayerAndNetworkTool(Player player, Item item) {
        int count = player.getInventory().countItem(item);
        
        // Also check network tool inventory
        var networkTool = NetworkToolItem.findNetworkToolInv(player);
        if (networkTool != null) {
            for (var stack : networkTool.getInventory()) {
                if (stack.getItem() == item) {
                    count += stack.getCount();
                }
            }
        }
        
        return count;
    }

    /**
     * Count how many patterns are stored in the memory card data
     */
    private static int countPatternsInMemoryCard(DataComponentMap components) {
        var patterns = components.get(AEComponents.EXPORTED_PATTERNS);
        if (patterns == null || patterns == ItemContainerContents.EMPTY) {
            return 0;
        }
        
        int count = 0;
        for (var stack : patterns.nonEmptyItems()) {
            if (!stack.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Get the upgrades stored in the memory card data
     */
    private static Map<Item, Integer> getUpgradesInMemoryCard(DataComponentMap components) {
        Map<Item, Integer> upgrades = new HashMap<>();
        
        var exportedUpgrades = components.get(AEComponents.EXPORTED_UPGRADES);
        if (exportedUpgrades == null) {
            return upgrades;
        }
        
        for (var stack : exportedUpgrades.upgrades()) {
            if (!stack.isEmpty()) {
                upgrades.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }
        
        return upgrades;
    }

    /**
     * Pre-fetch blank patterns from AE network to player inventory before applying memory card settings.
     */
    private static int preFetchBlankPatternsFromNetwork(Player player, IGrid grid, DataComponentMap components) {
        if (player.getAbilities().instabuild) {
            return 0;
        }
        
        int patternsNeeded = countPatternsInMemoryCard(components);
        if (patternsNeeded <= 0) {
            return 0;
        }
        
        int existingBlankPatterns = countItemInPlayerAndNetworkTool(player, AEItems.BLANK_PATTERN.asItem());
        int needToFetch = patternsNeeded - existingBlankPatterns;
        
        if (needToFetch <= 0) {
            return 0;
        }
        
        var storage = grid.getStorageService().getInventory();
        var src = new PlayerSource(player);
        var blankPatternKey = AEItemKey.of(AEItems.BLANK_PATTERN.asItem());
        
        long available = storage.extract(blankPatternKey, needToFetch, Actionable.SIMULATE, src);
        if (available <= 0) {
            return 0;
        }
        
        long extracted = storage.extract(blankPatternKey, Math.min(available, needToFetch), Actionable.MODULATE, src);
        if (extracted > 0) {
            ItemStack blankPatterns = AEItems.BLANK_PATTERN.stack((int) extracted);
            player.getInventory().placeItemBackInInventory(blankPatterns);
        }
        
        return (int) extracted;
    }

    /**
     * Pre-fetch upgrades from AE network to player inventory before applying memory card settings.
     */
    private static int preFetchUpgradesFromNetwork(Player player, IGrid grid, DataComponentMap components) {
        if (player.getAbilities().instabuild) {
            return 0;
        }
        
        Map<Item, Integer> desiredUpgrades = getUpgradesInMemoryCard(components);
        if (desiredUpgrades.isEmpty()) {
            return 0;
        }
        
        var storage = grid.getStorageService().getInventory();
        var src = new PlayerSource(player);
        int totalFetched = 0;
        
        for (var entry : desiredUpgrades.entrySet()) {
            Item upgradeItem = entry.getKey();
            int needed = entry.getValue();
            
            int existing = countItemInPlayerAndNetworkTool(player, upgradeItem);
            int needToFetch = needed - existing;
            
            if (needToFetch <= 0) {
                continue;
            }
            
            var upgradeKey = AEItemKey.of(upgradeItem);
            if (upgradeKey == null) {
                continue;
            }
            
            long available = storage.extract(upgradeKey, needToFetch, Actionable.SIMULATE, src);
            if (available <= 0) {
                continue;
            }
            
            long extracted = storage.extract(upgradeKey, Math.min(available, needToFetch), Actionable.MODULATE, src);
            if (extracted > 0) {
                ItemStack upgradeStack = new ItemStack(upgradeItem, (int) extracted);
                player.getInventory().placeItemBackInInventory(upgradeStack);
                totalFetched += (int) extracted;
            }
        }
        
        return totalFetched;
    }

    /**
     * Pre-fetch all required items (blank patterns and upgrades) from AE network
     */
    private static void preFetchAllFromNetwork(Player player, IGrid grid, DataComponentMap components) {
        preFetchBlankPatternsFromNetwork(player, grid, components);
        preFetchUpgradesFromNetwork(player, grid, components);
    }

    /**
     * Resource check result for memory card application
     */
    public static class ResourceCheckResult {
        public final boolean sufficient;
        private final Map<Item, Integer> missingItems;

        public ResourceCheckResult(boolean sufficient, Map<Item, Integer> missingItems) {
            this.sufficient = sufficient;
            this.missingItems = missingItems != null ? missingItems : new HashMap<>();
        }

        public String getMissingItemsMessage() {
            if (missingItems.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (var entry : missingItems.entrySet()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(entry.getValue()).append("x ").append(entry.getKey().getDescription().getString());
            }
            return sb.toString();
        }
    }

    /**
     * Check if there are enough resources in the grid for memory card application
     */
    public static ResourceCheckResult checkResourcesForMultipleBlocks(Player player, IGrid grid, int blockCount) {
        ItemStack memoryCardStack = getOffHandMemoryCard(player);
        if (memoryCardStack.isEmpty() || blockCount <= 0) {
            return new ResourceCheckResult(true, null);
        }

        var components = memoryCardStack.getComponents();
        var settingsSource = memoryCardStack.get(AEComponents.EXPORTED_SETTINGS_SOURCE);
        if (settingsSource == null) {
            return new ResourceCheckResult(true, null);
        }

        // Creative mode doesn't need resources
        if (player.getAbilities().instabuild) {
            return new ResourceCheckResult(true, null);
        }

        Map<Item, Integer> missingItems = new HashMap<>();

        // Check blank patterns
        int patternsPerBlock = countPatternsInMemoryCard(components);
        if (patternsPerBlock > 0) {
            int totalPatternsNeeded = patternsPerBlock * blockCount;
            int available = countItemInPlayerAndNetworkTool(player, AEItems.BLANK_PATTERN.asItem());
            
            // Also count from AE network
            if (grid != null) {
                var storage = grid.getStorageService().getInventory();
                var blankPatternKey = AEItemKey.of(AEItems.BLANK_PATTERN.asItem());
                var src = new PlayerSource(player);
                available += (int) storage.extract(blankPatternKey, Integer.MAX_VALUE, Actionable.SIMULATE, src);
            }

            if (available < totalPatternsNeeded) {
                missingItems.put(AEItems.BLANK_PATTERN.asItem(), totalPatternsNeeded - available);
            }
        }

        // Check upgrades
        Map<Item, Integer> upgradesPerBlock = getUpgradesInMemoryCard(components);
        for (var entry : upgradesPerBlock.entrySet()) {
            Item upgradeItem = entry.getKey();
            int totalNeeded = entry.getValue() * blockCount;
            int available = countItemInPlayerAndNetworkTool(player, upgradeItem);

            // Also count from AE network
            if (grid != null) {
                var storage = grid.getStorageService().getInventory();
                var upgradeKey = AEItemKey.of(upgradeItem);
                if (upgradeKey != null) {
                    var src = new PlayerSource(player);
                    available += (int) storage.extract(upgradeKey, Integer.MAX_VALUE, Actionable.SIMULATE, src);
                }
            }

            if (available < totalNeeded) {
                missingItems.put(upgradeItem, totalNeeded - available);
            }
        }

        return new ResourceCheckResult(missingItems.isEmpty(), missingItems);
    }

    /**
     * Apply memory card settings to a placed block entity
     */
    public static boolean applyMemoryCardToBlock(Player player, Level level, BlockPos pos, boolean showMessage, IGrid grid) {
        ItemStack memoryCardStack = getOffHandMemoryCard(player);
        if (memoryCardStack.isEmpty()) {
            return false;
        }

        IMemoryCard memoryCard = (IMemoryCard) memoryCardStack.getItem();
        
        // Check if card has data using Data Components
        var settingsSource = memoryCardStack.get(AEComponents.EXPORTED_SETTINGS_SOURCE);
        if (settingsSource == null) {
            return false;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            return false;
        }

        try {
            var components = memoryCardStack.getComponents();
            
            // Pre-fetch all required items (blank patterns, upgrades) from AE network
            if (grid != null) {
                preFetchAllFromNetwork(player, grid, components);
            }
            
            // Check if we have an AE2 block entity that supports full import
            if (be instanceof AEBaseBlockEntity aeBlockEntity) {
                // Compare the block entity's name with the saved source name
                Component blockName = aeBlockEntity.getName();
                if (blockName.equals(settingsSource)) {
                    // Exact match - do full import including patterns
                    aeBlockEntity.importSettings(SettingsFrom.MEMORY_CARD, components, player);
                    if (showMessage) {
                        memoryCard.notifyUser(player, MemoryCardMessages.SETTINGS_LOADED);
                    }
                    return true;
                }
            }
            
            // Fallback to generic settings import for non-matching types
            MemoryCardItem.importGenericSettingsAndNotify(be, components, player);
            return true;
        } catch (Exception e) {
            LOGGER.warn("Failed to apply memory card settings to block at {}", pos, e);
        }

        return false;
    }

    /**
     * Apply memory card settings to a placed part
     */
    public static boolean applyMemoryCardToPart(Player player, IPart part, boolean showMessage, IGrid grid) {
        ItemStack memoryCardStack = getOffHandMemoryCard(player);
        if (memoryCardStack.isEmpty()) {
            return false;
        }

        IMemoryCard memoryCard = (IMemoryCard) memoryCardStack.getItem();
        
        // Check if card has data using Data Components
        var settingsSource = memoryCardStack.get(AEComponents.EXPORTED_SETTINGS_SOURCE);
        if (settingsSource == null) {
            return false;
        }

        try {
            var components = memoryCardStack.getComponents();
            
            // Pre-fetch all required items (blank patterns, upgrades) from AE network
            if (grid != null) {
                preFetchAllFromNetwork(player, grid, components);
            }
            
            // Check if we have an AE2 part that supports full import
            if (part instanceof AEBasePart aePart) {
                // Compare the part's name with the saved source name
                Component partName = aePart.getName();
                if (partName.equals(settingsSource)) {
                    // Exact match - do full import including patterns
                    aePart.importSettings(SettingsFrom.MEMORY_CARD, components, player);
                    if (showMessage) {
                        memoryCard.notifyUser(player, MemoryCardMessages.SETTINGS_LOADED);
                    }
                    return true;
                }
            }
            
            // Fallback to generic settings import for non-matching types
            MemoryCardItem.importGenericSettingsAndNotify(part, components, player);
            return true;
        } catch (Exception e) {
            LOGGER.warn("Failed to apply memory card settings to part", e);
        }

        return false;
    }
}
