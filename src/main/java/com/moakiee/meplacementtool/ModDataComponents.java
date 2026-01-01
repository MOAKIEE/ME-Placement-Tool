package com.moakiee.meplacementtool;

import com.mojang.serialization.Codec;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;

/**
 * Data Components for ME Placement Tool items.
 * Replaces NBT tag-based storage in 1.21.1+
 */
public class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MEPlacementToolMod.MODID);

    /**
     * Stores the placement configuration (items, fluids, selected slot) for placement tools.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CompoundTag>> PLACEMENT_CONFIG =
            DATA_COMPONENTS.register("placement_config", () ->
                    DataComponentType.<CompoundTag>builder()
                            .persistent(CompoundTag.CODEC)
                            .networkSynchronized(ByteBufCodecs.COMPOUND_TAG)
                            .build()
            );

    /**
     * Stores the linked wireless access point position for placement tools.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GlobalPos>> LINKED_POSITION =
            DATA_COMPONENTS.register("linked_position", () ->
                    DataComponentType.<GlobalPos>builder()
                            .persistent(GlobalPos.CODEC)
                            .networkSynchronized(GlobalPos.STREAM_CODEC)
                            .build()
            );

    /**
     * Stores the current page index for multi-page placement tools.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> CURRENT_PAGE =
            DATA_COMPONENTS.register("current_page", () ->
                    DataComponentType.<Integer>builder()
                            .persistent(Codec.INT)
                            .networkSynchronized(ByteBufCodecs.INT)
                            .build()
            );

    /**
     * Stores the selected slot index for placement tools.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> SELECTED_SLOT =
            DATA_COMPONENTS.register("selected_slot", () ->
                    DataComponentType.<Integer>builder()
                            .persistent(Codec.INT)
                            .networkSynchronized(ByteBufCodecs.INT)
                            .build()
            );

    public static void register(IEventBus modEventBus) {
        DATA_COMPONENTS.register(modEventBus);
    }
}
