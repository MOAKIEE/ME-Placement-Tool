package com.moakiee.meplacementtool.client;

import appeng.api.util.AEColor;
import appeng.client.Point;
import appeng.client.gui.widgets.UpgradesPanel;
import appeng.menu.SlotSemantics;
import appeng.menu.slot.AppEngSlot;
import com.moakiee.meplacementtool.CableToolMenu;
import com.moakiee.meplacementtool.ItemMECablePlacementTool;
import com.moakiee.meplacementtool.MEPlacementToolMod;
import com.moakiee.meplacementtool.network.UpdateCableToolPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen for the Cable Placement Tool GUI.
 * New layout with color selection bar, expandable color menu, cable/mode selection areas,
 * and inventory slots.
 */
public class CableToolScreen extends AbstractContainerScreen<CableToolMenu> {

    // Main GUI texture (background)
    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath("meplacementtool", "textures/gui/cable_tool.png");
    // Color cell textures
    private static final ResourceLocation COLOR_UNSELECTED = ResourceLocation.fromNamespaceAndPath("meplacementtool", "textures/gui/color_unselected.png");
    private static final ResourceLocation COLOR_FRAME = ResourceLocation.fromNamespaceAndPath("meplacementtool", "textures/gui/color_frame.png");
    // Expand button texture
    private static final ResourceLocation EXPAND_BUTTON = ResourceLocation.fromNamespaceAndPath("meplacementtool", "textures/gui/expand_button.png");
    // Color menu texture
    private static final ResourceLocation COLOR_MENU = ResourceLocation.fromNamespaceAndPath("meplacementtool", "textures/gui/color_menu.png");
    // Button textures
    private static final ResourceLocation BUTTON_NORMAL = ResourceLocation.fromNamespaceAndPath("meplacementtool", "textures/gui/button_normal.png");
    private static final ResourceLocation BUTTON_PRESSED = ResourceLocation.fromNamespaceAndPath("meplacementtool", "textures/gui/button_pressed.png");

    // GUI dimensions
    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 195;

    // Color selection bar (9,18) to (116,32) - 6 color cells + expand button
    private static final int COLOR_BAR_X = 9;
    private static final int COLOR_BAR_Y = 18;
    private static final int COLOR_CELL_SIZE = 11;
    private static final int COLOR_CELL_COUNT = 6;
    private static final int COLOR_CELL_SPACING = 7; // Spacing between cells to fit in area

    // Expand button position (rightmost in color bar)
    private static final int EXPAND_BTN_SIZE = 12;

    // Expanded color menu position (7,33)
    private static final int COLOR_MENU_X = 7;
    private static final int COLOR_MENU_Y = 33;
    private static final int COLOR_MENU_WIDTH = 112;
    private static final int COLOR_MENU_HEIGHT = 49;
    private static final int COLOR_MENU_CELL_SIZE = 11;
    private static final int COLOR_MENU_CELL_SPACING = 2;

    // Cable selection area (7,49) to (118,102)
    private static final int CABLE_AREA_X = 7;
    private static final int CABLE_AREA_Y = 49;
    private static final int CABLE_BTN_SIZE = 12;
    private static final int CABLE_BTN_SPACING = 2;

    // Mode selection area (124,49) to (168,102)
    private static final int MODE_AREA_X = 124;
    private static final int MODE_AREA_Y = 49;
    private static final int MODE_BTN_SIZE = 12;
    private static final int MODE_BTN_SPACING = 2;

    // AE2 Upgrade Panel constants
    private static final int AE2_PADDING = 5;

    // UpgradesPanel for rendering upgrade slots in AE2 style
    private final UpgradesPanel upgradesPanel;

    // UI state
    private boolean colorMenuExpanded = false;
    private int hoveredColorIndex = -1;
    private int hoveredExpandedColorIndex = -1;
    private int hoveredCableIndex = -1;
    private int hoveredModeIndex = -1;
    private boolean hoveredExpandButton = false;

    @Nullable
    private Component hintText = null;
    private int hintColor = 0xFFFFFF;

    // Selected colors for the 6 color bar cells (indices into AEColor.values())
    // -1 means no color selected for that cell
    private int[] colorBarSelections = new int[]{0, -1, -1, -1, -1, -1}; // Default: first slot = TRANSPARENT

    public CableToolScreen(CableToolMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
        this.titleLabelX = -9999;
        this.titleLabelY = -9999;
        this.inventoryLabelX = -9999;
        this.inventoryLabelY = -9999;

        // Create the upgrades panel with upgrade slots from menu
        this.upgradesPanel = new UpgradesPanel(menu.getSlots(SlotSemantics.UPGRADE));

        // Initialize color bar with current selected color
        colorBarSelections[0] = menu.currentColor;
    }

    @Override
    protected void init() {
        super.init();
        // Set up the upgrades panel position (right side of GUI)
        this.upgradesPanel.setPosition(new Point(GUI_WIDTH - 1, AE2_PADDING));
        this.upgradesPanel.populateScreen(this::addRenderableWidget, getBounds(), null);
    }

    private Rect2i getBounds() {
        return new Rect2i(leftPos, topPos, imageWidth, imageHeight);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTicks, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        // Draw main GUI background (only the portion we need: 0,0 to 175,194)
        guiGraphics.blit(BACKGROUND, x, y, 0, 0, GUI_WIDTH, GUI_HEIGHT);

        // Reset hover states
        hoveredColorIndex = -1;
        hoveredExpandedColorIndex = -1;
        hoveredCableIndex = -1;
        hoveredModeIndex = -1;
        hoveredExpandButton = false;
        hintText = null;

        // Draw color selection bar
        drawColorBar(guiGraphics, x, y, mouseX, mouseY);

        // Draw expanded color menu if open
        if (colorMenuExpanded) {
            drawColorMenu(guiGraphics, x, y, mouseX, mouseY);
        }

        // Draw cable selection area
        drawCableSection(guiGraphics, x, y, mouseX, mouseY);

        // Draw mode selection area
        drawModeSection(guiGraphics, x, y, mouseX, mouseY);

        // Draw hint text if any
        if (hintText != null) {
            int hintY = y + GUI_HEIGHT - 30;
            guiGraphics.drawCenteredString(font, hintText, x + GUI_WIDTH / 2, hintY, hintColor);
        }

        // Update and draw upgrade panel
        upgradesPanel.updateBeforeRender();
        upgradesPanel.drawBackgroundLayer(guiGraphics, getBounds(), new Point(mouseX - leftPos, mouseY - topPos));

        // Draw ghost icon for empty upgrade slot
        drawUpgradeSlotIcon(guiGraphics);
    }

    /**
     * Draw the color selection bar with 6 cells and expand button.
     * Area: (9,18) to (116,32)
     */
    private void drawColorBar(GuiGraphics guiGraphics, int baseX, int baseY, int mouseX, int mouseY) {
        int startX = baseX + COLOR_BAR_X;
        int startY = baseY + COLOR_BAR_Y;

        // Calculate spacing to fit 6 cells + expand button in the area
        // Area width: 116 - 9 = 107px
        // 6 cells * 11px = 66px, expand button 12px = 78px, remaining = 29px for spacing
        int cellSpacing = 5; // spacing between cells

        // Draw 6 color cells
        for (int i = 0; i < COLOR_CELL_COUNT; i++) {
            int cellX = startX + i * (COLOR_CELL_SIZE + cellSpacing);
            int cellY = startY;

            int colorIndex = colorBarSelections[i];
            boolean isSelected = (colorIndex >= 0 && colorIndex == menu.currentColor);
            boolean isHovered = isInBounds(mouseX, mouseY, cellX, cellY, COLOR_CELL_SIZE, COLOR_CELL_SIZE);

            if (isHovered) {
                hoveredColorIndex = i;
                if (colorIndex >= 0) {
                    AEColor color = AEColor.values()[colorIndex];
                    hintText = Component.translatable("meplacementtool.color." + color.name().toLowerCase());
                    hintColor = color == AEColor.TRANSPARENT ? 0x8B479B : (color.mediumVariant & 0xFFFFFF);
                } else {
                    hintText = Component.translatable("gui.meplacementtool.no_color");
                    hintColor = 0x808080;
                }
            }

            if (colorIndex < 0) {
                // No color selected - draw unselected texture
                guiGraphics.blit(COLOR_UNSELECTED, cellX, cellY, 0, 0, COLOR_CELL_SIZE, COLOR_CELL_SIZE, COLOR_CELL_SIZE, COLOR_CELL_SIZE);
            } else {
                // Color selected - draw frame and fill with color
                guiGraphics.blit(COLOR_FRAME, cellX, cellY, 0, 0, COLOR_CELL_SIZE, COLOR_CELL_SIZE, COLOR_CELL_SIZE, COLOR_CELL_SIZE);

                // Fill inner 10x10 area with color (offset by 0.5px for the frame)
                AEColor color = AEColor.values()[colorIndex];
                int colorValue = color == AEColor.TRANSPARENT ? 0x8B479B : color.mediumVariant;
                int innerX = cellX + 1;
                int innerY = cellY + 1;
                int innerSize = 9; // 10x10 but we have 1px frame on each side, so 11-2=9
                guiGraphics.fill(innerX, innerY, innerX + innerSize, innerY + innerSize, 0xFF000000 | colorValue);
            }

            // Draw selection indicator if this is the currently selected color
            if (isSelected) {
                // Draw a highlight border or indicator
                int borderColor = 0xFFFFFFFF;
                guiGraphics.renderOutline(cellX - 1, cellY - 1, COLOR_CELL_SIZE + 2, COLOR_CELL_SIZE + 2, borderColor);
            }
        }

        // Draw expand button (rightmost)
        int expandX = startX + COLOR_CELL_COUNT * (COLOR_CELL_SIZE + cellSpacing);
        int expandY = startY;
        boolean expandHovered = isInBounds(mouseX, mouseY, expandX, expandY, EXPAND_BTN_SIZE, EXPAND_BTN_SIZE);

        if (expandHovered) {
            hoveredExpandButton = true;
            hintText = Component.translatable(colorMenuExpanded ? "gui.meplacementtool.collapse_colors" : "gui.meplacementtool.expand_colors");
            hintColor = 0xFFFFFF;
        }

        guiGraphics.blit(EXPAND_BUTTON, expandX, expandY, 0, 0, EXPAND_BTN_SIZE, EXPAND_BTN_SIZE, EXPAND_BTN_SIZE, EXPAND_BTN_SIZE);
    }

    /**
     * Draw the expanded color menu at (7,33).
     * Contains 17 colors (16 Minecraft dyes + Fluix)
     */
    private void drawColorMenu(GuiGraphics guiGraphics, int baseX, int baseY, int mouseX, int mouseY) {
        int menuX = baseX + COLOR_MENU_X;
        int menuY = baseY + COLOR_MENU_Y;

        // Draw menu background
        guiGraphics.blit(COLOR_MENU, menuX, menuY, 0, 0, COLOR_MENU_WIDTH, COLOR_MENU_HEIGHT, COLOR_MENU_WIDTH, COLOR_MENU_HEIGHT);

        // Draw 17 color cells in the menu
        // Layout: probably 6 columns, 3 rows (17 colors total, with last row having 5)
        AEColor[] colors = AEColor.values();
        int cols = 6;
        int cellSpacing = COLOR_MENU_CELL_SPACING;
        int cellSize = COLOR_MENU_CELL_SIZE;

        // Offset inside the menu for the cells
        int cellStartX = menuX + 3;
        int cellStartY = menuY + 3;

        for (int i = 0; i < colors.length; i++) {
            int col = i % cols;
            int row = i / cols;

            int cellX = cellStartX + col * (cellSize + cellSpacing);
            int cellY = cellStartY + row * (cellSize + cellSpacing);

            boolean isHovered = isInBounds(mouseX, mouseY, cellX, cellY, cellSize, cellSize);
            boolean isSelected = (i == menu.currentColor);

            if (isHovered) {
                hoveredExpandedColorIndex = i;
                AEColor color = colors[i];
                hintText = Component.translatable("meplacementtool.color." + color.name().toLowerCase());
                hintColor = color == AEColor.TRANSPARENT ? 0x8B479B : (color.mediumVariant & 0xFFFFFF);
            }

            // Draw frame
            guiGraphics.blit(COLOR_FRAME, cellX, cellY, 0, 0, cellSize, cellSize, cellSize, cellSize);

            // Fill with color
            AEColor color = colors[i];
            int colorValue = color == AEColor.TRANSPARENT ? 0x8B479B : color.mediumVariant;
            int innerX = cellX + 1;
            int innerY = cellY + 1;
            int innerSize = cellSize - 2;
            guiGraphics.fill(innerX, innerY, innerX + innerSize, innerY + innerSize, 0xFF000000 | colorValue);

            // Draw selection indicator
            if (isSelected) {
                guiGraphics.renderOutline(cellX - 1, cellY - 1, cellSize + 2, cellSize + 2, 0xFFFFFFFF);
            }
        }
    }

    /**
     * Draw cable type selection area at (7,49)-(118,102).
     */
    private void drawCableSection(GuiGraphics guiGraphics, int baseX, int baseY, int mouseX, int mouseY) {
        int startX = baseX + CABLE_AREA_X;
        int startY = baseY + CABLE_AREA_Y;

        int selectedCable = menu.currentCableType;
        ItemMECablePlacementTool.CableType[] types = ItemMECablePlacementTool.CableType.values();
        String[] cableKeys = {"glass", "covered", "smart", "dense_covered", "dense_smart"};

        for (int i = 0; i < types.length; i++) {
            int btnX = startX;
            int btnY = startY + i * (CABLE_BTN_SIZE + CABLE_BTN_SPACING);

            boolean isHovered = isInBounds(mouseX, mouseY, btnX, btnY, CABLE_BTN_SIZE, CABLE_BTN_SIZE);
            boolean isSelected = (i == selectedCable);

            if (isHovered) {
                hoveredCableIndex = i;
                hintText = Component.translatable("meplacementtool.cable." + cableKeys[i]);
                hintColor = 0x8B479B;
            }

            // Draw button texture
            ResourceLocation btnTexture = isSelected ? BUTTON_PRESSED : BUTTON_NORMAL;
            guiGraphics.blit(btnTexture, btnX, btnY, 0, 0, CABLE_BTN_SIZE, CABLE_BTN_SIZE, CABLE_BTN_SIZE, CABLE_BTN_SIZE);

            // Draw cable item icon
            ItemStack cableStack = types[i].getStack(AEColor.TRANSPARENT);
            guiGraphics.pose().pushPose();
            float scale = 0.6f;
            float offsetX = btnX + (CABLE_BTN_SIZE - 16 * scale) / 2;
            float offsetY = btnY + (CABLE_BTN_SIZE - 16 * scale) / 2;
            guiGraphics.pose().translate(offsetX, offsetY, 0);
            guiGraphics.pose().scale(scale, scale, 1.0f);
            guiGraphics.renderItem(cableStack, 0, 0);
            guiGraphics.pose().popPose();

            // Draw cable name text
            String cableName = Component.translatable("meplacementtool.cable." + cableKeys[i] + ".short").getString();
            int textColor = isSelected ? 0xFFFFFF : 0x404040;
            guiGraphics.drawString(font, cableName, btnX + CABLE_BTN_SIZE + 3, btnY + 2, textColor, false);
        }
    }

    /**
     * Draw mode selection area at (124,49)-(168,102).
     */
    private void drawModeSection(GuiGraphics guiGraphics, int baseX, int baseY, int mouseX, int mouseY) {
        int startX = baseX + MODE_AREA_X;
        int startY = baseY + MODE_AREA_Y;

        int selectedMode = menu.currentMode;
        ItemMECablePlacementTool.PlacementMode[] modes = ItemMECablePlacementTool.PlacementMode.values();
        String[] modeIcons = {"L", "F", "B"};
        String[] modeKeys = {"line", "plane_fill", "plane_branching"};

        for (int i = 0; i < modes.length; i++) {
            int btnX = startX;
            int btnY = startY + i * (MODE_BTN_SIZE + MODE_BTN_SPACING);

            boolean isHovered = isInBounds(mouseX, mouseY, btnX, btnY, MODE_BTN_SIZE, MODE_BTN_SIZE);
            boolean isSelected = (i == selectedMode);

            if (isHovered) {
                hoveredModeIndex = i;
                hintText = Component.translatable("meplacementtool.mode." + modeKeys[i]);
                hintColor = 0xFFFFFF;
            }

            // Draw button texture
            ResourceLocation btnTexture = isSelected ? BUTTON_PRESSED : BUTTON_NORMAL;
            guiGraphics.blit(btnTexture, btnX, btnY, 0, 0, MODE_BTN_SIZE, MODE_BTN_SIZE, MODE_BTN_SIZE, MODE_BTN_SIZE);

            // Draw mode icon
            int iconColor = isSelected ? 0xFFFFFF : 0xE0E0E0;
            guiGraphics.drawCenteredString(font, modeIcons[i], btnX + MODE_BTN_SIZE / 2, btnY + 2, iconColor);

            // Draw mode name text
            String modeName = Component.translatable("meplacementtool.mode." + modeKeys[i] + ".short").getString();
            int textColor = isSelected ? 0xFFFFFF : 0x404040;
            guiGraphics.drawString(font, modeName, btnX + MODE_BTN_SIZE + 3, btnY + 2, textColor, false);
        }
    }

    /**
     * Draw ghost icon for upgrade slot when empty (AE2 style).
     */
    private void drawUpgradeSlotIcon(GuiGraphics guiGraphics) {
        List<Slot> upgradeSlots = menu.getSlots(SlotSemantics.UPGRADE);
        if (!upgradeSlots.isEmpty()) {
            Slot upgradeSlot = upgradeSlots.get(0);
            if (upgradeSlot.getItem().isEmpty() && upgradeSlot instanceof AppEngSlot appEngSlot) {
                if (appEngSlot.isSlotEnabled() && appEngSlot.getIcon() != null) {
                    appEngSlot.getIcon().getBlitter()
                        .dest(leftPos + upgradeSlot.x, topPos + upgradeSlot.y)
                        .opacity(0.7f)
                        .blit(guiGraphics);
                }
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Don't render default labels
    }

    @Override
    protected void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Check if hovering over upgrade slot
        List<Slot> upgradeSlots = menu.getSlots(SlotSemantics.UPGRADE);
        if (!upgradeSlots.isEmpty()) {
            Slot upgradeSlot = upgradeSlots.get(0);
            int slotX = leftPos + upgradeSlot.x;
            int slotY = topPos + upgradeSlot.y;

            if (isInBounds(mouseX, mouseY, slotX, slotY, 16, 16)) {
                if (upgradeSlot.getItem().isEmpty()) {
                    List<Component> tooltip = new ArrayList<>();
                    tooltip.add(Component.translatable("gui.meplacementtool.compatible_upgrades")
                            .withStyle(ChatFormatting.GOLD));
                    tooltip.add(Component.translatable("gui.meplacementtool.upgrade_hint",
                            new ItemStack(MEPlacementToolMod.KEY_OF_SPECTRUM.get()).getHoverName())
                            .withStyle(ChatFormatting.GRAY));
                    guiGraphics.renderTooltip(font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
                    return;
                } else {
                    guiGraphics.renderTooltip(font, upgradeSlot.getItem(), mouseX, mouseY);
                    return;
                }
            }
        }

        super.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    private boolean isInBounds(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // Handle expand button click
            if (hoveredExpandButton) {
                colorMenuExpanded = !colorMenuExpanded;
                playButtonClickSound();
                return true;
            }

            // Handle expanded color menu click
            if (colorMenuExpanded && hoveredExpandedColorIndex >= 0) {
                menu.setColor(hoveredExpandedColorIndex);
                colorBarSelections[0] = hoveredExpandedColorIndex;
                syncToServer();
                playButtonClickSound();
                colorMenuExpanded = false;
                return true;
            }

            // Handle color bar click (quick select from remembered colors)
            if (hoveredColorIndex >= 0 && colorBarSelections[hoveredColorIndex] >= 0) {
                menu.setColor(colorBarSelections[hoveredColorIndex]);
                syncToServer();
                playButtonClickSound();
                return true;
            }

            // Handle cable selection
            if (hoveredCableIndex >= 0) {
                menu.setCableType(hoveredCableIndex);
                syncToServer();
                playButtonClickSound();
                return true;
            }

            // Handle mode selection
            if (hoveredModeIndex >= 0) {
                menu.setMode(hoveredModeIndex);
                syncToServer();
                playButtonClickSound();
                return true;
            }
        }

        // Close color menu if clicking outside
        if (colorMenuExpanded) {
            int menuX = leftPos + COLOR_MENU_X;
            int menuY = topPos + COLOR_MENU_Y;
            if (!isInBounds((int)mouseX, (int)mouseY, menuX, menuY, COLOR_MENU_WIDTH, COLOR_MENU_HEIGHT)) {
                // Also check if not clicking on expand button
                if (!hoveredExpandButton) {
                    colorMenuExpanded = false;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void playButtonClickSound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private void syncToServer() {
        PacketDistributor.sendToServer(new UpdateCableToolPayload(
            menu.currentMode,
            menu.currentCableType,
            menu.currentColor
        ));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Allow the same key to close this menu (toggle behavior)
        if (ModKeyBindings.OPEN_CABLE_TOOL_GUI.matches(keyCode, scanCode)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTicks);
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
