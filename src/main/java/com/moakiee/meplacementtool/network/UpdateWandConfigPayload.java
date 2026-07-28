package com.moakiee.meplacementtool.network;

import com.moakiee.meplacementtool.BasePlacementToolItem;
import com.moakiee.meplacementtool.MEPlacementToolMod;
import com.moakiee.meplacementtool.ModDataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Payload for updating wand configuration from client to server
 */
public record UpdateWandConfigPayload(CompoundTag tag) implements CustomPacketPayload {
    private static final int TOTAL_SLOTS = 18;

    public static final Type<UpdateWandConfigPayload> TYPE = 
            new Type<>(ResourceLocation.fromNamespaceAndPath(MEPlacementToolMod.MODID, "update_wand_config"));

    public static final StreamCodec<FriendlyByteBuf, UpdateWandConfigPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.COMPOUND_TAG,
                    UpdateWandConfigPayload::tag,
                    UpdateWandConfigPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(UpdateWandConfigPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            // Wand config applies to any placement tool subclass (ME or multiblock); allow either hand.
            ItemStack stack = BasePlacementToolItem.findHeldTool(player, BasePlacementToolItem.class);
            if (stack.isEmpty()) return;

            // Never trust the raw client tag: rebuild it from a whitelist of known keys
            // to prevent NBT bombs from being persisted and broadcast.
            stack.set(ModDataComponents.PLACEMENT_CONFIG.get(), sanitize(payload.tag, player));
        });
    }

    /**
     * Rebuild a placement config tag accepting only whitelisted keys with bounded content.
     */
    static CompoundTag sanitize(CompoundTag raw, ServerPlayer player) {
        CompoundTag clean = new CompoundTag();
        if (raw == null) {
            return clean;
        }

        var registries = player.level().registryAccess();

        // items: round-trip through a fixed-size ItemStackHandler, one item per slot
        if (raw.contains("items", CompoundTag.TAG_COMPOUND)) {
            ItemStackHandler handler = new ItemStackHandler(TOTAL_SLOTS);
            try {
                // ItemStackHandler trusts the serialized Size and reallocates its backing
                // list. Never pass the client-controlled value through: huge sizes can OOM,
                // while sizes below TOTAL_SLOTS make the normalization loop go out of bounds.
                CompoundTag rawItems = raw.getCompound("items").copy();
                rawItems.putInt("Size", TOTAL_SLOTS);
                handler.deserializeNBT(registries, rawItems);
            } catch (Exception ignored) {
                handler = new ItemStackHandler(TOTAL_SLOTS);
            }
            ItemStackHandler normalized = new ItemStackHandler(TOTAL_SLOTS);
            for (int i = 0; i < TOTAL_SLOTS; i++) {
                ItemStack s = handler.getStackInSlot(i);
                if (!s.isEmpty()) {
                    normalized.setStackInSlot(i, s.copyWithCount(1));
                }
            }
            clean.put("items", normalized.serializeNBT(registries));
        }

        // fluids: map of slot index -> fluid id string (must be valid resource locations)
        if (raw.contains("fluids", CompoundTag.TAG_COMPOUND)) {
            CompoundTag rawFluids = raw.getCompound("fluids");
            CompoundTag cleanFluids = new CompoundTag();
            for (String key : rawFluids.getAllKeys()) {
                int slot;
                try {
                    slot = Integer.parseInt(key);
                } catch (NumberFormatException e) {
                    continue;
                }
                if (slot < 0 || slot >= TOTAL_SLOTS) continue;
                String fluidId = rawFluids.getString(key);
                if (!fluidId.isEmpty() && ResourceLocation.tryParse(fluidId) != null) {
                    cleanFluids.putString(key, fluidId);
                }
            }
            clean.put("fluids", cleanFluids);
        }

        if (raw.contains("SelectedSlot")) {
            clean.putInt("SelectedSlot", Math.max(0, Math.min(TOTAL_SLOTS - 1, raw.getInt("SelectedSlot"))));
        }
        if (raw.contains("PlacementCount")) {
            // Value is further whitelist-filtered on read; just bound it here.
            clean.putInt("PlacementCount", Math.max(1, Math.min(1024, raw.getInt("PlacementCount"))));
        }
        if (raw.contains("DirectionMode")) {
            clean.putInt("DirectionMode", Math.max(0, raw.getInt("DirectionMode")));
        }

        return clean;
    }
}
