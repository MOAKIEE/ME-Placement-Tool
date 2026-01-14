package com.moakiee.meplacementtool.client;

import appeng.api.util.AEColor;
import appeng.menu.SlotSemantics;
import com.moakiee.meplacementtool.CableToolMenu;
import com.moakiee.meplacementtool.ItemMECablePlacementTool;
import com.moakiee.meplacementtool.MEPlacementToolMod;
import com.moakiee.meplacementtool.network.UpdateCableToolPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
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
 * Layout based on exact pixel coordinates from GUI design.
 * 
 * Features:
 * - Color shortcut bar with marking capability (works without upgrade too)
 * - First slot always shows currently selected color (no green frame)
 * - Green frame only on marked slots (2-6) and expanded menu when that color is selected
 * - Upgrade slot at (152,8)-(167,23) inside GUI
 */
public class CableToolScreen extends AbstractContainerScreen<CableToolMenu> {

    // Textures
    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath("meplacementtool", "textures/gui/cable_tool.png");
    private static final ResourceLocation COLOR_UNSELECTED = ResourceLocation.fromNamespaceAndPath("meplacementtool", "textures/gui/color_unselected.png");
    private static final ResourceLocation COLOR_FRAME = ResourceLocation.fromNamespaceAndPath("meplacementtool", "textures/gui/color_frame.png");
    private static final ResourceLocation EXPAND_BUTTON = ResourceLocation.fromNamespaceAndPath("meplacementtool", "textures/gui/expand_button.png");
    private static final ResourceLocation COLOR_MENU = ResourceLocation.fromNamespaceAndPath("meplacementtool", "textures/gui/color_menu.png");
    private static final ResourceLocation BUTTON_NORMAL = ResourceLocation.fromNamespaceAndPath("meplacementtool", "textures/gui/button_normal.png");
    private static final ResourceLocation BUTTON_PRESSED = ResourceLocation.fromNamespaceAndPath("meplacementtool", "textures/gui/button_pressed.png");

    // GUI dimensions
    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 195;

    // === Color selection bar: (9,18) to (116,32) ===
    private static final int COLOR_BAR_LEFT = 9;
    private static final int COLOR_BAR_TOP = 18;
    private static final int COLOR_BAR_RIGHT = 116;
    private static final int COLOR_BAR_BOTTOM = 32;
    private static final int COLOR_CELL_SIZE = 11;
    private static final int COLOR_CELL_COUNT = 6;
    private static final int COLOR_CELL_SPACING = 5;
    private static final int EXPAND_BTN_SIZE = 12;

    // === Expanded color menu: at (7,33), 112x49px ===
    private static final int COLOR_MENU_X = 7;
    private static final int COLOR_MENU_Y = 33;
    private static final int COLOR_MENU_WIDTH = 112;
    private static final int COLOR_MENU_HEIGHT = 49;
    private static final int COLOR_MENU_COLS = 6;
    private static final int COLOR_MENU_CELL_SIZE = 11;
    private static final int COLOR_MENU_CELL_SPACING = 2;
    private static final int COLOR_MENU_PADDING = 5;

    // === Cable selection: (7,49) to (118,102) - TWO COLUMNS ===
    private static final int CABLE_AREA_LEFT = 7;
    private static final int CABLE_AREA_TOP = 49;
    private static final int CABLE_AREA_RIGHT = 118;
    private static final int CABLE_AREA_BOTTOM = 102;
    private static final int CABLE_BTN_SIZE = 12;
    private static final int CABLE_BTN_SPACING = 3;

    // === Mode selection: (124,49) to (168,102) ===
    private static final int MODE_AREA_LEFT = 124;
    private static final int MODE_AREA_TOP = 49;
    private static final int MODE_AREA_RIGHT = 168;
    private static final int MODE_AREA_BOTTOM = 102;
    private static final int MODE_BTN_SIZE = 12;
    private static final int MODE_BTN_SPACING = 3;

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

    // Color shortcut bar - stores color indices (-1 = empty slot)
    // Slot 0: always shows current selected color (Fluix = 0 by default)
    // Slots 1-5: user-marked colors
    private int[] colorShortcuts = new int[]{0, -1, -1, -1, -1, -1};

    public CableToolScreen(CableToolMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
        this.titleLabelX = -9999;
        this.titleLabelY = -9999;
        this.inventoryLabelX = -9999;
        this.inventoryLabelY = -9999;

        // First slot always reflects current selection
        colorShortcuts[0] = menu.currentColor;
    }

    @Override
    protected void init() {
        super.init();
        // No AE2 UpgradesPanel - upgrade slot is now integrated in GUI at (152,8)
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTicks, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        // Draw main GUI background
        guiGraphics.blit(BACKGROUND, x, y, 0, 0, GUI_WIDTH, GUI_HEIGHT);

        // Reset hover states
        hoveredColorIndex = -1;
        hoveredExpandedColorIndex = -1;
        hoveredCableIndex = -1;
        hoveredModeIndex = -1;
        hoveredExpandButton = false;
        hintText = null;

        // Draw color shortcut bar
        drawColorBar(guiGraphics, x, y, mouseX, mouseY);

        // Draw cable and mode selection areas (unless color menu is covering them)
        if (!colorMenuExpanded) {
            drawCableSection(guiGraphics, x, y, mouseX, mouseY);
            drawModeSection(guiGraphics, x, y, mouseX, mouseY);
        }

        // Draw expanded color menu ON TOP
        if (colorMenuExpanded) {
            drawColorMenu(guiGraphics, x, y, mouseX, mouseY);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Draw hint text ABOVE the mode area (around Y=38, between color bar and selection areas)
        if (hintText != null) {
            int hintY = 38;
            guiGraphics.drawCenteredString(font, hintText, GUI_WIDTH / 2, hintY, hintColor);
        }
    }

    /**
     * Draw color shortcut bar at (9,18)-(116,32)
     * Slot 0: current selected color (no green frame)
     * Slots 1-5: marked colors (green frame if that color is currently selected)
     */
    private void drawColorBar(GuiGraphics guiGraphics, int baseX, int baseY, int mouseX, int mouseY) {
        int areaWidth = COLOR_BAR_RIGHT - COLOR_BAR_LEFT + 1;
        int areaHeight = COLOR_BAR_BOTTOM - COLOR_BAR_TOP + 1;
        
        int totalCellsWidth = COLOR_CELL_COUNT * COLOR_CELL_SIZE + (COLOR_CELL_COUNT - 1) * COLOR_CELL_SPACING;
        int startX = baseX + COLOR_BAR_LEFT + (areaWidth - totalCellsWidth - EXPAND_BTN_SIZE - COLOR_CELL_SPACING) / 2;
        int startY = baseY + COLOR_BAR_TOP + (areaHeight - COLOR_CELL_SIZE) / 2;

        // Always update slot 0 to reflect current selection
        colorShortcuts[0] = menu.currentColor;

        for (int i = 0; i < COLOR_CELL_COUNT; i++) {
            int cellX = startX + i * (COLOR_CELL_SIZE + COLOR_CELL_SPACING);
            int cellY = startY;

            int colorIndex = colorShortcuts[i];
            boolean isHovered = isInBounds(mouseX, mouseY, cellX, cellY, COLOR_CELL_SIZE, COLOR_CELL_SIZE);
            
            // Green frame logic: only show on slots 1-5 when that color matches current selection
            boolean showGreenFrame = (i > 0) && (colorIndex >= 0) && (colorIndex == menu.currentColor);

            if (isHovered) {
                hoveredColorIndex = i;
                if (colorIndex >= 0) {
                    AEColor color = AEColor.values()[colorIndex];
                    hintText = Component.translatable("meplacementtool.color." + color.name().toLowerCase());
                    hintColor = getDisplayColor(color);
                } else {
                    hintText = Component.translatable("gui.meplacementtool.empty_slot");
                    hintColor = 0x808080;
                }
            }

            if (colorIndex < 0) {
                // Empty slot
                guiGraphics.blit(COLOR_UNSELECTED, cellX, cellY, 0, 0, COLOR_CELL_SIZE, COLOR_CELL_SIZE, COLOR_CELL_SIZE, COLOR_CELL_SIZE);
            } else {
                // Draw frame and fill
                guiGraphics.blit(COLOR_FRAME, cellX, cellY, 0, 0, COLOR_CELL_SIZE, COLOR_CELL_SIZE, COLOR_CELL_SIZE, COLOR_CELL_SIZE);
                AEColor color = AEColor.values()[colorIndex];
                int fillColor = getDisplayColor(color);
                guiGraphics.fill(cellX + 1, cellY + 1, cellX + COLOR_CELL_SIZE - 1, cellY + COLOR_CELL_SIZE - 1, 0xFF000000 | fillColor);
            }

            // Green frame for marked slots (not slot 0) when selected
            if (showGreenFrame) {
                guiGraphics.renderOutline(cellX - 1, cellY - 1, COLOR_CELL_SIZE + 2, COLOR_CELL_SIZE + 2, 0xFF00FF00);
            }
        }

        // Draw expand button
        int expandX = startX + COLOR_CELL_COUNT * (COLOR_CELL_SIZE + COLOR_CELL_SPACING);
        int expandY = startY + (COLOR_CELL_SIZE - EXPAND_BTN_SIZE) / 2;
        hoveredExpandButton = isInBounds(mouseX, mouseY, expandX, expandY, EXPAND_BTN_SIZE, EXPAND_BTN_SIZE);

        if (hoveredExpandButton) {
            hintText = Component.translatable(colorMenuExpanded ? "gui.meplacementtool.collapse_colors" : "gui.meplacementtool.expand_colors");
            hintColor = 0xFFFFFF;
        }

        guiGraphics.blit(EXPAND_BUTTON, expandX, expandY, 0, 0, EXPAND_BTN_SIZE, EXPAND_BTN_SIZE, EXPAND_BTN_SIZE, EXPAND_BTN_SIZE);
    }

    /**
     * Draw expanded color menu at (7,33).
     * Green frame shows on currently selected color.
     */
    private void drawColorMenu(GuiGraphics guiGraphics, int baseX, int baseY, int mouseX, int mouseY) {
        int menuX = baseX + COLOR_MENU_X;
        int menuY = baseY + COLOR_MENU_Y;

        // Draw menu background
        guiGraphics.blit(COLOR_MENU, menuX, menuY, 0, 0, COLOR_MENU_WIDTH, COLOR_MENU_HEIGHT, COLOR_MENU_WIDTH, COLOR_MENU_HEIGHT);

        AEColor[] colors = AEColor.values();
        int cellStartX = menuX + COLOR_MENU_PADDING;
        int cellStartY = menuY + COLOR_MENU_PADDING;

        for (int i = 0; i < colors.length; i++) {
            int col = i % COLOR_MENU_COLS;
            int row = i / COLOR_MENU_COLS;
            int cellX = cellStartX + col * (COLOR_MENU_CELL_SIZE + COLOR_MENU_CELL_SPACING);
            int cellY = cellStartY + row * (COLOR_MENU_CELL_SIZE + COLOR_MENU_CELL_SPACING);

            boolean isHovered = isInBounds(mouseX, mouseY, cellX, cellY, COLOR_MENU_CELL_SIZE, COLOR_MENU_CELL_SIZE);
            boolean isSelected = (i == menu.currentColor);

            if (isHovered) {
                hoveredExpandedColorIndex = i;
                AEColor color = colors[i];
                hintText = Component.translatable("meplacementtool.color." + color.name().toLowerCase());
                hintColor = getDisplayColor(color);
            }

            // Draw cell
            guiGraphics.blit(COLOR_FRAME, cellX, cellY, 0, 0, COLOR_MENU_CELL_SIZE, COLOR_MENU_CELL_SIZE, COLOR_MENU_CELL_SIZE, COLOR_MENU_CELL_SIZE);
            AEColor color = colors[i];
            int fillColor = getDisplayColor(color);
            guiGraphics.fill(cellX + 1, cellY + 1, cellX + COLOR_MENU_CELL_SIZE - 1, cellY + COLOR_MENU_CELL_SIZE - 1, 0xFF000000 | fillColor);

            // Green frame for selected color
            if (isSelected) {
                guiGraphics.renderOutline(cellX - 1, cellY - 1, COLOR_MENU_CELL_SIZE + 2, COLOR_MENU_CELL_SIZE + 2, 0xFF00FF00);
            }
        }

        // Show key hint for marking
        Component markHint = Component.translatable("gui.meplacementtool.mark_hint", 
            ModKeyBindings.MARK_COLOR_SHORTCUT.getTranslatedKeyMessage());
        guiGraphics.drawString(font, markHint, menuX + 2, menuY + COLOR_MENU_HEIGHT + 2, 0xAAAAAA, false);
    }

    /**
     * Draw cable type selection at (7,49)-(118,102) - TWO COLUMNS
     */
    private void drawCableSection(GuiGraphics guiGraphics, int baseX, int baseY, int mouseX, int mouseY) {
        int areaWidth = CABLE_AREA_RIGHT - CABLE_AREA_LEFT + 1;
        int areaHeight = CABLE_AREA_BOTTOM - CABLE_AREA_TOP + 1;
        
        int selectedCable = menu.currentCableType;
        ItemMECablePlacementTool.CableType[] types = ItemMECablePlacementTool.CableType.values();
        String[] cableKeys = {"glass", "covered", "smart", "dense_covered", "dense_smart"};

        int colWidth = areaWidth / 2;
        int rowHeight = CABLE_BTN_SIZE + CABLE_BTN_SPACING;
        
        int leftColX = baseX + CABLE_AREA_LEFT + 5;
        int rightColX = baseX + CABLE_AREA_LEFT + colWidth + 5;
        int startY = baseY + CABLE_AREA_TOP + 5;

        for (int i = 0; i < types.length; i++) {
            int col = i / 3;
            int row = i % 3;
            
            int btnX = (col == 0) ? leftColX : rightColX;
            int btnY = startY + row * rowHeight;

            boolean isHovered = isInBounds(mouseX, mouseY, btnX, btnY, CABLE_BTN_SIZE, CABLE_BTN_SIZE);
            boolean isSelected = (i == selectedCable);

            if (isHovered) {
                hoveredCableIndex = i;
                hintText = Component.translatable("meplacementtool.cable." + cableKeys[i]);
                hintColor = 0x8B479B;
            }

            ResourceLocation btnTex = isSelected ? BUTTON_PRESSED : BUTTON_NORMAL;
            guiGraphics.blit(btnTex, btnX, btnY, 0, 0, CABLE_BTN_SIZE, CABLE_BTN_SIZE, CABLE_BTN_SIZE, CABLE_BTN_SIZE);

            ItemStack cableStack = types[i].getStack(AEColor.TRANSPARENT);
            guiGraphics.pose().pushPose();
            float scale = 0.6f;
            guiGraphics.pose().translate(btnX + (CABLE_BTN_SIZE - 16 * scale) / 2, btnY + (CABLE_BTN_SIZE - 16 * scale) / 2, 0);
            guiGraphics.pose().scale(scale, scale, 1.0f);
            guiGraphics.renderItem(cableStack, 0, 0);
            guiGraphics.pose().popPose();

            String label = Component.translatable("meplacementtool.cable." + cableKeys[i] + ".short").getString();
            int textColor = isSelected ? 0xFFFFFF : 0x404040;
            guiGraphics.drawString(font, label, btnX + CABLE_BTN_SIZE + 2, btnY + 2, textColor, false);
        }
    }

    /**
     * Draw placement mode selection at (124,49)-(168,102)
     */
    private void drawModeSection(GuiGraphics guiGraphics, int baseX, int baseY, int mouseX, int mouseY) {
        int areaHeight = MODE_AREA_BOTTOM - MODE_AREA_TOP + 1;

        int selectedMode = menu.currentMode;
        ItemMECablePlacementTool.PlacementMode[] modes = ItemMECablePlacementTool.PlacementMode.values();
        String[] modeIcons = {"L", "F", "B"};
        String[] modeKeys = {"line", "plane_fill", "plane_branching"};

        int rowHeight = MODE_BTN_SIZE + MODE_BTN_SPACING;
        int totalHeight = modes.length * MODE_BTN_SIZE + (modes.length - 1) * MODE_BTN_SPACING;
        int startX = baseX + MODE_AREA_LEFT + 5;
        int startY = baseY + MODE_AREA_TOP + (areaHeight - totalHeight) / 2;

        for (int i = 0; i < modes.length; i++) {
            int btnX = startX;
            int btnY = startY + i * rowHeight;

            boolean isHovered = isInBounds(mouseX, mouseY, btnX, btnY, MODE_BTN_SIZE, MODE_BTN_SIZE);
            boolean isSelected = (i == selectedMode);

            if (isHovered) {
                hoveredModeIndex = i;
                hintText = Component.translatable("meplacementtool.mode." + modeKeys[i]);
                hintColor = 0xFFFFFF;
            }

            ResourceLocation btnTex = isSelected ? BUTTON_PRESSED : BUTTON_NORMAL;
            guiGraphics.blit(btnTex, btnX, btnY, 0, 0, MODE_BTN_SIZE, MODE_BTN_SIZE, MODE_BTN_SIZE, MODE_BTN_SIZE);

            guiGraphics.drawCenteredString(font, modeIcons[i], btnX + MODE_BTN_SIZE / 2, btnY + 2, isSelected ? 0xFFFFFF : 0xE0E0E0);

            String label = Component.translatable("meplacementtool.mode." + modeKeys[i] + ".short").getString();
            int textColor = isSelected ? 0xFFFFFF : 0x404040;
            guiGraphics.drawString(font, label, btnX + MODE_BTN_SIZE + 2, btnY + 2, textColor, false);
        }
    }

    private int getDisplayColor(AEColor color) {
        return color == AEColor.TRANSPARENT ? 0x8B479B : color.mediumVariant;
    }

    private boolean isInBounds(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    protected void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Upgrade slot tooltip - now at (152,8) inside GUI
        List<Slot> upgradeSlots = menu.getSlots(SlotSemantics.UPGRADE);
        if (!upgradeSlots.isEmpty()) {
            Slot upgradeSlot = upgradeSlots.get(0);
            int slotX = leftPos + upgradeSlot.x;
            int slotY = topPos + upgradeSlot.y;

            if (isInBounds(mouseX, mouseY, slotX, slotY, 16, 16)) {
                if (upgradeSlot.getItem().isEmpty()) {
                    List<Component> tooltip = new ArrayList<>();
                    tooltip.add(Component.translatable("gui.meplacementtool.compatible_upgrades").withStyle(ChatFormatting.GOLD));
                    tooltip.add(Component.translatable("gui.meplacementtool.upgrade_hint",
                            new ItemStack(MEPlacementToolMod.KEY_OF_SPECTRUM.get()).getHoverName()).withStyle(ChatFormatting.GRAY));
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (hoveredExpandButton) {
                colorMenuExpanded = !colorMenuExpanded;
                playButtonClickSound();
                return true;
            }

            // Color menu click - directly select color (only works with upgrade for actual coloring)
            if (colorMenuExpanded && hoveredExpandedColorIndex >= 0) {
                menu.setColor(hoveredExpandedColorIndex);
                syncToServer();
                playButtonClickSound();
                colorMenuExpanded = false;
                return true;
            }

            // Color shortcut bar click - select that color
            if (!colorMenuExpanded && hoveredColorIndex >= 0 && colorShortcuts[hoveredColorIndex] >= 0) {
                menu.setColor(colorShortcuts[hoveredColorIndex]);
                syncToServer();
                playButtonClickSound();
                return true;
            }

            if (hoveredCableIndex >= 0) {
                menu.setCableType(hoveredCableIndex);
                syncToServer();
                playButtonClickSound();
                return true;
            }

            if (hoveredModeIndex >= 0) {
                menu.setMode(hoveredModeIndex);
                syncToServer();
                playButtonClickSound();
                return true;
            }
        }

        // Close menu if clicking outside
        if (colorMenuExpanded && button == 0) {
            int menuX = leftPos + COLOR_MENU_X;
            int menuY = topPos + COLOR_MENU_Y;
            if (!isInBounds((int)mouseX, (int)mouseY, menuX, menuY, COLOR_MENU_WIDTH, COLOR_MENU_HEIGHT + 15) && !hoveredExpandButton) {
                colorMenuExpanded = false;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ModKeyBindings.OPEN_CABLE_TOOL_GUI.matches(keyCode, scanCode)) {
            this.onClose();
            return true;
        }

        // Mark color to shortcut bar (A key) - works even without upgrade
        if (ModKeyBindings.MARK_COLOR_SHORTCUT.matches(keyCode, scanCode)) {
            if (colorMenuExpanded && hoveredExpandedColorIndex >= 0) {
                markColorToShortcut(hoveredExpandedColorIndex);
                playButtonClickSound();
                return true;
            } else if (!colorMenuExpanded && hoveredColorIndex > 0) {
                // Unmark from slots 1-5 (not slot 0)
                colorShortcuts[hoveredColorIndex] = -1;
                playButtonClickSound();
                return true;
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void markColorToShortcut(int colorIndex) {
        // Find first empty slot (slots 1-5, skip slot 0)
        for (int i = 1; i < colorShortcuts.length; i++) {
            if (colorShortcuts[i] < 0) {
                colorShortcuts[i] = colorIndex;
                return;
            }
        }
        // If no empty slot, replace last slot
        colorShortcuts[colorShortcuts.length - 1] = colorIndex;
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
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTicks);
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
