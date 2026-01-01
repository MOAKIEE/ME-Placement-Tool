package com.moakiee.meplacementtool.network;

import com.moakiee.meplacementtool.MEPlacementToolMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Payload for undo action from client to server.
 * Includes the BlockPos that the player is looking at for range checking.
 */
public record UndoPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<UndoPayload> TYPE = 
            new Type<>(ResourceLocation.fromNamespaceAndPath(MEPlacementToolMod.MODID, "undo"));

    public static final StreamCodec<FriendlyByteBuf, UndoPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeBlockPos(payload.pos),
            buf -> new UndoPayload(buf.readBlockPos())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(UndoPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                // Perform undo action with position for range checking
                if (MEPlacementToolMod.instance != null && MEPlacementToolMod.instance.undoHistory != null) {
                    MEPlacementToolMod.instance.undoHistory.undo(player, player.level(), payload.pos);
                }
            }
        });
    }
}
