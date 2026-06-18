package com.moakiee.meplacementtool.client;

import net.minecraft.core.BlockPos;

/**
 * Shared color helper retained for future preview rendering.
 */
public final class RainbowRenderHelper {
    private static final float CYCLE_DURATION_MS = 3000.0f;

    private RainbowRenderHelper() {
    }

    public static float[] getTimeBasedRainbowColor() {
        float time = (System.currentTimeMillis() % (long) CYCLE_DURATION_MS) / CYCLE_DURATION_MS;
        return hsvToRgb(time, 1.0f, 1.0f);
    }

    public static float[] getRainbowColor(double x, double y, double z) {
        return getTimeBasedRainbowColor();
    }

    public static float[] getRainbowColor(BlockPos pos) {
        return getTimeBasedRainbowColor();
    }

    private static float[] hsvToRgb(float h, float s, float v) {
        int i = (int) (h * 6);
        float f = h * 6 - i;
        float p = v * (1 - s);
        float q = v * (1 - f * s);
        float t = v * (1 - (1 - f) * s);

        return switch (i % 6) {
            case 0 -> new float[] { v, t, p };
            case 1 -> new float[] { q, v, p };
            case 2 -> new float[] { p, v, t };
            case 3 -> new float[] { p, q, v };
            case 4 -> new float[] { t, p, v };
            default -> new float[] { v, p, q };
        };
    }
}
