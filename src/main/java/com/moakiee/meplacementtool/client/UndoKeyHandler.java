package com.moakiee.meplacementtool.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.moakiee.meplacementtool.MEPlacementToolMod;
import com.moakiee.meplacementtool.network.ModNetwork;
import com.moakiee.meplacementtool.network.UndoPacket;

public class UndoKeyHandler
{
    @SubscribeEvent
    public void onMouseButton(InputEvent.MouseButton.Pre event) {
        Player player = Minecraft.getInstance().player;
        if(player == null) return;

        ItemStack mainHand = player.getMainHandItem();
        if(mainHand.isEmpty() || (mainHand.getItem() != MEPlacementToolMod.MULTIBLOCK_PLACEMENT_TOOL.get())) return;

        if(event.getButton() == 0 && event.getAction() == InputConstants.PRESS) {
            boolean ctrlPressed = InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), 341) ||
                                 InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), 345);
            if(ctrlPressed) {
                HitResult hitResult = Minecraft.getInstance().hitResult;
                if(hitResult != null && hitResult.getType() == HitResult.Type.BLOCK) {
                    BlockHitResult blockHitResult = (BlockHitResult) hitResult;
                    ModNetwork.CHANNEL.sendToServer(new UndoPacket(blockHitResult.getBlockPos()));
                    event.setCanceled(true);
                }
            }
        }
    }
}
