package com.moakiee.meplacementtool;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A ghost slot that doesn't store items in any container - purely visual for configuration.
 * This slot uses a dummy container to avoid interfering with the actual item handler.
 * 
 * The slot can be configured with a displayStackSupplier to return the item that should
 * be displayed, allowing proper integration with vanilla slot rendering (which handles
 * Z-ordering correctly for things like JEI/REI overlays).
 */
public class GhostSlot extends Slot {
    // Dummy container that doesn't persist anything
    private static final Container DUMMY_CONTAINER = new SimpleContainer(1);
    
    private final int visualIndex;
    private final Consumer<ItemStack> onChange;
    private Supplier<ItemStack> displayStackSupplier = () -> ItemStack.EMPTY;

    public GhostSlot(int visualIndex, int x, int y, Consumer<ItemStack> onChange) {
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

    public Consumer<ItemStack> getOnChange() {
        return onChange;
    }
    
    /**
     * Set a supplier that provides the ItemStack to display in this slot.
     * This is used to integrate with vanilla's slot rendering system for proper Z-ordering.
     */
    public void setDisplayStackSupplier(Supplier<ItemStack> supplier) {
        this.displayStackSupplier = supplier;
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
        // Return the display stack from the supplier for proper vanilla rendering
        return displayStackSupplier.get();
    }

    @Override
    public void set(ItemStack stack) {
        // Only trigger the callback when a NON-EMPTY item is set (e.g., by JEI/REI)
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
        // Return true if displayStackSupplier has an item
        return !displayStackSupplier.get().isEmpty();
    }
}
