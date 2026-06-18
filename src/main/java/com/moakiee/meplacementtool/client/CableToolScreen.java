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
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import appeng.client.gui.style.Blitter;
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
 * - AE2-style upgrade panel on the right side of GUI
 */
public class CableToolScreen extends AbstractContainerScreen<CableToolMenu> {

    // Textures
    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath("meplacementtool", "textures/gui/cable_tool.png");
    private static final Identifier COLOR_UNSELECTED = Identifier.fromNamespaceAndPath("meplacementtool", "textures/gui/color_unselected.png");
    private static final Identifier COLOR_FRAME = Identifier.fromNamespaceAndPath("meplacementtool", "textures/gui/color_frame.png");
    private static final Identifier EXPAND_BUTTON = Identifier.fromNamespaceAndPath("meplacementtool", "textures/gui/expand_button.png");
    private static final Identifier COLOR_MENU = Identifier.fromNamespaceAndPath("meplacementtool", "textures/gui/color_menu.png");
    private static final Identifier BUTTON_NORMAL = Identifier.fromNamespaceAndPath("meplacementtool", "textures/gui/button_normal.png");
    private static final Identifier BUTTON_PRESSED = Identifier.fromNamespaceAndPath("meplacementtool", "textures/gui/button_pressed.png");

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
    private static final int COLOR_CELL_SPACING = 4; // Match expanded menu spacing
    private static final int EXPAND_BTN_SIZE = 12;

    // === Expanded color menu: at (7,33), 112x49px ===
    private static final int COLOR_MENU_X = 7;
    private static final int COLOR_MENU_Y = 33;
    private static final int COLOR_MENU_WIDTH = 112;
    private static final int COLOR_MENU_HEIGHT = 49;
    private static final int COLOR_MENU_COLS = 6;
    private static final int COLOR_MENU_CELL_SIZE = 11;
    private static final int COLOR_MENU_CELL_SPACING = 4;
    private static final int COLOR_MENU_PADDING = 2; // Reduced from 5 to move colors up 3px

    // === Cable selection: (7,49) to (118,102) - TWO COLUMNS ===
    private static final int CABLE_AREA_LEFT = 7;
    private static final int CABLE_AREA_TOP = 49;
    private static final int CABLE_AREA_RIGHT = 118;
    private static final int CABLE_AREA_BOTTOM = 102;
    private static final int CABLE_BTN_WIDTH = 12;
    private static final int CABLE_BTN_HEIGHT = 11;
    private static final int CABLE_BTN_SPACING = 3;

    // === Mode selection: (124,49) to (168,102) ===
    private static final int MODE_AREA_LEFT = 124;
    private static final int MODE_AREA_TOP = 49;
    private static final int MODE_AREA_RIGHT = 168;
    private static final int MODE_AREA_BOTTOM = 102;
    private static final int MODE_BTN_WIDTH = 12;
    private static final int MODE_BTN_HEIGHT = 11;
    private static final int MODE_BTN_SPACING = 3;

    // AE2 Upgrade Panel - positioned to the right of GUI
    private static final int AE2_PADDING = 5;
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

    // Color shortcut bar - stores color indices (-1 = empty slot)
    private int[] colorShortcuts = new int[]{0, -1, -1, -1, -1, -1};

    public CableToolScreen(CableToolMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
        this.titleLabelX = -9999;
        this.titleLabelY = -9999;
        this.inventoryLabelX = -9999;
        this.inventoryLabelY = -9999;

        // Create AE2 UpgradesPanel for the upgrade slot
        this.upgradesPanel = new UpgradesPanel(menu.getSlots(SlotSemantics.UPGRADE));

        // Load current color for slot 0
        colorShortcuts[0] = menu.currentColor;
        
        // Load saved color shortcuts from menu (slots 1-5)
        int[] savedShortcuts = menu.getColorShortcuts();
        for (int i = 0; i < 5 && i < savedShortcuts.length; i++) {
            colorShortcuts[i + 1] = savedShortcuts[i];
        }
    }

    @Override
    protected void init() {
        super.init();
        // Position the AE2 upgrade panel to the right side of GUI (left-shifted by 1px)
        this.upgradesPanel.setPosition(new Point(GUI_WIDTH - 2, AE2_PADDING));
        this.upgradesPanel.populateScreen(this::addRenderableWidget, getBounds(), null);
    }

    private Rect2i getBounds() {
        return new Rect2i(leftPos, topPos, imageWidth, imageHeight);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor GuiGraphicsExtractor, int mouseX, int mouseY, float partialTicks) {
        super.extractBackground(GuiGraphicsExtractor, mouseX, mouseY, partialTicks);
        int x = this.leftPos;
        int y = this.topPos;

        // Draw main GUI background
        GuiGraphicsExtractor.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, x, y, 0, 0, GUI_WIDTH, GUI_HEIGHT, GUI_WIDTH, GUI_HEIGHT);
    }

    @Override
    public void extractContents(GuiGraphicsExtractor GuiGraphicsExtractor, int mouseX, int mouseY, float partialTicks) {
        int x = this.leftPos;
        int y = this.topPos;

        // Reset hover states
        hoveredColorIndex = -1;
        hoveredExpandedColorIndex = -1;
        hoveredCableIndex = -1;
        hoveredModeIndex = -1;
        hoveredExpandButton = false;
        hintText = null;

        // Draw color shortcut bar
        drawColorBar(GuiGraphicsExtractor, x, y, mouseX, mouseY);

        // Draw cable and mode selection areas (unless color menu is covering them)
        if (!colorMenuExpanded) {
            drawCableSection(GuiGraphicsExtractor, x, y, mouseX, mouseY);
            drawModeSection(GuiGraphicsExtractor, x, y, mouseX, mouseY);
        }

        // Draw expanded color menu ON TOP
        if (colorMenuExpanded) {
            drawColorMenu(GuiGraphicsExtractor, x, y, mouseX, mouseY);
        }

        // Update and draw AE2 upgrade panel
        upgradesPanel.updateBeforeRender();
        upgradesPanel.drawBackgroundLayer(GuiGraphicsExtractor, getBounds(), new Point(mouseX - leftPos, mouseY - topPos));
        
        // Draw upgrade slot icon if empty
        drawUpgradeSlotIcon(GuiGraphicsExtractor);

        super.extractContents(GuiGraphicsExtractor, mouseX, mouseY, partialTicks);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor GuiGraphicsExtractor, int mouseX, int mouseY) {
        // Draw hint text in the area to the right of color bar
        // Position: starts at COLOR_BAR_RIGHT + 5px gap, centered in available width
        if (hintText != null) {
            int hintAreaLeft = COLOR_BAR_RIGHT + 5; // 5px gap from color bar
            int hintAreaRight = GUI_WIDTH - 5;       // 5px from right edge
            int hintAreaWidth = hintAreaRight - hintAreaLeft;
            int hintY = 18;  // Same Y as color bar
            
            // Use font.split for auto word-wrap
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(hintText, hintAreaWidth);
            for (int i = 0; i < lines.size() && i < 3; i++) { // Max 3 lines
                // Center each line in the available area
                int lineWidth = font.width(lines.get(i));
                int lineX = hintAreaLeft + (hintAreaWidth - lineWidth) / 2;
                GuiGraphicsExtractor.text(font, lines.get(i), lineX, hintY + i * 10, hintColor, false);
            }
        }
    }

    /**
     * Draw color shortcut bar at (9,18)-(116,32)
     */
    private void drawColorBar(GuiGraphicsExtractor GuiGraphicsExtractor, int baseX, int baseY, int mouseX, int mouseY) {
        int areaWidth = COLOR_BAR_RIGHT - COLOR_BAR_LEFT + 1;
        int areaHeight = COLOR_BAR_BOTTOM - COLOR_BAR_TOP + 1;
        
        // Calculate centered position for color cells
        int totalCellsWidth = COLOR_CELL_COUNT * COLOR_CELL_SIZE + (COLOR_CELL_COUNT - 1) * COLOR_CELL_SPACING + EXPAND_BTN_SIZE + COLOR_CELL_SPACING;
        int startX = baseX + COLOR_BAR_LEFT + (areaWidth - totalCellsWidth) / 2;
        int startY = baseY + COLOR_BAR_TOP + (areaHeight - COLOR_CELL_SIZE) / 2;

        // Slot 0: shows current color only if upgrade is installed, otherwise stays Fluix
        if (menu.hasUpgrade) {
            colorShortcuts[0] = menu.currentColor;
        } else {
            colorShortcuts[0] = AEColor.TRANSPARENT.ordinal(); // Fluix = 16
        }

        for (int i = 0; i < COLOR_CELL_COUNT; i++) {
            int cellX = startX + i * (COLOR_CELL_SIZE + COLOR_CELL_SPACING);
            int cellY = startY;

            int colorIndex = colorShortcuts[i];
            boolean isHovered = isInBounds(mouseX, mouseY, cellX, cellY, COLOR_CELL_SIZE, COLOR_CELL_SIZE);

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
                GuiGraphicsExtractor.blit(COLOR_UNSELECTED, cellX, cellY, 0, 0, COLOR_CELL_SIZE, COLOR_CELL_SIZE, COLOR_CELL_SIZE, COLOR_CELL_SIZE);
            } else {
                GuiGraphicsExtractor.blit(COLOR_FRAME, cellX, cellY, 0, 0, COLOR_CELL_SIZE, COLOR_CELL_SIZE, COLOR_CELL_SIZE, COLOR_CELL_SIZE);
                AEColor color = AEColor.values()[colorIndex];
                int fillColor = getDisplayColor(color);
                GuiGraphicsExtractor.fill(cellX + 1, cellY + 1, cellX + COLOR_CELL_SIZE - 1, cellY + COLOR_CELL_SIZE - 1, 0xFF000000 | fillColor);
            }
        }

        int expandX = startX + COLOR_CELL_COUNT * (COLOR_CELL_SIZE + COLOR_CELL_SPACING);
        int expandY = startY + (COLOR_CELL_SIZE - EXPAND_BTN_SIZE) / 2;
        hoveredExpandButton = isInBounds(mouseX, mouseY, expandX, expandY, EXPAND_BTN_SIZE, EXPAND_BTN_SIZE);

        if (hoveredExpandButton) {
            if (menu.hasUpgrade) {
                hintText = Component.translatable(colorMenuExpanded ? "gui.meplacementtool.collapse_colors" : "gui.meplacementtool.expand_colors");
                hintColor = 0x000000;
            } else {
                hintText = Component.translatable("gui.meplacementtool.need_spectrum_key");
                hintColor = 0xFF5555; // Red color to indicate requirement
            }
        }

        GuiGraphicsExtractor.blit(EXPAND_BUTTON, expandX, expandY, 0, 0, EXPAND_BTN_SIZE, EXPAND_BTN_SIZE, EXPAND_BTN_SIZE, EXPAND_BTN_SIZE);
    }

    /**
     * Draw expanded color menu at (7,33).
     */
    private void drawColorMenu(GuiGraphicsExtractor GuiGraphicsExtractor, int baseX, int baseY, int mouseX, int mouseY) {
        int menuX = baseX + COLOR_MENU_X;
        int menuY = baseY + COLOR_MENU_Y;

        GuiGraphicsExtractor.blit(COLOR_MENU, menuX, menuY, 0, 0, COLOR_MENU_WIDTH, COLOR_MENU_HEIGHT, COLOR_MENU_WIDTH, COLOR_MENU_HEIGHT);

        AEColor[] colors = AEColor.values();
        
        // Align with shortcut bar: use same startX calculation
        int areaWidth = COLOR_BAR_RIGHT - COLOR_BAR_LEFT + 1;
        int totalCellsWidth = COLOR_CELL_COUNT * COLOR_CELL_SIZE + (COLOR_CELL_COUNT - 1) * COLOR_CELL_SPACING + EXPAND_BTN_SIZE + COLOR_CELL_SPACING;
        int cellStartX = baseX + COLOR_BAR_LEFT + (areaWidth - totalCellsWidth) / 2;
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

            GuiGraphicsExtractor.blit(COLOR_FRAME, cellX, cellY, 0, 0, COLOR_MENU_CELL_SIZE, COLOR_MENU_CELL_SIZE, COLOR_MENU_CELL_SIZE, COLOR_MENU_CELL_SIZE);
            AEColor color = colors[i];
            int fillColor = getDisplayColor(color);
            GuiGraphicsExtractor.fill(cellX + 1, cellY + 1, cellX + COLOR_MENU_CELL_SIZE - 1, cellY + COLOR_MENU_CELL_SIZE - 1, 0xFF000000 | fillColor);

            if (isSelected) {
                GuiGraphicsExtractor.outline(cellX - 1, cellY - 1, COLOR_MENU_CELL_SIZE + 2, COLOR_MENU_CELL_SIZE + 2, 0xFF00FF00);
            }
        }

        Component markHint = Component.translatable("gui.meplacementtool.mark_hint", 
            ModKeyBindings.MARK_COLOR_SHORTCUT.getTranslatedKeyMessage());
        GuiGraphicsExtractor.text(font, markHint, menuX + 2, menuY + COLOR_MENU_HEIGHT + 2, 0xAAAAAA, false);
    }

    /**
     * Draw cable type selection at (7,49)-(118,102) - TWO COLUMNS
     */
    private void drawCableSection(GuiGraphicsExtractor GuiGraphicsExtractor, int baseX, int baseY, int mouseX, int mouseY) {
        int areaWidth = CABLE_AREA_RIGHT - CABLE_AREA_LEFT + 1;
        
        int selectedCable = menu.currentCableType;
        ItemMECablePlacementTool.CableType[] types = ItemMECablePlacementTool.CableType.values();
        String[] cableKeys = {"glass", "covered", "smart", "dense_covered", "dense_smart"};

        int colWidth = areaWidth / 2;
        int rowHeight = CABLE_BTN_HEIGHT + CABLE_BTN_SPACING;
        
        int leftColX = baseX + CABLE_AREA_LEFT + 5;
        int rightColX = baseX + CABLE_AREA_LEFT + colWidth + 5;
        int startY = baseY + CABLE_AREA_TOP + 5;

        for (int i = 0; i < types.length; i++) {
            int col = i / 3;
            int row = i % 3;
            
            int btnX = (col == 0) ? leftColX : rightColX;
            int btnY = startY + row * rowHeight;

            boolean isHovered = isInBounds(mouseX, mouseY, btnX, btnY, CABLE_BTN_WIDTH, CABLE_BTN_HEIGHT);
            boolean isSelected = (i == selectedCable);

            if (isHovered) {
                hoveredCableIndex = i;
                hintText = Component.translatable("meplacementtool.cable." + cableKeys[i]);
                hintColor = 0x8B479B;
            }

            Identifier btnTex = isSelected ? BUTTON_PRESSED : BUTTON_NORMAL;
            GuiGraphicsExtractor.blit(btnTex, btnX, btnY, 0, 0, CABLE_BTN_WIDTH, CABLE_BTN_HEIGHT, CABLE_BTN_WIDTH, CABLE_BTN_HEIGHT);

            ItemStack cableStack = types[i].getStack(AEColor.TRANSPARENT);
            GuiGraphicsExtractor.pose().pushMatrix();
            float scale = 0.55f;
            GuiGraphicsExtractor.pose().translate(btnX + (CABLE_BTN_WIDTH - 16 * scale) / 2, btnY + (CABLE_BTN_HEIGHT - 16 * scale) / 2);
            GuiGraphicsExtractor.pose().scale(scale, scale);
            GuiGraphicsExtractor.item(cableStack, 0, 0);
            GuiGraphicsExtractor.pose().popMatrix();

            String label = Component.translatable("meplacementtool.cable." + cableKeys[i] + ".short").getString();
            int textColor = isSelected ? 0xFFFFFF : 0x404040;
            GuiGraphicsExtractor.text(font, label, btnX + CABLE_BTN_WIDTH + 2, btnY + 1, textColor, false);
        }
    }

    /**
     * Draw placement mode selection at (124,49)-(168,102)
     */
    private void drawModeSection(GuiGraphicsExtractor GuiGraphicsExtractor, int baseX, int baseY, int mouseX, int mouseY) {
        int areaHeight = MODE_AREA_BOTTOM - MODE_AREA_TOP + 1;

        int selectedMode = menu.currentMode;
        ItemMECablePlacementTool.PlacementMode[] modes = ItemMECablePlacementTool.PlacementMode.values();
        String[] modeIcons = {"L", "F", "B"};
        String[] modeKeys = {"line", "plane_fill", "plane_branching"};

        int rowHeight = MODE_BTN_HEIGHT + MODE_BTN_SPACING;
        int totalHeight = modes.length * MODE_BTN_HEIGHT + (modes.length - 1) * MODE_BTN_SPACING;
        int startX = baseX + MODE_AREA_LEFT + 5;
        int startY = baseY + MODE_AREA_TOP + (areaHeight - totalHeight) / 2;

        for (int i = 0; i < modes.length; i++) {
            int btnX = startX;
            int btnY = startY + i * rowHeight;

            boolean isHovered = isInBounds(mouseX, mouseY, btnX, btnY, MODE_BTN_WIDTH, MODE_BTN_HEIGHT);
            boolean isSelected = (i == selectedMode);

            if (isHovered) {
                hoveredModeIndex = i;
                hintText = Component.translatable("meplacementtool.mode." + modeKeys[i]);
                hintColor = 0x000000; // Black color for mode hints
            }

            Identifier btnTex = isSelected ? BUTTON_PRESSED : BUTTON_NORMAL;
            GuiGraphicsExtractor.blit(btnTex, btnX, btnY, 0, 0, MODE_BTN_WIDTH, MODE_BTN_HEIGHT, MODE_BTN_WIDTH, MODE_BTN_HEIGHT);

            GuiGraphicsExtractor.centeredText(font, modeIcons[i], btnX + MODE_BTN_WIDTH / 2, btnY + 1, isSelected ? 0xFFFFFF : 0xE0E0E0);

            String label = Component.translatable("meplacementtool.mode." + modeKeys[i] + ".short").getString();
            int textColor = isSelected ? 0xFFFFFF : 0x404040;
            GuiGraphicsExtractor.text(font, label, btnX + MODE_BTN_WIDTH + 2, btnY + 1, textColor, false);
        }
    }

    /**
     * Draw upgrade slot icon when slot is empty
     */
    private void drawUpgradeSlotIcon(GuiGraphicsExtractor GuiGraphicsExtractor) {
        List<Slot> upgradeSlots = menu.getSlots(SlotSemantics.UPGRADE);
        if (!upgradeSlots.isEmpty()) {
            Slot upgradeSlot = upgradeSlots.get(0);
            if (upgradeSlot.getItem().isEmpty() && upgradeSlot instanceof AppEngSlot appEngSlot) {
                if (appEngSlot.isSlotEnabled() && appEngSlot.getIcon() != null) {
                    Blitter.icon(appEngSlot.getIcon())
                        .dest(leftPos + upgradeSlot.x, topPos + upgradeSlot.y)
                        .opacity(0.7f)
                        .blit(GuiGraphicsExtractor);
                }
            }
        }
    }

    private int getDisplayColor(AEColor color) {
        return color == AEColor.TRANSPARENT ? 0x8B479B : color.mediumVariant;
    }

    private boolean isInBounds(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor GuiGraphicsExtractor, int mouseX, int mouseY) {
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
                    GuiGraphicsExtractor.setTooltipForNextFrame(font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
                    return;
                } else {
                    GuiGraphicsExtractor.setTooltipForNextFrame(font, upgradeSlot.getItem(), mouseX, mouseY);
                    return;
                }
            }
        }
        super.extractTooltip(GuiGraphicsExtractor, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();

        if (button == 0) {
            if (hoveredExpandButton) {
                // Only allow expanding if upgrade is installed
                if (menu.hasUpgrade) {
                    colorMenuExpanded = !colorMenuExpanded;
                    playButtonClickSound();
                }
                return true;
            }

            if (colorMenuExpanded && hoveredExpandedColorIndex >= 0) {
                menu.setColor(hoveredExpandedColorIndex);
                syncToServer();
                playButtonClickSound();
                colorMenuExpanded = false;
                return true;
            }

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

        if (colorMenuExpanded && button == 0) {
            int menuX = leftPos + COLOR_MENU_X;
            int menuY = topPos + COLOR_MENU_Y;
            if (!isInBounds((int)mouseX, (int)mouseY, menuX, menuY, COLOR_MENU_WIDTH, COLOR_MENU_HEIGHT + 15) && !hoveredExpandButton) {
                colorMenuExpanded = false;
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (ModKeyBindings.OPEN_CABLE_TOOL_GUI.matches(event)) {
            this.onClose();
            return true;
        }

        if (ModKeyBindings.MARK_COLOR_SHORTCUT.matches(event)) {
            // When expanded menu is open, check both expanded menu hover and shortcut bar hover
            if (colorMenuExpanded && hoveredExpandedColorIndex >= 0) {
                // Toggle mark/unmark in expanded menu
                markColorToShortcut(hoveredExpandedColorIndex);
                playButtonClickSound();
                return true;
            }
            // Also allow unmark from shortcut bar even when menu is expanded
            if (hoveredColorIndex > 0 && colorShortcuts[hoveredColorIndex] >= 0) {
                // Toggle mark/unmark in shortcut bar (slots 1-5 only)
                markColorToShortcut(colorShortcuts[hoveredColorIndex]);
                playButtonClickSound();
                return true;
            }
        }

        return super.keyPressed(event);
    }

    /**
     * Mark or unmark a color in the shortcut bar.
     * If the color is already in shortcuts (slots 1-5), remove it.
     * Otherwise, add it to the first empty slot.
     */
    private void markColorToShortcut(int colorIndex) {
        // Check if this color is already in shortcuts (slots 1-5) - if so, remove it
        for (int i = 1; i < colorShortcuts.length; i++) {
            if (colorShortcuts[i] == colorIndex) {
                colorShortcuts[i] = -1; // Unmark
                menu.setColorShortcut(i - 1, -1); // Save to menu (0-indexed for menu)
                return;
            }
        }
        
        // Not found, add to first empty slot
        for (int i = 1; i < colorShortcuts.length; i++) {
            if (colorShortcuts[i] < 0) {
                colorShortcuts[i] = colorIndex;
                menu.setColorShortcut(i - 1, colorIndex); // Save to menu (0-indexed for menu)
                return;
            }
        }
        // If no empty slot, replace last slot
        colorShortcuts[colorShortcuts.length - 1] = colorIndex;
        menu.setColorShortcut(4, colorIndex); // Save to menu
    }

    private void playButtonClickSound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private void syncToServer() {
        ClientPacketDistributor.sendToServer(new UpdateCableToolPayload(
            menu.currentMode,
            menu.currentCableType,
            menu.currentColor
        ));
    }

}
