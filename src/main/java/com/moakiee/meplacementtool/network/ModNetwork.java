package com.moakiee.meplacementtool.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import com.mojang.serialization.Codec;

public class ModNetwork {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("meplacementtool", "network"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private static int id = 0;

    public static void register() {
        CHANNEL.registerMessage(id++, UpdateWandConfigPacket.class, UpdateWandConfigPacket::encode,
                UpdateWandConfigPacket::decode, UpdateWandConfigPacket::handle);
    }
}
