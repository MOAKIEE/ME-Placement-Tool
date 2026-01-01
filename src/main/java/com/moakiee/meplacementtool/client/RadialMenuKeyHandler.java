package com.moakiee.meplacementtool.client;

import com.moakiee.meplacementtool.BasePlacementToolItem;
import com.moakiee.meplacementtool.ItemMEPlacementTool;
import com.moakiee.meplacementtool.ItemMultiblockPlacementTool;
import com.moakiee.meplacementtool.MEPlacementToolMod;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Handler for radial menu key functionality
 */
public class RadialMenuKeyHandler {
    private boolean wasPressed = false;

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            return;
        }

        // Check if holding a placement tool
        var mainHandItem = mc.player.getMainHandItem();
        if (!(mainHandItem.getItem() instanceof BasePlacementToolItem)) {
            return;
        }

        boolean isPressed = ModKeyBindings.OPEN_RADIAL_MENU.isDown();

        if (isPressed && !wasPressed) {
            // Open radial menu
            if (mainHandItem.getItem() instanceof ItemMultiblockPlacementTool) {
                mc.setScreen(new DualLayerRadialMenuScreen());
            } else {
                mc.setScreen(new RadialMenuScreen());
            }
        }

        wasPressed = isPressed;
    }
}
