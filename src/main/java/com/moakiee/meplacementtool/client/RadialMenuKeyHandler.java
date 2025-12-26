package com.moakiee.meplacementtool.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.moakiee.meplacementtool.MEPlacementToolMod;

/**
 * Event handler for radial menu key binding.
 * Opens the radial menu when G is pressed while holding ME Placement Tool or Multiblock Placement Tool.
 */
public class RadialMenuKeyHandler {

    @SubscribeEvent
    public void onKeyInput(InputEvent.Key event) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        // Only respond to key press (not release or repeat)
        if (event.getAction() != InputConstants.PRESS) return;

        // Check if the radial menu key is pressed
        if (!ModKeyBindings.OPEN_RADIAL_MENU.matches(event.getKey(), event.getScanCode())) return;

        // Don't open if another screen is already open
        if (Minecraft.getInstance().screen != null) return;

        // Check which tool player is holding
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.isEmpty()) return;

        int openKey = ModKeyBindings.OPEN_RADIAL_MENU.getKey().getValue();

        if (mainHand.getItem() == MEPlacementToolMod.ME_PLACEMENT_TOOL.get()) {
            // Open single-layer radial menu for ME Placement Tool
            Minecraft.getInstance().setScreen(new RadialMenuScreen(openKey));
        } else if (mainHand.getItem() == MEPlacementToolMod.MULTIBLOCK_PLACEMENT_TOOL.get()) {
            // Open dual-layer radial menu for Multiblock Placement Tool
            Minecraft.getInstance().setScreen(new DualLayerRadialMenuScreen(openKey));
        }
    }
}
