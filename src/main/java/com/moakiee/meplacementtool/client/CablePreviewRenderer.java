package com.moakiee.meplacementtool.client;

import static net.minecraft.client.renderer.RenderStateShard.COLOR_WRITE;
import static net.minecraft.client.renderer.RenderStateShard.ITEM_ENTITY_TARGET;
import static net.minecraft.client.renderer.RenderStateShard.NO_CULL;
import static net.minecraft.client.renderer.RenderStateShard.RENDERTYPE_LINES_SHADER;
import static net.minecraft.client.renderer.RenderStateShard.TRANSLUCENT_TRANSPARENCY;
import static net.minecraft.client.renderer.RenderStateShard.VIEW_OFFSET_Z_LAYERING;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;

import com.moakiee.meplacementtool.ItemMECablePlacementTool;
import com.moakiee.meplacementtool.MEPlacementToolMod;

/**
 * Renders placement preview for ME Cable Placement Tool.
 * Shows preview of where cables will be placed based on current mode.
 * Only renders connections between preview cables (internal connections only).
 */
public class CablePreviewRenderer {
    private CablePreviewRenderer() {
    }

    /**
     * Similar to {@link RenderType#LINES}, but with inverted depth test.
     */
    public static final RenderType LINES_BEHIND_BLOCK = RenderType.create(
            "meplacementtool_cable_lines_behind",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES,
            256,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_LINES_SHADER)
                    .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.empty()))
                    .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(new RenderStateShard.DepthTestStateShard(">", GL11.GL_GREATER))
                    .setOutputState(ITEM_ENTITY_TARGET)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    // Preview box colors (cyan/blue like other tools)
    private static final float PREVIEW_RED = 0.0f;
    private static final float PREVIEW_GREEN = 0.75f;
    private static final float PREVIEW_BLUE = 1.0f;

    // First point marker color (yellow)
    private static final float POINT1_RED = 1.0f;
    private static final float POINT1_GREEN = 1.0f;
    private static final float POINT1_BLUE = 0.0f;

    // Cable core dimensions (center block)
    private static final double CABLE_CORE_MIN = 0.3125; // 5/16
    private static final double CABLE_CORE_MAX = 0.6875; // 11/16
    
    // Cable arm dimensions
    private static final double CABLE_ARM_MIN = 0.375;   // 6/16
    private static final double CABLE_ARM_MAX = 0.625;   // 10/16

    // Cache for last target position (used when looking at air)
    private static BlockPos lastTargetPos = null;

    public static void install() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, CablePreviewRenderer::handleBlockEvent);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, CablePreviewRenderer::handleRenderEvent);
    }

    // Handle block highlight event (normal case)
    private static void handleBlockEvent(RenderHighlightEvent.Block evt) {
        var level = Minecraft.getInstance().level;
        var poseStack = evt.getPoseStack();
        var buffers = evt.getMultiBufferSource();
        var camera = evt.getCamera();
        if (level == null || buffers == null) {
            return;
        }

        var blockHitResult = evt.getTarget();
        if (blockHitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }

        // Update cached target position
        BlockPos clickedPos = blockHitResult.getBlockPos();
        Direction face = blockHitResult.getDirection();
        BlockPos targetPos = clickedPos.relative(face);
        if (!level.getBlockState(targetPos).isAir()) {
            targetPos = clickedPos;
        }
        lastTargetPos = targetPos;

        if (renderCablePreview(level, poseStack, buffers, camera, blockHitResult, false)) {
            // Don't cancel the event - let normal highlighting still work
        }
    }

    // Handle render level event (for air preview when point1 is set)
    private static void handleRenderEvent(net.minecraftforge.client.event.RenderLevelStageEvent evt) {
        if (evt.getStage() != net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        
        var mc = Minecraft.getInstance();
        var player = mc.player;
        var level = mc.level;
        if (player == null || level == null) {
            return;
        }
        
        // Check if player is holding ME Cable Placement Tool
        ItemStack wand = player.getMainHandItem();
        if (wand.isEmpty() || wand.getItem() != MEPlacementToolMod.ME_CABLE_PLACEMENT_TOOL.get()) {
            lastTargetPos = null;  // Clear cache when not holding tool
            return;
        }
        
        // Render air preview for all modes when point1 is set and looking at air
        BlockPos point1 = ItemMECablePlacementTool.getPoint1(wand);
        
        if (point1 != null) {
            var hitResult = mc.hitResult;
            // Only render if NOT looking at a block (air preview)
            if (hitResult == null || hitResult.getType() != HitResult.Type.BLOCK) {
                var poseStack = evt.getPoseStack();
                var camera = evt.getCamera();
                var buffers = mc.renderBuffers().bufferSource();
                
                renderCablePreview((ClientLevel) level, poseStack, buffers, camera, null, true);
                buffers.endBatch();
            }
        }
    }

    private static boolean renderCablePreview(ClientLevel level,
            PoseStack poseStack,
            MultiBufferSource buffers,
            Camera camera,
            BlockHitResult hitResult,
            boolean isAirPreview) {

        var player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }

        // Check if player is holding ME Cable Placement Tool
        ItemStack wand = player.getMainHandItem();
        if (wand.isEmpty() || wand.getItem() != MEPlacementToolMod.ME_CABLE_PLACEMENT_TOOL.get()) {
            return false;
        }

        BlockPos point1 = ItemMECablePlacementTool.getPoint1(wand);
        BlockPos point2 = ItemMECablePlacementTool.getPoint2(wand);
        ItemMECablePlacementTool.PlacementMode mode = ItemMECablePlacementTool.getMode(wand);

        // For air preview (when hitResult is null), use cached lastTargetPos
        if (isAirPreview && point1 != null) {
            // Render point1 marker always
            renderSingleBlockOutline(poseStack, buffers, camera, point1, POINT1_RED, POINT1_GREEN, POINT1_BLUE, 0.8f, false);
            renderSingleBlockOutline(poseStack, buffers, camera, point1, POINT1_RED, POINT1_GREEN, POINT1_BLUE, 0.3f, true);

            if (mode == ItemMECablePlacementTool.PlacementMode.LINE) {
                // LINE mode: use player look direction
                BlockPos lineEnd = ItemMECablePlacementTool.findLine(player, point1);
                if (lineEnd != null) {
                    List<BlockPos> positions = ItemMECablePlacementTool.calculatePositions(point1, lineEnd, mode);
                    renderBoundingBox(poseStack, buffers, camera, level, positions, PREVIEW_RED, PREVIEW_GREEN, PREVIEW_BLUE, 0.5f, false);
                    renderBoundingBox(poseStack, buffers, camera, level, positions, PREVIEW_RED, PREVIEW_GREEN, PREVIEW_BLUE, 0.15f, true);
                }
            } else if (mode == ItemMECablePlacementTool.PlacementMode.PLANE_FILL) {
                // PLANE_FILL: use cached lastTargetPos
                if (lastTargetPos != null) {
                    List<BlockPos> positions = ItemMECablePlacementTool.calculatePositions(point1, lastTargetPos, mode);
                    renderBoundingBox(poseStack, buffers, camera, level, positions, PREVIEW_RED, PREVIEW_GREEN, PREVIEW_BLUE, 0.5f, false);
                    renderBoundingBox(poseStack, buffers, camera, level, positions, PREVIEW_RED, PREVIEW_GREEN, PREVIEW_BLUE, 0.15f, true);
                }
            } else if (mode == ItemMECablePlacementTool.PlacementMode.PLANE_BRANCHING) {
                // PLANE_BRANCHING: use cached lastTargetPos for point2 or point3
                if (point2 != null) {
                    renderSingleBlockOutline(poseStack, buffers, camera, point2, 1.0f, 0.5f, 0.0f, 0.8f, false);
                    renderSingleBlockOutline(poseStack, buffers, camera, point2, 1.0f, 0.5f, 0.0f, 0.3f, true);
                    if (lastTargetPos != null) {
                        renderBranchingSegments(poseStack, buffers, camera, level, point1, point2, lastTargetPos);
                    }
                }
            }
            return true;
        }

        // Normal block hit case
        if (hitResult == null) {
            return false;
        }

        // Calculate target position (same logic as useOn)
        BlockPos clickedPos = hitResult.getBlockPos();
        Direction face = hitResult.getDirection();
        BlockPos targetPos = clickedPos.relative(face);
        
        // If the target position is not air, use clicked position instead
        if (!level.getBlockState(targetPos).isAir()) {
            targetPos = clickedPos;
        }

        if (mode == ItemMECablePlacementTool.PlacementMode.PLANE_BRANCHING) {
            // Branching mode uses 3 points
            if (point1 != null) {
                // Render point1 marker
                renderSingleBlockOutline(poseStack, buffers, camera, point1, POINT1_RED, POINT1_GREEN, POINT1_BLUE, 0.8f, false);
                renderSingleBlockOutline(poseStack, buffers, camera, point1, POINT1_RED, POINT1_GREEN, POINT1_BLUE, 0.3f, true);
                
                if (point2 != null) {
                    // Render point2 marker (orange)
                    renderSingleBlockOutline(poseStack, buffers, camera, point2, 1.0f, 0.5f, 0.0f, 0.8f, false);
                    renderSingleBlockOutline(poseStack, buffers, camera, point2, 1.0f, 0.5f, 0.0f, 0.3f, true);
                    
                    // Show preview with point3 as target - render each segment separately
                    renderBranchingSegments(poseStack, buffers, camera, level, point1, point2, targetPos);
                }
                return true;
            }
        } else {
            // LINE and PLANE_FILL use 2 points
            if (point1 != null) {
                // Render point1 marker
                renderSingleBlockOutline(poseStack, buffers, camera, point1, POINT1_RED, POINT1_GREEN, POINT1_BLUE, 0.8f, false);
                renderSingleBlockOutline(poseStack, buffers, camera, point1, POINT1_RED, POINT1_GREEN, POINT1_BLUE, 0.3f, true);

                // For LINE mode, use player look direction to calculate endpoint
                BlockPos endPos = targetPos;
                if (mode == ItemMECablePlacementTool.PlacementMode.LINE) {
                    BlockPos lineEnd = ItemMECablePlacementTool.findLine(player, point1);
                    if (lineEnd != null) {
                        endPos = lineEnd;
                    }
                }

                // Calculate and render preview positions
                List<BlockPos> positions = ItemMECablePlacementTool.calculatePositions(point1, endPos, mode);
                renderBoundingBox(poseStack, buffers, camera, level, positions, PREVIEW_RED, PREVIEW_GREEN, PREVIEW_BLUE, 0.5f, false);
                renderBoundingBox(poseStack, buffers, camera, level, positions, PREVIEW_RED, PREVIEW_GREEN, PREVIEW_BLUE, 0.15f, true);

                return true;
            }
        }
        
        // Show preview of current target position (next click will set point1)
        if (level.getBlockState(targetPos).isAir()) {
            renderSingleBlockOutline(poseStack, buffers, camera, targetPos, POINT1_RED, POINT1_GREEN, POINT1_BLUE, 0.3f, false);
        }

        return false;
    }

    private static void renderBranchingSegments(PoseStack poseStack, MultiBufferSource buffers, Camera camera, ClientLevel level, BlockPos p1, BlockPos p2, BlockPos p3) {
        int x1 = p1.getX(), y1 = p1.getY(), z1 = p1.getZ();
        int x2 = p2.getX(), y2 = p2.getY(), z2 = p2.getZ();
        int x3 = p3.getX(), y3 = p3.getY(), z3 = p3.getZ();

        // P1 to P2 determines trunk direction and branch interval
        int dx12 = x2 - x1;
        int dz12 = z2 - z1;

        // Branch interval is the distance from P1 to P2
        int interval = Math.max(1, Math.abs(dx12) + Math.abs(dz12));
        if (interval <= 0) interval = 1;

        // Determine main trunk direction (X or Z) based on P1-P2
        boolean trunkAlongX = Math.abs(dx12) >= Math.abs(dz12);
        int trunkDir = trunkAlongX ? Integer.signum(dx12) : Integer.signum(dz12);
        if (trunkDir == 0) trunkDir = 1;

        // P1 to P3 determines the extent of the plane
        int dx13 = x3 - x1;
        int dz13 = z3 - z1;

        // Calculate trunk length and branch length
        int trunkLength, branchLength;
        int branchDir;

        if (trunkAlongX) {
            trunkLength = Math.abs(dx13);
            branchLength = Math.abs(dz13);
            branchDir = dz13 == 0 ? 1 : Integer.signum(dz13);
        } else {
            trunkLength = Math.abs(dz13);
            branchLength = Math.abs(dx13);
            branchDir = dx13 == 0 ? 1 : Integer.signum(dx13);
        }

        // 1. Render Trunk as one bounding box
        List<BlockPos> trunkPositions = new ArrayList<>();
        for (int t = 0; t <= trunkLength; t++) {
            int trunkX = trunkAlongX ? x1 + t * trunkDir : x1;
            int trunkZ = trunkAlongX ? z1 : z1 + t * trunkDir;
            trunkPositions.add(new BlockPos(trunkX, y1, trunkZ));
        }
        renderBoundingBox(poseStack, buffers, camera, level, trunkPositions, PREVIEW_RED, PREVIEW_GREEN, PREVIEW_BLUE, 0.5f, false);
        renderBoundingBox(poseStack, buffers, camera, level, trunkPositions, PREVIEW_RED, PREVIEW_GREEN, PREVIEW_BLUE, 0.15f, true);

        // 2. Render Branches as separate bounding boxes
        for (int t = 0; t <= trunkLength; t++) {
            if (t % interval == 0) {
                int trunkX = trunkAlongX ? x1 + t * trunkDir : x1;
                int trunkZ = trunkAlongX ? z1 : z1 + t * trunkDir;
                
                List<BlockPos> branchPositions = new ArrayList<>();
                for (int b = 0; b <= branchLength; b++) {
                    int branchX = trunkAlongX ? trunkX : trunkX + b * branchDir;
                    int branchZ = trunkAlongX ? trunkZ + b * branchDir : trunkZ;
                    branchPositions.add(new BlockPos(branchX, y1, branchZ));
                }
                if (!branchPositions.isEmpty()) {
                    renderBoundingBox(poseStack, buffers, camera, level, branchPositions, PREVIEW_RED, PREVIEW_GREEN, PREVIEW_BLUE, 0.5f, false);
                    renderBoundingBox(poseStack, buffers, camera, level, branchPositions, PREVIEW_RED, PREVIEW_GREEN, PREVIEW_BLUE, 0.15f, true);
                }
            }
        }
    }

    /**
     * Render a bounding box around all positions as a single outline with exactly 12 edges.
     */
    private static void renderBoundingBox(PoseStack poseStack,
            MultiBufferSource buffers,
            Camera camera,
            ClientLevel level,
            List<BlockPos> positions,
            float red, float green, float blue, float alpha,
            boolean insideBlock) {

        if (positions.isEmpty()) return;

        // Calculate bounding box of all positions
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        for (BlockPos pos : positions) {
            if (level.getBlockState(pos).isAir()) {  // Only include air blocks
                minX = Math.min(minX, pos.getX());
                minY = Math.min(minY, pos.getY());
                minZ = Math.min(minZ, pos.getZ());
                maxX = Math.max(maxX, pos.getX());
                maxY = Math.max(maxY, pos.getY());
                maxZ = Math.max(maxZ, pos.getZ());
            }
        }

        if (minX == Integer.MAX_VALUE) return;  // No valid positions

        RenderType renderType = insideBlock ? LINES_BEHIND_BLOCK : RenderType.lines();
        var buffer = buffers.getBuffer(renderType);

        double camX = camera.getPosition().x;
        double camY = camera.getPosition().y;
        double camZ = camera.getPosition().z;

        // Calculate box corners using cable core dimensions at the edges
        double x0 = minX + CABLE_CORE_MIN - camX;
        double y0 = minY + CABLE_CORE_MIN - camY;
        double z0 = minZ + CABLE_CORE_MIN - camZ;
        double x1 = maxX + CABLE_CORE_MAX - camX;
        double y1 = maxY + CABLE_CORE_MAX - camY;
        double z1 = maxZ + CABLE_CORE_MAX - camZ;

        poseStack.pushPose();
        var matrix = poseStack.last().pose();
        var normal = poseStack.last().normal();

        // Draw exactly 12 edges of the bounding box
        // Bottom 4 edges
        drawLine(buffer, matrix, normal, (float)x0, (float)y0, (float)z0, (float)x1, (float)y0, (float)z0, red, green, blue, alpha);
        drawLine(buffer, matrix, normal, (float)x1, (float)y0, (float)z0, (float)x1, (float)y0, (float)z1, red, green, blue, alpha);
        drawLine(buffer, matrix, normal, (float)x1, (float)y0, (float)z1, (float)x0, (float)y0, (float)z1, red, green, blue, alpha);
        drawLine(buffer, matrix, normal, (float)x0, (float)y0, (float)z1, (float)x0, (float)y0, (float)z0, red, green, blue, alpha);

        // Top 4 edges
        drawLine(buffer, matrix, normal, (float)x0, (float)y1, (float)z0, (float)x1, (float)y1, (float)z0, red, green, blue, alpha);
        drawLine(buffer, matrix, normal, (float)x1, (float)y1, (float)z0, (float)x1, (float)y1, (float)z1, red, green, blue, alpha);
        drawLine(buffer, matrix, normal, (float)x1, (float)y1, (float)z1, (float)x0, (float)y1, (float)z1, red, green, blue, alpha);
        drawLine(buffer, matrix, normal, (float)x0, (float)y1, (float)z1, (float)x0, (float)y1, (float)z0, red, green, blue, alpha);

        // 4 vertical edges
        drawLine(buffer, matrix, normal, (float)x0, (float)y0, (float)z0, (float)x0, (float)y1, (float)z0, red, green, blue, alpha);
        drawLine(buffer, matrix, normal, (float)x1, (float)y0, (float)z0, (float)x1, (float)y1, (float)z0, red, green, blue, alpha);
        drawLine(buffer, matrix, normal, (float)x1, (float)y0, (float)z1, (float)x1, (float)y1, (float)z1, red, green, blue, alpha);
        drawLine(buffer, matrix, normal, (float)x0, (float)y0, (float)z1, (float)x0, (float)y1, (float)z1, red, green, blue, alpha);

        poseStack.popPose();
    }

    /**
     * Render a single block outline (for point markers).
     */
    private static void renderSingleBlockOutline(PoseStack poseStack,
            MultiBufferSource buffers,
            Camera camera,
            BlockPos pos,
            float red, float green, float blue, float alpha,
            boolean insideBlock) {

        RenderType renderType = insideBlock ? LINES_BEHIND_BLOCK : RenderType.lines();
        var buffer = buffers.getBuffer(renderType);

        double camX = camera.getPosition().x;
        double camY = camera.getPosition().y;
        double camZ = camera.getPosition().z;

        double x0 = pos.getX() + CABLE_CORE_MIN - camX;
        double y0 = pos.getY() + CABLE_CORE_MIN - camY;
        double z0 = pos.getZ() + CABLE_CORE_MIN - camZ;
        double x1 = pos.getX() + CABLE_CORE_MAX - camX;
        double y1 = pos.getY() + CABLE_CORE_MAX - camY;
        double z1 = pos.getZ() + CABLE_CORE_MAX - camZ;

        poseStack.pushPose();
        var matrix = poseStack.last().pose();
        var normal = poseStack.last().normal();

        // Draw 12 edges
        // Bottom 4
        drawLine(buffer, matrix, normal, (float)x0, (float)y0, (float)z0, (float)x1, (float)y0, (float)z0, red, green, blue, alpha);
        drawLine(buffer, matrix, normal, (float)x1, (float)y0, (float)z0, (float)x1, (float)y0, (float)z1, red, green, blue, alpha);
        drawLine(buffer, matrix, normal, (float)x1, (float)y0, (float)z1, (float)x0, (float)y0, (float)z1, red, green, blue, alpha);
        drawLine(buffer, matrix, normal, (float)x0, (float)y0, (float)z1, (float)x0, (float)y0, (float)z0, red, green, blue, alpha);
        // Top 4
        drawLine(buffer, matrix, normal, (float)x0, (float)y1, (float)z0, (float)x1, (float)y1, (float)z0, red, green, blue, alpha);
        drawLine(buffer, matrix, normal, (float)x1, (float)y1, (float)z0, (float)x1, (float)y1, (float)z1, red, green, blue, alpha);
        drawLine(buffer, matrix, normal, (float)x1, (float)y1, (float)z1, (float)x0, (float)y1, (float)z1, red, green, blue, alpha);
        drawLine(buffer, matrix, normal, (float)x0, (float)y1, (float)z1, (float)x0, (float)y1, (float)z0, red, green, blue, alpha);
        // Vertical 4
        drawLine(buffer, matrix, normal, (float)x0, (float)y0, (float)z0, (float)x0, (float)y1, (float)z0, red, green, blue, alpha);
        drawLine(buffer, matrix, normal, (float)x1, (float)y0, (float)z0, (float)x1, (float)y1, (float)z0, red, green, blue, alpha);
        drawLine(buffer, matrix, normal, (float)x1, (float)y0, (float)z1, (float)x1, (float)y1, (float)z1, red, green, blue, alpha);
        drawLine(buffer, matrix, normal, (float)x0, (float)y0, (float)z1, (float)x0, (float)y1, (float)z1, red, green, blue, alpha);

        poseStack.popPose();
    }

    /**
     * Draw a line between two points.
     */
    private static void drawLine(VertexConsumer buffer, Matrix4f matrix, Matrix3f normal,
            float x1, float y1, float z1, float x2, float y2, float z2,
            float red, float green, float blue, float alpha) {
        // Calculate normal direction for the line
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length > 0) {
            dx /= length;
            dy /= length;
            dz /= length;
        }

        buffer.vertex(matrix, x1, y1, z1).color(red, green, blue, alpha).normal(normal, dx, dy, dz).endVertex();
        buffer.vertex(matrix, x2, y2, z2).color(red, green, blue, alpha).normal(normal, dx, dy, dz).endVertex();
    }
}
