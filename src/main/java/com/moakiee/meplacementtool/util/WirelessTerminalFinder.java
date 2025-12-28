package com.moakiee.meplacementtool.util;

import appeng.items.tools.powered.WirelessCraftingTerminalItem;
import appeng.items.tools.powered.WirelessTerminalItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.util.function.Consumer;

/**
 * Utility to find a wireless terminal in player's inventory.
 * Checks: main hand, off hand, inventory slots, and Curios slots (if loaded).
 */
public final class WirelessTerminalFinder {
    private WirelessTerminalFinder() {}

    public static final class FoundTerminal {
        public final ItemStack stack;
        public final int slotIndex;
        public final InteractionHand hand;
        public final String curiosSlotId;
        public final int curiosIndex;
        private final Consumer<ItemStack> setter;

        public FoundTerminal(ItemStack stack, Consumer<ItemStack> setter, int slotIndex, InteractionHand hand, String curiosSlotId, int curiosIndex) {
            this.stack = stack;
            this.setter = setter;
            this.slotIndex = slotIndex;
            this.hand = hand;
            this.curiosSlotId = curiosSlotId;
            this.curiosIndex = curiosIndex;
        }

        public boolean isEmpty() {
            return stack == null || stack.isEmpty();
        }

        public boolean isInCurios() {
            return curiosSlotId != null;
        }

        public void set(ItemStack newStack) {
            setter.accept(newStack);
        }
    }

    /**
     * Find a wireless terminal in the player's inventory (including Curios slots if available).
     * @param player The player to search
     * @return Found terminal info, or empty if not found
     */
    public static FoundTerminal find(Player player) {
        if (player == null) {
            return new FoundTerminal(ItemStack.EMPTY, s -> {}, -1, null, null, -1);
        }

        // 1) Check main hand
        var main = player.getMainHandItem();
        if (isWirelessTerminal(main)) {
            return new FoundTerminal(main, ns -> player.setItemInHand(InteractionHand.MAIN_HAND, ns), -1, InteractionHand.MAIN_HAND, null, -1);
        }

        // 2) Check off hand
        var off = player.getOffhandItem();
        if (isWirelessTerminal(off)) {
            return new FoundTerminal(off, ns -> player.setItemInHand(InteractionHand.OFF_HAND, ns), -1, InteractionHand.OFF_HAND, null, -1);
        }

        // 3) Check inventory slots
        var inv = player.getInventory();
        int size = inv.getContainerSize();
        for (int i = 0; i < size; i++) {
            ItemStack st = inv.getItem(i);
            if (isWirelessTerminal(st)) {
                final int slot = i;
                return new FoundTerminal(st, ns -> inv.setItem(slot, ns), slot, null, null, -1);
            }
        }

        // 4) Check Curios slots (if Curios mod is loaded)
        if (ModList.get().isLoaded("curios")) {
            try {
                var result = findInCurios(player);
                if (result != null && !result.isEmpty()) {
                    return result;
                }
            } catch (Throwable ignored) {
                // Curios API not available at runtime, ignore
            }
        }

        return new FoundTerminal(ItemStack.EMPTY, s -> {}, -1, null, null, -1);
    }

    private static boolean isWirelessTerminal(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.getItem() instanceof WirelessTerminalItem 
            || stack.getItem() instanceof WirelessCraftingTerminalItem;
    }

    /**
     * Search Curios slots for a wireless terminal.
     * This is in a separate method to avoid class loading issues when Curios is not present.
     */
    private static FoundTerminal findInCurios(Player player) {
        var resolved = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).resolve();
        if (resolved.isPresent()) {
            var handler = resolved.get();
            for (var entry : handler.getCurios().entrySet()) {
                String slotId = entry.getKey();
                var stacksHandler = entry.getValue();
                var stacks = stacksHandler.getStacks();
                int slots = stacks.getSlots();
                for (int i = 0; i < slots; i++) {
                    ItemStack st = stacks.getStackInSlot(i);
                    if (isWirelessTerminal(st)) {
                        final int slot = i;
                        Consumer<ItemStack> setter = ns -> stacks.setStackInSlot(slot, ns);
                        return new FoundTerminal(st, setter, -1, null, slotId, slot);
                    }
                }
            }
        }
        return null;
    }
}
