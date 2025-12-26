package com.moakiee.meplacementtool;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.ResourceLocation;
// custom fluid rendering removed — rely on default slot/item rendering
import net.minecraft.world.entity.player.Inventory;
import com.moakiee.meplacementtool.network.ModNetwork;
import com.moakiee.meplacementtool.network.SyncPagePacket;

public class WandScreen extends AbstractContainerScreen<WandMenu> {
    private static final ResourceLocation BG = new ResourceLocation("textures/gui/container/dispenser.png");
    private int currentPage = 0;
    private static final int MAX_PAGE = 2;
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

        // Previous page button (left arrow) - positioned to the left of the grid
        this.prevButton = Button.builder(Component.literal("<"), button -> {
            if (currentPage > 0) {
                currentPage--;
                this.menu.setCurrentPage(currentPage);
                // Sync to server
                ModNetwork.CHANNEL.sendToServer(new SyncPagePacket(currentPage));
                updateButtonVisibility();
            }
        }).bounds(relX + 44, relY + 35, 12, 20).build();

        // Next page button (right arrow) - positioned to the right of the grid
        this.nextButton = Button.builder(Component.literal(">"), button -> {
            if (currentPage < MAX_PAGE - 1) {
                currentPage++;
                this.menu.setCurrentPage(currentPage);
                // Sync to server
                ModNetwork.CHANNEL.sendToServer(new SyncPagePacket(currentPage));
                updateButtonVisibility();
            }
        }).bounds(relX + 116, relY + 35, 12, 20).build();

        this.addRenderableWidget(prevButton);
        this.addRenderableWidget(nextButton);
        updateButtonVisibility();
    }

    private void updateButtonVisibility() {
        this.prevButton.visible = currentPage > 0;
        this.nextButton.visible = currentPage < MAX_PAGE - 1;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        // No custom per-slot fluid rendering — default slot rendering will show JEI/AE2 icon
        this.renderTooltip(guiGraphics, mouseX, mouseY);

        // Draw page indicator (inside the slot grid area)
        int relX = (this.width - this.imageWidth) / 2;
        int relY = (this.height - this.imageHeight) / 2;
        String pageText = (currentPage + 1) + "/" + MAX_PAGE;
        int textWidth = this.font.width(pageText);
        // Position in center of the 3x3 grid area (grid is at 62,17 with size 54x54)
        guiGraphics.drawString(this.font, pageText, relX + 89 - textWidth / 2, relY + 6, 0x404040, false);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTicks, int x, int y) {
        int relX = (this.width - this.imageWidth) / 2;
        int relY = (this.height - this.imageHeight) / 2;
        guiGraphics.blit(BG, relX, relY, 0, 0, this.imageWidth, this.imageHeight);
    }
}
