package com.moakiee.meplacementtool.client;

import appeng.api.util.AEColor;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.UpgradesPanel;
import appeng.core.localization.GuiText;
import com.moakiee.meplacementtool.CableToolMenu;
import com.moakiee.meplacementtool.ItemMECablePlacementTool;
import com.moakiee.meplacementtool.MEPlacementToolMod;
import com.moakiee.meplacementtool.network.ModNetwork;
import com.moakiee.meplacementtool.network.UpdateCableToolPacket;
import appeng.menu.SlotSemantics;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen for the Cable Placement Tool GUI.
 * Displays cable type, color (if upgrade installed), and placement mode selection.
 */
public class CableToolScreen extends AEBaseScreen<CableToolMenu> {

    // Layout constants - larger GUI (256x200)
    private static final int CONTENT_TOP = 20;
    private static final int CONTENT_HEIGHT = 80;
    
    // Color grid layout - 4 columns x 5 rows (17 colors + Fluix)
    private static final int COLOR_SIZE = 16;
    private static final int COLOR_SPACING = 2;
    private static final int COLOR_COLS = 4;
    
    // Cable buttons - 5 types in column
    private static final int CABLE_BUTTON_SIZE = 20;
    private static final int CABLE_SPACING = 2;
    
    // Mode buttons
    private static final int MODE_BUTTON_HEIGHT = 20;
    private static final int MODE_SPACING = 6;
    
    // Section widths for 3-column layout
    private static final int COLOR_SECTION_WIDTH = 80;
    private static final int CABLE_SECTION_WIDTH = 70;
    private static final int MODE_SECTION_WIDTH = 80;

    // Hint text
    @Nullable
    private Component hintText = null;
    private int hintColor = 0xFFFFFF;

    // Hover state
    private int hoveredColorIndex = -1;
    private int hoveredCableIndex = -1;
    private int hoveredModeIndex = -1;

    public CableToolScreen(CableToolMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
        
        // Set image dimensions for a larger container with 3-column layout
        this.imageHeight = 220;
        this.imageWidth = 256;
        
        // Add upgrades panel with tooltip showing compatible upgrades (Key of Spectrum only)
        // This follows AE2's pattern from UpgradeableScreen
        this.widgets.add("upgrades", new UpgradesPanel(
                menu.getSlots(SlotSemantics.UPGRADE),
                this::getCompatibleUpgrades));
    }

    /**
     * Gets the tooltip text that is shown for empty slots of the upgrade panel to indicate which upgrades are
     * compatible. Only Key of Spectrum is allowed.
     * Follows AE2's UpgradeableScreen pattern.
     */
    private List<Component> getCompatibleUpgrades() {
        var list = new ArrayList<Component>();
        // Use AE2's "Compatible Upgrades:" text (already localized by AE2)
        list.add(GuiText.CompatibleUpgrades.text());
        // Add Key of Spectrum as the only compatible upgrade, with gray formatting like AE2
        list.add(new ItemStack(MEPlacementToolMod.KEY_OF_SPECTRUM.get()).getHoverName().copy().withStyle(ChatFormatting.GRAY));
        return list;
    }

    @Override
    public void init() {
        super.init();
    }

    @Override
    public void drawBG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY, float partialTicks) {
        super.drawBG(guiGraphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        
        // Reset hover state
        hoveredColorIndex = -1;
        hoveredCableIndex = -1;
        hoveredModeIndex = -1;
        hintText = null;

        boolean hasUpgrade = menu.hasUpgradeInstalled();
        
        int contentLeft = offsetX + 8;
        int contentTop = offsetY + CONTENT_TOP;
        
        if (hasUpgrade) {
            // Three-column layout: Color | Cable | Mode (left to right)
            int sectionHeight = CONTENT_HEIGHT;
            
            // Color section - LEFT
            int colorX = contentLeft;
            drawColorSection(guiGraphics, colorX, contentTop, mouseX, mouseY);
            
            // Divider line after Color section
            int divider1X = contentLeft + COLOR_SECTION_WIDTH;
            guiGraphics.fill(divider1X, contentTop, divider1X + 1, contentTop + sectionHeight, 0xFF555555);
            
            // Cable section - CENTER
            int cableX = divider1X + 6;
            drawCableSection(guiGraphics, cableX, contentTop, mouseX, mouseY);
            
            // Divider line after Cable section
            int divider2X = cableX + CABLE_SECTION_WIDTH;
            guiGraphics.fill(divider2X, contentTop, divider2X + 1, contentTop + sectionHeight, 0xFF555555);
            
            // Mode section - RIGHT
            int modeX = divider2X + 6;
            drawModeSection(guiGraphics, modeX, contentTop, mouseX, mouseY);
        } else {
            // Two-column layout: Cable | Mode (centered)
            int totalWidth = CABLE_SECTION_WIDTH + 10 + MODE_SECTION_WIDTH;
            int startX = offsetX + (imageWidth - totalWidth) / 2;
            
            // Cable section - LEFT
            drawCableSection(guiGraphics, startX, contentTop, mouseX, mouseY);
            
            // Divider line
            int dividerX = startX + CABLE_SECTION_WIDTH + 4;
            guiGraphics.fill(dividerX, contentTop, dividerX + 1, contentTop + CONTENT_HEIGHT, 0xFF555555);
            
            // Mode section - RIGHT
            drawModeSection(guiGraphics, dividerX + 6, contentTop, mouseX, mouseY);
        }

        // Draw hint text at bottom (above inventory title)
        if (hintText != null) {
            int hintY = offsetY + 105;
            guiGraphics.drawCenteredString(font, hintText, offsetX + imageWidth / 2, hintY, hintColor);
        }
        
        // Draw slot backgrounds for player inventory (格子线)
        drawPlayerInventorySlotBackgrounds(guiGraphics, offsetX, offsetY);
    }
    
    /**
     * Draw slot backgrounds for player inventory to show grid lines.
     * Uses AE2's standard slot background icon.
     */
    private void drawPlayerInventorySlotBackgrounds(GuiGraphics guiGraphics, int offsetX, int offsetY) {
        for (Slot slot : menu.slots) {
            // Only draw for player inventory slots (not upgrade slot)
            if (slot.container instanceof Inventory) {
                int slotX = offsetX + slot.x - 1;
                int slotY = offsetY + slot.y - 1;
                Icon.SLOT_BACKGROUND.getBlitter()
                        .dest(slotX, slotY)
                        .blit(guiGraphics);
            }
        }
    }

    private void drawColorSection(GuiGraphics guiGraphics, int x, int y, int mouseX, int mouseY) {
        int selectedColor = menu.currentColor;
        
        // Draw section label
        guiGraphics.drawString(font, "颜色", x, y - 10, 0xE0E0E0, false);
        
        for (int i = 0; i < AEColor.values().length; i++) {
            AEColor color = AEColor.values()[i];
            int col = i % COLOR_COLS;
            int row = i / COLOR_COLS;
            
            int colorX = x + col * (COLOR_SIZE + COLOR_SPACING);
            int colorY = y + row * (COLOR_SIZE + COLOR_SPACING);
            
            // Check if hovered
            boolean hovered = mouseX >= colorX && mouseX < colorX + COLOR_SIZE && 
                              mouseY >= colorY && mouseY < colorY + COLOR_SIZE;
            boolean selected = (i == selectedColor);
            
            if (hovered) {
                hoveredColorIndex = i;
                hintText = Component.translatable(getColorTranslationKey(color));
                hintColor = color == AEColor.TRANSPARENT ? 0x8B479B : (color.mediumVariant & 0xFFFFFF);
            }
            
            // Draw color block
            int colorValue = color == AEColor.TRANSPARENT ? 0x8B479B : color.mediumVariant;
            guiGraphics.fill(colorX, colorY, colorX + COLOR_SIZE, colorY + COLOR_SIZE, 0xFF000000 | colorValue);
            
            // Draw border - white for selected, highlight for hovered, dark for others
            int borderColor = selected ? 0xFFFFFFFF : (hovered ? 0xFFAAAAAA : 0xFF444444);
            drawBorder(guiGraphics, colorX, colorY, COLOR_SIZE, COLOR_SIZE, borderColor);
            
            // Draw inner highlight for selected
            if (selected) {
                guiGraphics.fill(colorX + 1, colorY + 1, colorX + COLOR_SIZE - 1, colorY + 2, 0x88FFFFFF);
                guiGraphics.fill(colorX + 1, colorY + 1, colorX + 2, colorY + COLOR_SIZE - 1, 0x88FFFFFF);
            }
        }
    }

    private void drawCableSection(GuiGraphics guiGraphics, int x, int y, int mouseX, int mouseY) {
        int selectedCable = menu.currentCableType;
        ItemMECablePlacementTool.CableType[] types = ItemMECablePlacementTool.CableType.values();
        
        // Draw section label
        guiGraphics.drawString(font, "线缆", x, y - 10, 0xE0E0E0, false);
        
        // Draw in vertical column (or 2 columns if more than 3)
        int cols = types.length > 3 ? 2 : 1;
        for (int i = 0; i < types.length; i++) {
            int col = i / 3;
            int row = i % 3;
            
            int cableX = x + col * (CABLE_BUTTON_SIZE + CABLE_SPACING);
            int cableY = y + row * (CABLE_BUTTON_SIZE + CABLE_SPACING);
            
            boolean hovered = mouseX >= cableX && mouseX < cableX + CABLE_BUTTON_SIZE && 
                              mouseY >= cableY && mouseY < cableY + CABLE_BUTTON_SIZE;
            boolean selected = (i == selectedCable);
            
            if (hovered) {
                hoveredCableIndex = i;
                hintText = Component.translatable(getCableTranslationKey(types[i]));
                hintColor = 0x8B479B; // Fluix purple
            }
            
            // Draw slot background with MC-style slot look
            int bgColor = selected ? 0xFF4A6B8B : (hovered ? 0xFF5A5A5A : 0xFF373737);
            guiGraphics.fill(cableX, cableY, cableX + CABLE_BUTTON_SIZE, cableY + CABLE_BUTTON_SIZE, bgColor);
            
            // Draw cable item icon (always use Fluix color for consistent look)
            ItemStack cableStack = types[i].getStack(AEColor.TRANSPARENT);  // TRANSPARENT = Fluix
            guiGraphics.renderItem(cableStack, cableX + 1, cableY + 1);
            
            // Draw border - purple for selected
            int borderColor = selected ? 0xFF8B479B : 0xFF444444;
            drawBorder(guiGraphics, cableX, cableY, CABLE_BUTTON_SIZE, CABLE_BUTTON_SIZE, borderColor);
        }
    }

    private void drawModeSection(GuiGraphics guiGraphics, int x, int y, int mouseX, int mouseY) {
        int selectedMode = menu.currentMode;
        ItemMECablePlacementTool.PlacementMode[] modes = ItemMECablePlacementTool.PlacementMode.values();
        
        // Draw section label
        guiGraphics.drawString(font, "模式", x, y - 10, 0xE0E0E0, false);
        
        String[] modeNames = {"直线", "填充", "分支"};
        int modeWidth = 58;
        
        for (int i = 0; i < modes.length; i++) {
            int modeY = y + i * (MODE_BUTTON_HEIGHT + MODE_SPACING);
            
            boolean hovered = mouseX >= x && mouseX < x + modeWidth && 
                              mouseY >= modeY && mouseY < modeY + MODE_BUTTON_HEIGHT;
            boolean selected = (i == selectedMode);
            
            if (hovered) {
                hoveredModeIndex = i;
                hintText = Component.translatable("meplacementtool.mode." + modes[i].name().toLowerCase());
                hintColor = 0xFFFFFF;
            }
            
            // Draw button background with MC-style look
            int bgColor = selected ? 0xFF4A6B8B : (hovered ? 0xFF4A4A4A : 0xFF373737);
            guiGraphics.fill(x, modeY, x + modeWidth, modeY + MODE_BUTTON_HEIGHT, bgColor);
            
            // Draw radio button indicator (circle-like)
            int radioX = x + 4;
            int radioY = modeY + 4;
            int radioSize = 10;
            guiGraphics.fill(radioX, radioY, radioX + radioSize, radioY + radioSize, 0xFF222222);
            drawBorder(guiGraphics, radioX, radioY, radioSize, radioSize, 0xFF555555);
            if (selected) {
                guiGraphics.fill(radioX + 2, radioY + 2, radioX + radioSize - 2, radioY + radioSize - 2, 0xFF8B479B);
            }
            
            // Draw mode text
            guiGraphics.drawString(font, modeNames[i], x + 18, modeY + 5, selected ? 0xFFFFFF : 0xE0E0E0, false);
            
            // Draw border - purple for selected
            int borderColor = selected ? 0xFF8B479B : 0xFF444444;
            drawBorder(guiGraphics, x, modeY, modeWidth, MODE_BUTTON_HEIGHT, borderColor);
        }
    }

    private void drawBorder(GuiGraphics guiGraphics, int x, int y, int width, int height, int color) {
        guiGraphics.fill(x, y, x + width, y + 1, color); // Top
        guiGraphics.fill(x, y + height - 1, x + width, y + height, color); // Bottom
        guiGraphics.fill(x, y, x + 1, y + height, color); // Left
        guiGraphics.fill(x + width - 1, y, x + width, y + height, color); // Right
    }

    private String getColorTranslationKey(AEColor color) {
        return "meplacementtool.color." + color.name().toLowerCase();
    }

    private String getCableTranslationKey(ItemMECablePlacementTool.CableType type) {
        return "meplacementtool.cable." + type.name().toLowerCase();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // Handle color selection
            if (hoveredColorIndex >= 0 && menu.hasUpgradeInstalled()) {
                menu.setColor(hoveredColorIndex);
                syncToServer();
                return true;
            }
            
            // Handle cable selection
            if (hoveredCableIndex >= 0) {
                menu.setCableType(hoveredCableIndex);
                syncToServer();
                return true;
            }
            
            // Handle mode selection
            if (hoveredModeIndex >= 0) {
                menu.setMode(hoveredModeIndex);
                syncToServer();
                return true;
            }
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void syncToServer() {
        ModNetwork.CHANNEL.sendToServer(new UpdateCableToolPacket(
            menu.currentMode,
            menu.currentCableType,
            menu.currentColor
        ));
    }
}
