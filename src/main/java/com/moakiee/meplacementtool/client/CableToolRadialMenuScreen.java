package com.moakiee.meplacementtool.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import appeng.api.util.AEColor;
import com.moakiee.meplacementtool.ItemMECablePlacementTool;
import com.moakiee.meplacementtool.network.ModNetwork;
import com.moakiee.meplacementtool.network.UpdateCableToolPacket;

/**
 * Radial menu for ME Cable Placement Tool.
 * Based on DualLayerRadialMenuScreen implementation.
 * Inner ring: placement mode selection (3 options)
 * Middle ring: cable type selection (5 options)
 * Outer ring: color selection (16 options, if upgrade is present)
 */
public class CableToolRadialMenuScreen extends Screen {
    private static final float PRECISION = 5.0f;
    private static final float OPEN_ANIMATION_LENGTH = 0.25f;

    private final int openKey;
    private final ItemStack toolStack;
    private float totalTime;
    private float prevTick;
    private float extraTick;
    private boolean closing = false;

    private ItemMECablePlacementTool.PlacementMode currentMode;
    private ItemMECablePlacementTool.CableType currentCableType;
    private AEColor currentColor;
    private boolean hasUpgrade;

    // Selection state
    private int selectionLayer = -1; // 0: mode, 1: cable, 2: color
    private int selectedModeIndex = -1;
    private int selectedCableIndex = -1;
    private int selectedColorIndex = -1;

    // Translation keys for cable types
    private static final String[] CABLE_TRANSLATION_KEYS = {
        "meplacementtool.cable.glass",
        "meplacementtool.cable.covered",
        "meplacementtool.cable.smart",
        "meplacementtool.cable.dense_covered",
        "meplacementtool.cable.dense_smart"
    };

    // Translation keys for placement modes
    private static final String[] MODE_TRANSLATION_KEYS = {
        "meplacementtool.mode.line",
        "meplacementtool.mode.plane_fill",
        "meplacementtool.mode.plane_branching"
    };

    // Translation keys for colors (matches AEColor enum order)
    private static final String[] COLOR_TRANSLATION_KEYS = {
        "meplacementtool.color.white",
        "meplacementtool.color.orange",
        "meplacementtool.color.magenta",
        "meplacementtool.color.light_blue",
        "meplacementtool.color.yellow",
        "meplacementtool.color.lime",
        "meplacementtool.color.pink",
        "meplacementtool.color.gray",
        "meplacementtool.color.light_gray",
        "meplacementtool.color.cyan",
        "meplacementtool.color.purple",
        "meplacementtool.color.blue",
        "meplacementtool.color.brown",
        "meplacementtool.color.green",
        "meplacementtool.color.red",
        "meplacementtool.color.black",
        "meplacementtool.color.transparent"
    };

    public CableToolRadialMenuScreen(int openKey) {
        super(Component.literal("Cable Tool Menu"));
        this.openKey = openKey;
        this.minecraft = Minecraft.getInstance();
        this.toolStack = minecraft.player.getMainHandItem();
        loadState();
    }

    private void loadState() {
        currentMode = ItemMECablePlacementTool.getMode(toolStack);
        currentCableType = ItemMECablePlacementTool.getCableType(toolStack);
        currentColor = ItemMECablePlacementTool.getColor(toolStack);
        hasUpgrade = ItemMECablePlacementTool.hasUpgrade(toolStack);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
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

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // Don't call super.render() - it draws a background

        PoseStack ms = graphics.pose();
        float openAnimation = closing ? 1.0f - totalTime / OPEN_ANIMATION_LENGTH : totalTime / OPEN_ANIMATION_LENGTH;
        float currTick = minecraft.getFrameTime();
        totalTime += (currTick + extraTick - prevTick) / 20f;
        extraTick = 0;
        prevTick = currTick;

        float animProgress = Mth.clamp(openAnimation, 0, 1);
        animProgress = (float) (1 - Math.pow(1 - animProgress, 3));

        int numberOfModeSlices = 3;
        int numberOfCableSlices = 5;
        int numberOfColorSlices = 17; // 16 colors + TRANSPARENT (Fluix)

        // Scale factor to reduce menu size to 2/3
        float scale = 0.67f;

        // Ring radii with gaps between rings (scaled to 2/3)
        float modeRadiusMin = Math.max(0.1f, 15 * scale * animProgress);
        float modeRadiusMax = Math.max(0.1f, 40 * scale * animProgress);
        float cableRadiusMin = modeRadiusMax + 5 * scale * animProgress; // Gap
        float cableRadiusMax = cableRadiusMin + 28 * scale * animProgress;
        float colorRadiusMin = cableRadiusMax + 5 * scale * animProgress; // Gap
        float colorRadiusMax = colorRadiusMin + 24 * scale * animProgress;

        float modeItemRadius = (modeRadiusMin + modeRadiusMax) * 0.5f;
        float cableItemRadius = (cableRadiusMin + cableRadiusMax) * 0.5f;
        float colorItemRadius = (colorRadiusMin + colorRadiusMax) * 0.5f;

        int centerX = width / 2;
        int centerY = height / 2;

        double mouseAngle = Math.toDegrees(Math.atan2(mouseY - centerY, mouseX - centerX));
        double mouseDistance = Math.sqrt(Math.pow(mouseX - centerX, 2) + Math.pow(mouseY - centerY, 2));

        // Adjust mouse angle for slot calculation (same logic as DualLayerRadialMenuScreen)
        float modeSlot0 = (((0 - 0.5f) / (float) numberOfModeSlices) + 0.25f) * 360;
        float cableSlot0 = (((0 - 0.5f) / (float) numberOfCableSlices) + 0.25f) * 360;
        float colorSlot0 = (((0 - 0.5f) / (float) numberOfColorSlices) + 0.25f) * 360;
        double modeMouseAngle = mouseAngle < modeSlot0 ? mouseAngle + 360 : mouseAngle;
        double cableMouseAngle = mouseAngle < cableSlot0 ? mouseAngle + 360 : mouseAngle;
        double colorMouseAngle = mouseAngle < colorSlot0 ? mouseAngle + 360 : mouseAngle;

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
            selectedModeIndex = -1;
            selectedCableIndex = -1;
            selectedColorIndex = -1;

            // Check mode ring (inner)
            if (mouseDistance >= modeRadiusMin && mouseDistance < modeRadiusMax) {
                selectionLayer = 0;
                for (int i = 0; i < numberOfModeSlices; i++) {
                    float sliceBorderLeft = (((i - 0.5f) / (float) numberOfModeSlices) + 0.25f) * 360;
                    float sliceBorderRight = (((i + 0.5f) / (float) numberOfModeSlices) + 0.25f) * 360;
                    if (modeMouseAngle >= sliceBorderLeft && modeMouseAngle < sliceBorderRight) {
                        selectedModeIndex = i;
                        break;
                    }
                }
            }
            // Check cable ring (middle)
            else if (mouseDistance >= cableRadiusMin && mouseDistance < cableRadiusMax) {
                selectionLayer = 1;
                for (int i = 0; i < numberOfCableSlices; i++) {
                    float sliceBorderLeft = (((i - 0.5f) / (float) numberOfCableSlices) + 0.25f) * 360;
                    float sliceBorderRight = (((i + 0.5f) / (float) numberOfCableSlices) + 0.25f) * 360;
                    if (cableMouseAngle >= sliceBorderLeft && cableMouseAngle < sliceBorderRight) {
                        selectedCableIndex = i;
                        break;
                    }
                }
            }
            // Check color ring (outer, only if upgrade present)
            else if (hasUpgrade && mouseDistance >= colorRadiusMin && mouseDistance < colorRadiusMax) {
                selectionLayer = 2;
                for (int i = 0; i < numberOfColorSlices; i++) {
                    float sliceBorderLeft = (((i - 0.5f) / (float) numberOfColorSlices) + 0.25f) * 360;
                    float sliceBorderRight = (((i + 0.5f) / (float) numberOfColorSlices) + 0.25f) * 360;
                    if (colorMouseAngle >= sliceBorderLeft && colorMouseAngle < sliceBorderRight) {
                        selectedColorIndex = i;
                        break;
                    }
                }
            }
        }

        // Draw gray background rings
        drawSlice(buffer, centerX, centerY, 9, modeRadiusMin, modeRadiusMax, 0, 360, 80, 80, 80, 120);
        drawSlice(buffer, centerX, centerY, 9, cableRadiusMin, cableRadiusMax, 0, 360, 80, 80, 80, 120);
        if (hasUpgrade) {
            drawSlice(buffer, centerX, centerY, 9, colorRadiusMin, colorRadiusMax, 0, 360, 80, 80, 80, 120);
        }

        // Draw mode ring highlights
        for (int i = 0; i < numberOfModeSlices; i++) {
            float sliceBorderLeft = (((i - 0.5f) / (float) numberOfModeSlices) + 0.25f) * 360;
            float sliceBorderRight = (((i + 0.5f) / (float) numberOfModeSlices) + 0.25f) * 360;

            int adjusted = adjustIndex(i, numberOfModeSlices);
            boolean isCurrentlySelected = adjusted >= 0 && adjusted < ItemMECablePlacementTool.PlacementMode.values().length 
                    && ItemMECablePlacementTool.PlacementMode.values()[adjusted] == currentMode;

            if (selectionLayer == 0 && selectedModeIndex == i) {
                drawSlice(buffer, centerX, centerY, 10, modeRadiusMin, modeRadiusMax, sliceBorderLeft, sliceBorderRight, 63, 161, 191, 150);
            } else if (isCurrentlySelected) {
                drawSlice(buffer, centerX, centerY, 10, modeRadiusMin, modeRadiusMax, sliceBorderLeft, sliceBorderRight, 80, 180, 80, 130);
            }
        }

        // Draw cable ring highlights
        for (int i = 0; i < numberOfCableSlices; i++) {
            float sliceBorderLeft = (((i - 0.5f) / (float) numberOfCableSlices) + 0.25f) * 360;
            float sliceBorderRight = (((i + 0.5f) / (float) numberOfCableSlices) + 0.25f) * 360;

            int adjusted = adjustIndex(i, numberOfCableSlices);
            boolean isCurrentlySelected = adjusted >= 0 && adjusted < ItemMECablePlacementTool.CableType.values().length 
                    && ItemMECablePlacementTool.CableType.values()[adjusted] == currentCableType;

            if (selectionLayer == 1 && selectedCableIndex == i) {
                drawSlice(buffer, centerX, centerY, 10, cableRadiusMin, cableRadiusMax, sliceBorderLeft, sliceBorderRight, 63, 161, 191, 150);
            } else if (isCurrentlySelected) {
                drawSlice(buffer, centerX, centerY, 10, cableRadiusMin, cableRadiusMax, sliceBorderLeft, sliceBorderRight, 80, 180, 80, 130);
            }
        }

        // Draw color ring highlights
        if (hasUpgrade) {
            for (int i = 0; i < numberOfColorSlices; i++) {
                float sliceBorderLeft = (((i - 0.5f) / (float) numberOfColorSlices) + 0.25f) * 360;
                float sliceBorderRight = (((i + 0.5f) / (float) numberOfColorSlices) + 0.25f) * 360;

                int adjusted = adjustIndex(i, numberOfColorSlices);
                boolean isCurrentlySelected = adjusted >= 0 && adjusted < AEColor.values().length 
                        && AEColor.values()[adjusted] == currentColor;

                if (selectionLayer == 2 && selectedColorIndex == i) {
                    drawSlice(buffer, centerX, centerY, 10, colorRadiusMin, colorRadiusMax, sliceBorderLeft, sliceBorderRight, 63, 161, 191, 150);
                } else if (isCurrentlySelected) {
                    drawSlice(buffer, centerX, centerY, 10, colorRadiusMin, colorRadiusMax, sliceBorderLeft, sliceBorderRight, 80, 180, 80, 130);
                }
            }
        }

        BufferUploader.drawWithShader(buffer.end());

        // Draw divider lines
        buffer = tessellator.getBuilder();
        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

        drawRingDividers(buffer, centerX, centerY, modeRadiusMin, modeRadiusMax, numberOfModeSlices);
        drawRingDividers(buffer, centerX, centerY, cableRadiusMin, cableRadiusMax, numberOfCableSlices);
        if (hasUpgrade) {
            drawRingDividers(buffer, centerX, centerY, colorRadiusMin, colorRadiusMax, numberOfColorSlices);
        }
        BufferUploader.drawWithShader(buffer.end());

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        // Draw center hint text
        String hintText = null;
        int hintColor = 0xFFFFFF;

        if (selectionLayer == 0 && selectedModeIndex >= 0) {
            int adjusted = adjustIndex(selectedModeIndex, numberOfModeSlices);
            if (adjusted >= 0 && adjusted < MODE_TRANSLATION_KEYS.length) {
                hintText = Component.translatable(MODE_TRANSLATION_KEYS[adjusted]).getString();
            }
        } else if (selectionLayer == 1 && selectedCableIndex >= 0) {
            int adjusted = adjustIndex(selectedCableIndex, numberOfCableSlices);
            if (adjusted >= 0 && adjusted < CABLE_TRANSLATION_KEYS.length) {
                hintText = Component.translatable(CABLE_TRANSLATION_KEYS[adjusted]).getString();
            }
        } else if (selectionLayer == 2 && selectedColorIndex >= 0) {
            int adjusted = adjustIndex(selectedColorIndex, numberOfColorSlices);
            if (adjusted >= 0 && adjusted < COLOR_TRANSLATION_KEYS.length) {
                AEColor c = AEColor.values()[adjusted];
                hintText = Component.translatable(COLOR_TRANSLATION_KEYS[adjusted]).getString();
                hintColor = c.mediumVariant;
            }
        }

        if (hintText != null) {
            graphics.drawCenteredString(font, hintText, centerX, (height - font.lineHeight) / 2, hintColor);
        }

        ms.popPose();

        // Draw mode labels
        for (int i = 0; i < numberOfModeSlices; i++) {
            float angle = ((i / (float) numberOfModeSlices) - 0.25f) * 2 * (float) Math.PI;
            if (numberOfModeSlices % 2 != 0) {
                angle += Math.PI / numberOfModeSlices;
            }
            float posX = centerX + modeItemRadius * (float) Math.cos(angle);
            float posY = centerY + modeItemRadius * (float) Math.sin(angle);
            String text = Component.translatable(MODE_TRANSLATION_KEYS[i]).getString();
            graphics.drawCenteredString(font, text, (int) posX, (int) posY - font.lineHeight / 2, 0xFFFFFF);
        }

        // Draw cable labels
        for (int i = 0; i < numberOfCableSlices; i++) {
            float angle = ((i / (float) numberOfCableSlices) - 0.25f) * 2 * (float) Math.PI;
            if (numberOfCableSlices % 2 != 0) {
                angle += Math.PI / numberOfCableSlices;
            }
            float posX = centerX + cableItemRadius * (float) Math.cos(angle);
            float posY = centerY + cableItemRadius * (float) Math.sin(angle);
            String text = Component.translatable(CABLE_TRANSLATION_KEYS[i]).getString();

            ms.pushPose();
            ms.translate(posX, posY, 0);
            ms.scale(0.7f, 0.7f, 1.0f);
            graphics.drawCenteredString(font, text, 0, -font.lineHeight / 2, 0xFFFFFF);
            ms.popPose();
        }

        // Draw color indicators
        if (hasUpgrade) {
            for (int i = 0; i < numberOfColorSlices; i++) {
                float angle = ((i / (float) numberOfColorSlices) - 0.25f) * 2 * (float) Math.PI;
                if (numberOfColorSlices % 2 != 0) {
                    angle += Math.PI / numberOfColorSlices;
                }
                float posX = centerX + colorItemRadius * (float) Math.cos(angle);
                float posY = centerY + colorItemRadius * (float) Math.sin(angle);
                AEColor c = AEColor.values()[i];
                graphics.drawCenteredString(font, "●", (int) posX, (int) posY - font.lineHeight / 2, c.mediumVariant);
            }
        }

        // Adjust selection indices for display mapping (same as DualLayerRadialMenuScreen)
        if (selectionLayer == 0 && selectedModeIndex >= 0) {
            selectedModeIndex = adjustIndex(selectedModeIndex, numberOfModeSlices);
        } else if (selectionLayer == 1 && selectedCableIndex >= 0) {
            selectedCableIndex = adjustIndex(selectedCableIndex, numberOfCableSlices);
        } else if (selectionLayer == 2 && selectedColorIndex >= 0) {
            selectedColorIndex = adjustIndex(selectedColorIndex, numberOfColorSlices);
        }
    }

    private int adjustIndex(int i, int count) {
        int adjusted = ((i + (count / 2 + 1)) % count);
        adjusted = adjusted == 0 ? count - 1 : adjusted - 1;
        return adjusted;
    }

    private void drawRingDividers(BufferBuilder buffer, int cx, int cy, float rIn, float rOut, int segments) {
        for (int i = 0; i < segments; i++) {
            float angle = (float) Math.toRadians((((i - 0.5f) / (float) segments) + 0.25f) * 360);
            float x1 = cx + rIn * (float) Math.cos(angle);
            float y1 = cy + rIn * (float) Math.sin(angle);
            float x2 = cx + rOut * (float) Math.cos(angle);
            float y2 = cy + rOut * (float) Math.sin(angle);
            buffer.vertex(x1, y1, 11).color(200, 200, 200, 100).endVertex();
            buffer.vertex(x2, y2, 11).color(200, 200, 200, 100).endVertex();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // Apply selection based on adjusted index
            if (selectionLayer == 0 && selectedModeIndex >= 0 && selectedModeIndex < ItemMECablePlacementTool.PlacementMode.values().length) {
                currentMode = ItemMECablePlacementTool.PlacementMode.values()[selectedModeIndex];
            } else if (selectionLayer == 1 && selectedCableIndex >= 0 && selectedCableIndex < ItemMECablePlacementTool.CableType.values().length) {
                currentCableType = ItemMECablePlacementTool.CableType.values()[selectedCableIndex];
            } else if (selectionLayer == 2 && selectedColorIndex >= 0 && selectedColorIndex < AEColor.values().length) {
                currentColor = AEColor.values()[selectedColorIndex];
            }

            // Send packet to server
            ModNetwork.CHANNEL.sendToServer(new UpdateCableToolPacket(currentMode.ordinal(), currentCableType.ordinal(), currentColor.ordinal()));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (InputConstants.getKey(keyCode, scanCode).getValue() == openKey) {
            this.onClose();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
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
}
