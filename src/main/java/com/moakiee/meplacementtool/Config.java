package com.moakiee.meplacementtool;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber(modid = MEPlacementToolMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config
{
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.DoubleValue ME_PLACEMENT_TOOL_ENERGY_CAPACITY;
    public static final ForgeConfigSpec.DoubleValue ME_PLACEMENT_TOOL_ENERGY_COST;
    public static final ForgeConfigSpec.DoubleValue MULTIBLOCK_PLACEMENT_TOOL_ENERGY_CAPACITY;
    public static final ForgeConfigSpec.DoubleValue MULTIBLOCK_PLACEMENT_TOOL_BASE_ENERGY_COST;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> NBT_WHITELIST_MODS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> NBT_WHITELIST_ITEMS;

    static {
        BUILDER.push("energy");

        BUILDER.comment("Energy capacity for ME Placement Tool (in AE)");
        ME_PLACEMENT_TOOL_ENERGY_CAPACITY = BUILDER
                .defineInRange("mePlacementToolEnergyCapacity", 1_600_000.0d, 0, Double.MAX_VALUE);

        BUILDER.comment("Energy cost per placement for ME Placement Tool (in AE)");
        ME_PLACEMENT_TOOL_ENERGY_COST = BUILDER
                .defineInRange("mePlacementToolEnergyCost", 50.0d, 0, Double.MAX_VALUE);

        BUILDER.comment("Energy capacity for Multiblock Placement Tool (in AE)");
        MULTIBLOCK_PLACEMENT_TOOL_ENERGY_CAPACITY = BUILDER
                .defineInRange("multiblockPlacementToolEnergyCapacity", 3_200_000.0d, 0, Double.MAX_VALUE);

        BUILDER.comment("Base energy cost per placement for Multiblock Placement Tool (in AE)");
        MULTIBLOCK_PLACEMENT_TOOL_BASE_ENERGY_COST = BUILDER
                .defineInRange("multiblockPlacementToolBaseEnergyCost", 200.0d, 0, Double.MAX_VALUE);

        BUILDER.pop();

        BUILDER.push("nbt");

        BUILDER.comment("List of mod IDs whose items should NOT ignore NBT when placing.",
                "By default, items only match by ID and ignore NBT data.",
                "Add mod IDs here to preserve NBT matching for those mods' items.",
                "For AE2 facades, this ensures that facades with different textures are treated as different items.",
                "Example: [\"ae2\", \"refinedstorage\"]");
        NBT_WHITELIST_MODS = BUILDER
                .defineListAllowEmpty("nbtWhitelistMods", List.of(), obj -> obj instanceof String);

        BUILDER.comment("List of specific items that should NOT ignore NBT when placing.",
                "Format: \"modid:itemname\". Wildcards are supported: \"modid:*\" for all items from a mod.",
                "This takes priority over nbtWhitelistMods.",
                "For AE2 facades, use \"ae2:facade\" to preserve NBT matching for all facades.",
                "Example: [\"ae2:facade\", \"mekanism:basic_block\", \"refinedstorage:*\"]");
        NBT_WHITELIST_ITEMS = BUILDER
                .defineListAllowEmpty("nbtWhitelistItems", List.of("ae2:facade"), obj -> obj instanceof String);

        BUILDER.pop();
    }

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static double mePlacementToolEnergyCapacity;
    public static double mePlacementToolEnergyCost;
    public static double multiblockPlacementToolEnergyCapacity;
    public static double multiblockPlacementToolBaseEnergyCost;
    public static Set<String> nbtWhitelistMods;
    public static Set<String> nbtWhitelistItems;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event)
    {
        mePlacementToolEnergyCapacity = ME_PLACEMENT_TOOL_ENERGY_CAPACITY.get();
        mePlacementToolEnergyCost = ME_PLACEMENT_TOOL_ENERGY_COST.get();
        multiblockPlacementToolEnergyCapacity = MULTIBLOCK_PLACEMENT_TOOL_ENERGY_CAPACITY.get();
        multiblockPlacementToolBaseEnergyCost = MULTIBLOCK_PLACEMENT_TOOL_BASE_ENERGY_COST.get();

        nbtWhitelistMods = NBT_WHITELIST_MODS.get().stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
        
        nbtWhitelistItems = NBT_WHITELIST_ITEMS.get().stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }

    /**
     * Check if the given mod ID is in the NBT whitelist (items from this mod should preserve NBT)
     */
    public static boolean isModInNbtWhitelist(String modId) {
        return nbtWhitelistMods != null && nbtWhitelistMods.contains(modId.toLowerCase());
    }

    /**
     * Check if the given item is in the NBT whitelist (this item should preserve NBT)
     * Supports wildcards: "modid:*" matches all items from that mod
     */
    public static boolean isItemInNbtWhitelist(net.minecraft.world.item.Item item) {
        if (nbtWhitelistItems == null || nbtWhitelistItems.isEmpty()) {
            return false;
        }
        
        var itemId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item);
        if (itemId == null) {
            return false;
        }
        
        String itemIdStr = itemId.toString().toLowerCase();
        String modId = itemId.getNamespace().toLowerCase();
        
        // Check exact match first
        if (nbtWhitelistItems.contains(itemIdStr)) {
            return true;
        }
        
        // Check wildcard match (modid:*)
        String wildcard = modId + ":*";
        if (nbtWhitelistItems.contains(wildcard)) {
            return true;
        }
        
        return false;
    }

    /**
     * Check if the given ItemStack should ignore NBT when matching in AE network.
     * Returns true if NBT should be ignored, false if NBT should be preserved.
     */
    public static boolean shouldIgnoreNbt(net.minecraft.world.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        
        // Check item-level whitelist first (highest priority)
        if (isItemInNbtWhitelist(stack.getItem())) {
            return false;
        }
        
        // Check mod-level whitelist
        var itemId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId == null) {
            return false;
        }
        return !isModInNbtWhitelist(itemId.getNamespace());
    }

    /**
     * Find all AEItemKeys in the storage that match the target item (by item ID, ignoring NBT if configured).
     * Returns a list of (AEItemKey, count) pairs.
     * 
     * @param storage The AE storage to search in
     * @param target The target ItemStack to match
     * @return List of matching AEItemKeys with their counts, empty if none found
     */
    public static java.util.List<java.util.Map.Entry<appeng.api.stacks.AEItemKey, Long>> findAllMatchingKeys(
            appeng.api.storage.MEStorage storage,
            net.minecraft.world.item.ItemStack target) {
        java.util.List<java.util.Map.Entry<appeng.api.stacks.AEItemKey, Long>> result = new java.util.ArrayList<>();
        
        // DEBUG LOGS - Remove after debugging facade placement issues
        var targetItemId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(target.getItem());
        LOGGER.debug("=== FIND MATCHING KEYS DEBUG START ===");
        LOGGER.debug("Target item: {}, hasTag: {}", targetItemId, target.hasTag());
        if (target.hasTag()) {
            LOGGER.debug("Target NBT: {}", target.getTag());
        }
        
        if (target == null || target.isEmpty()) {
            LOGGER.debug("Target is null or empty, returning empty result");
            LOGGER.debug("=== FIND MATCHING KEYS DEBUG END ===");
            return result;
        }
        
        var itemId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(target.getItem());
        if (itemId == null) {
            var key = appeng.api.stacks.AEItemKey.of(target);
            if (key != null) {
                long count = storage.extract(key, Long.MAX_VALUE, appeng.api.config.Actionable.SIMULATE, null);
                if (count > 0) {
                    result.add(java.util.Map.entry(key, count));
                }
            }
            LOGGER.debug("itemId is null, result size: {}", result.size());
            LOGGER.debug("=== FIND MATCHING KEYS DEBUG END ===");
            return result;
        }
        
        boolean ignoreNbt = shouldIgnoreNbt(target);
        LOGGER.debug("ignoreNbt: {}", ignoreNbt);
        LOGGER.debug("isItemInNbtWhitelist: {}", isItemInNbtWhitelist(target.getItem()));
        LOGGER.debug("isModInNbtWhitelist: {}", isModInNbtWhitelist(itemId.getNamespace()));
        
        // If we need exact NBT match, just check for that specific key
        if (!ignoreNbt) {
            LOGGER.debug("Exact NBT match required");
            var key = appeng.api.stacks.AEItemKey.of(target);
            if (key != null) {
                long count = storage.extract(key, Long.MAX_VALUE, appeng.api.config.Actionable.SIMULATE, null);
                if (count > 0) {
                    result.add(java.util.Map.entry(key, count));
                    LOGGER.debug("Found exact NBT match: {}, count: {}", key, count);
                } else {
                    LOGGER.debug("No exact NBT match found");
                }
            }
            LOGGER.debug("=== FIND MATCHING KEYS DEBUG END ===");
            return result;
        }
        
        // Otherwise, find all items with the same ID (ignoring NBT)
        LOGGER.debug("Ignoring NBT, finding all items with same ID");
        var targetItem = target.getItem();
        var availableStacks = storage.getAvailableStacks();
        
        for (var entry : availableStacks) {
            var key = entry.getKey();
            if (key instanceof appeng.api.stacks.AEItemKey itemKey) {
                if (itemKey.getItem() == targetItem && entry.getLongValue() > 0) {
                    result.add(java.util.Map.entry(itemKey, entry.getLongValue()));
                    LOGGER.debug("Found matching item (ignoring NBT): {}, count: {}", itemKey, entry.getLongValue());
                }
            }
        }
        
        LOGGER.debug("Total matches found: {}", result.size());
        LOGGER.debug("=== FIND MATCHING KEYS DEBUG END ===");
        
        return result;
    }

    /**
     * Get the total count of all matching items in storage (summing across different NBTs if ignoring NBT).
     */
    public static long getTotalMatchingCount(
            appeng.api.storage.MEStorage storage,
            net.minecraft.world.item.ItemStack target) {
        return findAllMatchingKeys(storage, target).stream()
                .mapToLong(java.util.Map.Entry::getValue)
                .sum();
    }

    /**
     * Find an AEItemKey in the storage that matches the target item.
     * If the item's mod is NOT in the whitelist, NBT will be ignored (finds any item with same ID).
     * If the item's mod IS in the whitelist, exact NBT match is required.
     * 
     * @param storage The AE storage to search in
     * @param target The target ItemStack to match
     * @return The matching AEItemKey found in storage, or null if not found
     */
    public static appeng.api.stacks.AEItemKey findMatchingKey(
            appeng.api.storage.MEStorage storage,
            net.minecraft.world.item.ItemStack target) {
        var matches = findAllMatchingKeys(storage, target);
        return matches.isEmpty() ? null : matches.get(0).getKey();
    }

}
