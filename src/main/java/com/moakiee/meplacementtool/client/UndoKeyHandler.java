package com.moakiee.meplacementtool.client;

import com.moakiee.meplacementtool.MEPlacementToolMod;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import com.moakiee.meplacementtool.network.UndoPayload;
import org.lwjgl.glfw.GLFW;

/**
 * Handler for undo key functionality.
 * Matches 1.20.1 behavior - UNDO_MODIFIER + left click to undo.
 */
public class UndoKeyHandler {

    @SubscribeEvent
    public void onMouseButton(InputEvent.MouseButton.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            return;
        }

        // Check if holding a multiblock placement tool
        var mainHandItem = mc.player.getMainHandItem();
        if (mainHandItem.isEmpty() || mainHandItem.getItem() != MEPlacementToolMod.MULTIBLOCK_PLACEMENT_TOOL.get()) {
            return;
        }

        // Left click + UNDO_MODIFIER
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT && event.getAction() == GLFW.GLFW_PRESS) {
            if (ModKeyBindings.UNDO_MODIFIER.isDown()) {
                HitResult hitResult = mc.hitResult;
                if (hitResult != null && hitResult.getType() == HitResult.Type.BLOCK) {
                    BlockHitResult blockHitResult = (BlockHitResult) hitResult;
                    PacketDistributor.sendToServer(new UndoPayload(blockHitResult.getBlockPos()));
                    event.setCanceled(true);
                }
            }
        }
    }
}
