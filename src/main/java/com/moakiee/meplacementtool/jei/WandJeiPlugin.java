package com.moakiee.meplacementtool.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.resources.ResourceLocation;

import com.moakiee.meplacementtool.MEPlacementToolMod;
import com.moakiee.meplacementtool.WandScreen;

/**
 * JEI plugin for ME Placement Tool
 */
@JeiPlugin
public class WandJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = 
            ResourceLocation.fromNamespaceAndPath(MEPlacementToolMod.MODID, "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(WandScreen.class, new WandGhostHandler());
    }
}
