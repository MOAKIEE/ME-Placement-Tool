package com.moakiee.meplacementtool.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

/**
 * Key bindings for ME Placement Tool
 */
public class ModKeyBindings {
    public static final String CATEGORY = "key.meplacementtool.category";

    public static final KeyMapping OPEN_RADIAL_MENU = new KeyMapping(
            "key.meplacementtool.radial_menu",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_G,
            CATEGORY
    );

    public static final KeyMapping UNDO_MODIFIER = new KeyMapping(
            "key.meplacementtool.undo",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_LCONTROL,
            CATEGORY
    );
}
