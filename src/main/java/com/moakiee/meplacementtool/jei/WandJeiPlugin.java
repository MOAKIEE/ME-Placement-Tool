package com.moakiee.meplacementtool.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;

import com.moakiee.meplacementtool.MEPlacementToolMod;
import com.moakiee.meplacementtool.WandScreen;
import com.moakiee.meplacementtool.client.CableToolScreen;

import java.util.Collections;
import java.util.List;

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
        
        // Hide JEI overlay when CableToolScreen is open by providing a massive exclusion zone
        registration.addGuiContainerHandler(CableToolScreen.class, new IGuiContainerHandler<CableToolScreen>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(CableToolScreen screen) {
                // Return exclusion zone that covers entire screen to hide JEI
                return Collections.singletonList(new Rect2i(0, 0, 10000, 10000));
            }
        });
    }
}
