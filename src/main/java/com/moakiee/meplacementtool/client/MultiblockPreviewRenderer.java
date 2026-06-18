package com.moakiee.meplacementtool.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;

import com.moakiee.meplacementtool.ItemMultiblockPlacementTool;
import com.moakiee.meplacementtool.ItemMultiblockPlacementTool.DirectionMode;
import com.moakiee.meplacementtool.MEPlacementToolMod;

import java.util.*;

/**
 * Renders an in-world preview (cyan wireframe boxes) of the positions the Multiblock Placement
 * Tool will fill. Ported to NeoForge 26.1's block-outline extraction pipeline: instead of drawing
 * directly in {@code RenderHighlightEvent.Block}, we register a {@code CustomBlockOutlineRenderer}
 * during {@link ExtractBlockOutlineRenderStateEvent} and draw the boxes with
 * {@code ShapeRenderer.renderShape}.
 */
public class MultiblockPreviewRenderer {
    private BlockHitResult lastRayTraceResult;
    private ItemStack lastWand;
    private Set<BlockPos> cachedPositions;
    private int lastPlacementCount;
    private DirectionMode lastDirectionMode;

    @SubscribeEvent
    public void extractBlockOutline(ExtractBlockOutlineRenderStateEvent event) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        ItemStack wand = player.getMainHandItem();
        if (wand.isEmpty() || wand.getItem() != MEPlacementToolMod.MULTIBLOCK_PLACEMENT_TOOL.get()) return;

        if (!(event.getHitResult() instanceof BlockHitResult rtr) || rtr.getType() != HitResult.Type.BLOCK) return;

        int placementCount = ItemMultiblockPlacementTool.getPlacementCount(wand);
        DirectionMode directionMode = ItemMultiblockPlacementTool.getDirectionMode(wand);

        Set<BlockPos> blocks;
        if (cachedPositions == null || !compareRTR(lastRayTraceResult, rtr) ||
                !ItemStack.matches(lastWand, wand) || lastPlacementCount != placementCount ||
                lastDirectionMode != directionMode) {
            blocks = calculatePlacementPositions(player, rtr, wand, placementCount, directionMode);
            cachedPositions = blocks;
            lastRayTraceResult = rtr;
            lastWand = wand.copy();
            lastPlacementCount = placementCount;
            lastDirectionMode = directionMode;
        } else {
            blocks = cachedPositions;
        }

        if (blocks == null || blocks.isEmpty()) return;

        final Set<BlockPos> toDraw = blocks;
        final double camX = event.getCamera().position().x;
        final double camY = event.getCamera().position().y;
        final double camZ = event.getCamera().position().z;

        event.addCustomRenderer((outlineState, bufferSource, poseStack, translucentPass, levelState) -> {
            var buffer = bufferSource.getBuffer(RenderTypes.lines());
            int color = ARGB.colorFromFloat(0.4f, 0.0f, 0.75f, 1.0f); // cyan, matches 1.21.1 original
            for (BlockPos block : toDraw) {
                ShapeRenderer.renderShape(poseStack, buffer, Shapes.block(),
                        block.getX() - camX, block.getY() - camY, block.getZ() - camZ,
                        color, 7);
            }
            return true; // suppress the vanilla single-block highlight
        });
    }

    private Set<BlockPos> calculatePlacementPositions(Player player, BlockHitResult rtr, ItemStack wand, int placementCount, DirectionMode directionMode) {
        Set<BlockPos> placePositions = new HashSet<>();
        if (placementCount <= 0) return placePositions;

        Level level = player.level();
        BlockPos clickedPos = rtr.getBlockPos();
        Direction clickedFace = rtr.getDirection();
        var clickedState = level.getBlockState(clickedPos);

        LinkedList<BlockPos> candidates = new LinkedList<>();
        Set<BlockPos> allCandidates = new HashSet<>();
        ArrayList<BlockPos> positions = new ArrayList<>();

        // MAX_CANDIDATES limit to prevent infinite loops when placeable positions are fewer than requested
        final int MAX_CANDIDATES = placementCount * 10;

        BlockPos startingPoint = clickedPos.relative(clickedFace);
        candidates.add(startingPoint);

        while (!candidates.isEmpty() && positions.size() < placementCount && allCandidates.size() < MAX_CANDIDATES) {
            BlockPos currentCandidate = candidates.removeFirst();
            if (!allCandidates.add(currentCandidate)) {
                continue;
            }

            // Match ConstructionWand: even when direction is locked, the supporting block
            // (opposite the clicked face) must equal the clicked block.
            BlockPos supportingPoint = currentCandidate.relative(clickedFace.getOpposite());
            var supportingState = level.getBlockState(supportingPoint);

            if (supportingState.getBlock() == clickedState.getBlock()) {
                var currentState = level.getBlockState(currentCandidate);
                boolean canPlace = level.isEmptyBlock(currentCandidate);
                if (!canPlace) {
                    try {
                        var checkContext = new BlockPlaceContext(new UseOnContext(
                                player, InteractionHand.MAIN_HAND, new BlockHitResult(
                                        rtr.getLocation(), rtr.getDirection(), currentCandidate, rtr.isInside()
                                )
                        ));
                        canPlace = currentState.canBeReplaced(checkContext);
                    } catch (Throwable t) {}
                }
                if (canPlace) {
                    positions.add(currentCandidate);
                    // Only expand candidates after successful placement (prevents cross-pit overflow)
                    addAdjacentPositions(candidates, currentCandidate, clickedFace, directionMode);
                }
            }
        }

        placePositions.addAll(positions);
        return placePositions;
    }

    private void addAdjacentPositions(LinkedList<BlockPos> candidates, BlockPos pos, Direction face, DirectionMode directionMode) {
        switch (directionMode) {
            case NORTH_SOUTH -> {
                candidates.add(pos.north());
                candidates.add(pos.south());
            }
            case EAST_WEST -> {
                candidates.add(pos.east());
                candidates.add(pos.west());
            }
            case VERTICAL -> {
                candidates.add(pos.above());
                candidates.add(pos.below());
            }
            case AUTO -> addAutoAdjacentPositions(candidates, pos, face);
        }
    }

    private void addAutoAdjacentPositions(LinkedList<BlockPos> candidates, BlockPos pos, Direction face) {
        switch (face) {
            case DOWN, UP -> {
                candidates.add(pos.north());
                candidates.add(pos.south());
                candidates.add(pos.east());
                candidates.add(pos.west());
                candidates.add(pos.north().east());
                candidates.add(pos.north().west());
                candidates.add(pos.south().east());
                candidates.add(pos.south().west());
            }
            case NORTH, SOUTH -> {
                candidates.add(pos.above());
                candidates.add(pos.below());
                candidates.add(pos.east());
                candidates.add(pos.west());
                candidates.add(pos.above().east());
                candidates.add(pos.above().west());
                candidates.add(pos.below().east());
                candidates.add(pos.below().west());
            }
            case EAST, WEST -> {
                candidates.add(pos.above());
                candidates.add(pos.below());
                candidates.add(pos.north());
                candidates.add(pos.south());
                candidates.add(pos.above().north());
                candidates.add(pos.above().south());
                candidates.add(pos.below().north());
                candidates.add(pos.below().south());
            }
        }
    }

    private static boolean compareRTR(BlockHitResult rtr1, BlockHitResult rtr2) {
        if (rtr1 == null || rtr2 == null) return false;
        return rtr1.getBlockPos().equals(rtr2.getBlockPos()) && rtr1.getDirection().equals(rtr2.getDirection());
    }

    public void reset() {
        cachedPositions = null;
        lastRayTraceResult = null;
        lastWand = null;
        lastPlacementCount = 0;
        lastDirectionMode = null;
    }
}
