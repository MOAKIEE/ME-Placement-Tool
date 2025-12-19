package com.moakiee.meplacementtool;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.ResourceLocation;
// custom fluid rendering removed — rely on default slot/item rendering
import net.minecraft.world.entity.player.Inventory;

public class WandScreen extends AbstractContainerScreen<WandMenu> {
    private static final ResourceLocation BG = new ResourceLocation("textures/gui/container/dispenser.png");

    public WandScreen(WandMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.titleLabelX = 8;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        // No custom per-slot fluid rendering — default slot rendering will show JEI/AE2 icon
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTicks, int x, int y) {
        int relX = (this.width - this.imageWidth) / 2;
        int relY = (this.height - this.imageHeight) / 2;
        guiGraphics.blit(BG, relX, relY, 0, 0, this.imageWidth, this.imageHeight);
    }
}
