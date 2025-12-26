package com.moakiee.meplacementtool.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.AEFluidKey;
import com.moakiee.meplacementtool.MEPlacementToolMod;
import com.moakiee.meplacementtool.WandMenu;
import com.moakiee.meplacementtool.network.ModNetwork;
import com.moakiee.meplacementtool.network.UpdateWandConfigPacket;
import com.moakiee.meplacementtool.network.UpdatePlacementCountPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * Dual-layer radial menu for Multiblock Placement Tool.
 * Inner ring: placement count selection (1-64)
 * Outer ring: item selection
 * Left-click selects without closing, G release closes menu
 */
public class DualLayerRadialMenuScreen extends Screen {
    private static final float PRECISION = 5.0f;
    private static final int MAX_SLOTS = 18;
    private static final float OPEN_ANIMATION_LENGTH = 0.25f;

    // Count options for inner ring
    private static final int[] COUNT_OPTIONS = {1, 8, 64, 256, 1024};

    private final int openKey;
    private float totalTime;
    private float prevTick;
    private float extraTick;
    private int selectedItem = -1;
    private int selectedCount = -1;
    private boolean closing = false;
    
    // Currently configured values from wand
    private int currentPlacementCount = 1;
    private int currentSelectedSlot = -1;

    // Slot data
    private final List<SlotData> slots = new ArrayList<>();
    private final ItemStack wandStack;

    // Current selection layer: 0 = count (inner), 1 = item (outer)
    private int selectionLayer = -1;

    public record SlotData(int index, ItemStack displayStack, String name, boolean isFluid) {}

    public DualLayerRadialMenuScreen(int openKey) {
        super(Component.literal(""));
        this.openKey = openKey;
        this.minecraft = Minecraft.getInstance();
        this.wandStack = minecraft.player.getMainHandItem();
        loadSlots();
        loadCurrentConfig();
    }

    private void loadCurrentConfig() {
        if (wandStack.isEmpty()) return;
        CompoundTag data = wandStack.getOrCreateTag();
        
        // Load current placement count
        if (data.contains("placement_count")) {
            currentPlacementCount = data.getInt("placement_count");
        }
        
        // Load current selected slot
        CompoundTag cfg = data.contains(WandMenu.TAG_KEY) ? data.getCompound(WandMenu.TAG_KEY) : null;
        if (cfg != null && cfg.contains("SelectedSlot")) {
            currentSelectedSlot = cfg.getInt("SelectedSlot");
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    private void loadSlots() {
        slots.clear();
        if (wandStack.isEmpty()) return;

        CompoundTag data = wandStack.getOrCreateTag();
        CompoundTag cfg = data.contains(WandMenu.TAG_KEY) ? data.getCompound(WandMenu.TAG_KEY) : null;
        if (cfg == null) return;

        ItemStackHandler handler = new ItemStackHandler(18);
        if (cfg.contains("items")) {
            handler.deserializeNBT(cfg.getCompound("items"));
        }

        CompoundTag fluids = cfg.contains("fluids") ? cfg.getCompound("fluids") : new CompoundTag();

        int slotCount = handler.getSlots();
        for (int i = 0; i < slotCount; i++) {
            ItemStack stack = handler.getStackInSlot(i);
            String fluidId = fluids.getString(Integer.toString(i));

            if (!stack.isEmpty()) {
                try {
                    var gs = GenericStack.unwrapItemStack(stack);
                    if (gs != null) {
                        String name = gs.what().getDisplayName().getString();
                        slots.add(new SlotData(i, stack, name, AEFluidKey.is(gs.what())));
                        continue;
                    }
                } catch (Throwable ignored) {}

                slots.add(new SlotData(i, stack, stack.getHoverName().getString(), false));
            } else if (fluidId != null && !fluidId.isEmpty()) {
                try {
                    var rl = new net.minecraft.resources.ResourceLocation(fluidId);
                    var fluid = net.minecraftforge.registries.ForgeRegistries.FLUIDS.getValue(rl);
                    if (fluid != null) {
                        var aeFluidKey = AEFluidKey.of(fluid);
                        ItemStack wrapped = GenericStack.wrapInItemStack(aeFluidKey, 1);
                        String name = aeFluidKey.getDisplayName().getString();
                        slots.add(new SlotData(i, wrapped, name, true));
                    }
                } catch (Throwable ignored) {}
            }
        }
    }

    @Override
    public void tick() {
        if (totalTime < OPEN_ANIMATION_LENGTH) {
            extraTick++;
        }

        boolean keyIsDown = InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), openKey);
        if (!keyIsDown) {
            minecraft.player.closeContainer();
        }
    }

    private void selectSlot(int slotIndex) {
        if (wandStack.isEmpty()) return;

        currentSelectedSlot = slotIndex; // Update local state for highlight
        CompoundTag data = wandStack.getOrCreateTag();
        CompoundTag cfg = data.contains(WandMenu.TAG_KEY) ? data.getCompound(WandMenu.TAG_KEY).copy() : new CompoundTag();
        cfg.putInt("SelectedSlot", slotIndex);
        data.put(WandMenu.TAG_KEY, cfg);

        ModNetwork.CHANNEL.sendToServer(new UpdateWandConfigPacket(cfg));

        String name = "Empty";
        for (SlotData slot : slots) {
            if (slot.index == slotIndex) {
                name = slot.name;
                break;
            }
        }
        MEPlacementToolMod.ClientForgeEvents.showSelectedOverlay(name);
    }

    private void selectCount(int count) {
        if (wandStack.isEmpty()) return;

        currentPlacementCount = count; // Update local state for highlight
        wandStack.getOrCreateTag().putInt("placement_count", count);
        ModNetwork.CHANNEL.sendToServer(new UpdatePlacementCountPacket(count));
        MEPlacementToolMod.ClientForgeEvents.showCountOverlay("Placement Count: " + count);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // Don't call super.render() - it draws a background that covers our radial menu
        // Instead just render our content directly

        if (slots.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("message.meplacementtool.no_configured_item"), width / 2, height / 2, 0xFFFFFF);
            return;
        }

        PoseStack ms = graphics.pose();
        float openAnimation = closing ? 1.0f - totalTime / OPEN_ANIMATION_LENGTH : totalTime / OPEN_ANIMATION_LENGTH;
        float currTick = minecraft.getFrameTime();
        totalTime += (currTick + extraTick - prevTick) / 20f;
        extraTick = 0;
        prevTick = currTick;

        float animProgress = Mth.clamp(openAnimation, 0, 1);
        animProgress = (float) (1 - Math.pow(1 - animProgress, 3));

        int numberOfCountSlices = COUNT_OPTIONS.length;
        int numberOfItemSlices = Math.max(1, slots.size());
        
        // Dynamic radius based on number of item slices
        float baseOuterRadius = Math.max(45, 30 + numberOfItemSlices * 3);
        
        // Inner ring (count) and outer ring (items) radii
        float innerRadiusMin = Math.max(0.1f, 20 * animProgress);
        float innerRadiusMax = Math.max(0.1f, 50 * animProgress);
        float outerRadiusMin = innerRadiusMax + 8 * animProgress;
        float outerRadiusMax = outerRadiusMin + Math.max(40, 25 + numberOfItemSlices * 2) * animProgress;

        float innerItemRadius = (innerRadiusMin + innerRadiusMax) * 0.5f;
        float outerItemRadius = (outerRadiusMin + outerRadiusMax) * 0.5f;

        int centerX = width / 2;
        int centerY = height / 2;

        double mouseAngle = Math.toDegrees(Math.atan2(mouseY - centerY, mouseX - centerX));
        double mouseDistance = Math.sqrt(Math.pow(mouseX - centerX, 2) + Math.pow(mouseY - centerY, 2));

        // Adjust mouse angle for slot calculation
        float countSlot0 = (((0 - 0.5f) / (float) numberOfCountSlices) + 0.25f) * 360;
        float itemSlot0 = (((0 - 0.5f) / (float) numberOfItemSlices) + 0.25f) * 360;
        double countMouseAngle = mouseAngle;
        double itemMouseAngle = mouseAngle;
        if (countMouseAngle < countSlot0) countMouseAngle += 360;
        if (itemMouseAngle < itemSlot0) itemMouseAngle += 360;

        ms.pushPose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        Tesselator tessellator = Tesselator.getInstance();
        BufferBuilder buffer = tessellator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        // Determine selection layer and selected item
        if (!closing) {
            selectionLayer = -1;
            selectedCount = -1;
            selectedItem = -1;

            // Check inner ring (count)
            if (mouseDistance >= innerRadiusMin && mouseDistance < innerRadiusMax) {
                selectionLayer = 0;
                for (int i = 0; i < numberOfCountSlices; i++) {
                    float sliceBorderLeft = (((i - 0.5f) / (float) numberOfCountSlices) + 0.25f) * 360;
                    float sliceBorderRight = (((i + 0.5f) / (float) numberOfCountSlices) + 0.25f) * 360;
                    if (countMouseAngle >= sliceBorderLeft && countMouseAngle < sliceBorderRight) {
                        selectedCount = i;
                        break;
                    }
                }
            }
            // Check outer ring (items)
            else if (mouseDistance >= outerRadiusMin && mouseDistance < outerRadiusMax && !slots.isEmpty()) {
                selectionLayer = 1;
                for (int i = 0; i < numberOfItemSlices; i++) {
                    float sliceBorderLeft = (((i - 0.5f) / (float) numberOfItemSlices) + 0.25f) * 360;
                    float sliceBorderRight = (((i + 0.5f) / (float) numberOfItemSlices) + 0.25f) * 360;
                    if (itemMouseAngle >= sliceBorderLeft && itemMouseAngle < sliceBorderRight) {
                        selectedItem = i;
                        break;
                    }
                }
            }
        }

        // Draw gray background rings as full backgrounds
        drawSlice(buffer, centerX, centerY, 9, innerRadiusMin, innerRadiusMax, 0, 360, 80, 80, 80, 120);
        if (!slots.isEmpty()) {
            drawSlice(buffer, centerX, centerY, 9, outerRadiusMin, outerRadiusMax, 0, 360, 80, 80, 80, 120);
        }

        // Draw inner ring (count options) - only highlights, background already drawn
        for (int i = 0; i < numberOfCountSlices; i++) {
            float sliceBorderLeft = (((i - 0.5f) / (float) numberOfCountSlices) + 0.25f) * 360;
            float sliceBorderRight = (((i + 0.5f) / (float) numberOfCountSlices) + 0.25f) * 360;
            
            // Calculate adjusted index for checking current selection
            int adjusted = ((i + (numberOfCountSlices / 2 + 1)) % numberOfCountSlices);
            adjusted = adjusted == 0 ? numberOfCountSlices - 1 : adjusted - 1;
            boolean isCurrentlySelected = adjusted >= 0 && adjusted < COUNT_OPTIONS.length && COUNT_OPTIONS[adjusted] == currentPlacementCount;
            
            if (selectionLayer == 0 && selectedCount == i) {
                // Hovered count - orange/gold highlight
                drawSlice(buffer, centerX, centerY, 10, innerRadiusMin, innerRadiusMax, sliceBorderLeft, sliceBorderRight, 191, 161, 63, 150);
            } else if (isCurrentlySelected) {
                // Currently selected count - green highlight
                drawSlice(buffer, centerX, centerY, 10, innerRadiusMin, innerRadiusMax, sliceBorderLeft, sliceBorderRight, 80, 180, 80, 130);
            }
            // Non-selected, non-hovered: no color, gray background shows through
        }

        // Draw outer ring (item slots) - only highlights, background already drawn
        if (!slots.isEmpty()) {
            for (int i = 0; i < numberOfItemSlices; i++) {
                float sliceBorderLeft = (((i - 0.5f) / (float) numberOfItemSlices) + 0.25f) * 360;
                float sliceBorderRight = (((i + 0.5f) / (float) numberOfItemSlices) + 0.25f) * 360;
                
                // Calculate adjusted index for checking current selection
                int adjusted = ((i + (numberOfItemSlices / 2 + 1)) % numberOfItemSlices) - 1;
                adjusted = adjusted == -1 ? numberOfItemSlices - 1 : adjusted;
                boolean isCurrentlySelected = adjusted < slots.size() && slots.get(adjusted).index == currentSelectedSlot;
                
                if (selectionLayer == 1 && selectedItem == i) {
                    // Hovered item - blue highlight
                    drawSlice(buffer, centerX, centerY, 10, outerRadiusMin, outerRadiusMax, sliceBorderLeft, sliceBorderRight, 63, 161, 191, 150);
                } else if (isCurrentlySelected) {
                    // Currently selected slot - green highlight
                    drawSlice(buffer, centerX, centerY, 10, outerRadiusMin, outerRadiusMax, sliceBorderLeft, sliceBorderRight, 80, 180, 80, 130);
                }
                // Non-selected, non-hovered: no color, gray background shows through
            }
        } else {
            // No items configured - show message
            graphics.drawCenteredString(font, Component.translatable("message.meplacementtool.no_configured_item"), centerX, centerY + (int)outerRadiusMax + 10, 0xFFFFFF);
        }

        BufferUploader.drawWithShader(buffer.end());
        
        // Draw divider lines between slices
        buffer = tessellator.getBuilder();
        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        
        // Inner ring dividers
        for (int i = 0; i < numberOfCountSlices; i++) {
            float angle = (float) Math.toRadians((((i - 0.5f) / (float) numberOfCountSlices) + 0.25f) * 360);
            float x1 = centerX + innerRadiusMin * (float) Math.cos(angle);
            float y1 = centerY + innerRadiusMin * (float) Math.sin(angle);
            float x2 = centerX + innerRadiusMax * (float) Math.cos(angle);
            float y2 = centerY + innerRadiusMax * (float) Math.sin(angle);
            buffer.vertex(x1, y1, 11).color(200, 200, 200, 100).endVertex();
            buffer.vertex(x2, y2, 11).color(200, 200, 200, 100).endVertex();
        }
        
        // Outer ring dividers
        if (!slots.isEmpty()) {
            for (int i = 0; i < numberOfItemSlices; i++) {
                float angle = (float) Math.toRadians((((i - 0.5f) / (float) numberOfItemSlices) + 0.25f) * 360);
                float x1 = centerX + outerRadiusMin * (float) Math.cos(angle);
                float y1 = centerY + outerRadiusMin * (float) Math.sin(angle);
                float x2 = centerX + outerRadiusMax * (float) Math.cos(angle);
                float y2 = centerY + outerRadiusMax * (float) Math.sin(angle);
                buffer.vertex(x1, y1, 11).color(200, 200, 200, 100).endVertex();
                buffer.vertex(x2, y2, 11).color(200, 200, 200, 100).endVertex();
            }
        }
        BufferUploader.drawWithShader(buffer.end());
        
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        // Draw center hint text
        if (selectionLayer == 0 && selectedCount >= 0 && selectedCount < COUNT_OPTIONS.length) {
            int adjustedCount = ((selectedCount + (numberOfCountSlices / 2 + 1)) % numberOfCountSlices);
            adjustedCount = adjustedCount == 0 ? numberOfCountSlices - 1 : adjustedCount - 1;
            if (adjustedCount >= 0 && adjustedCount < COUNT_OPTIONS.length) {
                String countText = String.valueOf(COUNT_OPTIONS[adjustedCount]);
                graphics.drawCenteredString(font, countText, centerX, (height - font.lineHeight) / 2, 0xFFFF00);
            }
        } else if (selectionLayer == 1 && selectedItem >= 0 && selectedItem < slots.size()) {
            int adjusted = ((selectedItem + (numberOfItemSlices / 2 + 1)) % numberOfItemSlices) - 1;
            adjusted = adjusted == -1 ? numberOfItemSlices - 1 : adjusted;
            if (adjusted >= 0 && adjusted < slots.size()) {
                graphics.drawCenteredString(font, slots.get(adjusted).name, centerX, (height - font.lineHeight) / 2, 0xFFFFFF);
            }
        }

        ms.popPose();

        // Draw count numbers on inner ring
        for (int i = 0; i < numberOfCountSlices; i++) {
            float angle = ((i / (float) numberOfCountSlices) - 0.25f) * 2 * (float) Math.PI;
            if (numberOfCountSlices % 2 != 0) {
                angle += Math.PI / numberOfCountSlices;
            }
            float posX = centerX + innerItemRadius * (float) Math.cos(angle);
            float posY = centerY + innerItemRadius * (float) Math.sin(angle);
            String countText = String.valueOf(COUNT_OPTIONS[i]);
            graphics.drawCenteredString(font, countText, (int) posX, (int) posY - font.lineHeight / 2, 0xFFFFFF);
        }

        // Draw item icons on outer ring
        if (!slots.isEmpty()) {
            for (int i = 0; i < numberOfItemSlices; i++) {
                float angle = ((i / (float) numberOfItemSlices) - 0.25f) * 2 * (float) Math.PI;
                if (numberOfItemSlices % 2 != 0) {
                    angle += Math.PI / numberOfItemSlices;
                }
                float posX = centerX - 8 + outerItemRadius * (float) Math.cos(angle);
                float posY = centerY - 8 + outerItemRadius * (float) Math.sin(angle);
                RenderSystem.disableDepthTest();

                SlotData slot = slots.get(i);
                if (!slot.displayStack.isEmpty()) {
                    graphics.renderItem(slot.displayStack, (int) posX, (int) posY);
                }
            }
        }

        // Adjust selection indices for display mapping
        if (selectionLayer == 0 && selectedCount >= 0) {
            int adjusted = ((selectedCount + (numberOfCountSlices / 2 + 1)) % numberOfCountSlices);
            adjusted = adjusted == 0 ? numberOfCountSlices - 1 : adjusted - 1;
            selectedCount = adjusted;
        } else if (selectionLayer == 1 && selectedItem >= 0) {
            int adjusted = ((selectedItem + (numberOfItemSlices / 2 + 1)) % numberOfItemSlices) - 1;
            adjusted = adjusted == -1 ? numberOfItemSlices - 1 : adjusted;
            selectedItem = adjusted;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Left-click to select without closing
        if (button == 0) {
            if (selectionLayer == 0 && selectedCount >= 0 && selectedCount < COUNT_OPTIONS.length) {
                selectCount(COUNT_OPTIONS[selectedCount]);
            } else if (selectionLayer == 1 && selectedItem >= 0 && selectedItem < slots.size()) {
                selectSlot(slots.get(selectedItem).index);
            }
            // Don't close menu on left-click
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        return super.keyPressed(key, scanCode, modifiers);
    }

    private void drawSlice(BufferBuilder buffer, float x, float y, float z, float radiusIn, float radiusOut,
                           float startAngle, float endAngle, int r, int g, int b, int a) {
        float angle = endAngle - startAngle;
        int sections = Math.max(1, Mth.ceil(angle / PRECISION));

        for (int i = 0; i < sections; i++) {
            float angle1 = (float) Math.toRadians(startAngle + (i / (float) sections) * angle);
            float angle2 = (float) Math.toRadians(startAngle + ((i + 1) / (float) sections) * angle);

            float x1In = x + radiusIn * (float) Math.cos(angle1);
            float y1In = y + radiusIn * (float) Math.sin(angle1);
            float x1Out = x + radiusOut * (float) Math.cos(angle1);
            float y1Out = y + radiusOut * (float) Math.sin(angle1);
            float x2In = x + radiusIn * (float) Math.cos(angle2);
            float y2In = y + radiusIn * (float) Math.sin(angle2);
            float x2Out = x + radiusOut * (float) Math.cos(angle2);
            float y2Out = y + radiusOut * (float) Math.sin(angle2);

            buffer.vertex(x1In, y1In, z).color(r, g, b, a).endVertex();
            buffer.vertex(x1Out, y1Out, z).color(r, g, b, a).endVertex();
            buffer.vertex(x2Out, y2Out, z).color(r, g, b, a).endVertex();
            buffer.vertex(x2In, y2In, z).color(r, g, b, a).endVertex();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
