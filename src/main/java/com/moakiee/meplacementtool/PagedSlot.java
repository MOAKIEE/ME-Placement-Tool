package com.moakiee.meplacementtool;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;

import java.util.function.IntSupplier;

/**
 * A slot that displays items from different handler indices based on current page.
 * Visual position stays fixed, but the actual slot index changes with page.
 */
public class PagedSlot extends SlotItemHandler {
    private final int visualIndex; // 0-8 position in the 3x3 grid
    private final IntSupplier pageSupplier;
    private final IItemHandler itemHandler;

    public PagedSlot(IItemHandler itemHandler, int visualIndex, int x, int y, IntSupplier pageSupplier) {
        super(itemHandler, visualIndex, x, y);
        this.visualIndex = visualIndex;
        this.pageSupplier = pageSupplier;
        this.itemHandler = itemHandler;
    }

    /**
     * Get the actual slot index in the handler based on current page.
     */
    public int getActualSlotIndex() {
        return pageSupplier.getAsInt() * 9 + visualIndex;
    }

    @Override
    public ItemStack getItem() {
        int actualIndex = getActualSlotIndex();
        if (actualIndex >= 0 && actualIndex < itemHandler.getSlots()) {
            return itemHandler.getStackInSlot(actualIndex);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void set(ItemStack stack) {
        int actualIndex = getActualSlotIndex();
        if (actualIndex >= 0 && actualIndex < itemHandler.getSlots()) {
            ((net.minecraftforge.items.ItemStackHandler) itemHandler).setStackInSlot(actualIndex, stack);
            this.setChanged();
        }
    }

    @Override
    public void setChanged() {
        // Notify that the slot contents changed
    }

    @Override
    public ItemStack remove(int amount) {
        int actualIndex = getActualSlotIndex();
        if (actualIndex >= 0 && actualIndex < itemHandler.getSlots()) {
            return itemHandler.extractItem(actualIndex, amount, false);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return true;
    }

    @Override
    public int getMaxStackSize() {
        return 64;
    }

    @Override
    public boolean isSameInventory(net.minecraft.world.inventory.Slot other) {
        return other instanceof PagedSlot ps && ps.itemHandler == this.itemHandler;
    }
}
