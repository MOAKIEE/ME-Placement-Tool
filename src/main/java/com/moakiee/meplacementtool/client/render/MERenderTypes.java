package com.moakiee.meplacementtool.client.render;

import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

/**
 * Custom render types for the in-world placement previews.
 *
 * <p>Mirrors AE2's {@code appeng.client.render.AERenderTypes#LINES_BEHIND_BLOCK}.
 */
public final class MERenderTypes {

    private MERenderTypes() {
    }

    /**
     * Like {@code RenderTypes.lines()}, but with inverted depth test so the outline is drawn
     * behind solid blocks (see-through pass). Backed by {@link MERenderPipelines#LINES_BEHIND_BLOCK}.
     */
    public static final RenderType LINES_BEHIND_BLOCK = RenderType.create(
            "meplacementtool:lines_behind_block",
            RenderSetup.builder(MERenderPipelines.LINES_BEHIND_BLOCK)
                    .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                    .createRenderSetup());
}
