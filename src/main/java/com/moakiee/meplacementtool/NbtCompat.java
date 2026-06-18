package com.moakiee.meplacementtool;

import com.mojang.serialization.DataResult;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Small bridge for Minecraft 26.1's value-IO/codec based NBT APIs.
 */
public final class NbtCompat {
    private NbtCompat() {
    }

    public static ItemStack parseItemStack(HolderLookup.Provider lookup, CompoundTag tag) {
        DataResult<ItemStack> result = ItemStack.OPTIONAL_CODEC.parse(
                lookup.createSerializationContext(NbtOps.INSTANCE), tag);
        return result.result().orElse(ItemStack.EMPTY);
    }

    public static ItemStackHandler readItemStackHandler(HolderLookup.Provider lookup, CompoundTag tag, int size) {
        ItemStackHandler handler = new ItemStackHandler(size);
        handler.deserialize(TagValueInput.create(ProblemReporter.DISCARDING, lookup, tag));
        return handler;
    }

    public static CompoundTag writeItemStackHandler(HolderLookup.Provider lookup, ItemStackHandler handler) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, lookup);
        handler.serialize(output);
        return output.buildResult();
    }
}
