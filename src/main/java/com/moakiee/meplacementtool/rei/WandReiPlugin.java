package com.moakiee.meplacementtool.rei;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import net.minecraft.client.renderer.Rect2i;

import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.screen.ExclusionZones;
import me.shedaniel.rei.api.client.registry.screen.ScreenRegistry;
import me.shedaniel.rei.forge.REIPluginClient;

import com.moakiee.meplacementtool.client.CableToolScreen;

/**
 * REI plugin for ME Placement Tool.
 * Provides REI integration similar to JEI integration, allowing drag & drop of items
 * from REI panels into wand configuration slots.
 * 
 * References AE2's ReiPlugin implementation.
 */
@REIPluginClient
public class WandReiPlugin implements REIClientPlugin {

    @Override
    public String getPluginProviderName() {
        return "ME Placement Tool";
    }

    @Override
    public void registerScreens(ScreenRegistry registry) {
        // Register draggable stack visitor for ghost ingredient handling
        registry.registerDraggableStackVisitor(new WandGhostIngredientHandler());
    }

    @Override
    public void registerExclusionZones(ExclusionZones zones) {
        // Hide REI overlay when CableToolScreen is open by providing a massive exclusion zone
        zones.register(CableToolScreen.class, screen -> {
            if (screen != null) {
                // Return exclusion zone that covers entire screen to hide REI
                return Collections.singletonList(new Rectangle(0, 0, 10000, 10000));
            }
            return Collections.emptyList();
        });
    }
}
