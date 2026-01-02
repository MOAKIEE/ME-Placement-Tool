package com.moakiee.meplacementtool.client;

import com.moakiee.meplacementtool.ItemMEPlacementTool;
import com.moakiee.meplacementtool.ItemMultiblockPlacementTool;
import com.moakiee.meplacementtool.ModDataComponents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders tool information HUD on the right side of the crosshair.
 * Displays different information based on the tool type held by the player.
 * Shows HUD for 2 seconds when switching to the tool, then auto-hides.
 * 
 * TODO: When adding ME Cable Placement Tool support to 1.21.1:
 * - Add import for ItemMECablePlacementTool
 * - Add checks for ItemMECablePlacementTool in isPlacementTool() method
 * - Add collectCableToolInfo() method
 * - Add getCableTypeName(), getColorName(), getModeName() helper methods
 * - Uncomment CableToolRadialMenuScreen check in onRenderGuiOverlay
 */
@OnlyIn(Dist.CLIENT)
public class ToolInfoHudRenderer {

    private static final int CROSSHAIR_OFFSET_X = 15;
    private static final float FONT_SCALE = 0.75f;
    private static final int LINE_HEIGHT = 10;
    private static final int TEXT_COLOR = 0xFFFFFF;
    private static final int TEXT_COLOR_DIM = 0xAAAAAA;
    
    // HUD display duration in milliseconds
    private static final long HUD_DISPLAY_DURATION = 2000L;
    
    // Track the last held tool item to detect switching
    private Item lastHeldToolItem = null;
    // Track when the tool was switched to
    private long toolSwitchTime = 0L;

    @SubscribeEvent
    public void onRenderGuiOverlay(RenderGuiLayerEvent.Post event) {
        // Only render after crosshair layer
        if (!event.getName().equals(VanillaGuiLayers.CROSSHAIR)) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.options.hideGui) {
            return;
        }

        // Check if any radial menu is open - don't render HUD if so
        if (mc.screen instanceof RadialMenuScreen || 
            mc.screen instanceof DualLayerRadialMenuScreen) {
            // TODO: Add CableToolRadialMenuScreen check when ME Cable Placement Tool is added
            return;
        }

        // Check main hand and off hand for placement tools
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();

        // Determine current tool item
        Item currentToolItem = null;
        ItemStack currentToolStack = ItemStack.EMPTY;
        
        if (isPlacementTool(mainHand)) {
            currentToolItem = mainHand.getItem();
            currentToolStack = mainHand;
        } else if (isPlacementTool(offHand)) {
            currentToolItem = offHand.getItem();
            currentToolStack = offHand;
        }
        
        // Handle tool switching detection
        long currentTime = System.currentTimeMillis();
        
        if (currentToolItem != null) {
            // Check if we just switched to a tool
            if (lastHeldToolItem != currentToolItem) {
                // Switched to a new tool, reset timer
                toolSwitchTime = currentTime;
                lastHeldToolItem = currentToolItem;
            }
            
            // Check if we're within the display duration
            if (currentTime - toolSwitchTime > HUD_DISPLAY_DURATION) {
                // Time expired, don't render
                return;
            }
        } else {
            // Not holding any tool, reset state
            lastHeldToolItem = null;
            toolSwitchTime = 0L;
            return;
        }

        List<String> lines = new ArrayList<>();

        if (currentToolStack.getItem() instanceof ItemMEPlacementTool) {
            collectMEPlacementToolInfo(currentToolStack, lines);
        } else if (currentToolStack.getItem() instanceof ItemMultiblockPlacementTool) {
            collectMultiblockToolInfo(currentToolStack, lines);
        }
        // TODO: Add ItemMECablePlacementTool check when ME Cable Placement Tool is added
        // else if (currentToolStack.getItem() instanceof ItemMECablePlacementTool) {
        //     collectCableToolInfo(currentToolStack, lines);
        // }

        if (!lines.isEmpty()) {
            renderHudLines(event.getGuiGraphics(), mc, lines);
        }
    }

    /**
     * Check if the given item stack is a placement tool.
     * TODO: Add ItemMECablePlacementTool check when ME Cable Placement Tool is added
     */
    private boolean isPlacementTool(ItemStack stack) {
        return stack.getItem() instanceof ItemMEPlacementTool ||
               stack.getItem() instanceof ItemMultiblockPlacementTool;
        // TODO: Add check for ItemMECablePlacementTool
    }

    /**
     * Collect information for ME Placement Tool.
     */
    private void collectMEPlacementToolInfo(ItemStack tool, List<String> lines) {
        CompoundTag cfg = tool.get(ModDataComponents.PLACEMENT_CONFIG.get());
        if (cfg == null) {
            return;
        }

        int selected = cfg.getInt("SelectedSlot");
        if (selected < 0 || selected >= 18) selected = 0;

        // Get item from config using similar logic to ItemMEPlacementTool
        ItemStack target = getItemFromConfig(cfg, selected);
        if (target != null && !target.isEmpty()) {
            String itemName = target.getHoverName().getString();
            lines.add(Component.translatable("meplacementtool.hud.item", itemName).getString());
        }
    }
    
    /**
     * Get item from config tag for the specified slot.
     */
    private ItemStack getItemFromConfig(CompoundTag cfg, int slot) {
        if (cfg == null) return ItemStack.EMPTY;
        
        CompoundTag itemsTag = cfg.contains("items") ? cfg.getCompound("items") : cfg;
        if (itemsTag.contains("Items")) {
            net.minecraft.nbt.ListTag list = itemsTag.getList("Items", 10);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag itemTag = list.getCompound(i);
                if (itemTag.getInt("Slot") == slot) {
                    return ItemStack.parseOptional(net.minecraft.core.HolderLookup.Provider.create(
                            java.util.stream.Stream.empty()), itemTag);
                }
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * Collect information for Multiblock Placement Tool.
     */
    private void collectMultiblockToolInfo(ItemStack tool, List<String> lines) {
        // First collect item info same as ME Placement Tool
        collectMEPlacementToolInfo(tool, lines);

        // Add placement count
        int placementCount = ItemMultiblockPlacementTool.getPlacementCount(tool);
        lines.add(Component.translatable("meplacementtool.hud.placement_count", placementCount).getString());
    }

    // TODO: When adding ME Cable Placement Tool support, add the following methods:
    // - collectCableToolInfo(ItemStack tool, List<String> lines)
    // - getCableTypeName(ItemMECablePlacementTool.CableType type)
    // - getColorName(AEColor color)
    // - getModeName(ItemMECablePlacementTool.PlacementMode mode)

    /**
     * Render HUD lines on the right side of the crosshair with small font.
     */
    private void renderHudLines(GuiGraphics guiGraphics, Minecraft mc, List<String> lines) {
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        // Center of screen (where crosshair is)
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;

        // Start position (right of crosshair)
        int startX = centerX + CROSSHAIR_OFFSET_X;
        int startY = centerY - (lines.size() * (int)(LINE_HEIGHT * FONT_SCALE)) / 2;

        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(FONT_SCALE, FONT_SCALE, 1.0f);

        // Scale coordinates to match the scaled rendering
        float scaledStartX = startX / FONT_SCALE;
        float scaledStartY = startY / FONT_SCALE;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int y = (int)(scaledStartY + i * LINE_HEIGHT);
            
            // Draw with shadow for better visibility
            guiGraphics.drawString(mc.font, line, (int)scaledStartX, y, TEXT_COLOR, true);
        }

        guiGraphics.pose().popPose();
    }
}
