package com.moakiee.meplacementtool;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import com.moakiee.meplacementtool.network.SyncPagePayload;

/**
 * Screen for the ME Placement Tool configuration menu
 */
public class WandScreen extends AbstractContainerScreen<WandMenu> {
    private static final ResourceLocation BG = ResourceLocation.withDefaultNamespace("textures/gui/container/dispenser.png");
    private Button prevButton;
    private Button nextButton;

    public WandScreen(WandMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.titleLabelX = 8;
    }

    @Override
    protected void init() {
        super.init();
        int relX = (this.width - this.imageWidth) / 2;
        int relY = (this.height - this.imageHeight) / 2;

        // Previous page button
        this.prevButton = Button.builder(Component.literal("<"), button -> {
            int currentPage = this.menu.getCurrentPage();
            if (currentPage > 0) {
                this.menu.setCurrentPage(currentPage - 1);
                PacketDistributor.sendToServer(new SyncPagePayload(currentPage - 1));
                updateButtonVisibility();
            }
        }).bounds(relX + 44, relY + 35, 12, 20).build();

        // Next page button
        this.nextButton = Button.builder(Component.literal(">"), button -> {
            int currentPage = this.menu.getCurrentPage();
            if (currentPage < WandMenu.MAX_PAGES - 1) {
                this.menu.setCurrentPage(currentPage + 1);
                PacketDistributor.sendToServer(new SyncPagePayload(currentPage + 1));
                updateButtonVisibility();
            }
        }).bounds(relX + 116, relY + 35, 12, 20).build();

        this.addRenderableWidget(prevButton);
        this.addRenderableWidget(nextButton);
        updateButtonVisibility();
    }

    private void updateButtonVisibility() {
        int currentPage = this.menu.getCurrentPage();
        this.prevButton.visible = currentPage > 0;
        this.nextButton.visible = currentPage < WandMenu.MAX_PAGES - 1;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTicks);
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        
        // Render items in ghost slots based on current page
        renderGhostSlotItems(guiGraphics, mouseX, mouseY);
        
        this.renderTooltip(guiGraphics, mouseX, mouseY);

        // Draw page indicator
        int relX = (this.width - this.imageWidth) / 2;
        int relY = (this.height - this.imageHeight) / 2;
        int currentPage = this.menu.getCurrentPage();
        String pageText = (currentPage + 1) + "/" + WandMenu.MAX_PAGES;
        int textWidth = this.font.width(pageText);
        guiGraphics.drawString(this.font, pageText, relX + 89 - textWidth / 2, relY + 6, 0x404040, false);
    }

    private void renderGhostSlotItems(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int relX = (this.width - this.imageWidth) / 2;
        int relY = (this.height - this.imageHeight) / 2;

        for (int i = 0; i < WandMenu.SLOTS_PER_PAGE; i++) {
            GhostSlot slot = this.menu.getGhostSlots().get(i);
            ItemStack stack = this.menu.getItemAtVisualSlot(i);

            if (!stack.isEmpty()) {
                int x = relX + slot.x;
                int y = relY + slot.y;
                guiGraphics.renderItem(stack, x, y);
                guiGraphics.renderItemDecorations(this.font, stack, x, y);
            }
        }
    }

    @Override
    protected void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        super.renderTooltip(guiGraphics, mouseX, mouseY);

        // Custom tooltip for ghost slots
        if (this.hoveredSlot instanceof GhostSlot ghostSlot) {
            int visualIndex = ghostSlot.getVisualIndex();
            ItemStack stack = this.menu.getItemAtVisualSlot(visualIndex);
            if (!stack.isEmpty()) {
                guiGraphics.renderTooltip(this.font, stack, mouseX, mouseY);
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTicks, int x, int y) {
        int relX = (this.width - this.imageWidth) / 2;
        int relY = (this.height - this.imageHeight) / 2;
        guiGraphics.blit(BG, relX, relY, 0, 0, this.imageWidth, this.imageHeight);
    }
}
