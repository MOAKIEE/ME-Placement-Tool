package com.moakiee.meplacementtool;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A ghost slot that doesn't store items in any container - purely visual for configuration.
 * This slot uses a dummy container to avoid interfering with the actual item handler.
 */
public class GhostSlot extends Slot {
    // Dummy container that doesn't persist anything
    private static final Container DUMMY_CONTAINER = new SimpleContainer(1);
    
    private final int visualIndex;
    private final java.util.function.Consumer<ItemStack> onChange;

    public GhostSlot(int visualIndex, int x, int y, java.util.function.Consumer<ItemStack> onChange) {
        super(DUMMY_CONTAINER, 0, x, y);
        this.visualIndex = visualIndex;
        this.onChange = onChange;
    }

    public GhostSlot(int visualIndex, int x, int y) {
        this(visualIndex, x, y, (s) -> {});
    }

    public int getVisualIndex() {
        return visualIndex;
    }

    public java.util.function.Consumer<ItemStack> getOnChange() {
        return onChange;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return true; // Allow any item to be placed as a ghost
    }

    @Override
    public boolean mayPickup(Player player) {
        return true;
    }

    @Override
    public int getMaxStackSize() {
        return 1; // Ghost slots only hold 1 item visually
    }

    @Override
    public ItemStack getItem() {
        // Return empty - actual items are rendered manually based on page and handler
        return ItemStack.EMPTY;
    }

    @Override
    public void set(ItemStack stack) {
        // Only trigger the callback when a NON-EMPTY item is set (e.g., by JEI)
        // Empty items are usually from sync operations, not user intent
        // User clearing is handled by WandMenu.clicked()
        if (!stack.isEmpty() && onChange != null) {
            onChange.accept(stack);
        }
    }

    @Override
    public ItemStack remove(int amount) {
        // Do NOT trigger callback here - clearing is handled by WandMenu.clicked()
        // This method is often called by container sync which we don't want
        return ItemStack.EMPTY;
    }

    @Override
    public boolean hasItem() {
        return false;
    }
}
