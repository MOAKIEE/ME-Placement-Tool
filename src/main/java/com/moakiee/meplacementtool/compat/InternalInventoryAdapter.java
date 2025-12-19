package com.moakiee.meplacementtool.compat;

import appeng.api.inventories.InternalInventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

public class InternalInventoryAdapter implements InternalInventory {
    private final ItemStackHandler handler;

    public InternalInventoryAdapter(ItemStackHandler handler) {
        this.handler = handler;
    }

    public ItemStackHandler getHandler() {
        return handler;
    }

    @Override
    public int size() {
        return handler.getSlots();
    }

    @Override
    public int getSlotLimit(int slot) {
        return handler.getSlotLimit(slot);
    }

    @Override
    public ItemStack getStackInSlot(int slotIndex) {
        return handler.getStackInSlot(slotIndex);
    }

    @Override
    public void setItemDirect(int slotIndex, ItemStack stack) {
        handler.setStackInSlot(slotIndex, stack);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return true;
    }
}
