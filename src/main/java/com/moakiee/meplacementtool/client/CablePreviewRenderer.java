package com.moakiee.meplacementtool.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

import com.moakiee.meplacementtool.ItemMECablePlacementTool;
import com.moakiee.meplacementtool.MEPlacementToolMod;
import com.moakiee.meplacementtool.client.render.MERenderTypes;

/**
 * Renders an in-world placement preview for the ME Cable Placement Tool: point markers and a
 * time-based rainbow bounding box around the cable run, for LINE / PLANE_FILL / PLANE_BRANCHING modes.
 *
 * <p>Ported to NeoForge 26.1: the original split its work between {@code RenderHighlightEvent.Block}
 * (block look) and {@code RenderLevelStageEvent} (air preview). Here both cases run from a single
 * {@link RenderLevelStageEvent.AfterWeather} listener (the modern world-overlay hook used by AE2's
 * own area overlay), and every box is drawn with {@code ShapeRenderer.renderShape} instead of the
 * old manual line emission.
 */
public class CablePreviewRenderer {
    private CablePreviewRenderer() {
    }

    // First point marker color (yellow)
    private static final float POINT1_RED = 1.0f;
    private static final float POINT1_GREEN = 1.0f;
    private static final float POINT1_BLUE = 0.0f;

    // Cable core dimensions (center block)
    private static final double CABLE_CORE_MIN = 0.3125; // 5/16
    private static final double CABLE_CORE_MAX = 0.6875; // 11/16

    // Cache for last target position (used when looking at air)
    private static BlockPos lastTargetPos = null;

    public static void install() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, CablePreviewRenderer::onRenderLevelStage);
    }

    private static void onRenderLevelStage(RenderLevelStageEvent.AfterWeather evt) {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        var level = mc.level;
        if (player == null || level == null) {
            return;
        }

        // Only when holding the ME Cable Placement Tool.
        ItemStack wand = player.getMainHandItem();
        if (wand.isEmpty() || wand.getItem() != MEPlacementToolMod.ME_CABLE_PLACEMENT_TOOL.get()) {
            lastTargetPos = null;
            return;
        }

        // Resolve what the crosshair is pointing at.
        var hitResult = mc.hitResult;
        BlockHitResult blockHit = (hitResult instanceof BlockHitResult bhr
                && hitResult.getType() == HitResult.Type.BLOCK) ? bhr : null;

        // Keep the cached target up to date from the current block hit (used for the air preview).
        if (blockHit != null) {
            lastTargetPos = ItemMECablePlacementTool.getSmartTargetPos(level, blockHit.getBlockPos(), blockHit.getDirection());
        }

        Vec3 camPos = mc.gameRenderer.getMainCamera().position();
        PoseStack poseStack = evt.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        boolean isAirPreview = (blockHit == null);
        boolean drew = renderCablePreview(level, poseStack, buffers, camPos, blockHit, isAirPreview);
        if (drew) {
            buffers.endBatch();
        }
    }

    private static boolean renderCablePreview(ClientLevel level,
            PoseStack poseStack,
            MultiBufferSource buffers,
            Vec3 camPos,
            BlockHitResult hitResult,
            boolean isAirPreview) {

        var player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }

        ItemStack wand = player.getMainHandItem();
        if (wand.isEmpty() || wand.getItem() != MEPlacementToolMod.ME_CABLE_PLACEMENT_TOOL.get()) {
            return false;
        }

        BlockPos point1 = ItemMECablePlacementTool.getPoint1(wand);
        BlockPos point2 = ItemMECablePlacementTool.getPoint2(wand);
        ItemMECablePlacementTool.PlacementMode mode = ItemMECablePlacementTool.getMode(wand);

        // Air preview (looking at air) - use cached lastTargetPos.
        if (isAirPreview && point1 != null) {
            renderSingleBlockOutline(poseStack, buffers, camPos, point1, POINT1_RED, POINT1_GREEN, POINT1_BLUE, 0.8f, false);
            renderSingleBlockOutline(poseStack, buffers, camPos, point1, POINT1_RED, POINT1_GREEN, POINT1_BLUE, 0.3f, true);

            if (mode == ItemMECablePlacementTool.PlacementMode.LINE) {
                BlockPos lineEnd = ItemMECablePlacementTool.findLine(player, point1);
                if (lineEnd == null && lastTargetPos != null) {
                    lineEnd = lastTargetPos;
                }
                if (lineEnd != null) {
                    List<BlockPos> positions = ItemMECablePlacementTool.calculatePositions(point1, lineEnd, mode);
                    renderRainbowBoundingBox(poseStack, buffers, camPos, positions, 0.6f, false);
                    renderRainbowBoundingBox(poseStack, buffers, camPos, positions, 0.2f, true);
                }
            } else if (mode == ItemMECablePlacementTool.PlacementMode.PLANE_FILL) {
                if (lastTargetPos != null) {
                    List<BlockPos> positions = ItemMECablePlacementTool.calculatePositions(point1, lastTargetPos, mode);
                    renderRainbowBoundingBox(poseStack, buffers, camPos, positions, 0.6f, false);
                    renderRainbowBoundingBox(poseStack, buffers, camPos, positions, 0.2f, true);
                }
            } else if (mode == ItemMECablePlacementTool.PlacementMode.PLANE_BRANCHING) {
                if (point2 != null) {
                    renderSingleBlockOutline(poseStack, buffers, camPos, point2, 1.0f, 0.5f, 0.0f, 0.8f, false);
                    renderSingleBlockOutline(poseStack, buffers, camPos, point2, 1.0f, 0.5f, 0.0f, 0.3f, true);
                    if (lastTargetPos != null) {
                        renderBranchingSegments(poseStack, buffers, camPos, point1, point2, lastTargetPos);
                    }
                } else if (lastTargetPos != null) {
                    List<BlockPos> positions = ItemMECablePlacementTool.getLineBlocks(
                        point1.getX(), point1.getY(), point1.getZ(),
                        lastTargetPos.getX(), lastTargetPos.getY(), lastTargetPos.getZ());
                    renderRainbowBoundingBox(poseStack, buffers, camPos, positions, 0.6f, false);
                    renderRainbowBoundingBox(poseStack, buffers, camPos, positions, 0.2f, true);
                }
            }
            return true;
        }

        // Normal block hit case.
        if (hitResult == null) {
            return false;
        }

        BlockPos clickedPos = hitResult.getBlockPos();
        Direction face = hitResult.getDirection();
        BlockPos targetPos = ItemMECablePlacementTool.getSmartTargetPos(level, clickedPos, face);

        if (mode == ItemMECablePlacementTool.PlacementMode.PLANE_BRANCHING) {
            if (point1 != null) {
                renderSingleBlockOutline(poseStack, buffers, camPos, point1, POINT1_RED, POINT1_GREEN, POINT1_BLUE, 0.8f, false);
                renderSingleBlockOutline(poseStack, buffers, camPos, point1, POINT1_RED, POINT1_GREEN, POINT1_BLUE, 0.3f, true);

                if (point2 != null) {
                    renderSingleBlockOutline(poseStack, buffers, camPos, point2, 1.0f, 0.5f, 0.0f, 0.8f, false);
                    renderSingleBlockOutline(poseStack, buffers, camPos, point2, 1.0f, 0.5f, 0.0f, 0.3f, true);

                    renderBranchingSegments(poseStack, buffers, camPos, point1, point2, targetPos);
                } else {
                    List<BlockPos> positions = ItemMECablePlacementTool.getLineBlocks(
                        point1.getX(), point1.getY(), point1.getZ(),
                        targetPos.getX(), targetPos.getY(), targetPos.getZ());
                    renderRainbowBoundingBox(poseStack, buffers, camPos, positions, 0.6f, false);
                    renderRainbowBoundingBox(poseStack, buffers, camPos, positions, 0.2f, true);
                }
                return true;
            }
        } else {
            if (point1 != null) {
                renderSingleBlockOutline(poseStack, buffers, camPos, point1, POINT1_RED, POINT1_GREEN, POINT1_BLUE, 0.8f, false);
                renderSingleBlockOutline(poseStack, buffers, camPos, point1, POINT1_RED, POINT1_GREEN, POINT1_BLUE, 0.3f, true);

                BlockPos endPos = targetPos;
                if (mode == ItemMECablePlacementTool.PlacementMode.LINE) {
                    BlockPos lineEnd = ItemMECablePlacementTool.findLine(player, point1);
                    if (lineEnd != null) {
                        endPos = lineEnd;
                    }
                }

                List<BlockPos> positions = ItemMECablePlacementTool.calculatePositions(point1, endPos, mode);
                renderRainbowBoundingBox(poseStack, buffers, camPos, positions, 0.6f, false);
                renderRainbowBoundingBox(poseStack, buffers, camPos, positions, 0.2f, true);

                return true;
            }
        }

        // Show preview of current target position (next click will set point1).
        if (ItemMECablePlacementTool.canPlaceCableAt(level, targetPos)) {
            renderSingleBlockOutline(poseStack, buffers, camPos, targetPos, POINT1_RED, POINT1_GREEN, POINT1_BLUE, 0.3f, false);
        }

        return false;
    }

    /**
     * Render branching preview - renders trunk and each branch separately.
     * Uses the same algorithm as calculateBranchPositions for consistency.
     */
    private static void renderBranchingSegments(PoseStack poseStack, MultiBufferSource buffers, Vec3 camPos,
            BlockPos p1, BlockPos p2, BlockPos p3) {
        int x1 = p1.getX(), y1 = p1.getY(), z1 = p1.getZ();
        int x2 = p2.getX(), y2 = p2.getY(), z2 = p2.getZ();
        int x3 = p3.getX(), y3 = p3.getY(), z3 = p3.getZ();

        // P1 to P2 determines trunk direction and branch interval
        int dx12 = x2 - x1;
        int dy12 = y2 - y1;
        int dz12 = z2 - z1;

        int absDx = Math.abs(dx12);
        int absDy = Math.abs(dy12);
        int absDz = Math.abs(dz12);

        int trunkAxis;
        int trunkDir;
        int interval;

        if (absDx >= absDy && absDx >= absDz) {
            trunkAxis = 0;
            trunkDir = dx12 == 0 ? 1 : Integer.signum(dx12);
            interval = Math.max(1, absDx);
        } else if (absDy >= absDx && absDy >= absDz) {
            trunkAxis = 1;
            trunkDir = dy12 == 0 ? 1 : Integer.signum(dy12);
            interval = Math.max(1, absDy);
        } else {
            trunkAxis = 2;
            trunkDir = dz12 == 0 ? 1 : Integer.signum(dz12);
            interval = Math.max(1, absDz);
        }

        int dx13 = x3 - x1;
        int dy13 = y3 - y1;
        int dz13 = z3 - z1;

        int branchAxis;
        int branchDir;
        int trunkLength;
        int branchLength;

        if (trunkAxis == 0) {
            trunkLength = Math.abs(dx13);
            if (Math.abs(dy13) >= Math.abs(dz13)) {
                branchAxis = 1;
                branchLength = Math.abs(dy13);
                branchDir = dy13 == 0 ? 1 : Integer.signum(dy13);
            } else {
                branchAxis = 2;
                branchLength = Math.abs(dz13);
                branchDir = dz13 == 0 ? 1 : Integer.signum(dz13);
            }
        } else if (trunkAxis == 1) {
            trunkLength = Math.abs(dy13);
            if (Math.abs(dx13) >= Math.abs(dz13)) {
                branchAxis = 0;
                branchLength = Math.abs(dx13);
                branchDir = dx13 == 0 ? 1 : Integer.signum(dx13);
            } else {
                branchAxis = 2;
                branchLength = Math.abs(dz13);
                branchDir = dz13 == 0 ? 1 : Integer.signum(dz13);
            }
        } else {
            trunkLength = Math.abs(dz13);
            if (Math.abs(dx13) >= Math.abs(dy13)) {
                branchAxis = 0;
                branchLength = Math.abs(dx13);
                branchDir = dx13 == 0 ? 1 : Integer.signum(dx13);
            } else {
                branchAxis = 1;
                branchLength = Math.abs(dy13);
                branchDir = dy13 == 0 ? 1 : Integer.signum(dy13);
            }
        }

        // 1. Render Trunk with rainbow gradient bounding box
        List<BlockPos> trunkPositions = new ArrayList<>();
        for (int t = 0; t <= trunkLength; t++) {
            int tx = x1, ty = y1, tz = z1;
            if (trunkAxis == 0) tx = x1 + t * trunkDir;
            else if (trunkAxis == 1) ty = y1 + t * trunkDir;
            else tz = z1 + t * trunkDir;
            trunkPositions.add(new BlockPos(tx, ty, tz));
        }
        renderRainbowBoundingBox(poseStack, buffers, camPos, trunkPositions, 0.6f, false);
        renderRainbowBoundingBox(poseStack, buffers, camPos, trunkPositions, 0.2f, true);

        // 2. Render Branches with rainbow gradient bounding boxes
        for (int t = 0; t <= trunkLength; t++) {
            if (t % interval == 0) {
                int tx = x1, ty = y1, tz = z1;
                if (trunkAxis == 0) tx = x1 + t * trunkDir;
                else if (trunkAxis == 1) ty = y1 + t * trunkDir;
                else tz = z1 + t * trunkDir;

                List<BlockPos> branchPositions = new ArrayList<>();
                for (int b = 0; b <= branchLength; b++) {
                    int bx = tx, by = ty, bz = tz;
                    if (branchAxis == 0) bx = tx + b * branchDir;
                    else if (branchAxis == 1) by = ty + b * branchDir;
                    else bz = tz + b * branchDir;
                    branchPositions.add(new BlockPos(bx, by, bz));
                }
                if (!branchPositions.isEmpty()) {
                    renderRainbowBoundingBox(poseStack, buffers, camPos, branchPositions, 0.6f, false);
                    renderRainbowBoundingBox(poseStack, buffers, camPos, branchPositions, 0.2f, true);
                }
            }
        }
    }

    /**
     * Render a single bounding box around all positions, using a time-based rainbow color.
     */
    private static void renderRainbowBoundingBox(PoseStack poseStack,
            MultiBufferSource buffers,
            Vec3 camPos,
            List<BlockPos> positions,
            float alpha,
            boolean insideBlock) {

        if (positions.isEmpty()) return;

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        for (BlockPos pos : positions) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        if (minX == Integer.MAX_VALUE) return;

        AABB box = new AABB(
                minX + CABLE_CORE_MIN, minY + CABLE_CORE_MIN, minZ + CABLE_CORE_MIN,
                maxX + CABLE_CORE_MAX, maxY + CABLE_CORE_MAX, maxZ + CABLE_CORE_MAX);

        float[] color = RainbowRenderHelper.getTimeBasedRainbowColor();
        int packed = ARGB.colorFromFloat(alpha, color[0], color[1], color[2]);

        drawBox(poseStack, buffers, camPos, box, packed, insideBlock);
    }

    /**
     * Render a single block outline (for point markers), sized to the cable core.
     */
    private static void renderSingleBlockOutline(PoseStack poseStack,
            MultiBufferSource buffers,
            Vec3 camPos,
            BlockPos pos,
            float red, float green, float blue, float alpha,
            boolean insideBlock) {

        AABB box = new AABB(
                pos.getX() + CABLE_CORE_MIN, pos.getY() + CABLE_CORE_MIN, pos.getZ() + CABLE_CORE_MIN,
                pos.getX() + CABLE_CORE_MAX, pos.getY() + CABLE_CORE_MAX, pos.getZ() + CABLE_CORE_MAX);

        int packed = ARGB.colorFromFloat(alpha, red, green, blue);
        drawBox(poseStack, buffers, camPos, box, packed, insideBlock);
    }

    /**
     * Draws the 12 edges of a world-space AABB, camera-relative, via {@code ShapeRenderer.renderShape}.
     */
    private static void drawBox(PoseStack poseStack,
            MultiBufferSource buffers,
            Vec3 camPos,
            AABB box,
            int packedColor,
            boolean insideBlock) {
        RenderType renderType = insideBlock ? MERenderTypes.LINES_BEHIND_BLOCK : RenderTypes.lines();
        var buffer = buffers.getBuffer(renderType);
        ShapeRenderer.renderShape(
                poseStack,
                buffer,
                Shapes.create(box),
                -camPos.x, -camPos.y, -camPos.z,
                packedColor,
                7 /* line width */);
    }
}
