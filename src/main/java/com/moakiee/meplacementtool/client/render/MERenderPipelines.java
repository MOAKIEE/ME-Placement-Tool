package com.moakiee.meplacementtool.client.render;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import com.moakiee.meplacementtool.MEPlacementToolMod;

/**
 * Custom render pipelines for the in-world placement previews.
 *
 * <p>Mirrors AE2's {@code appeng.client.render.AERenderPipelines}: the placement tools draw
 * wireframe outlines that should also be visible through solid blocks, which needs a line
 * pipeline with an inverted depth test. Every custom pipeline must be registered through
 * {@code RegisterRenderPipelinesEvent} (see {@link MEPlacementToolMod.ClientModEvents}).
 */
public final class MERenderPipelines {

    private MERenderPipelines() {
    }

    /**
     * Like {@link RenderPipelines#LINES}, but with an inverted depth test so the lines are
     * drawn where they are occluded by geometry (the "see-through" outline pass).
     */
    public static final RenderPipeline LINES_BEHIND_BLOCK = RenderPipelines.LINES.toBuilder()
            .withLocation(Identifier.fromNamespaceAndPath(MEPlacementToolMod.MODID, "pipeline/lines_behind_block"))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN, false))
            .build();
}
