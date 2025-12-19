package com.moakiee.meplacementtool.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class UpdateWandConfigPacket {
    public final CompoundTag tag;

    public UpdateWandConfigPacket(CompoundTag tag) {
        this.tag = tag == null ? new CompoundTag() : tag;
    }

    public static void encode(UpdateWandConfigPacket pkt, FriendlyByteBuf buf) {
        buf.writeNbt(pkt.tag);
    }

    public static UpdateWandConfigPacket decode(FriendlyByteBuf buf) {
        return new UpdateWandConfigPacket(buf.readNbt());
    }

    public static void handle(UpdateWandConfigPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            var main = player.getMainHandItem();
            if (main.isEmpty()) return;
            main.getOrCreateTag().put("placement_config", pkt.tag);
        });
        ctx.get().setPacketHandled(true);
    }
}
