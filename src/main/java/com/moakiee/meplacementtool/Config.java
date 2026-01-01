package com.moakiee.meplacementtool;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.slf4j.Logger;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;

/**
 * Configuration for ME Placement Tool
 */
@EventBusSubscriber(modid = MEPlacementToolMod.MODID, bus = EventBusSubscriber.Bus.MOD)
public class Config {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // Energy settings
    public static final ModConfigSpec.DoubleValue ME_PLACEMENT_TOOL_ENERGY_CAPACITY;
    public static final ModConfigSpec.DoubleValue ME_PLACEMENT_TOOL_ENERGY_COST;
    public static final ModConfigSpec.DoubleValue MULTIBLOCK_PLACEMENT_TOOL_ENERGY_CAPACITY;
    public static final ModConfigSpec.DoubleValue MULTIBLOCK_PLACEMENT_TOOL_BASE_ENERGY_COST;

    // NBT whitelist settings
    public static final ModConfigSpec.ConfigValue<List<? extends String>> NBT_WHITELIST_MODS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> NBT_WHITELIST_ITEMS;

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
                .defineListAllowEmpty("nbtWhitelistMods", List.of(), () -> "", obj -> obj instanceof String);

        BUILDER.comment("List of specific items that should NOT ignore NBT when placing.",
                "Format: \"modid:itemname\". Wildcards are supported: \"modid:*\" for all items from a mod.",
                "This takes priority over nbtWhitelistMods.",
                "For AE2 facades, use \"ae2:facade\" to preserve NBT matching for all facades.",
                "Example: [\"ae2:facade\", \"mekanism:basic_block\", \"refinedstorage:*\"]");
        NBT_WHITELIST_ITEMS = BUILDER
                .defineListAllowEmpty("nbtWhitelistItems", List.of("ae2:facade"), () -> "", obj -> obj instanceof String);

        BUILDER.pop();
    }

    static final ModConfigSpec SPEC = BUILDER.build();

    // Cached config values
    public static double mePlacementToolEnergyCapacity;
    public static double mePlacementToolEnergyCost;
    public static double multiblockPlacementToolEnergyCapacity;
    public static double multiblockPlacementToolBaseEnergyCost;
    public static Set<String> nbtWhitelistMods;
    public static Set<String> nbtWhitelistItems;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
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

        LOGGER.info("ME Placement Tool config loaded");
    }

    /**
     * Check if the given mod ID is in the NBT whitelist
     */
    public static boolean isModInNbtWhitelist(String modId) {
        return nbtWhitelistMods != null && nbtWhitelistMods.contains(modId.toLowerCase());
    }

    /**
     * Check if the given item is in the NBT whitelist
     */
    public static boolean isItemInNbtWhitelist(Item item) {
        if (nbtWhitelistItems == null || nbtWhitelistItems.isEmpty()) {
            return false;
        }

        var itemId = BuiltInRegistries.ITEM.getKey(item);
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
        return nbtWhitelistItems.contains(wildcard);
    }

    /**
     * Check if the given ItemStack should ignore NBT when matching in AE network.
     */
    public static boolean shouldIgnoreNbt(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        // Check item-level whitelist first (highest priority)
        if (isItemInNbtWhitelist(stack.getItem())) {
            return false;
        }

        // Check mod-level whitelist
        var itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId == null) {
            return false;
        }

        // Special-case: AE facades should always preserve NBT
        try {
            if (stack.getItem() instanceof appeng.api.implementations.items.IFacadeItem) {
                return false;
            }
        } catch (Throwable ignored) {
        }

        return !isModInNbtWhitelist(itemId.getNamespace());
    }

    /**
     * Find all AEItemKeys in the storage that match the target item.
     */
    public static java.util.List<java.util.Map.Entry<AEItemKey, Long>> findAllMatchingKeys(
            MEStorage storage,
            ItemStack target) {
        java.util.List<java.util.Map.Entry<AEItemKey, Long>> result = new java.util.ArrayList<>();

        if (target == null || target.isEmpty()) {
            return result;
        }

        var itemId = BuiltInRegistries.ITEM.getKey(target.getItem());
        if (itemId == null) {
            var key = AEItemKey.of(target);
            if (key != null) {
                long count = storage.extract(key, Long.MAX_VALUE, appeng.api.config.Actionable.SIMULATE, null);
                if (count > 0) {
                    result.add(java.util.Map.entry(key, count));
                }
            }
            return result;
        }

        boolean ignoreNbt = shouldIgnoreNbt(target);
        var targetItem = target.getItem();
        var availableStacks = storage.getAvailableStacks();

        if (ignoreNbt) {
            // Ignore NBT: find all items with the same ID
            for (var entry : availableStacks) {
                var key = entry.getKey();
                if (key instanceof AEItemKey itemKey) {
                    if (itemKey.getItem() == targetItem && entry.getLongValue() > 0) {
                        result.add(java.util.Map.entry(itemKey, entry.getLongValue()));
                    }
                }
            }
        } else {
            // Preserve NBT: exact match required
            var exactKey = AEItemKey.of(target);
            if (exactKey != null) {
                for (var entry : availableStacks) {
                    var key = entry.getKey();
                    if (key instanceof AEItemKey itemKey) {
                        if (itemKey.equals(exactKey) && entry.getLongValue() > 0) {
                            result.add(java.util.Map.entry(itemKey, entry.getLongValue()));
                        }
                    }
                }
            }
        }

        return result;
    }

    /**
     * Get the total count of all matching items in storage.
     */
    public static long getTotalMatchingCount(MEStorage storage, ItemStack target) {
        return findAllMatchingKeys(storage, target).stream()
                .mapToLong(java.util.Map.Entry::getValue)
                .sum();
    }

    /**
     * Find an AEItemKey in the storage that matches the target item.
     */
    public static AEItemKey findMatchingKey(MEStorage storage, ItemStack target) {
        var matches = findAllMatchingKeys(storage, target);
        return matches.isEmpty() ? null : matches.get(0).getKey();
    }
}
