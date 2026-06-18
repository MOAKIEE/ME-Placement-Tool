package com.moakiee.meplacementtool.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GuiTextColorsTest {

    @Test
    void convertsLegacyRgbTextColorsToOpaqueArgb() {
        assertEquals(0xFFFFFFFF, GuiTextColors.opaque(0xFFFFFF));
        assertEquals(0xFF404040, GuiTextColors.opaque(0x404040));
        assertEquals(0xFF000000, GuiTextColors.opaque(0x000000));
    }

    @Test
    void preservesExistingArgbTextColors() {
        assertEquals(0xFFFF5555, GuiTextColors.opaque(0xFFFF5555));
        assertEquals(0xAAFFFFFF, GuiTextColors.opaque(0xAAFFFFFF));
    }
}
