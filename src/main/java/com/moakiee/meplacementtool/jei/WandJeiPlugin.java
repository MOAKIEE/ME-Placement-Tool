package com.moakiee.meplacementtool.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.resources.ResourceLocation;

@JeiPlugin
public class WandJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = new ResourceLocation("meplacementtool", "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(com.moakiee.meplacementtool.WandScreen.class,
                new WandGhostHandler());
    }
}
