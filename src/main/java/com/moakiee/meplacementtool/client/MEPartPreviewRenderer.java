package com.moakiee.meplacementtool.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.items.ItemStackHandler;

import appeng.api.implementations.items.IFacadeItem;
import appeng.api.parts.IFacadePart;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartItem;
import appeng.parts.BusCollisionHelper;
import appeng.parts.PartPlacement;

import com.moakiee.meplacementtool.MEPlacementToolMod;
import com.moakiee.meplacementtool.ModDataComponents;
import com.moakiee.meplacementtool.NbtCompat;
import com.moakiee.meplacementtool.client.render.MERenderTypes;

/**
 * Renders an in-world placement preview for the ME Placement Tool when its configured item is an
 * AE2 part (cable, panel, quartz fiber, ...) or a facade. Follows AE2's {@code RenderBlockOutlineHook}.
 *
 * <p>Ported to NeoForge 26.1's block-outline extraction pipeline: instead of drawing in
 * {@code RenderHighlightEvent.Block}, a {@code CustomBlockOutlineRenderer} is registered during
 * {@link ExtractBlockOutlineRenderStateEvent}, and the boxes are drawn with
 * {@code ShapeRenderer.renderShape} (no more hand-copied {@code LevelRenderer.renderShape}).
 */
public class MEPartPreviewRenderer {
    private MEPartPreviewRenderer() {
    }

    // Preview box color - same as MultiblockPreviewRenderer (cyan/blue)
    private static final float PREVIEW_RED = 0.0f;
    private static final float PREVIEW_GREEN = 0.75f;
    private static final float PREVIEW_BLUE = 1.0f;

    public static void install() {
        // HIGH priority preserves the original intent of running before AE2's own handler.
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, MEPartPreviewRenderer::extractBlockOutline);
    }

    private static void extractBlockOutline(ExtractBlockOutlineRenderStateEvent evt) {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        // Only when holding the ME Placement Tool.
        ItemStack wand = player.getMainHandItem();
        if (wand.isEmpty() || wand.getItem() != MEPlacementToolMod.ME_PLACEMENT_TOOL.get()) {
            return;
        }

        // Resolve the configured item the tool would place.
        ItemStack targetItem = getConfiguredItem(wand);
        if (targetItem == null || targetItem.isEmpty()) {
            return;
        }

        if (!(evt.getHitResult() instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }

        if (targetItem.getItem() instanceof IPartItem<?> partItem) {
            var placement = PartPlacement.getPartPlacement(player,
                    player.level(),
                    targetItem,
                    evt.getBlockPos(),
                    hit.getDirection(),
                    hit.getLocation());
            if (placement == null) {
                return;
            }
            IPart part = partItem.createPart();
            Direction side = placement.side();
            Vec3 cameraRelativePos = new Vec3(
                    placement.pos().getX() - evt.getCamera().position().x,
                    placement.pos().getY() - evt.getCamera().position().y,
                    placement.pos().getZ() - evt.getCamera().position().z);

            evt.addCustomRenderer((outlineState, bufferSource, poseStack, translucentPass, levelState) -> {
                // Render without depth test first to also preview parts inside blocks.
                renderPart(poseStack, bufferSource, cameraRelativePos, part, side, true, true);
                renderPart(poseStack, bufferSource, cameraRelativePos, part, side, true, false);
                return true; // replace the vanilla block outline (matches 1.21.1 behavior)
            });
        } else if (targetItem.getItem() instanceof IFacadeItem facadeItem) {
            Direction side = hit.getDirection();
            IFacadePart facade = facadeItem.createPartFromItemStack(targetItem, side);
            if (facade == null) {
                return;
            }
            var pos = evt.getBlockPos();
            Vec3 cameraRelativePos = new Vec3(
                    pos.getX() - evt.getCamera().position().x,
                    pos.getY() - evt.getCamera().position().y,
                    pos.getZ() - evt.getCamera().position().z);

            evt.addCustomRenderer((outlineState, bufferSource, poseStack, translucentPass, levelState) -> {
                renderFacade(poseStack, bufferSource, cameraRelativePos, facade, side);
                return true;
            });
        }
    }

    /**
     * Get the currently configured item from the wand's data component (client-side), mirroring the
     * radial-menu screens' use of {@link NbtCompat#readItemStackHandler}.
     */
    private static ItemStack getConfiguredItem(ItemStack wand) {
        CompoundTag cfg = wand.get(ModDataComponents.PLACEMENT_CONFIG.get());
        if (cfg == null) {
            return ItemStack.EMPTY;
        }

        int selected = cfg.getIntOr("SelectedSlot", 0);
        if (selected < 0 || selected >= 18) {
            selected = 0;
        }

        var level = Minecraft.getInstance().level;
        if (level == null) {
            return ItemStack.EMPTY;
        }

        ItemStackHandler handler = NbtCompat.readItemStackHandler(
                level.registryAccess(), cfg.getCompoundOrEmpty("items"), 18);
        ItemStack target = handler.getStackInSlot(selected);
        if (target == null || target.isEmpty()) {
            return ItemStack.EMPTY;
        }

        // Unwrap AE wrapped stacks (GenericStack -> AEItemKey).
        try {
            var unwrapped = appeng.api.stacks.GenericStack.unwrapItemStack(target);
            if (unwrapped != null && unwrapped.what() instanceof appeng.api.stacks.AEItemKey itemKey) {
                return itemKey.toStack();
            }
        } catch (Throwable ignored) {
        }

        return target;
    }

    private static void renderFacade(PoseStack poseStack,
            MultiBufferSource buffers,
            Vec3 cameraRelativePos,
            IFacadePart facade,
            Direction side) {
        var boxes = new ArrayList<AABB>();
        var helper = new BusCollisionHelper(boxes, side, true);
        facade.getBoxes(helper, false);
        renderBoxes(poseStack, buffers, cameraRelativePos, boxes, true, false);
    }

    private static void renderPart(PoseStack poseStack,
            MultiBufferSource buffers,
            Vec3 cameraRelativePos,
            IPart part,
            Direction side,
            boolean preview,
            boolean insideBlock) {
        var boxes = new ArrayList<AABB>();
        var helper = new BusCollisionHelper(boxes, side, true);
        part.getBoxes(helper);
        renderBoxes(poseStack, buffers, cameraRelativePos, boxes, preview, insideBlock);
    }

    private static void renderBoxes(PoseStack poseStack,
            MultiBufferSource buffers,
            Vec3 cameraRelativePos,
            List<AABB> boxes,
            boolean preview,
            boolean insideBlock) {
        RenderType renderType = insideBlock ? MERenderTypes.LINES_BEHIND_BLOCK : RenderTypes.lines();
        var buffer = buffers.getBuffer(renderType);
        float alpha = insideBlock ? 0.2f : preview ? 0.6f : 0.4f;
        int color = ARGB.colorFromFloat(alpha, PREVIEW_RED, PREVIEW_GREEN, PREVIEW_BLUE);

        for (var box : boxes) {
            var shape = Shapes.create(box);
            ShapeRenderer.renderShape(
                    poseStack,
                    buffer,
                    shape,
                    cameraRelativePos.x,
                    cameraRelativePos.y,
                    cameraRelativePos.z,
                    color,
                    7 /* line width */);
        }
    }
}
