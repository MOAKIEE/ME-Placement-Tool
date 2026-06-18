package com.moakiee.meplacementtool.client;

import appeng.api.util.AEColor;
import com.moakiee.meplacementtool.ItemMECablePlacementTool;
import com.moakiee.meplacementtool.ItemMEPlacementTool;
import com.moakiee.meplacementtool.ItemMultiblockPlacementTool;
import com.moakiee.meplacementtool.ModDataComponents;
import com.moakiee.meplacementtool.NbtCompat;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.gui.GuiLayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders tool information HUD on the right side of the crosshair.
 * Displays different information based on the tool type held by the player.
 * Shows HUD for 2 seconds when switching to the tool, then auto-hides.
 */
public class ToolInfoHudRenderer implements GuiLayer {

    public static final ToolInfoHudRenderer INSTANCE = new ToolInfoHudRenderer();

    private static final int CROSSHAIR_OFFSET_X = 15;
    private static final float FONT_SCALE = 0.75f;
    private static final int LINE_HEIGHT = 10;
    private static final int TEXT_COLOR = GuiTextColors.opaque(0xFFFFFF);
    private static final long HUD_DISPLAY_DURATION = 2000L;

    private final ToolHudDisplayTracker displayTracker = new ToolHudDisplayTracker(HUD_DISPLAY_DURATION);

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.options.hideGui) {
            displayTracker.shouldDisplay(null, System.currentTimeMillis());
            return;
        }

        // Check if any radial menu is open - don't render HUD if so
        if (mc.screen instanceof RadialMenuScreen || 
            mc.screen instanceof DualLayerRadialMenuScreen) {
            return;
        }

        HeldTool heldTool = findHeldTool(player);
        long currentTime = System.currentTimeMillis();

        if (heldTool == null) {
            displayTracker.shouldDisplay(null, currentTime);
            return;
        }

        List<String> lines = new ArrayList<>();
        collectToolInfo(heldTool.stack(), lines);

        ToolHudDisplayTracker.HeldToolKey key = new ToolHudDisplayTracker.HeldToolKey(
                heldTool.hand(),
                heldTool.inventorySlot(),
                BuiltInRegistries.ITEM.getKey(heldTool.stack().getItem()).toString(),
                String.join("\n", lines));

        if (!displayTracker.shouldDisplay(key, currentTime)) {
            return;
        }

        if (!lines.isEmpty()) {
            renderHudLines(graphics, mc, lines);
        }
    }

    private HeldTool findHeldTool(Player player) {
        ItemStack mainHand = player.getMainHandItem();
        if (isPlacementTool(mainHand)) {
            return new HeldTool("main", player.getInventory().getSelectedSlot(), mainHand);
        }

        ItemStack offHand = player.getOffhandItem();
        if (isPlacementTool(offHand)) {
            return new HeldTool("offhand", Inventory.SLOT_OFFHAND, offHand);
        }

        return null;
    }

    private boolean isPlacementTool(ItemStack stack) {
        return stack.getItem() instanceof ItemMEPlacementTool ||
               stack.getItem() instanceof ItemMultiblockPlacementTool ||
               stack.getItem() instanceof ItemMECablePlacementTool;
    }

    private void collectToolInfo(ItemStack currentToolStack, List<String> lines) {
        if (currentToolStack.getItem() instanceof ItemMEPlacementTool) {
            collectMEPlacementToolInfo(currentToolStack, lines);
        } else if (currentToolStack.getItem() instanceof ItemMultiblockPlacementTool) {
            collectMultiblockToolInfo(currentToolStack, lines);
        } else if (currentToolStack.getItem() instanceof ItemMECablePlacementTool) {
            collectCableToolInfo(currentToolStack, lines);
        }
    }

    /**
     * Collect information for ME Placement Tool.
     */
    private void collectMEPlacementToolInfo(ItemStack tool, List<String> lines) {
        CompoundTag cfg = tool.get(ModDataComponents.PLACEMENT_CONFIG.get());
        if (cfg == null) {
            return;
        }

        int selected = cfg.getIntOr("SelectedSlot", 0);
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
        
        CompoundTag itemsTag = cfg.contains("items") ? cfg.getCompoundOrEmpty("items") : cfg;
        if (itemsTag.contains("Items")) {
            net.minecraft.nbt.ListTag list = itemsTag.getListOrEmpty("Items");
            for (int i = 0; i < list.size(); i++) {
                CompoundTag itemTag = list.getCompoundOrEmpty(i);
                if (itemTag.getIntOr("Slot", -1) == slot) {
                    var level = Minecraft.getInstance().level;
                    var lookup = level != null
                            ? level.registryAccess()
                            : net.minecraft.core.HolderLookup.Provider.create(java.util.stream.Stream.empty());
                    return NbtCompat.parseItemStack(lookup, itemTag);
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

        // Add direction mode
        ItemMultiblockPlacementTool.DirectionMode direction = ItemMultiblockPlacementTool.getDirectionMode(tool);
        String directionName = Component.translatable(direction.translationKey()).getString();
        lines.add(Component.translatable("meplacementtool.hud.direction", directionName).getString());
    }

    /**
     * Collect information for Cable Placement Tool.
     */
    private void collectCableToolInfo(ItemStack tool, List<String> lines) {
        // Cable Type
        ItemMECablePlacementTool.CableType cableType = ItemMECablePlacementTool.getCableType(tool);
        String cableTypeName = getCableTypeName(cableType);
        lines.add(Component.translatable("meplacementtool.hud.cable_type", cableTypeName).getString());

        // Color - only show when upgrade (Key of Spectrum) is installed
        // Without upgrade, the tool always uses transparent color, so no need to display
        boolean hasUpgrade = ItemMECablePlacementTool.hasUpgrade(tool);
        if (hasUpgrade) {
            AEColor color = ItemMECablePlacementTool.getColor(tool);
            String colorName = getColorName(color);
            lines.add(Component.translatable("meplacementtool.hud.color", colorName).getString());
        }

        // Mode
        ItemMECablePlacementTool.PlacementMode mode = ItemMECablePlacementTool.getMode(tool);
        String modeName = getModeName(mode);
        lines.add(Component.translatable("meplacementtool.hud.mode", modeName).getString());
    }

    /**
     * Get localized cable type name.
     */
    private String getCableTypeName(ItemMECablePlacementTool.CableType type) {
        return switch (type) {
            case GLASS -> Component.translatable("meplacementtool.cable.glass").getString();
            case COVERED -> Component.translatable("meplacementtool.cable.covered").getString();
            case SMART -> Component.translatable("meplacementtool.cable.smart").getString();
            case DENSE_COVERED -> Component.translatable("meplacementtool.cable.dense_covered").getString();
            case DENSE_SMART -> Component.translatable("meplacementtool.cable.dense_smart").getString();
        };
    }

    /**
     * Get localized color name.
     */
    private String getColorName(AEColor color) {
        return switch (color) {
            case WHITE -> Component.translatable("meplacementtool.color.white").getString();
            case ORANGE -> Component.translatable("meplacementtool.color.orange").getString();
            case MAGENTA -> Component.translatable("meplacementtool.color.magenta").getString();
            case LIGHT_BLUE -> Component.translatable("meplacementtool.color.light_blue").getString();
            case YELLOW -> Component.translatable("meplacementtool.color.yellow").getString();
            case LIME -> Component.translatable("meplacementtool.color.lime").getString();
            case PINK -> Component.translatable("meplacementtool.color.pink").getString();
            case GRAY -> Component.translatable("meplacementtool.color.gray").getString();
            case LIGHT_GRAY -> Component.translatable("meplacementtool.color.light_gray").getString();
            case CYAN -> Component.translatable("meplacementtool.color.cyan").getString();
            case PURPLE -> Component.translatable("meplacementtool.color.purple").getString();
            case BLUE -> Component.translatable("meplacementtool.color.blue").getString();
            case BROWN -> Component.translatable("meplacementtool.color.brown").getString();
            case GREEN -> Component.translatable("meplacementtool.color.green").getString();
            case RED -> Component.translatable("meplacementtool.color.red").getString();
            case BLACK -> Component.translatable("meplacementtool.color.black").getString();
            case TRANSPARENT -> Component.translatable("meplacementtool.color.transparent").getString();
        };
    }

    /**
     * Get localized mode name.
     */
    private String getModeName(ItemMECablePlacementTool.PlacementMode mode) {
        return switch (mode) {
            case LINE -> Component.translatable("meplacementtool.mode.line").getString();
            case PLANE_FILL -> Component.translatable("meplacementtool.mode.plane_fill").getString();
            case PLANE_BRANCHING -> Component.translatable("meplacementtool.mode.plane_branching").getString();
        };
    }

    /**
     * Render HUD lines on the right side of the crosshair with small font.
     */
    private void renderHudLines(GuiGraphicsExtractor graphics, Minecraft mc, List<String> lines) {
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();

        // Center of screen (where crosshair is)
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;

        // Start position (right of crosshair)
        int startX = centerX + CROSSHAIR_OFFSET_X;
        int startY = centerY - (lines.size() * (int)(LINE_HEIGHT * FONT_SCALE)) / 2;

        graphics.pose().pushMatrix();
        graphics.pose().scale(FONT_SCALE, FONT_SCALE);

        // Scale coordinates to match the scaled rendering
        float scaledStartX = startX / FONT_SCALE;
        float scaledStartY = startY / FONT_SCALE;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int y = (int)(scaledStartY + i * LINE_HEIGHT);
            
            // Draw with shadow for better visibility
            graphics.text(mc.font, line, (int) scaledStartX, y, TEXT_COLOR, true);
        }

        graphics.pose().popMatrix();
    }

    private record HeldTool(String hand, int inventorySlot, ItemStack stack) {
    }
}
