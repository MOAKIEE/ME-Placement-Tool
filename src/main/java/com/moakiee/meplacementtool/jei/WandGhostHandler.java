package com.moakiee.meplacementtool.jei;

import com.moakiee.meplacementtool.WandMenu;
import com.moakiee.meplacementtool.WandScreen;
import com.moakiee.meplacementtool.GhostSlot;
import com.moakiee.meplacementtool.network.UpdateWandSlotPayload;

import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.neoforge.NeoForgeTypes;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.api.stacks.GenericStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Ghost ingredient handler for dragging items from JEI into wand slots.
 * References AE2-JEI-Integration's GhostIngredientHandler implementation.
 * Supports both items and fluids via GenericStack wrapping.
 */
public class WandGhostHandler implements IGhostIngredientHandler<WandScreen> {

    @Override
    public <I> List<Target<I>> getTargetsTyped(WandScreen gui, ITypedIngredient<I> ingredient, boolean doStart) {
        List<Target<I>> targets = new ArrayList<>();

        var menu = gui.getMenu();

        // Try to get ItemStack directly first, then try converting fluids via GenericStack
        ItemStack stack = ingredient.getItemStack().orElse(ItemStack.EMPTY);
        if (stack.isEmpty()) {
            // Check if this is a fluid ingredient
            stack = convertToItemStack(ingredient.getType(), ingredient.getIngredient());
        }
        if (stack == null || stack.isEmpty()) {
            return targets;
        }

        // Create targets for visible ghost slots
        var ghostSlots = menu.getGhostSlots();
        for (int i = 0; i < ghostSlots.size(); i++) {
            GhostSlot slot = ghostSlots.get(i);
            // Skip off-screen slots
            if (slot.x < 0 || slot.y < 0) continue;

            // Create target for this slot
            targets.add(new SlotTarget<>(gui, menu, i, stack.copyWithCount(1)));
        }

        return targets;
    }

    @Override
    public void onComplete() {
        // Nothing special to do
    }

    @Override
    public boolean shouldHighlightTargets() {
        return true;
    }

    /**
     * Target for a specific ghost slot, similar to AE2's ItemSlotTarget pattern.
     */
    private static class SlotTarget<I> implements Target<I> {
        private final Rect2i area;
        private final WandMenu menu;
        private final int visualIndex;
        private final int actualIndex;
        private final ItemStack stackToPlace;

        public SlotTarget(WandScreen gui, WandMenu menu, int visualIndex, ItemStack stackToPlace) {
            this.menu = menu;
            this.visualIndex = visualIndex;
            this.actualIndex = menu.getActualSlotIndex(visualIndex);
            this.stackToPlace = stackToPlace;

            GhostSlot slot = menu.getGhostSlots().get(visualIndex);
            this.area = new Rect2i(slot.x + gui.getGuiLeft(), slot.y + gui.getGuiTop(), 16, 16);
        }

        @Override
        public Rect2i getArea() {
            return area;
        }

        @Override
        public void accept(I ingredient) {
            try {
                // Update the local handler
                menu.getHandler().setStackInSlot(actualIndex, stackToPlace);

                // Send packet to server to persist the change (like AE2's InventoryActionPacket)
                PacketDistributor.sendToServer(new UpdateWandSlotPayload(actualIndex, stackToPlace));
            } catch (Throwable ignored) {
                // Swallow to avoid JEI breaking
            }
        }
    }

    /**
     * Convert a JEI ingredient to an ItemStack.
     * For fluids, wraps them in AE2's GenericStack wrapper item.
     */
    @SuppressWarnings("unchecked")
    private static <T> ItemStack convertToItemStack(IIngredientType<T> type, T ingredient) {
        // Check if it's a fluid stack
        if (type == NeoForgeTypes.FLUID_STACK) {
            FluidStack fluidStack = (FluidStack) ingredient;
            if (!fluidStack.isEmpty()) {
                // Convert to AE2 GenericStack then wrap
                var genericStack = GenericStack.fromFluidStack(fluidStack);
                if (genericStack != null) {
                    return GenericStack.wrapInItemStack(genericStack);
                }
            }
        }
        return ItemStack.EMPTY;
    }
}
