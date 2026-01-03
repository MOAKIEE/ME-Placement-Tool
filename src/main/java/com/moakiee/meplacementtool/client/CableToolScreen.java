package com.moakiee.meplacementtool.client;

import appeng.api.util.AEColor;
import appeng.client.gui.Icon;
import appeng.menu.SlotSemantics;
import com.moakiee.meplacementtool.CableToolMenu;
import com.moakiee.meplacementtool.ItemMECablePlacementTool;
import com.moakiee.meplacementtool.network.ModNetwork;
import com.moakiee.meplacementtool.network.UpdateCableToolPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen for the Cable Placement Tool GUI.
 * Uses custom texture-based controls matching the provided GUI design.
 * Upgrade slot is rendered outside the main GUI on the right side, like AE2.
 */
public class CableToolScreen extends AbstractContainerScreen<CableToolMenu> {

    // Background texture (256x256 with 175-wide GUI body on left)
    private static final ResourceLocation BACKGROUND = new ResourceLocation("meplacementtool", "textures/gui/cable_tool.png");
    
    // GUI dimensions
    private static final int GUI_WIDTH = 175;
    private static final int GUI_HEIGHT = 256;
    
    // Texture UV coordinates for controls
    // Button: normal (177,0)-(192,15), pressed (194,0)-(209,15)
    private static final int BTN_U_NORMAL = 177;
    private static final int BTN_V_NORMAL = 0;
    private static final int BTN_U_PRESSED = 194;
    private static final int BTN_V_PRESSED = 0;
    private static final int BTN_SIZE = 16;
    
    // Slot background: (177,17)-(200,40), size 24x24
    private static final int SLOT_U = 177;
    private static final int SLOT_V = 17;
    private static final int SLOT_BG_SIZE = 24;
    
    // Title text position: (7,161)
    private static final int TITLE_X = 7;
    private static final int TITLE_Y = 161;
    
    // Visible area: (8,8) to (167,159), width=159, height=151
    private static final int VISIBLE_X = 8;
    private static final int VISIBLE_Y = 8;
    private static final int VISIBLE_WIDTH = 159;
    private static final int VISIBLE_HEIGHT = 151;
    
    // Section header height
    private static final int HEADER_HEIGHT = 12;
    
    // Color selection: button style with small color block inside (17 colors, 3 cols x 6 rows)
    // Only shown when upgrade is installed
    private static final int COLOR_COLS = 3;
    private static final int COLOR_BTN_SPACING = 2;
    
    // With upgrade layout: 3 columns (Color, Cable, Mode)
    // Add padding from left edge and increase spacing between columns
    private static final int UPGRADE_COLOR_START_X = 12;      // +4 from edge
    private static final int UPGRADE_CABLE_START_X = 70;      // Increased gap from color
    private static final int UPGRADE_MODE_START_X = 118;      // Adjusted accordingly
    private static final int UPGRADE_CONTENT_START_Y = 26;    // +4 from top, after header
    
    // Without upgrade layout: 2 columns (Cable, Mode), centered in visible area
    private static final int NO_UPGRADE_CABLE_START_X = 28;   // More padding from left
    private static final int NO_UPGRADE_MODE_START_X = 95;    // Adjusted for balance
    private static final int NO_UPGRADE_CONTENT_START_Y = 26; // +4 from top, after header
    
    // External Upgrade panel (right side of GUI, like AE2)
    private static final int UPGRADE_PANEL_PADDING = 7;
    private static final int UPGRADE_SLOT_SIZE = 18;

    // Hover state
    private int hoveredColorIndex = -1;
    private int hoveredCableIndex = -1;
    private int hoveredModeIndex = -1;
    
    // Hint text
    @Nullable
    private Component hintText = null;
    private int hintColor = 0xFFFFFF;

    public CableToolScreen(CableToolMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
        // Hide default title and inventory labels
        this.titleLabelX = -9999;
        this.titleLabelY = -9999;
        this.inventoryLabelX = -9999;
        this.inventoryLabelY = -9999;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTicks, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        
        // Draw main background
        guiGraphics.blit(BACKGROUND, x, y, 0, 0, GUI_WIDTH, GUI_HEIGHT);
        
        // Reset hover state
        hoveredColorIndex = -1;
        hoveredCableIndex = -1;
        hoveredModeIndex = -1;
        hintText = null;
        
        // Draw "物品栏" title at (7,161)
        guiGraphics.drawString(font, Component.translatable("container.inventory"), x + TITLE_X, y + TITLE_Y, 0x404040, false);
        
        // Determine positions based on upgrade status
        boolean hasUpgrade = menu.hasUpgradeInstalled();
        
        if (hasUpgrade) {
            // With upgrade: 3 columns layout
            // Draw section headers
            drawSectionHeadersWithUpgrade(guiGraphics, x, y);
            // Draw color selection
            drawColorSection(guiGraphics, x + UPGRADE_COLOR_START_X, y + UPGRADE_CONTENT_START_Y, mouseX, mouseY);
            // Draw cable type buttons
            drawCableSection(guiGraphics, x + UPGRADE_CABLE_START_X, y + UPGRADE_CONTENT_START_Y, mouseX, mouseY);
            // Draw mode buttons
            drawModeSection(guiGraphics, x + UPGRADE_MODE_START_X, y + UPGRADE_CONTENT_START_Y, mouseX, mouseY);
        } else {
            // Without upgrade: 2 columns layout, centered
            // Draw section headers
            drawSectionHeadersNoUpgrade(guiGraphics, x, y);
            // Draw cable type buttons
            drawCableSection(guiGraphics, x + NO_UPGRADE_CABLE_START_X, y + NO_UPGRADE_CONTENT_START_Y, mouseX, mouseY);
            // Draw mode buttons
            drawModeSection(guiGraphics, x + NO_UPGRADE_MODE_START_X, y + NO_UPGRADE_CONTENT_START_Y, mouseX, mouseY);
        }
        
        // Draw external upgrade panel (right side of GUI, like AE2)
        drawUpgradePanel(guiGraphics, x + GUI_WIDTH, y);
        
        // Draw hint text
        if (hintText != null) {
            int hintY = y + 145;
            guiGraphics.drawCenteredString(font, hintText, x + GUI_WIDTH / 2, hintY, hintColor);
        }
    }
    
    /**
     * Draw section headers when upgrade is installed (3 columns).
     */
    private void drawSectionHeadersWithUpgrade(GuiGraphics guiGraphics, int x, int y) {
        int headerY = y + VISIBLE_Y + 4;  // Add padding from top edge
        
        // Color section header
        String colorHeader = Component.translatable("gui.meplacementtool.color").getString();
        guiGraphics.drawString(font, colorHeader, x + UPGRADE_COLOR_START_X, headerY, 0x404040, false);
        
        // Cable section header
        String cableHeader = Component.translatable("gui.meplacementtool.cable_type").getString();
        guiGraphics.drawString(font, cableHeader, x + UPGRADE_CABLE_START_X, headerY, 0x404040, false);
        
        // Mode section header
        String modeHeader = Component.translatable("gui.meplacementtool.mode").getString();
        guiGraphics.drawString(font, modeHeader, x + UPGRADE_MODE_START_X, headerY, 0x404040, false);
    }
    
    /**
     * Draw section headers when no upgrade is installed (2 columns, centered).
     */
    private void drawSectionHeadersNoUpgrade(GuiGraphics guiGraphics, int x, int y) {
        int headerY = y + VISIBLE_Y + 4;  // Add padding from top edge
        
        // Cable section header
        String cableHeader = Component.translatable("gui.meplacementtool.cable_type").getString();
        guiGraphics.drawString(font, cableHeader, x + NO_UPGRADE_CABLE_START_X, headerY, 0x404040, false);
        
        // Mode section header
        String modeHeader = Component.translatable("gui.meplacementtool.mode").getString();
        guiGraphics.drawString(font, modeHeader, x + NO_UPGRADE_MODE_START_X, headerY, 0x404040, false);
    }
    
    /**
     * Draw color section as buttons with small color blocks inside.
     * Labels show on right side of each button row.
     */
    private void drawColorSection(GuiGraphics guiGraphics, int baseX, int baseY, int mouseX, int mouseY) {
        int selectedColor = menu.currentColor;
        AEColor[] colors = AEColor.values();
        
        for (int i = 0; i < colors.length; i++) {
            AEColor color = colors[i];
            int col = i % COLOR_COLS;
            int row = i / COLOR_COLS;
            
            int bx = baseX + col * (BTN_SIZE + COLOR_BTN_SPACING);
            int by = baseY + row * (BTN_SIZE + COLOR_BTN_SPACING);
            
            boolean hovered = isInBounds(mouseX, mouseY, bx, by, BTN_SIZE, BTN_SIZE);
            boolean selected = (i == selectedColor);
            
            if (hovered) {
                hoveredColorIndex = i;
                hintText = Component.translatable("meplacementtool.color." + color.name().toLowerCase());
                hintColor = color == AEColor.TRANSPARENT ? 0x8B479B : (color.mediumVariant & 0xFFFFFF);
            }
            
            // Draw button background from texture
            int btnU = selected ? BTN_U_PRESSED : BTN_U_NORMAL;
            int btnV = selected ? BTN_V_PRESSED : BTN_V_NORMAL;
            guiGraphics.blit(BACKGROUND, bx, by, btnU, btnV, BTN_SIZE, BTN_SIZE);
            
            // Draw small color block inside button (centered, ~8x8)
            int colorBlockSize = 8;
            int cbx = bx + (BTN_SIZE - colorBlockSize) / 2;
            int cby = by + (BTN_SIZE - colorBlockSize) / 2;
            int colorValue = color == AEColor.TRANSPARENT ? 0x8B479B : color.mediumVariant;
            guiGraphics.fill(cbx, cby, cbx + colorBlockSize, cby + colorBlockSize, 0xFF000000 | colorValue);
            
            // Draw selection border on color block
            if (selected) {
                drawBorder(guiGraphics, cbx - 1, cby - 1, colorBlockSize + 2, colorBlockSize + 2, 0xFFFFFFFF);
            }
        }
    }
    
    /**
     * Draw cable type section: button on left, cable item icon on right.
     */
    private void drawCableSection(GuiGraphics guiGraphics, int baseX, int baseY, int mouseX, int mouseY) {
        int selectedCable = menu.currentCableType;
        ItemMECablePlacementTool.CableType[] types = ItemMECablePlacementTool.CableType.values();
        
        for (int i = 0; i < types.length; i++) {
            int bx = baseX;
            int by = baseY + i * (BTN_SIZE + 2);
            
            boolean hovered = isInBounds(mouseX, mouseY, bx, by, BTN_SIZE, BTN_SIZE);
            boolean selected = (i == selectedCable);
            
            if (hovered) {
                hoveredCableIndex = i;
                hintText = Component.translatable("meplacementtool.cable." + types[i].name().toLowerCase());
                hintColor = 0x8B479B;
            }
            
            // Draw button background from texture
            int btnU = selected ? BTN_U_PRESSED : BTN_U_NORMAL;
            int btnV = selected ? BTN_V_PRESSED : BTN_V_NORMAL;
            guiGraphics.blit(BACKGROUND, bx, by, btnU, btnV, BTN_SIZE, BTN_SIZE);
            
            // Draw cable item icon to the RIGHT of the button
            ItemStack cableStack = types[i].getStack(AEColor.TRANSPARENT);
            guiGraphics.renderItem(cableStack, bx + BTN_SIZE + 2, by);
        }
    }
    
    /**
     * Draw mode section: button with icon on left, full name on right.
     */
    private void drawModeSection(GuiGraphics guiGraphics, int baseX, int baseY, int mouseX, int mouseY) {
        int selectedMode = menu.currentMode;
        ItemMECablePlacementTool.PlacementMode[] modes = ItemMECablePlacementTool.PlacementMode.values();
        String[] modeIcons = {"L", "F", "B"};
        String[] modeKeys = {"line", "plane_fill", "plane_branching"};
        
        for (int i = 0; i < modes.length; i++) {
            int bx = baseX;
            int by = baseY + i * (BTN_SIZE + 2);
            
            boolean hovered = isInBounds(mouseX, mouseY, bx, by, BTN_SIZE, BTN_SIZE);
            boolean selected = (i == selectedMode);
            
            if (hovered) {
                hoveredModeIndex = i;
                hintText = Component.translatable("meplacementtool.mode." + modeKeys[i]);
                hintColor = 0xFFFFFF;
            }
            
            // Draw button background from texture
            int btnU = selected ? BTN_U_PRESSED : BTN_U_NORMAL;
            int btnV = selected ? BTN_V_PRESSED : BTN_V_NORMAL;
            guiGraphics.blit(BACKGROUND, bx, by, btnU, btnV, BTN_SIZE, BTN_SIZE);
            
            // Draw mode icon in button
            int iconColor = selected ? 0xFFFFFF : 0xE0E0E0;
            guiGraphics.drawCenteredString(font, modeIcons[i], bx + BTN_SIZE / 2, by + 4, iconColor);
            
            // Draw mode short name to the RIGHT of the button
            String modeName = Component.translatable("meplacementtool.mode." + modeKeys[i] + ".short").getString();
            int textColor = selected ? 0xFFFFFF : 0xA0A0A0;
            guiGraphics.drawString(font, modeName, bx + BTN_SIZE + 3, by + 4, textColor, false);
        }
    }
    
    /**
     * Draw external upgrade panel on the right side of GUI (like AE2).
     * Uses custom slot texture from our GUI image.
     */
    private void drawUpgradePanel(GuiGraphics guiGraphics, int panelX, int panelY) {
        // Get upgrade slot
        List<Slot> upgradeSlots = menu.getSlots(SlotSemantics.UPGRADE);
        if (upgradeSlots.isEmpty()) {
            return;
        }
        
        int slotCount = 1; // We only have 1 upgrade slot
        int panelWidth = UPGRADE_PANEL_PADDING * 2 + UPGRADE_SLOT_SIZE;
        int panelHeight = UPGRADE_PANEL_PADDING * 2 + slotCount * UPGRADE_SLOT_SIZE;
        
        // Draw panel background using our slot texture
        // Draw a simple panel background
        guiGraphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xFFC6C6C6);
        
        // Draw border
        drawBorder(guiGraphics, panelX, panelY, panelWidth, panelHeight, 0xFF555555);
        
        // Draw slot background from our texture
        int slotX = panelX + UPGRADE_PANEL_PADDING;
        int slotY = panelY + UPGRADE_PANEL_PADDING;
        
        // Use our custom slot texture (24x24, but we only need 18x18 for the slot)
        guiGraphics.blit(BACKGROUND, slotX - 3, slotY - 3, SLOT_U, SLOT_V, SLOT_BG_SIZE, SLOT_BG_SIZE);
        
        // Dynamically update slot position (possible with Access Transformer removing final)
        Slot upgradeSlot = upgradeSlots.get(0);
        upgradeSlot.x = slotX - this.leftPos + 1;
        upgradeSlot.y = slotY - this.topPos + 1;
        
        // Draw ghost icon when slot is empty (AE2 style)
        if (upgradeSlot.getItem().isEmpty()) {
            // Render AE2 upgrade background icon
            // Use absolute screen coordinates for Blitter.dest()
            Icon.BACKGROUND_UPGRADE.getBlitter()
                .dest(slotX, slotY)
                .opacity(0.4f)
                .blit(guiGraphics);
        }
    }
    
    /**
     * Get tooltip for upgrade slot when hovered.
     */
    private boolean isHoveringUpgradeSlot = false;
    
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
            int slotScreenX = this.leftPos + upgradeSlot.x;
            int slotScreenY = this.topPos + upgradeSlot.y;
            
            if (upgradeSlot.getItem().isEmpty() && 
                mouseX >= slotScreenX && mouseX < slotScreenX + 16 &&
                mouseY >= slotScreenY && mouseY < slotScreenY + 16) {
                // Show compatible upgrade tooltip
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(Component.translatable("gui.meplacementtool.compatible_upgrades").withStyle(net.minecraft.ChatFormatting.GOLD));
                tooltip.add(new ItemStack(com.moakiee.meplacementtool.MEPlacementToolMod.KEY_OF_SPECTRUM.get()).getHoverName().copy().withStyle(net.minecraft.ChatFormatting.GRAY));
                guiGraphics.renderTooltip(font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
                return;
            }
        }
        
        super.renderTooltip(guiGraphics, mouseX, mouseY);
    }
    
    private void drawBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);       // top
        g.fill(x, y + h - 1, x + w, y + h, color); // bottom
        g.fill(x, y, x + 1, y + h, color);       // left
        g.fill(x + w - 1, y, x + w, y + h, color); // right
    }
    
    private boolean isInBounds(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
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
    
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
