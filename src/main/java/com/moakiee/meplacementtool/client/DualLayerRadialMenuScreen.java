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

import com.moakiee.meplacementtool.ItemMultiblockPlacementTool;
import com.moakiee.meplacementtool.ItemMultiblockPlacementTool.DirectionMode;
import com.moakiee.meplacementtool.MEPlacementToolMod;
import com.moakiee.meplacementtool.ModDataComponents;
import com.moakiee.meplacementtool.NbtCompat;
import com.moakiee.meplacementtool.network.UpdateDirectionModePayload;
import com.moakiee.meplacementtool.network.UpdatePlacementCountPayload;
import com.moakiee.meplacementtool.network.UpdateWandConfigPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Triple-layer radial menu for Multiblock Placement Tool.
 * Innermost ring: placement direction mode (4 slices)
 * Middle ring: placement count (5 slices)
 * Outer ring: item selection
 */
public class DualLayerRadialMenuScreen extends Screen {
    private static final float PRECISION = 5.0f;
    private static final float OPEN_ANIMATION_LENGTH = 0.25f;

    // Count options for middle ring
    private static final int[] COUNT_OPTIONS = {1, 8, 64, 256, 1024};
    // Direction options for innermost ring
    private static final DirectionMode[] DIRECTION_OPTIONS = DirectionMode.values();

    private float totalTime;
    private float prevTick;
    private float extraTick;
    private int selectedItem = -1;
    private int selectedCount = -1;
    private int selectedDirection = -1;
    private boolean closing = false;

    private DirectionMode currentDirection = DirectionMode.AUTO;
    private int currentPlacementCount = 1;
    private int currentSelectedSlot = -1;

    // Slot data
    private final List<SlotData> slots = new ArrayList<>();
    private final ItemStack wandStack;

    // Current selection layer: 0 = direction (innermost), 1 = count (middle), 2 = item (outer)
    private int selectionLayer = -1;

    public record SlotData(int index, ItemStack displayStack, String name) {}

    public DualLayerRadialMenuScreen() {
        super(Component.literal(""));
        // Look up the wand from main hand first, off hand second, so the radial menu works in either hand.
        this.wandStack = com.moakiee.meplacementtool.BasePlacementToolItem
                .findHeldTool(minecraft.player, ItemMultiblockPlacementTool.class);
        loadSlots();
        loadCurrentConfig();
    }

    private void loadCurrentConfig() {
        if (wandStack.isEmpty()) return;

        CompoundTag cfg = wandStack.get(ModDataComponents.PLACEMENT_CONFIG.get());

        if (cfg != null) {
            if (cfg.contains("SelectedSlot")) {
                currentSelectedSlot = cfg.getIntOr("SelectedSlot", -1);
            }

            // Load placement count - use "PlacementCount" key to match ItemMultiblockPlacementTool
            if (cfg.contains("PlacementCount")) {
                currentPlacementCount = cfg.getIntOr("PlacementCount", 1);
            }

            if (cfg.contains("DirectionMode")) {
                currentDirection = DirectionMode.fromId(cfg.getIntOr("DirectionMode", 0));
            }
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

        ItemStackHandler handler = new ItemStackHandler(18);
        if (cfg.contains("items")) {
             handler = NbtCompat.readItemStackHandler(minecraft.level.registryAccess(), cfg.getCompoundOrEmpty("items"), 18);
        }

        CompoundTag fluids = cfg.contains("fluids") ? cfg.getCompoundOrEmpty("fluids") : new CompoundTag();

        int slotCount = handler.getSlots();
        for (int i = 0; i < slotCount; i++) {
            ItemStack stack = handler.getStackInSlot(i);
            String fluidId = fluids.getStringOr(Integer.toString(i), "");

            if (!stack.isEmpty()) {
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

        currentSelectedSlot = slotIndex;
        CompoundTag cfg = wandStack.get(ModDataComponents.PLACEMENT_CONFIG.get());
        if (cfg == null) cfg = new CompoundTag();
        else cfg = cfg.copy();

        cfg.putInt("SelectedSlot", slotIndex);
        wandStack.set(ModDataComponents.PLACEMENT_CONFIG.get(), cfg);

        ClientPacketDistributor.sendToServer(new UpdateWandConfigPayload(cfg));

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

        currentPlacementCount = count;

        // Write to the client-side stack immediately so the preview reflects it this frame
        CompoundTag cfg = wandStack.get(ModDataComponents.PLACEMENT_CONFIG.get());
        cfg = (cfg == null) ? new CompoundTag() : cfg.copy();
        cfg.putInt("PlacementCount", count);
        wandStack.set(ModDataComponents.PLACEMENT_CONFIG.get(), cfg);

        ClientPacketDistributor.sendToServer(new UpdatePlacementCountPayload(count));
        MEPlacementToolMod.ClientForgeEvents.showCountOverlay(
                Component.translatable("meplacementtool.hud.placement_count", count).getString());
    }

    private void selectDirection(DirectionMode mode) {
        if (wandStack.isEmpty()) return;

        currentDirection = mode;

        // Write to the client-side stack immediately so the preview reflects it this frame
        CompoundTag cfg = wandStack.get(ModDataComponents.PLACEMENT_CONFIG.get());
        cfg = (cfg == null) ? new CompoundTag() : cfg.copy();
        cfg.putInt("DirectionMode", mode.ordinal());
        wandStack.set(ModDataComponents.PLACEMENT_CONFIG.get(), cfg);

        ClientPacketDistributor.sendToServer(new UpdateDirectionModePayload(mode.ordinal()));

        String name = Component.translatable(mode.translationKey()).getString();
        MEPlacementToolMod.ClientForgeEvents.showCountOverlay(
                Component.translatable("meplacementtool.hud.direction", name).getString());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTicks);

        if (slots.isEmpty()) {
            graphics.centeredText(font, Component.translatable("message.meplacementtool.no_configured_item"), width / 2, height / 2, 0xFFFFFF);
            return;
        }

        float openAnimation = closing ? 1.0f - totalTime / OPEN_ANIMATION_LENGTH : totalTime / OPEN_ANIMATION_LENGTH;
        float currTick = partialTicks;
        totalTime += (currTick + extraTick - prevTick) / 20f;
        extraTick = 0;
        prevTick = currTick;

        float animProgress = Mth.clamp(openAnimation, 0, 1);
        animProgress = (float) (1 - Math.pow(1 - animProgress, 3));

        int numberOfDirectionSlices = DIRECTION_OPTIONS.length;
        int numberOfCountSlices = COUNT_OPTIONS.length;
        int numberOfItemSlices = Math.max(1, slots.size());

        float dirRadiusMin = Math.max(0.1f, 12 * animProgress);
        float dirRadiusMax = Math.max(0.1f, 32 * animProgress);
        float countRadiusMin = dirRadiusMax + 5 * animProgress;
        float countRadiusMax = countRadiusMin + 18 * animProgress;
        float outerRadiusMin = countRadiusMax + 7 * animProgress;
        float outerRadiusMax = outerRadiusMin + Math.max(33, 23 + numberOfItemSlices * 1.3f) * animProgress;

        float dirItemRadius = (dirRadiusMin + dirRadiusMax) * 0.5f;
        float countItemRadius = (countRadiusMin + countRadiusMax) * 0.5f;
        float outerItemRadius = (outerRadiusMin + outerRadiusMax) * 0.5f;

        int centerX = width / 2;
        int centerY = height / 2;

        double mouseAngle = Math.toDegrees(Math.atan2(mouseY - centerY, mouseX - centerX));
        double mouseDistance = Math.sqrt(Math.pow(mouseX - centerX, 2) + Math.pow(mouseY - centerY, 2));

        float dirSlot0 = (((0 - 0.5f) / (float) numberOfDirectionSlices) + 0.25f) * 360;
        float countSlot0 = (((0 - 0.5f) / (float) numberOfCountSlices) + 0.25f) * 360;
        float itemSlot0 = (((0 - 0.5f) / (float) numberOfItemSlices) + 0.25f) * 360;
        double dirMouseAngle = mouseAngle;
        double countMouseAngle = mouseAngle;
        double itemMouseAngle = mouseAngle;
        if (dirMouseAngle < dirSlot0) dirMouseAngle += 360;
        if (countMouseAngle < countSlot0) countMouseAngle += 360;
        if (itemMouseAngle < itemSlot0) itemMouseAngle += 360;

        if (!closing) {
            selectionLayer = -1;
            selectedDirection = -1;
            selectedCount = -1;
            selectedItem = -1;

            if (mouseDistance >= dirRadiusMin && mouseDistance < dirRadiusMax) {
                selectionLayer = 0;
                selectedDirection = findSlice(dirMouseAngle, numberOfDirectionSlices);
            } else if (mouseDistance >= countRadiusMin && mouseDistance < countRadiusMax) {
                selectionLayer = 1;
                selectedCount = findSlice(countMouseAngle, numberOfCountSlices);
            } else if (mouseDistance >= outerRadiusMin && mouseDistance < outerRadiusMax) {
                selectionLayer = 2;
                selectedItem = findSlice(itemMouseAngle, numberOfItemSlices);
            }
        }

        int hoveredDirection = selectedDirection >= 0 ? adjustIndex(selectedDirection, numberOfDirectionSlices) : -1;
        int hoveredCount = selectedCount >= 0 ? adjustIndex(selectedCount, numberOfCountSlices) : -1;
        int hoveredItem = selectedItem >= 0 ? adjustIndex(selectedItem, numberOfItemSlices) : -1;

        RadialMenuRenderer.drawSlice(graphics, centerX, centerY, dirRadiusMin, dirRadiusMax, 0, 360, 80, 80, 80, 120);
        RadialMenuRenderer.drawSlice(graphics, centerX, centerY, countRadiusMin, countRadiusMax, 0, 360, 80, 80, 80, 120);
        RadialMenuRenderer.drawSlice(graphics, centerX, centerY, outerRadiusMin, outerRadiusMax, 0, 360, 80, 80, 80, 120);

        for (int i = 0; i < numberOfDirectionSlices; i++) {
            float sliceBorderLeft = (((i - 0.5f) / (float) numberOfDirectionSlices) + 0.25f) * 360;
            float sliceBorderRight = (((i + 0.5f) / (float) numberOfDirectionSlices) + 0.25f) * 360;
            int adjusted = adjustIndex(i, numberOfDirectionSlices);
            boolean isCurrentlySelected = adjusted >= 0 && adjusted < DIRECTION_OPTIONS.length
                    && DIRECTION_OPTIONS[adjusted] == currentDirection;

            if (selectionLayer == 0 && selectedDirection == i) {
                RadialMenuRenderer.drawSlice(graphics, centerX, centerY, dirRadiusMin, dirRadiusMax,
                        sliceBorderLeft, sliceBorderRight, 191, 113, 63, 150);
            } else if (isCurrentlySelected) {
                RadialMenuRenderer.drawSlice(graphics, centerX, centerY, dirRadiusMin, dirRadiusMax,
                        sliceBorderLeft, sliceBorderRight, 80, 180, 80, 130);
            }
        }

        for (int i = 0; i < numberOfCountSlices; i++) {
            float sliceBorderLeft = (((i - 0.5f) / (float) numberOfCountSlices) + 0.25f) * 360;
            float sliceBorderRight = (((i + 0.5f) / (float) numberOfCountSlices) + 0.25f) * 360;
            int adjusted = adjustIndex(i, numberOfCountSlices);
            boolean isCurrentlySelected = adjusted >= 0 && adjusted < COUNT_OPTIONS.length
                    && COUNT_OPTIONS[adjusted] == currentPlacementCount;

            if (selectionLayer == 1 && selectedCount == i) {
                RadialMenuRenderer.drawSlice(graphics, centerX, centerY, countRadiusMin, countRadiusMax,
                        sliceBorderLeft, sliceBorderRight, 191, 161, 63, 150);
            } else if (isCurrentlySelected) {
                RadialMenuRenderer.drawSlice(graphics, centerX, centerY, countRadiusMin, countRadiusMax,
                        sliceBorderLeft, sliceBorderRight, 80, 180, 80, 130);
            }
        }

        for (int i = 0; i < numberOfItemSlices; i++) {
            float sliceBorderLeft = (((i - 0.5f) / (float) numberOfItemSlices) + 0.25f) * 360;
            float sliceBorderRight = (((i + 0.5f) / (float) numberOfItemSlices) + 0.25f) * 360;
            int adjusted = adjustIndex(i, numberOfItemSlices);
            boolean isCurrentlySelected = adjusted < slots.size() && slots.get(adjusted).index == currentSelectedSlot;

            if (selectionLayer == 2 && selectedItem == i) {
                RadialMenuRenderer.drawSlice(graphics, centerX, centerY, outerRadiusMin, outerRadiusMax,
                        sliceBorderLeft, sliceBorderRight, 63, 161, 191, 150);
            } else if (isCurrentlySelected) {
                RadialMenuRenderer.drawSlice(graphics, centerX, centerY, outerRadiusMin, outerRadiusMax,
                        sliceBorderLeft, sliceBorderRight, 80, 180, 80, 130);
            }
        }

        for (int i = 0; i < numberOfDirectionSlices; i++) {
            float angle = (((i - 0.5f) / (float) numberOfDirectionSlices) + 0.25f) * 360;
            RadialMenuRenderer.drawDivider(graphics, centerX, centerY, dirRadiusMin, dirRadiusMax, angle, 200, 200, 200, 100);
        }

        for (int i = 0; i < numberOfCountSlices; i++) {
            float angle = (((i - 0.5f) / (float) numberOfCountSlices) + 0.25f) * 360;
            RadialMenuRenderer.drawDivider(graphics, centerX, centerY, countRadiusMin, countRadiusMax, angle, 200, 200, 200, 100);
        }

        for (int i = 0; i < numberOfItemSlices; i++) {
            float angle = (((i - 0.5f) / (float) numberOfItemSlices) + 0.25f) * 360;
            RadialMenuRenderer.drawDivider(graphics, centerX, centerY, outerRadiusMin, outerRadiusMax, angle, 200, 200, 200, 100);
        }

        int hoverY = (int) (centerY - outerRadiusMax - font.lineHeight - 4);
        if (selectionLayer == 0 && hoveredDirection >= 0 && hoveredDirection < DIRECTION_OPTIONS.length) {
            String text = Component.translatable(DIRECTION_OPTIONS[hoveredDirection].translationKey()).getString();
            graphics.centeredText(font, text, centerX, hoverY, 0xFFCC88);
        } else if (selectionLayer == 1 && hoveredCount >= 0 && hoveredCount < COUNT_OPTIONS.length) {
            graphics.centeredText(font, String.valueOf(COUNT_OPTIONS[hoveredCount]), centerX, hoverY, 0xFFFF00);
        } else if (selectionLayer == 2 && hoveredItem >= 0 && hoveredItem < slots.size()) {
            graphics.centeredText(font, slots.get(hoveredItem).name, centerX, hoverY, 0xFFFFFF);
        }

        for (int i = 0; i < numberOfDirectionSlices; i++) {
            float angle = ((i / (float) numberOfDirectionSlices) - 0.25f) * 2 * (float) Math.PI;
            if (numberOfDirectionSlices % 2 != 0) {
                angle += Math.PI / numberOfDirectionSlices;
            }
            int posX = (int) (centerX + dirItemRadius * (float) Math.cos(angle));
            int posY = (int) (centerY + dirItemRadius * (float) Math.sin(angle));
            String label = Component.translatable(DIRECTION_OPTIONS[i].translationKey() + ".short").getString();
            graphics.centeredText(font, label, posX, posY - font.lineHeight / 2, 0xFFFFFF);
        }

        for (int i = 0; i < numberOfCountSlices; i++) {
            float angle = ((i / (float) numberOfCountSlices) - 0.25f) * 2 * (float) Math.PI;
            if (numberOfCountSlices % 2 != 0) {
                angle += Math.PI / numberOfCountSlices;
            }
            int posX = (int) (centerX + countItemRadius * (float) Math.cos(angle));
            int posY = (int) (centerY + countItemRadius * (float) Math.sin(angle));
            graphics.centeredText(font, String.valueOf(COUNT_OPTIONS[i]), posX, posY - font.lineHeight / 2, 0xFFFFFF);
        }

        for (int i = 0; i < numberOfItemSlices; i++) {
            float angle = ((i / (float) numberOfItemSlices) - 0.25f) * 2 * (float) Math.PI;
            if (numberOfItemSlices % 2 != 0) {
                angle += Math.PI / numberOfItemSlices;
            }
            int posX = (int) (centerX - 8 + outerItemRadius * (float) Math.cos(angle));
            int posY = (int) (centerY - 8 + outerItemRadius * (float) Math.sin(angle));

            SlotData slot = slots.get(i);
            if (!slot.displayStack.isEmpty()) {
                graphics.item(slot.displayStack, posX, posY);
            }
        }

        selectedDirection = hoveredDirection;
        selectedCount = hoveredCount;
        selectedItem = hoveredItem;
    }

    /**
     * Convert a slice index (0 = right, incrementing clockwise) into the corresponding
     * index in the option array. Matches the original dual-layer formula so the first
     * option in each array lands on the top of the ring.
     */
    private static int adjustIndex(int sliceIndex, int totalSlices) {
        int adjusted = ((sliceIndex + (totalSlices / 2 + 1)) % totalSlices);
        adjusted = adjusted == 0 ? totalSlices - 1 : adjusted - 1;
        return adjusted;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            if (selectionLayer == 0 && selectedDirection >= 0 && selectedDirection < DIRECTION_OPTIONS.length) {
                selectDirection(DIRECTION_OPTIONS[selectedDirection]);
            } else if (selectionLayer == 1 && selectedCount >= 0 && selectedCount < COUNT_OPTIONS.length) {
                selectCount(COUNT_OPTIONS[selectedCount]);
            } else if (selectionLayer == 2 && selectedItem >= 0 && selectedItem < slots.size()) {
                selectSlot(slots.get(selectedItem).index);
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
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

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
