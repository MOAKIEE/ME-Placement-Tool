package com.moakiee.meplacementtool.client;

import com.moakiee.meplacementtool.BasePlacementToolItem;
import com.moakiee.meplacementtool.ItemMECablePlacementTool;
import com.moakiee.meplacementtool.ItemMultiblockPlacementTool;
import com.moakiee.meplacementtool.MEPlacementToolMod;
import com.moakiee.meplacementtool.network.OpenCableToolGuiPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Handler for radial menu and GUI key functionality.
 * Opens the radial menu when G is pressed while holding ME Placement Tool or Multiblock Placement Tool.
 * Opens the GUI screen when G is pressed while holding ME Cable Placement Tool.
 */
public class RadialMenuKeyHandler {
    private boolean wasRadialPressed = false;
    private boolean wasCableGuiPressed = false;

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            wasRadialPressed = false;
            wasCableGuiPressed = false;
            return;
        }

        var mainHandItem = mc.player.getMainHandItem();
        
        // Handle Cable Placement Tool - opens GUI directly
        if (mainHandItem.getItem() instanceof ItemMECablePlacementTool) {
            boolean isPressed = ModKeyBindings.OPEN_CABLE_TOOL_GUI.isDown();
            if (isPressed && !wasCableGuiPressed) {
                // Send packet to server to open the menu
                PacketDistributor.sendToServer(new OpenCableToolGuiPayload());
            }
            wasCableGuiPressed = isPressed;
            return;
        }
        
        // Handle other placement tools - open radial menu
        if (!(mainHandItem.getItem() instanceof BasePlacementToolItem)) {
            wasRadialPressed = false;
            return;
        }

        boolean isPressed = ModKeyBindings.OPEN_RADIAL_MENU.isDown();

        if (isPressed && !wasRadialPressed) {
            // Open radial menu based on tool type
            if (mainHandItem.getItem() instanceof ItemMultiblockPlacementTool) {
                mc.setScreen(new DualLayerRadialMenuScreen());
            } else {
                mc.setScreen(new RadialMenuScreen());
            }
        }

        wasRadialPressed = isPressed;
    }
}
