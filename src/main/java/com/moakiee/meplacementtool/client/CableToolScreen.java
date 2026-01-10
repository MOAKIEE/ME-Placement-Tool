package com.moakiee.meplacementtool.client;

import appeng.api.util.AEColor;
import appeng.client.gui.Icon;
import appeng.menu.SlotSemantics;
import com.moakiee.meplacementtool.CableToolMenu;
import com.moakiee.meplacementtool.ItemMECablePlacementTool;
import com.moakiee.meplacementtool.MEPlacementToolMod;
import com.moakiee.meplacementtool.network.UpdateCableToolPayload;
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
    
    private static final int SLOT_U = 177;
    private static final int SLOT_V = 17;
    private static final int SLOT_BG_SIZE = 24;
    
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
    
    private static final int UPGRADE_PANEL_PADDING = 7;
    private static final int UPGRADE_SLOT_SIZE = 18;

    private int hoveredColorIndex = -1;
    private int hoveredCableIndex = -1;
    private int hoveredModeIndex = -1;
    
    @Nullable
    private Component hintText = null;
    private int hintColor = 0xFFFFFF;
    
    // Real upgrade slot position (screen coordinates)
    private int upgradeSlotRealX = 0;
    private int upgradeSlotRealY = 0;

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
        
        drawUpgradePanel(guiGraphics, x + GUI_WIDTH, y);
        
        if (hintText != null) {
            int hintY = y + 145;
            guiGraphics.drawCenteredString(font, hintText, x + GUI_WIDTH / 2, hintY, hintColor);
        }
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
    
    private void drawUpgradePanel(GuiGraphics guiGraphics, int panelX, int panelY) {
        List<Slot> upgradeSlots = menu.getSlots(SlotSemantics.UPGRADE);
        if (upgradeSlots.isEmpty()) {
            return;
        }
        
        int panelWidth = UPGRADE_PANEL_PADDING * 2 + UPGRADE_SLOT_SIZE;
        int panelHeight = UPGRADE_PANEL_PADDING * 2 + UPGRADE_SLOT_SIZE;
        
        guiGraphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xFFC6C6C6);
        drawBorder(guiGraphics, panelX, panelY, panelWidth, panelHeight, 0xFF555555);
        
        int slotX = panelX + UPGRADE_PANEL_PADDING;
        int slotY = panelY + UPGRADE_PANEL_PADDING;
        
        // Save real slot position for click handling
        upgradeSlotRealX = slotX;
        upgradeSlotRealY = slotY;
        
        guiGraphics.blit(BACKGROUND, slotX - 3, slotY - 3, SLOT_U, SLOT_V, SLOT_BG_SIZE, SLOT_BG_SIZE);
        
        Slot upgradeSlot = upgradeSlots.get(0);
        ItemStack upgradeItem = upgradeSlot.getItem();
        
        if (upgradeItem.isEmpty()) {
            // Draw ghost icon when empty
            Icon.BACKGROUND_UPGRADE.getBlitter()
                .dest(slotX, slotY)
                .opacity(0.4f)
                .blit(guiGraphics);
        } else {
            // Manually render the upgrade item at the correct position
            guiGraphics.renderItem(upgradeItem, slotX, slotY);
            guiGraphics.renderItemDecorations(font, upgradeItem, slotX, slotY);
        }
    }
    
    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
    }
    
    @Override
    protected void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Check if hovering over upgrade slot at its real position
        if (isInBounds(mouseX, mouseY, upgradeSlotRealX, upgradeSlotRealY, 16, 16)) {
            List<Slot> upgradeSlots = menu.getSlots(SlotSemantics.UPGRADE);
            if (!upgradeSlots.isEmpty()) {
                Slot upgradeSlot = upgradeSlots.get(0);
                if (upgradeSlot.getItem().isEmpty()) {
                    List<Component> tooltip = new ArrayList<>();
                    tooltip.add(Component.translatable("gui.meplacementtool.compatible_upgrades").withStyle(net.minecraft.ChatFormatting.GOLD));
                    tooltip.add(new ItemStack(MEPlacementToolMod.KEY_OF_SPECTRUM.get()).getHoverName().copy().withStyle(net.minecraft.ChatFormatting.GRAY));
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
    
    private void drawBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }
    
    private boolean isInBounds(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // Handle upgrade slot click at real position
            if (isInBounds((int)mouseX, (int)mouseY, upgradeSlotRealX, upgradeSlotRealY, 16, 16)) {
                List<Slot> upgradeSlots = menu.getSlots(SlotSemantics.UPGRADE);
                if (!upgradeSlots.isEmpty()) {
                    Slot upgradeSlot = upgradeSlots.get(0);
                    ItemStack carried = menu.getCarried();
                    
                    if (!carried.isEmpty()) {
                        // Try to place item in upgrade slot
                        if (upgradeSlot.mayPlace(carried)) {
                            ItemStack toPlace = carried.split(1);
                            upgradeSlot.set(toPlace);
                            playButtonClickSound();
                            return true;
                        }
                    } else if (!upgradeSlot.getItem().isEmpty()) {
                        // Take item from upgrade slot
                        menu.setCarried(upgradeSlot.getItem().copy());
                        upgradeSlot.set(ItemStack.EMPTY);
                        playButtonClickSound();
                        return true;
                    }
                }
            }
            
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
