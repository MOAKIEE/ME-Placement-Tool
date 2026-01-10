package com.moakiee.meplacementtool.network;

import com.moakiee.meplacementtool.ItemMECablePlacementTool;
import com.moakiee.meplacementtool.MEPlacementToolMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Payload to clear Cable Tool selected points from client side.
 * Used when player left-clicks (LeftClickEmpty event only fires on client).
 */
public record ClearCableToolPointsPayload(int slot) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ClearCableToolPointsPayload> TYPE = 
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MEPlacementToolMod.MODID, "clear_cable_points"));

    public static final StreamCodec<FriendlyByteBuf, ClearCableToolPointsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, ClearCableToolPointsPayload::slot,
            ClearCableToolPointsPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ClearCableToolPointsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            ItemStack stack = player.getInventory().getItem(payload.slot);
            if (stack.getItem() != MEPlacementToolMod.ME_CABLE_PLACEMENT_TOOL.get()) {
                return;
            }

            // Check if any points are set
            var p1 = ItemMECablePlacementTool.getPoint1(stack);
            var p2 = ItemMECablePlacementTool.getPoint2(stack);
            var p3 = ItemMECablePlacementTool.getPoint3(stack);

            if (p1 != null || p2 != null || p3 != null) {
                // Clear all points
                ItemMECablePlacementTool.clearAllPoints(stack);

                player.displayClientMessage(Component.translatable("message.meplacementtool.points_cleared"), true);
            }
        });
    }
}
