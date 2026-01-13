package com.moakiee.meplacementtool.rei;

import java.util.stream.Stream;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;

import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.drag.DraggableStack;
import me.shedaniel.rei.api.client.gui.drag.DraggableStackVisitor;
import me.shedaniel.rei.api.client.gui.drag.DraggedAcceptorResult;
import me.shedaniel.rei.api.client.gui.drag.DraggingContext;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.entry.type.VanillaEntryTypes;

import dev.architectury.fluid.FluidStack;
import dev.architectury.hooks.fluid.forge.FluidStackHooksForge;

import appeng.api.stacks.GenericStack;

import com.moakiee.meplacementtool.GhostSlot;
import com.moakiee.meplacementtool.WandMenu;
import com.moakiee.meplacementtool.WandScreen;
import com.moakiee.meplacementtool.network.UpdateWandSlotPayload;

import net.neoforged.neoforge.network.PacketDistributor;

/**
 * REI ghost ingredient handler for dragging items from REI into wand slots.
 * References AE2's GhostIngredientHandler implementation.
 * Supports both items and fluids via GenericStack wrapping.
 */
@SuppressWarnings("rawtypes")
class WandGhostIngredientHandler implements DraggableStackVisitor<WandScreen> {

    @Override
    public <R extends Screen> boolean isHandingScreen(R screen) {
        return screen instanceof WandScreen;
    }

    @Override
    public Stream<BoundsProvider> getDraggableAcceptingBounds(DraggingContext<WandScreen> context,
            DraggableStack stack) {
        
        // Convert REI entry stack to ItemStack
        ItemStack itemStack = entryStackToItemStack(stack.getStack());
        if (itemStack == null || itemStack.isEmpty()) {
            return Stream.of();
        }

        WandScreen screen = context.getScreen();
        WandMenu menu = screen.getMenu();
        
        // Return bounds for all visible ghost slots
        return menu.getGhostSlots().stream()
                .filter(slot -> slot.x >= 0 && slot.y >= 0) // Skip off-screen slots
                .map(slot -> {
                    int x = slot.x + screen.getGuiLeft();
                    int y = slot.y + screen.getGuiTop();
                    return BoundsProvider.ofRectangle(new Rectangle(x, y, 16, 16));
                });
    }

    @Override
    public DraggedAcceptorResult acceptDraggedStack(DraggingContext<WandScreen> context, DraggableStack stack) {
        // Convert REI entry stack to ItemStack
        ItemStack itemStack = entryStackToItemStack(stack.getStack());
        if (itemStack == null || itemStack.isEmpty()) {
            return DraggedAcceptorResult.PASS;
        }

        var pos = context.getCurrentPosition();
        if (pos == null) {
            return DraggedAcceptorResult.PASS;
        }

        WandScreen screen = context.getScreen();
        WandMenu menu = screen.getMenu();
        
        // Find which slot was targeted
        var ghostSlots = menu.getGhostSlots();
        for (int i = 0; i < ghostSlots.size(); i++) {
            GhostSlot slot = ghostSlots.get(i);
            // Skip off-screen slots
            if (slot.x < 0 || slot.y < 0) continue;
            
            int slotX = slot.x + screen.getGuiLeft();
            int slotY = slot.y + screen.getGuiTop();
            
            // Check if position is within this slot
            if (pos.x >= slotX && pos.x < slotX + 16 && pos.y >= slotY && pos.y < slotY + 16) {
                try {
                    int actualIndex = menu.getActualSlotIndex(i);
                    ItemStack stackToPlace = itemStack.copyWithCount(1);
                    
                    // Update the local handler
                    menu.getHandler().setStackInSlot(actualIndex, stackToPlace);
                    
                    // Send packet to server to persist the change
                    PacketDistributor.sendToServer(new UpdateWandSlotPayload(actualIndex, stackToPlace));
                    
                    return DraggedAcceptorResult.ACCEPTED;
                } catch (Throwable ignored) {
                    // Swallow to avoid REI breaking
                }
            }
        }

        return DraggedAcceptorResult.PASS;
    }

    /**
     * Convert a REI EntryStack to an ItemStack.
     * For fluids, wraps them in AE2's GenericStack wrapper item.
     * References AE2's GenericEntryStackHelper and FluidIngredientConverter.
     */
    private static ItemStack entryStackToItemStack(EntryStack<?> entryStack) {
        if (entryStack == null) {
            return ItemStack.EMPTY;
        }
        
        // Handle vanilla item type
        if (entryStack.getType() == VanillaEntryTypes.ITEM) {
            return entryStack.<ItemStack>castValue();
        }
        
        // Handle fluid type - wrap in GenericStack using AE2's approach
        if (entryStack.getType() == VanillaEntryTypes.FLUID) {
            FluidStack fluidStack = entryStack.castValue();
            if (fluidStack != null && !fluidStack.isEmpty()) {
                // Convert architectury FluidStack to NeoForge FluidStack
                var neoForgeFluidStack = FluidStackHooksForge.toForge(fluidStack);
                // Convert to GenericStack and wrap in ItemStack
                var genericStack = GenericStack.fromFluidStack(neoForgeFluidStack);
                if (genericStack != null) {
                    return GenericStack.wrapInItemStack(genericStack);
                }
            }
        }
        
        return ItemStack.EMPTY;
    }
}
