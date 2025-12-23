package com.moakiee.meplacementtool.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.moakiee.meplacementtool.ItemMultiblockPlacementTool;
import com.moakiee.meplacementtool.MEPlacementToolMod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

public class MultiblockPreviewRenderer
{
    private BlockHitResult lastRayTraceResult;
    private ItemStack lastWand;
    private Set<BlockPos> cachedPositions;
    private int lastPlacementCount;

    @SubscribeEvent
    public void renderBlockHighlight(RenderHighlightEvent.Block event) {
        if(event.getTarget().getType() != HitResult.Type.BLOCK) return;

        BlockHitResult rtr = event.getTarget();
        Entity entity = event.getCamera().getEntity();
        if(!(entity instanceof Player player)) return;

        ItemStack wand = player.getMainHandItem();
        if(wand.isEmpty() || wand.getItem() != MEPlacementToolMod.MULTIBLOCK_PLACEMENT_TOOL.get()) return;

        int placementCount = ItemMultiblockPlacementTool.getPlacementCount(wand);

        Set<BlockPos> blocks;
        if(cachedPositions == null || !compareRTR(lastRayTraceResult, rtr) || !lastWand.equals(wand) || lastPlacementCount != placementCount) {
            blocks = calculatePlacementPositions(player, rtr, wand, placementCount);
            cachedPositions = blocks;
            lastRayTraceResult = rtr;
            lastWand = wand.copy();
            lastPlacementCount = placementCount;
        } else {
            blocks = cachedPositions;
        }

        if(blocks == null || blocks.isEmpty()) return;

        PoseStack ms = event.getPoseStack();
        MultiBufferSource buffer = event.getMultiBufferSource();
        VertexConsumer lineBuilder = buffer.getBuffer(RenderType.LINES);

        double partialTicks = event.getPartialTick();
        double d0 = player.xOld + (player.getX() - player.xOld) * partialTicks;
        double d1 = player.yOld + player.getEyeHeight() + (player.getY() - player.yOld) * partialTicks;
        double d2 = player.zOld + (player.getZ() - player.zOld) * partialTicks;

        for(BlockPos block : blocks) {
            AABB aabb = new AABB(block).move(-d0, -d1, -d2);
            LevelRenderer.renderLineBox(ms, lineBuilder, aabb, 0.0F, 0.75F, 1.0F, 0.4F);
        }

        event.setCanceled(true);
    }

    private Set<BlockPos> calculatePlacementPositions(Player player, BlockHitResult rtr, ItemStack wand, int placementCount) {
        Set<BlockPos> placePositions = new HashSet<>();
        if(placementCount <= 0) return placePositions;

        var level = player.level();
        BlockPos clickedPos = rtr.getBlockPos();
        var clickedFace = rtr.getDirection();
        var clickedState = level.getBlockState(clickedPos);

        LinkedList<BlockPos> candidates = new LinkedList<>();
        Set<BlockPos> allCandidates = new HashSet<>();
        ArrayList<BlockPos> positions = new ArrayList<>();

        BlockPos startingPoint = clickedPos.relative(clickedFace);
        candidates.add(startingPoint);

        while(!candidates.isEmpty() && positions.size() < placementCount) {
            BlockPos currentCandidate = candidates.removeFirst();
            if(!allCandidates.add(currentCandidate)) {
                continue;
            }

            BlockPos supportingPoint = currentCandidate.relative(clickedFace.getOpposite());
            var supportingState = level.getBlockState(supportingPoint);

            if(supportingState.getBlock() == clickedState.getBlock()) {
                var currentState = level.getBlockState(currentCandidate);
                boolean canPlace = level.isEmptyBlock(currentCandidate);
                if(!canPlace) {
                    try {
                        var checkContext = new net.minecraft.world.item.context.BlockPlaceContext(new net.minecraft.world.item.context.UseOnContext(
                            player, player.getUsedItemHand(), new net.minecraft.world.phys.BlockHitResult(
                                rtr.getLocation(), rtr.getDirection(), currentCandidate, rtr.isInside()
                            )
                        ));
                        canPlace = currentState.canBeReplaced(checkContext);
                    } catch(Throwable t) {}
                }
                if(canPlace) {
                    positions.add(currentCandidate);
                }

                switch(clickedFace) {
                    case DOWN:
                    case UP:
                        candidates.add(currentCandidate.north());
                        candidates.add(currentCandidate.south());
                        candidates.add(currentCandidate.east());
                        candidates.add(currentCandidate.west());
                        candidates.add(currentCandidate.north().east());
                        candidates.add(currentCandidate.north().west());
                        candidates.add(currentCandidate.south().east());
                        candidates.add(currentCandidate.south().west());
                        break;
                    case NORTH:
                    case SOUTH:
                        candidates.add(currentCandidate.above());
                        candidates.add(currentCandidate.below());
                        candidates.add(currentCandidate.east());
                        candidates.add(currentCandidate.west());
                        candidates.add(currentCandidate.above().east());
                        candidates.add(currentCandidate.above().west());
                        candidates.add(currentCandidate.below().east());
                        candidates.add(currentCandidate.below().west());
                        break;
                    case EAST:
                    case WEST:
                        candidates.add(currentCandidate.above());
                        candidates.add(currentCandidate.below());
                        candidates.add(currentCandidate.north());
                        candidates.add(currentCandidate.south());
                        candidates.add(currentCandidate.above().north());
                        candidates.add(currentCandidate.above().south());
                        candidates.add(currentCandidate.below().north());
                        candidates.add(currentCandidate.below().south());
                        break;
                }
            }
        }

        placePositions.addAll(positions);
        return placePositions;
    }

    private static boolean compareRTR(BlockHitResult rtr1, BlockHitResult rtr2) {
        if(rtr1 == null || rtr2 == null) return false;
        return rtr1.getBlockPos().equals(rtr2.getBlockPos()) && rtr1.getDirection().equals(rtr2.getDirection());
    }

    public void reset() {
        cachedPositions = null;
        lastRayTraceResult = null;
        lastWand = null;
        lastPlacementCount = 0;
    }
}
