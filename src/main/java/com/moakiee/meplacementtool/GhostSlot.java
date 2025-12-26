package com.moakiee.meplacementtool;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;

/**
 * A ghost slot that does NOT render its contents automatically.
 * The actual item display and click handling is managed externally (by WandScreen/WandMenu)
 * based on the current page. This avoids Container sync issues with dynamic slot indices.
 */
public class GhostSlot extends SlotItemHandler {
    private final int visualIndex; // 0-8 position in the 3x3 grid
    private final IItemHandler itemHandler;

    public GhostSlot(IItemHandler itemHandler, int visualIndex, int x, int y) {
        super(itemHandler, visualIndex, x, y);
        this.visualIndex = visualIndex;
        this.itemHandler = itemHandler;
    }

    public int getVisualIndex() {
        return visualIndex;
    }

    /**
     * Returns EMPTY so vanilla rendering shows nothing.
     * The actual item is rendered by WandScreen based on current page.
     */
    @Override
    public ItemStack getItem() {
        // Return empty so the default slot rendering shows nothing
        // WandScreen will render the correct item based on page
        return ItemStack.EMPTY;
    }

    @Override
    public void set(ItemStack stack) {
        // Do nothing - item setting is handled by WandMenu.clicked() with page offset
    }

    @Override
    public void setChanged() {
        // No-op
    }

    @Override
    public ItemStack remove(int amount) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return false; // Ghost slot, no placing
    }

    @Override
    public boolean mayPickup(Player player) {
        return false; // Ghost slot, no picking up
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }
}
