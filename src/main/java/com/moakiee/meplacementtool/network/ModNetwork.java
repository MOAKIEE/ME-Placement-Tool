package com.moakiee.meplacementtool.network;

import com.moakiee.meplacementtool.MEPlacementToolMod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Network system for ME Placement Tool using NeoForge 1.21.1 payload system
 */
public class ModNetwork {
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ModNetwork::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(MEPlacementToolMod.MODID)
                .versioned("1.0");

        // Client -> Server packets
        registrar.playToServer(
                UpdateWandConfigPayload.TYPE,
                UpdateWandConfigPayload.STREAM_CODEC,
                UpdateWandConfigPayload::handle
        );

        registrar.playToServer(
                UpdatePlacementCountPayload.TYPE,
                UpdatePlacementCountPayload.STREAM_CODEC,
                UpdatePlacementCountPayload::handle
        );

        registrar.playToServer(
                UndoPayload.TYPE,
                UndoPayload.STREAM_CODEC,
                UndoPayload::handle
        );

        registrar.playToServer(
                SyncPagePayload.TYPE,
                SyncPagePayload.STREAM_CODEC,
                SyncPagePayload::handle
        );

        registrar.playToServer(
                UpdateWandSlotPayload.TYPE,
                UpdateWandSlotPayload.STREAM_CODEC,
                UpdateWandSlotPayload::handle
        );
    }
}
