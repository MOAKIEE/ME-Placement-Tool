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

    // ==================== Cable Tool Data Components ====================

    /**
     * Stores the placement mode for cable tool (LINE, PLANE_FILL, PLANE_BRANCHING).
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> CABLE_TOOL_MODE =
            DATA_COMPONENTS.register("cable_tool_mode", () ->
                    DataComponentType.<Integer>builder()
                            .persistent(Codec.INT)
                            .networkSynchronized(ByteBufCodecs.INT)
                            .build()
            );

    /**
     * Stores the cable type for cable tool (GLASS, COVERED, SMART, DENSE_COVERED, DENSE_SMART).
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> CABLE_TYPE =
            DATA_COMPONENTS.register("cable_type", () ->
                    DataComponentType.<Integer>builder()
                            .persistent(Codec.INT)
                            .networkSynchronized(ByteBufCodecs.INT)
                            .build()
            );

    /**
     * Stores the cable color for cable tool (AEColor ordinal).
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> CABLE_COLOR =
            DATA_COMPONENTS.register("cable_color", () ->
                    DataComponentType.<Integer>builder()
                            .persistent(Codec.INT)
                            .networkSynchronized(ByteBufCodecs.INT)
                            .build()
            );

    /**
     * Stores the selected points for cable tool (up to 3 points encoded as CompoundTag).
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CompoundTag>> CABLE_POINTS =
            DATA_COMPONENTS.register("cable_points", () ->
                    DataComponentType.<CompoundTag>builder()
                            .persistent(CompoundTag.CODEC)
                            .networkSynchronized(ByteBufCodecs.COMPOUND_TAG)
                            .build()
            );

    /**
     * Stores whether the cable tool has the Key of Spectrum upgrade.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> HAS_UPGRADE =
            DATA_COMPONENTS.register("has_upgrade", () ->
                    DataComponentType.<Boolean>builder()
                            .persistent(Codec.BOOL)
                            .networkSynchronized(ByteBufCodecs.BOOL)
                            .build()
            );

    public static void register(IEventBus modEventBus) {
        DATA_COMPONENTS.register(modEventBus);
    }
}
