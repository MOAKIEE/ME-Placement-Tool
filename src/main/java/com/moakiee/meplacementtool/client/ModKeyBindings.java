package com.moakiee.meplacementtool.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * Key bindings for the ME Placement Tool mod.
 */
public class ModKeyBindings {
    public static final String CATEGORY = "key.categories.meplacementtool";

    public static final KeyMapping OPEN_RADIAL_MENU = new KeyMapping(
            "key.meplacementtool.open_radial_menu",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            CATEGORY
    );
}
