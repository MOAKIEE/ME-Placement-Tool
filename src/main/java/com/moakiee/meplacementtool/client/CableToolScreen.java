package com.moakiee.meplacementtool.client;

import appeng.api.util.AEColor;
import appeng.client.gui.Icon;
import appeng.client.gui.style.Blitter;
import appeng.menu.SlotSemantics;
import com.moakiee.meplacementtool.CableToolMenu;
import com.moakiee.meplacementtool.ItemMECablePlacementTool;
import com.moakiee.meplacementtool.MEPlacementToolMod;
import com.moakiee.meplacementtool.network.UpdateCableToolPayload;
import com.mojang.blaze3d.platform.InputConstants;
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
 * Upgrade slot rendering is handled by vanilla/AE2's Slot system.
 */
public class CableToolScreen extends AbstractContainerScreen<CableToolMenu> {

    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath("meplacementtool", "textures/gui/cable_tool.png");
    
    private static final int GUI_WIDTH = 175;
    private static final int GUI_HEIGHT = 256;
    
    private static final int BTN_U_NORMAL = 177;
    private static final int BTN_V_NORMAL = 0;
    private static final int BTN_U_PRESSED = 194;
    private static final int BTN_V_PRESSED = 0;
    private static final int BTN_SIZE = 16;
    
    private static final int TITLE_X = 7;
    private static final int TITLE_Y = 161;
    
    private static final int VISIBLE_Y = 8;
    
    private static final int COLOR_COLS = 3;
    private static final int COLOR_BTN_SPACING = 2;
    
    private static final int UPGRADE_COLOR_START_X = 14;
    private static final int UPGRADE_CABLE_START_X = 74;
    private static final int UPGRADE_MODE_START_X = 122;
    private static final int UPGRADE_CONTENT_START_Y = 30;
    
    private static final int NO_UPGRADE_CABLE_START_X = 32;
    private static final int NO_UPGRADE_MODE_START_X = 100;
    private static final int NO_UPGRADE_CONTENT_START_Y = 30;
    
    // AE2 Upgrade Panel constants (matching UpgradesPanel)
    private static final int AE2_SLOT_SIZE = 18;
    private static final int AE2_PADDING = 5;
    private static final Blitter AE2_PANEL_BACKGROUND = Blitter.texture("guis/extra_panels.png", 128, 128);

    private int hoveredColorIndex = -1;
    private int hoveredCableIndex = -1;
    private int hoveredModeIndex = -1;
    
    @Nullable
    private Component hintText = null;
    private int hintColor = 0xFFFFFF;

    public CableToolScreen(CableToolMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
        this.titleLabelX = -9999;
        this.titleLabelY = -9999;
        this.inventoryLabelX = -9999;
        this.inventoryLabelY = -9999;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTicks, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        
        guiGraphics.blit(BACKGROUND, x, y, 0, 0, GUI_WIDTH, GUI_HEIGHT);
        
        hoveredColorIndex = -1;
        hoveredCableIndex = -1;
        hoveredModeIndex = -1;
        hintText = null;
        
        guiGraphics.drawString(font, Component.translatable("container.inventory"), x + TITLE_X, y + TITLE_Y, 0x404040, false);
        
        boolean hasUpgrade = menu.hasUpgradeInstalled();
        
        if (hasUpgrade) {
            drawSectionHeadersWithUpgrade(guiGraphics, x, y);
            drawColorSection(guiGraphics, x + UPGRADE_COLOR_START_X, y + UPGRADE_CONTENT_START_Y, mouseX, mouseY);
            drawCableSection(guiGraphics, x + UPGRADE_CABLE_START_X, y + UPGRADE_CONTENT_START_Y, mouseX, mouseY);
            drawModeSection(guiGraphics, x + UPGRADE_MODE_START_X, y + UPGRADE_CONTENT_START_Y, mouseX, mouseY);
        } else {
            drawSectionHeadersNoUpgrade(guiGraphics, x, y);
            drawCableSection(guiGraphics, x + NO_UPGRADE_CABLE_START_X, y + NO_UPGRADE_CONTENT_START_Y, mouseX, mouseY);
            drawModeSection(guiGraphics, x + NO_UPGRADE_MODE_START_X, y + NO_UPGRADE_CONTENT_START_Y, mouseX, mouseY);
        }
        
        if (hintText != null) {
            int hintY = y + 145;
            guiGraphics.drawCenteredString(font, hintText, x + GUI_WIDTH / 2, hintY, hintColor);
        }
        
        // Draw upgrade panel on the right side of the GUI
        drawUpgradePanel(guiGraphics, x + GUI_WIDTH, y + AE2_PADDING);
    }
    
    private void drawSectionHeadersWithUpgrade(GuiGraphics guiGraphics, int x, int y) {
        int headerY = y + VISIBLE_Y + 8;
        
        String colorHeader = Component.translatable("gui.meplacementtool.color").getString();
        guiGraphics.drawString(font, colorHeader, x + UPGRADE_COLOR_START_X, headerY, 0x404040, false);
        
        String cableHeader = Component.translatable("gui.meplacementtool.cable_type").getString();
        guiGraphics.drawString(font, cableHeader, x + UPGRADE_CABLE_START_X, headerY, 0x404040, false);
        
        String modeHeader = Component.translatable("gui.meplacementtool.mode").getString();
        guiGraphics.drawString(font, modeHeader, x + UPGRADE_MODE_START_X, headerY, 0x404040, false);
    }
    
    private void drawSectionHeadersNoUpgrade(GuiGraphics guiGraphics, int x, int y) {
        int headerY = y + VISIBLE_Y + 8;
        
        String cableHeader = Component.translatable("gui.meplacementtool.cable_type").getString();
        guiGraphics.drawString(font, cableHeader, x + NO_UPGRADE_CABLE_START_X, headerY, 0x404040, false);
        
        String modeHeader = Component.translatable("gui.meplacementtool.mode").getString();
        guiGraphics.drawString(font, modeHeader, x + NO_UPGRADE_MODE_START_X, headerY, 0x404040, false);
    }
    
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
            
            int btnU = selected ? BTN_U_PRESSED : BTN_U_NORMAL;
            int btnV = selected ? BTN_V_PRESSED : BTN_V_NORMAL;
            guiGraphics.blit(BACKGROUND, bx, by, btnU, btnV, BTN_SIZE, BTN_SIZE);
            
            int colorBlockSize = 8;
            int cbx = bx + (BTN_SIZE - colorBlockSize) / 2;
            int cby = by + (BTN_SIZE - colorBlockSize) / 2 - 1;
            if (selected) {
                cby += 1;
            }
            int colorValue = color == AEColor.TRANSPARENT ? 0x8B479B : color.mediumVariant;
            guiGraphics.fill(cbx, cby, cbx + colorBlockSize, cby + colorBlockSize, 0xFF000000 | colorValue);
        }
    }
    
    private void drawCableSection(GuiGraphics guiGraphics, int baseX, int baseY, int mouseX, int mouseY) {
        int selectedCable = menu.currentCableType;
        ItemMECablePlacementTool.CableType[] types = ItemMECablePlacementTool.CableType.values();
        String[] cableKeys = {"glass", "covered", "smart", "dense_covered", "dense_smart"};
        
        for (int i = 0; i < types.length; i++) {
            int bx = baseX;
            int by = baseY + i * (BTN_SIZE + 2);
            
            boolean hovered = isInBounds(mouseX, mouseY, bx, by, BTN_SIZE, BTN_SIZE);
            boolean selected = (i == selectedCable);
            
            if (hovered) {
                hoveredCableIndex = i;
                hintText = Component.translatable("meplacementtool.cable." + cableKeys[i]);
                hintColor = 0x8B479B;
            }
            
            int btnU = selected ? BTN_U_PRESSED : BTN_U_NORMAL;
            int btnV = selected ? BTN_V_PRESSED : BTN_V_NORMAL;
            guiGraphics.blit(BACKGROUND, bx, by, btnU, btnV, BTN_SIZE, BTN_SIZE);
            
            ItemStack cableStack = types[i].getStack(AEColor.TRANSPARENT);
            guiGraphics.pose().pushPose();
            float scale = 0.75f;
            float offsetX = bx + (BTN_SIZE - 16 * scale) / 2;
            float offsetY = by + (BTN_SIZE - 16 * scale) / 2;
            guiGraphics.pose().translate(offsetX, offsetY, 0);
            guiGraphics.pose().scale(scale, scale, 1.0f);
            guiGraphics.renderItem(cableStack, 0, 0);
            guiGraphics.pose().popPose();
            
            String cableName = Component.translatable("meplacementtool.cable." + cableKeys[i] + ".short").getString();
            int textColor = selected ? 0xFFFFFF : 0x404040;
            guiGraphics.drawString(font, cableName, bx + BTN_SIZE + 3, by + 4, textColor, false);
        }
    }
    
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
            
            int btnU = selected ? BTN_U_PRESSED : BTN_U_NORMAL;
            int btnV = selected ? BTN_V_PRESSED : BTN_V_NORMAL;
            guiGraphics.blit(BACKGROUND, bx, by, btnU, btnV, BTN_SIZE, BTN_SIZE);
            
            int iconColor = selected ? 0xFFFFFF : 0xE0E0E0;
            guiGraphics.drawCenteredString(font, modeIcons[i], bx + BTN_SIZE / 2, by + 4, iconColor);
            
            String modeName = Component.translatable("meplacementtool.mode." + modeKeys[i] + ".short").getString();
            int textColor = selected ? 0xFFFFFF : 0x404040;
            guiGraphics.drawString(font, modeName, bx + BTN_SIZE + 3, by + 4, textColor, false);
        }
    }
    
    /**
     * Draw external upgrade panel using AE2's extra_panels.png texture.
     * This replicates the visual style of AE2's UpgradesPanel.
     */
    private void drawUpgradePanel(GuiGraphics guiGraphics, int panelX, int panelY) {
        List<Slot> upgradeSlots = menu.getSlots(SlotSemantics.UPGRADE);
        if (upgradeSlots.isEmpty()) {
            return;
        }
        
        // Calculate slot position (matching AE2 UpgradesPanel layout)
        int slotOriginX = panelX + AE2_PADDING;
        int slotOriginY = panelY + AE2_PADDING;
        
        // Update the slot's position to match our panel
        Slot upgradeSlot = upgradeSlots.get(0);
        upgradeSlot.x = slotOriginX + 1 - leftPos;
        upgradeSlot.y = slotOriginY + 1 - topPos;
        
        // Draw slot background using AE2's extra_panels.png texture
        drawAE2SlotBackground(guiGraphics, slotOriginX, slotOriginY, true, true, true, true);
        
        // Draw AE2-style border lines around the panel
        int borderColor = 0xFFf2f2f2;
        guiGraphics.hLine(slotOriginX - 4, slotOriginX + 11, slotOriginY, borderColor);
        guiGraphics.hLine(slotOriginX - 4, slotOriginX + 11, slotOriginY + AE2_SLOT_SIZE - 1, borderColor);
        guiGraphics.vLine(slotOriginX - 5, slotOriginY - 1, slotOriginY + AE2_SLOT_SIZE, borderColor);
        guiGraphics.vLine(slotOriginX + 12, slotOriginY - 1, slotOriginY + AE2_SLOT_SIZE, borderColor);
        
        // Draw ghost icon when slot is empty (AE2 style)
        if (upgradeSlot.getItem().isEmpty()) {
            Icon.BACKGROUND_UPGRADE.getBlitter()
                .dest(slotOriginX, slotOriginY)
                .opacity(0.7f)
                .blit(guiGraphics);
        }
    }
    
    /**
     * Draw a single slot background using AE2's extra_panels.png texture.
     * This method replicates the drawSlot logic from AE2's UpgradesPanel.
     */
    private void drawAE2SlotBackground(GuiGraphics guiGraphics, int x, int y,
            boolean borderLeft, boolean borderTop, boolean borderRight, boolean borderBottom) {
        int srcX = AE2_PADDING;
        int srcY = AE2_PADDING;
        int srcWidth = AE2_SLOT_SIZE;
        int srcHeight = AE2_SLOT_SIZE;
        
        if (borderLeft) {
            x -= AE2_PADDING;
            srcX = 0;
            srcWidth += AE2_PADDING;
        }
        if (borderRight) {
            srcWidth += AE2_PADDING;
        }
        if (borderTop) {
            y -= AE2_PADDING;
            srcY = 0;
            srcHeight += AE2_PADDING;
        }
        if (borderBottom) {
            srcHeight += AE2_PADDING + 2;
        }
        
        AE2_PANEL_BACKGROUND.src(srcX, srcY, srcWidth, srcHeight)
                .dest(x, y)
                .blit(guiGraphics);
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
                    // Show tooltip for compatible upgrades when slot is empty
                    List<Component> tooltip = new ArrayList<>();
                    tooltip.add(Component.translatable("gui.meplacementtool.compatible_upgrades")
                            .withStyle(ChatFormatting.GOLD));
                    tooltip.add(Component.translatable("gui.meplacementtool.upgrade_hint",
                            new ItemStack(MEPlacementToolMod.KEY_OF_SPECTRUM.get()).getHoverName())
                            .withStyle(ChatFormatting.GRAY));
                    guiGraphics.renderTooltip(font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
                    return;
                } else {
                    // Show item tooltip
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
            if (hoveredColorIndex >= 0 && menu.hasUpgradeInstalled()) {
                menu.setColor(hoveredColorIndex);
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
