package com.moakiee.meplacementtool.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

/**
 * Submits radial menu geometry to the 26.1 GUI render-state pipeline.
 */
final class RadialMenuRenderer {
    private static final float PRECISION = 5.0f;

    private RadialMenuRenderer() {
    }

    static void drawSlice(GuiGraphicsExtractor graphics, float x, float y, float radiusIn, float radiusOut,
            float startAngle, float endAngle, int r, int g, int b, int a) {
        graphics.submitGuiElementRenderState(new SliceRenderState(
                new Matrix3x2f(graphics.pose()),
                graphics.peekScissorStack(),
                boundsForCircle(x, y, radiusOut, graphics.pose(), graphics.peekScissorStack()),
                x,
                y,
                radiusIn,
                radiusOut,
                startAngle,
                endAngle,
                ARGB.color(a, r, g, b)));
    }

    static void drawDivider(GuiGraphicsExtractor graphics, float x, float y, float radiusIn, float radiusOut,
            float angleDegrees, int r, int g, int b, int a) {
        graphics.submitGuiElementRenderState(new DividerRenderState(
                new Matrix3x2f(graphics.pose()),
                graphics.peekScissorStack(),
                boundsForCircle(x, y, radiusOut + 1.0f, graphics.pose(), graphics.peekScissorStack()),
                x,
                y,
                radiusIn,
                radiusOut,
                angleDegrees,
                1.0f,
                ARGB.color(a, r, g, b)));
    }

    private static @Nullable ScreenRectangle boundsForCircle(float x, float y, float radius,
            Matrix3x2fc pose, @Nullable ScreenRectangle scissorArea) {
        int left = Mth.floor(x - radius);
        int top = Mth.floor(y - radius);
        int size = Mth.ceil(radius * 2.0f);
        ScreenRectangle bounds = new ScreenRectangle(left, top, size, size).transformMaxBounds(pose);
        return scissorArea != null ? scissorArea.intersection(bounds) : bounds;
    }

    private record SliceRenderState(
            Matrix3x2fc pose,
            @Nullable ScreenRectangle scissorArea,
            @Nullable ScreenRectangle bounds,
            float x,
            float y,
            float radiusIn,
            float radiusOut,
            float startAngle,
            float endAngle,
            int color) implements GuiElementRenderState {
        @Override
        public RenderPipeline pipeline() {
            return RenderPipelines.GUI;
        }

        @Override
        public TextureSetup textureSetup() {
            return TextureSetup.noTexture();
        }

        @Override
        public void buildVertices(VertexConsumer vertexConsumer) {
            float angle = this.endAngle - this.startAngle;
            int sections = Math.max(1, Mth.ceil(Math.abs(angle) / PRECISION));

            for (int i = 0; i < sections; i++) {
                float angle1 = (float) Math.toRadians(this.startAngle + (i / (float) sections) * angle);
                float angle2 = (float) Math.toRadians(this.startAngle + ((i + 1) / (float) sections) * angle);

                float x1In = this.x + this.radiusIn * (float) Math.cos(angle1);
                float y1In = this.y + this.radiusIn * (float) Math.sin(angle1);
                float x1Out = this.x + this.radiusOut * (float) Math.cos(angle1);
                float y1Out = this.y + this.radiusOut * (float) Math.sin(angle1);
                float x2In = this.x + this.radiusIn * (float) Math.cos(angle2);
                float y2In = this.y + this.radiusIn * (float) Math.sin(angle2);
                float x2Out = this.x + this.radiusOut * (float) Math.cos(angle2);
                float y2Out = this.y + this.radiusOut * (float) Math.sin(angle2);

                vertexConsumer.addVertexWith2DPose(this.pose, x1In, y1In).setColor(this.color);
                vertexConsumer.addVertexWith2DPose(this.pose, x2In, y2In).setColor(this.color);
                vertexConsumer.addVertexWith2DPose(this.pose, x2Out, y2Out).setColor(this.color);
                vertexConsumer.addVertexWith2DPose(this.pose, x1Out, y1Out).setColor(this.color);
            }
        }
    }

    private record DividerRenderState(
            Matrix3x2fc pose,
            @Nullable ScreenRectangle scissorArea,
            @Nullable ScreenRectangle bounds,
            float x,
            float y,
            float radiusIn,
            float radiusOut,
            float angleDegrees,
            float width,
            int color) implements GuiElementRenderState {
        @Override
        public RenderPipeline pipeline() {
            return RenderPipelines.GUI;
        }

        @Override
        public TextureSetup textureSetup() {
            return TextureSetup.noTexture();
        }

        @Override
        public void buildVertices(VertexConsumer vertexConsumer) {
            float angle = (float) Math.toRadians(this.angleDegrees);
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            float halfWidth = this.width * 0.5f;
            float offsetX = -sin * halfWidth;
            float offsetY = cos * halfWidth;

            float x1 = this.x + this.radiusIn * cos;
            float y1 = this.y + this.radiusIn * sin;
            float x2 = this.x + this.radiusOut * cos;
            float y2 = this.y + this.radiusOut * sin;

            vertexConsumer.addVertexWith2DPose(this.pose, x1 + offsetX, y1 + offsetY).setColor(this.color);
            vertexConsumer.addVertexWith2DPose(this.pose, x2 + offsetX, y2 + offsetY).setColor(this.color);
            vertexConsumer.addVertexWith2DPose(this.pose, x2 - offsetX, y2 - offsetY).setColor(this.color);
            vertexConsumer.addVertexWith2DPose(this.pose, x1 - offsetX, y1 - offsetY).setColor(this.color);
        }
    }
}
