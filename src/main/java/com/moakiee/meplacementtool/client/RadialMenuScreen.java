package com.moakiee.meplacementtool.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import com.moakiee.meplacementtool.ItemMEPlacementTool;
import com.moakiee.meplacementtool.MEPlacementToolMod;
import com.moakiee.meplacementtool.ModDataComponents;
import com.moakiee.meplacementtool.NbtCompat;
import com.moakiee.meplacementtool.WandMenu;
import com.moakiee.meplacementtool.network.UpdateWandConfigPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Radial menu for ME Placement Tool item selection.
 * Restored functionality for NeoForge 1.21.1
 */
public class RadialMenuScreen extends Screen {
    private static final float PRECISION = 5.0f;
    private static final int MAX_SLOTS = 18;
    private static final float OPEN_ANIMATION_LENGTH = 0.25f;

    private float totalTime;
    private float prevTick;
    private float extraTick;
    private int selectedItem = -1;
    private boolean closing = false;
    private int currentSelectedSlot = -1; // Currently selected slot from config

    // Slot data
    private final List<SlotData> slots = new ArrayList<>();
    private final ItemStack wandStack;

    public record SlotData(int index, ItemStack displayStack, String name) {}

    public RadialMenuScreen() {
        super(Component.literal(""));
        // Look up the wand from main hand first, off hand second, so the radial menu works in either hand.
        this.wandStack = minecraft.player != null
                ? com.moakiee.meplacementtool.BasePlacementToolItem.findHeldTool(minecraft.player, ItemMEPlacementTool.class)
                : ItemStack.EMPTY;
        loadSlots();
        loadCurrentSelection();
    }

    private void loadCurrentSelection() {
        if (wandStack.isEmpty()) return;
        CompoundTag cfg = wandStack.get(ModDataComponents.PLACEMENT_CONFIG.get());
        if (cfg != null && cfg.contains("SelectedSlot")) {
            currentSelectedSlot = cfg.getIntOr("SelectedSlot", -1);
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        // Radial menus are in-world overlays; the 1.21.1 screen did not draw a menu background.
    }

    private void loadSlots() {
        slots.clear();
        if (wandStack.isEmpty()) return;

        CompoundTag cfg = wandStack.get(ModDataComponents.PLACEMENT_CONFIG.get());
        if (cfg == null) return;

        ItemStackHandler handler = new ItemStackHandler(MAX_SLOTS);
        if (cfg.contains("items")) {
            handler = NbtCompat.readItemStackHandler(minecraft.level.registryAccess(), cfg.getCompoundOrEmpty("items"), MAX_SLOTS);
        }

        CompoundTag fluids = cfg.contains("fluids") ? cfg.getCompoundOrEmpty("fluids") : new CompoundTag();

        int slotCount = handler.getSlots();
        for (int i = 0; i < slotCount; i++) {
            ItemStack stack = handler.getStackInSlot(i);
            String fluidId = fluids.getStringOr(Integer.toString(i), "");

            if (!stack.isEmpty()) {
                 // Simplified item display for now, assuming standard items
                 slots.add(new SlotData(i, stack, stack.getHoverName().getString()));
            } else if (fluidId != null && !fluidId.isEmpty()) {
                var rl = net.minecraft.resources.Identifier.tryParse(fluidId);
                if (rl != null) {
                    var fluid = net.minecraft.core.registries.BuiltInRegistries.FLUID
                            .getOptional(rl)
                            .orElse(net.minecraft.world.level.material.Fluids.EMPTY);
                    if (fluid != net.minecraft.world.level.material.Fluids.EMPTY) {
                        var aeFluidKey = appeng.api.stacks.AEFluidKey.of(fluid);
                        var genericStack = new appeng.api.stacks.GenericStack(
                                aeFluidKey, appeng.api.stacks.AEFluidKey.AMOUNT_BLOCK);
                        ItemStack displayStack = appeng.api.stacks.GenericStack.wrapInItemStack(genericStack);
                        slots.add(new SlotData(i, displayStack,
                                aeFluidKey.getDisplayName().getString()));
                    }
                }
            }
        }
    }

    @Override
    public void tick() {
        if (totalTime < OPEN_ANIMATION_LENGTH) {
            extraTick++;
        }

        // Check raw input to ensure menu doesn't close while holding key
        boolean keyIsDown = ModKeyBindings.OPEN_RADIAL_MENU.isDown();
        if (!keyIsDown) {
            var key = ModKeyBindings.OPEN_RADIAL_MENU.getKey();
            var window = Minecraft.getInstance().getWindow();
            if (key.getType() == InputConstants.Type.KEYSYM) {
                keyIsDown = InputConstants.isKeyDown(window, key.getValue());
            } else if (key.getType() == InputConstants.Type.MOUSE) {
                keyIsDown = org.lwjgl.glfw.GLFW.glfwGetMouseButton(window.handle(), key.getValue()) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
            }
        }

        if (!keyIsDown) {
            this.onClose();
        }
    }

    private void selectSlot(int slotIndex) {
        if (wandStack.isEmpty()) return;

        currentSelectedSlot = slotIndex; // Update local state for highlight
        
        CompoundTag cfg = wandStack.get(ModDataComponents.PLACEMENT_CONFIG.get());
        if (cfg == null) cfg = new CompoundTag();
        else cfg = cfg.copy();
        
        cfg.putInt("SelectedSlot", slotIndex);
        
        // Update client side item immediately
        wandStack.set(ModDataComponents.PLACEMENT_CONFIG.get(), cfg);

        // Send to server
        ClientPacketDistributor.sendToServer(new UpdateWandConfigPayload(cfg));

        // Show overlay
        String name = "Empty";
        for (SlotData slot : slots) {
            if (slot.index == slotIndex) {
                name = slot.name;
                break;
            }
        }
        MEPlacementToolMod.ClientForgeEvents.showSelectedOverlay(name);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTicks);

        if (slots.isEmpty()) {
            graphics.centeredText(font, Component.translatable("message.meplacementtool.no_configured_item"), width / 2, height / 2, GuiTextColors.opaque(0xFFFFFF));
            return;
        }

        float openAnimation = closing ? 1.0f - totalTime / OPEN_ANIMATION_LENGTH : totalTime / OPEN_ANIMATION_LENGTH;
        float currTick = partialTicks;
        totalTime += (currTick + extraTick - prevTick) / 20f;
        extraTick = 0;
        prevTick = currTick;

        float animProgress = Mth.clamp(openAnimation, 0, 1);
        animProgress = (float) (1 - Math.pow(1 - animProgress, 3));
        
        int numberOfSlices = Math.min(MAX_SLOTS, slots.size());
        // Dynamic radius based on number of slices - reduced to 2/3 size
        float baseRadius = Math.max(40, 30 + numberOfSlices * 2);
        float radiusIn = Math.max(0.1f, baseRadius * animProgress);
        float radiusOut = radiusIn + Math.max(35, 25 + numberOfSlices * 1.5f) * animProgress;
        float itemRadius = (radiusIn + radiusOut) * 0.5f;

        int centerX = width / 2;
        int centerY = height / 2;

        int hoveredSlice = closing ? -1 : findHoveredSlice(mouseX, mouseY, numberOfSlices);
        int mousedOverSlot = hoveredSlice >= 0 ? adjustIndex(hoveredSlice, numberOfSlices) : -1;
        selectedItem = mousedOverSlot;

        RadialMenuRenderer.drawSlice(graphics, centerX, centerY, radiusIn, radiusOut, 0, 360, 80, 80, 80, 120);

        for (int i = 0; i < numberOfSlices; i++) {
            float sliceBorderLeft = (((i - 0.5f) / (float) numberOfSlices) + 0.25f) * 360;
            float sliceBorderRight = (((i + 0.5f) / (float) numberOfSlices) + 0.25f) * 360;
            int adjusted = adjustIndex(i, numberOfSlices);
            boolean isCurrentlySelected = adjusted < slots.size() && slots.get(adjusted).index == currentSelectedSlot;

            if (hoveredSlice == i) {
                RadialMenuRenderer.drawSlice(graphics, centerX, centerY, radiusIn, radiusOut,
                        sliceBorderLeft, sliceBorderRight, 63, 161, 191, 150);
            } else if (isCurrentlySelected) {
                RadialMenuRenderer.drawSlice(graphics, centerX, centerY, radiusIn, radiusOut,
                        sliceBorderLeft, sliceBorderRight, 80, 180, 80, 130);
            }
        }

        for (int i = 0; i < numberOfSlices; i++) {
            float angle = (((i - 0.5f) / (float) numberOfSlices) + 0.25f) * 360;
            RadialMenuRenderer.drawDivider(graphics, centerX, centerY, radiusIn, radiusOut, angle, 200, 200, 200, 100);
        }

        graphics.nextStratum();

        // Draw hovered item name
        if (mousedOverSlot != -1) {
            if (mousedOverSlot >= 0 && mousedOverSlot < slots.size()) {
                graphics.centeredText(font, slots.get(mousedOverSlot).name, centerX, (height - font.lineHeight) / 2, GuiTextColors.opaque(0xFFFFFF));
            }
        }

        // Draw item icons
        for (int i = 0; i < numberOfSlices; i++) {
            float angle = ((i / (float) numberOfSlices) - 0.25f) * 2 * (float) Math.PI;
            if (numberOfSlices % 2 != 0) {
                angle += Math.PI / numberOfSlices;
            }
            float posX = centerX - 8 + itemRadius * (float) Math.cos(angle);
            float posY = centerY - 8 + itemRadius * (float) Math.sin(angle);

            SlotData slot = slots.get(i);
            if (!slot.displayStack.isEmpty()) {
                graphics.item(slot.displayStack, (int) posX, (int) posY);
            }
        }

        selectedItem = mousedOverSlot;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int clickedSlot = findHoveredSlot((int) event.x(), (int) event.y(), Math.min(MAX_SLOTS, slots.size()));
        if (event.button() == 0 && clickedSlot >= 0 && clickedSlot < slots.size()) {
            selectSlot(slots.get(clickedSlot).index);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private int findHoveredSlot(int mouseX, int mouseY, int numberOfSlices) {
        int hoveredSlice = findHoveredSlice(mouseX, mouseY, numberOfSlices);
        return hoveredSlice >= 0 ? adjustIndex(hoveredSlice, numberOfSlices) : -1;
    }

    private int findHoveredSlice(int mouseX, int mouseY, int numberOfSlices) {
        if (numberOfSlices <= 0) {
            return -1;
        }

        int centerX = width / 2;
        int centerY = height / 2;
        double mouseAngle = Math.toDegrees(Math.atan2(mouseY - centerY, mouseX - centerX));
        float slot0 = (((0 - 0.5f) / (float) numberOfSlices) + 0.25f) * 360;
        if (mouseAngle < slot0) {
            mouseAngle += 360;
        }
        return findSlice(mouseAngle, numberOfSlices);
    }

    private static int findSlice(double mouseAngle, int totalSlices) {
        for (int i = 0; i < totalSlices; i++) {
            float sliceBorderLeft = (((i - 0.5f) / (float) totalSlices) + 0.25f) * 360;
            float sliceBorderRight = (((i + 0.5f) / (float) totalSlices) + 0.25f) * 360;
            if (mouseAngle >= sliceBorderLeft && mouseAngle < sliceBorderRight) {
                return i;
            }
        }
        return -1;
    }

    private static int adjustIndex(int sliceIndex, int totalSlices) {
        int adjusted = ((sliceIndex + (totalSlices / 2 + 1)) % totalSlices) - 1;
        return adjusted == -1 ? totalSlices - 1 : adjusted;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
