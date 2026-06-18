package com.moakiee.meplacementtool.client;

public final class GuiTextColors {
    private GuiTextColors() {
    }

    public static int opaque(int color) {
        return (color & 0xFF000000) == 0 ? color | 0xFF000000 : color;
    }
}
