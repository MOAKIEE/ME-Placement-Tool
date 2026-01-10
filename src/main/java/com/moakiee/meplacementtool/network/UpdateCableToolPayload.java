package com.moakiee.meplacementtool.network;

import com.moakiee.meplacementtool.ItemMECablePlacementTool;
import com.moakiee.meplacementtool.MEPlacementToolMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import appeng.api.util.AEColor;

/**
 * Payload for updating cable tool settings from client to server
 */
public record UpdateCableToolPayload(int mode, int cableType, int color) implements CustomPacketPayload {
    public static final Type<UpdateCableToolPayload> TYPE = 
            new Type<>(ResourceLocation.fromNamespaceAndPath(MEPlacementToolMod.MODID, "update_cable_tool"));

    public static final StreamCodec<FriendlyByteBuf, UpdateCableToolPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT,
                    UpdateCableToolPayload::mode,
                    ByteBufCodecs.INT,
                    UpdateCableToolPayload::cableType,
                    ByteBufCodecs.INT,
                    UpdateCableToolPayload::color,
                    UpdateCableToolPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(UpdateCableToolPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ItemStack main = player.getMainHandItem();
                ItemStack off = player.getOffhandItem();
                
                ItemStack tool = null;
                if (main.getItem() instanceof ItemMECablePlacementTool) {
                    tool = main;
                } else if (off.getItem() instanceof ItemMECablePlacementTool) {
                    tool = off;
                }
                
                if (tool != null) {
                    if (payload.mode >= 0 && payload.mode < ItemMECablePlacementTool.PlacementMode.values().length) {
                        ItemMECablePlacementTool.setMode(tool, ItemMECablePlacementTool.PlacementMode.values()[payload.mode]);
                    }
                    if (payload.cableType >= 0 && payload.cableType < ItemMECablePlacementTool.CableType.values().length) {
                        ItemMECablePlacementTool.setCableType(tool, ItemMECablePlacementTool.CableType.values()[payload.cableType]);
                    }
                    if (payload.color >= 0 && payload.color < AEColor.values().length) {
                        ItemMECablePlacementTool.setColor(tool, AEColor.values()[payload.color]);
                    }
                }
            }
        });
    }
}
