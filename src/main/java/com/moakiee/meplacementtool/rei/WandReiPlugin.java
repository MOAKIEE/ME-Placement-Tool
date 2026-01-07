package com.moakiee.meplacementtool.rei;

import com.moakiee.meplacementtool.client.CableToolScreen;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.screen.ExclusionZones;
import me.shedaniel.rei.api.client.registry.screen.ScreenRegistry;
import me.shedaniel.rei.forge.REIPluginClient;

import java.util.Collections;

/**
 * REI plugin for ME Placement Tool.
 * Provides ghost ingredient drag support for WandScreen and hides REI when CableToolScreen is open.
 */
@REIPluginClient
public class WandReiPlugin implements REIClientPlugin {
    
    @Override
    public void registerScreens(ScreenRegistry registry) {
        // Register ghost ingredient handler for drag and drop support
        registry.registerDraggableStackVisitor(new WandGhostIngredientHandler());
    }
    
    @Override
    public void registerExclusionZones(ExclusionZones zones) {
        // Hide REI overlay when CableToolScreen is open by providing a massive exclusion zone
        zones.register(CableToolScreen.class, screen -> 
            Collections.singletonList(new Rectangle(0, 0, 10000, 10000)));
    }
}
