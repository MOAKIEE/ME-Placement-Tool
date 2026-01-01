package com.moakiee.meplacementtool.network;

import com.moakiee.meplacementtool.MEPlacementToolMod;
import com.moakiee.meplacementtool.WandMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Payload for syncing page selection from client to server
 */
public record SyncPagePayload(int page) implements CustomPacketPayload {
    public static final Type<SyncPagePayload> TYPE = 
            new Type<>(ResourceLocation.fromNamespaceAndPath(MEPlacementToolMod.MODID, "sync_page"));

    public static final StreamCodec<FriendlyByteBuf, SyncPagePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT,
                    SyncPagePayload::page,
                    SyncPagePayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncPagePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                // Sync page to server-side menu
                if (player.containerMenu instanceof WandMenu menu) {
                    menu.setCurrentPage(payload.page);
                }
            }
        });
    }
}
