package com.moakiee.meplacementtool;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.function.DoubleSupplier;
import java.util.function.BiConsumer;

import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.api.implementations.menuobjects.IMenuItem;
import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.api.storage.ISubMenuHost;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.menu.me.crafting.CraftAmountMenu;
import appeng.menu.ISubMenu;
import appeng.helpers.WirelessTerminalMenuHost;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.items.ItemStackHandler;

public class ItemMultiblockPlacementTool extends WirelessTerminalItem implements IMenuItem {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double ENERGY_CAPACITY = 1_600_000.0d;

    public ItemMultiblockPlacementTool(Item.Properties props) {
        super((DoubleSupplier) () -> ENERGY_CAPACITY, props);
    }

    @Override
    public ItemMenuHost getMenuHost(Player player, int inventorySlot, ItemStack itemStack, BlockPos pos) {
        return new PlacementToolMenuHost(player, inventorySlot, itemStack, (p, subMenu) -> {
        });
    }

    private void openCraftingMenu(ServerPlayer player, ItemStack wand, AEKey whatToCraft) {
        int slot = findInventorySlot(player, wand);
        if (slot < 0) {
            LOGGER.warn("Could not find wand in player inventory");
            return;
        }

        CraftAmountMenu.open(player, MenuLocators.forInventorySlot(slot), whatToCraft, 1);
    }

    private int findInventorySlot(Player player, ItemStack itemStack) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i) == itemStack) {
                return i;
            }
        }
        return -1;
    }

    private static class PlacementToolMenuHost extends WirelessTerminalMenuHost implements ISubMenuHost {
        public PlacementToolMenuHost(Player player, Integer slot, ItemStack itemStack,
                BiConsumer<Player, ISubMenu> returnToMainMenu) {
            super(player, slot, itemStack, returnToMainMenu);
        }

        @Override
        public void returnToMainMenu(Player player, ISubMenu subMenu) {
            player.closeContainer();
        }
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) {
            return InteractionResult.sidedSuccess(true);
        }

        Player player = context.getPlayer();
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }

        ItemStack wand = player.getItemInHand(context.getHand());

        final double ENERGY_COST = 200d;

        if (!this.hasPower(player, ENERGY_COST, wand)) {
            player.displayClientMessage(Component.translatable("message.meplacementtool.device_not_powered"), true);
            return InteractionResult.FAIL;
        }

        var grid = this.getLinkedGrid(wand, level, player);
        if (grid == null) {
            return InteractionResult.FAIL;
        }

        CompoundTag data = wand.getOrCreateTag();
        CompoundTag cfg = null;
        if (data.contains(WandMenu.TAG_KEY)) {
            cfg = data.getCompound(WandMenu.TAG_KEY);
        }

        int selected = 0;
        if (cfg != null && cfg.contains("SelectedSlot")) {
            selected = cfg.getInt("SelectedSlot");
            if (selected < 0 || selected >= 9) selected = 0;
        }

        var handler = new ItemStackHandler(9);
        if (cfg != null) {
            if (cfg.contains("items")) {
                handler.deserializeNBT(cfg.getCompound("items"));
            } else {
                handler.deserializeNBT(cfg);
            }
        }

        ItemStack target = handler.getStackInSlot(selected);
        if (target == null || target.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.meplacementtool.no_configured_item"), true);
            return InteractionResult.FAIL;
        }

        var storage = grid.getStorageService().getInventory();
        var src = new appeng.me.helpers.PlayerSource(player);

        BlockPos lastPlacementPos = null;
        boolean lastPlacementWasBlock = false;

        try {
            var unwrapped = appeng.api.stacks.GenericStack.unwrapItemStack(target);
            if (unwrapped != null && appeng.api.stacks.AEFluidKey.is(unwrapped.what())) {
                var aeFluidKey = (appeng.api.stacks.AEFluidKey) unwrapped.what();
                var fluid = aeFluidKey.getFluid();

                BlockPos fluidPlacePos = context.getClickedPos().relative(context.getClickedFace());
                var prevState = level.getBlockState(fluidPlacePos);

                long simAvail = storage.extract(aeFluidKey, appeng.api.stacks.AEFluidKey.AMOUNT_BLOCK, appeng.api.config.Actionable.SIMULATE, src);
                if (simAvail < appeng.api.stacks.AEFluidKey.AMOUNT_BLOCK) {
                    player.displayClientMessage(Component.translatable("message.meplacementtool.network_missing", aeFluidKey.getDisplayName()), true);
                    return InteractionResult.FAIL;
                }

                boolean placedFluid = false;
                try {
                    if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                        var stateAtPos = level.getBlockState(fluidPlacePos);
                        boolean isFlowingFluid = fluid instanceof net.minecraft.world.level.material.FlowingFluid;
                        var legacyBlock = fluid.defaultFluidState().createLegacyBlock();
                        boolean stateIsLegacy = stateAtPos == legacyBlock;
                        boolean stateIsAir = stateAtPos.isAir();
                        boolean canBeReplaced = false;
                        try { canBeReplaced = stateAtPos.canBeReplaced(fluid); } catch (Throwable ignored2) {}
                        boolean isLiquidContainer = stateAtPos.getBlock() instanceof net.minecraft.world.level.block.LiquidBlockContainer;
                        boolean containerCanPlace = false;
                        if (isLiquidContainer) {
                            try {
                                containerCanPlace = ((net.minecraft.world.level.block.LiquidBlockContainer) stateAtPos.getBlock())
                                        .canPlaceLiquid(level, fluidPlacePos, stateAtPos, fluid);
                            } catch (Throwable ignored2) {}
                        }
                        boolean hasTag = aeFluidKey.hasTag();

                        boolean aeCanPlace = isFlowingFluid && !stateIsLegacy && !hasTag && (stateIsAir || canBeReplaced || (isLiquidContainer && containerCanPlace));

                        if (aeCanPlace) {
                            boolean success = false;
                            if (level.dimensionType().ultraWarm() && fluid.is(net.minecraft.tags.FluidTags.WATER)) {
                                level.playSound(null, fluidPlacePos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F, 2.6F);
                                success = true;
                            } else if (isLiquidContainer && fluid == net.minecraft.world.level.material.Fluids.WATER) {
                                ((net.minecraft.world.level.block.LiquidBlockContainer) stateAtPos.getBlock())
                                        .placeLiquid(level, fluidPlacePos, stateAtPos, ((net.minecraft.world.level.material.FlowingFluid) fluid).getSource(false));
                                success = true;
                            } else {
                                if (canBeReplaced && !stateAtPos.liquid()) {
                                    level.destroyBlock(fluidPlacePos, true);
                                }
                                success = level.setBlock(fluidPlacePos, legacyBlock, net.minecraft.world.level.block.Block.UPDATE_ALL_IMMEDIATE);
                            }
                            if (success) {
                                placedFluid = true;
                                lastPlacementPos = fluidPlacePos;
                                lastPlacementWasBlock = true;
                            }
                        }
                    }
                } catch (Throwable t) {
                    LOGGER.warn("Exception during wrapped fluid placement for player {} at {}", player.getName().getString(), fluidPlacePos, t);
                }

                if (placedFluid) {
                    long extracted = storage.extract(aeFluidKey, appeng.api.stacks.AEFluidKey.AMOUNT_BLOCK, appeng.api.config.Actionable.MODULATE, src);
                    if (extracted <= 0) {
                        try { level.setBlockAndUpdate(fluidPlacePos, prevState); } catch (Throwable t) { LOGGER.warn("Failed to revert fluid block at {}", fluidPlacePos, t); }
                        player.displayClientMessage(Component.translatable("message.meplacementtool.cannot_place"), true);
                        return InteractionResult.sidedSuccess(false);
                    } else {
                        this.usePower(player, ENERGY_COST, wand);
                        level.playSound(null, fluidPlacePos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
                        return InteractionResult.sidedSuccess(false);
                    }
                } else {
                    player.displayClientMessage(Component.translatable("message.meplacementtool.cannot_place"), true);
                    return InteractionResult.sidedSuccess(false);
                }
            }
        } catch (Throwable ignored) {}

        String fluidId = null;
        if (cfg != null && cfg.contains("fluids")) {
            var ftag = cfg.getCompound("fluids");
            if (ftag.contains(Integer.toString(selected))) {
                fluidId = ftag.getString(Integer.toString(selected));
                if (fluidId != null && fluidId.isEmpty()) fluidId = null;
            }
        }

        if (fluidId != null) {
            try {
                var fid = new net.minecraft.resources.ResourceLocation(fluidId);
                var fluid = net.minecraftforge.registries.ForgeRegistries.FLUIDS.getValue(fid);
                if (fluid == null) {
                    player.displayClientMessage(Component.translatable("message.meplacementtool.unsupported_target"), true);
                    return InteractionResult.FAIL;
                }

                var bucketItem = fluid.getBucket();
                if (bucketItem == net.minecraft.world.item.Items.AIR) {
                    player.displayClientMessage(Component.translatable("message.meplacementtool.unsupported_target"), true);
                    return InteractionResult.FAIL;
                }

                BlockPos fluidPlacePos = context.getClickedPos().relative(context.getClickedFace());
                var prevState = level.getBlockState(fluidPlacePos);

                try {
                    boolean isFlowing = fluid instanceof net.minecraft.world.level.material.FlowingFluid;
                    var legacy = fluid.defaultFluidState().createLegacyBlock();
                    if (prevState == legacy) {
                        player.displayClientMessage(Component.translatable("message.meplacementtool.cannot_place"), true);
                        return InteractionResult.sidedSuccess(false);
                    }
                    boolean canPlaceLikeAE = isFlowing && (prevState.isAir() || prevState.canBeReplaced(fluid)
                            || (prevState.getBlock() instanceof net.minecraft.world.level.block.LiquidBlockContainer
                                && ((net.minecraft.world.level.block.LiquidBlockContainer) prevState.getBlock()).canPlaceLiquid(level, fluidPlacePos, prevState, fluid)));
                    if (!canPlaceLikeAE) {
                        player.displayClientMessage(Component.translatable("message.meplacementtool.cannot_place"), true);
                        return InteractionResult.sidedSuccess(false);
                    }
                } catch (Throwable t) {
                    LOGGER.warn("Error during pre-check for fluid placement at {}", fluidPlacePos, t);
                }

                var aeFluidKey = appeng.api.stacks.AEFluidKey.of(fluid);
                long simAvail = storage.extract(aeFluidKey, appeng.api.stacks.AEFluidKey.AMOUNT_BLOCK, appeng.api.config.Actionable.SIMULATE, src);
                if (simAvail < appeng.api.stacks.AEFluidKey.AMOUNT_BLOCK) {
                    player.displayClientMessage(Component.translatable("message.meplacementtool.network_missing", aeFluidKey.getDisplayName()), true);
                    return InteractionResult.FAIL;
                }

                boolean placedFluid = false;
                try {
                    if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                        var stateAtPos = level.getBlockState(fluidPlacePos);
                        boolean isFlowingFluid = fluid instanceof net.minecraft.world.level.material.FlowingFluid;
                        var legacyBlock = fluid.defaultFluidState().createLegacyBlock();
                        boolean stateIsLegacy = stateAtPos == legacyBlock;
                        boolean stateIsAir = stateAtPos.isAir();
                        boolean canBeReplaced = false;
                        try { canBeReplaced = stateAtPos.canBeReplaced(fluid); } catch (Throwable ignored) {}
                        boolean isLiquidContainer = stateAtPos.getBlock() instanceof net.minecraft.world.level.block.LiquidBlockContainer;
                        boolean containerCanPlace = false;
                        if (isLiquidContainer) {
                            try {
                                containerCanPlace = ((net.minecraft.world.level.block.LiquidBlockContainer) stateAtPos.getBlock())
                                        .canPlaceLiquid(level, fluidPlacePos, stateAtPos, fluid);
                            } catch (Throwable ignored) {}
                        }
                        boolean hasTag = aeFluidKey.hasTag();
                        
                        boolean aeCanPlace = isFlowingFluid && !stateIsLegacy && !hasTag && (stateIsAir || canBeReplaced || (isLiquidContainer && containerCanPlace));
                        
                        if (!aeCanPlace) {
                            player.displayClientMessage(Component.translatable("message.meplacementtool.cannot_place"), true);
                        } else {
                            boolean success = false;
                            if (level.dimensionType().ultraWarm() && fluid.is(net.minecraft.tags.FluidTags.WATER)) {
                                level.playSound(null, fluidPlacePos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F, 2.6F);
                                success = true;
                            } else if (isLiquidContainer && fluid == net.minecraft.world.level.material.Fluids.WATER) {
                                ((net.minecraft.world.level.block.LiquidBlockContainer) stateAtPos.getBlock())
                                        .placeLiquid(level, fluidPlacePos, stateAtPos, ((net.minecraft.world.level.material.FlowingFluid) fluid).getSource(false));
                                success = true;
                            } else {
                                if (canBeReplaced && !stateAtPos.liquid()) {
                                    level.destroyBlock(fluidPlacePos, true);
                                }
                                success = level.setBlock(fluidPlacePos, legacyBlock, net.minecraft.world.level.block.Block.UPDATE_ALL_IMMEDIATE);
                            }
                            if (success) {
                                placedFluid = true;
                                lastPlacementPos = fluidPlacePos;
                                lastPlacementWasBlock = true;
                            }
                        }
                    }
                } catch (Throwable t) {
                    LOGGER.warn("Exception during fluid placement for player {} at {}", player.getName().getString(), fluidPlacePos, t);
                }

                if (placedFluid) {
                    long extracted = storage.extract(aeFluidKey, appeng.api.stacks.AEFluidKey.AMOUNT_BLOCK, appeng.api.config.Actionable.MODULATE, src);
                    if (extracted <= 0) {
                        try { level.setBlockAndUpdate(fluidPlacePos, prevState); } catch (Throwable t) { LOGGER.warn("Failed to revert fluid block at {}", fluidPlacePos, t); }
                        player.displayClientMessage(Component.translatable("message.meplacementtool.cannot_place"), true);
                        return InteractionResult.sidedSuccess(false);
                    } else {
                        this.usePower(player, ENERGY_COST, wand);
                        level.playSound(null, fluidPlacePos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
                        return InteractionResult.sidedSuccess(false);
                    }
                } else {
                    player.displayClientMessage(Component.translatable("message.meplacementtool.cannot_place"), true);
                    return InteractionResult.sidedSuccess(false);
                }
            } catch (Throwable t) {
                LOGGER.warn("Exception during fluid placement for player {} at {}", player.getName().getString(), context.getClickedPos(), t);
            }
        }

        var itemKey = appeng.api.stacks.AEItemKey.of(target);
        if (itemKey == null) {
            player.displayClientMessage(Component.translatable("message.meplacementtool.unsupported_target"), true);
            return InteractionResult.FAIL;
        }

        long simAvail = storage.extract(itemKey, target.getCount(), appeng.api.config.Actionable.SIMULATE, src);
        if (simAvail < target.getCount()) {
            player.displayClientMessage(Component.translatable("message.meplacementtool.network_missing", itemKey.getDisplayName()), true);
            return InteractionResult.FAIL;
        }

        var blockItem = target.getItem();
        if (!(blockItem instanceof BlockItem)) {
            player.displayClientMessage(Component.translatable("message.meplacementtool.unsupported_target"), true);
            return InteractionResult.FAIL;
        }

        var block = ((BlockItem) blockItem).getBlock();
        var placeContext = new BlockPlaceContext(context);
        var placed = block.place(placeContext);
        if (placed != net.minecraft.world.level.block.state.BlockState.UPDATE_ALL_IMMEDIATE) {
            player.displayClientMessage(Component.translatable("message.meplacementtool.cannot_place"), true);
            return InteractionResult.sidedSuccess(false);
        }

        long extracted = storage.extract(itemKey, target.getCount(), appeng.api.config.Actionable.MODULATE, src);
        if (extracted <= 0) {
            player.displayClientMessage(Component.translatable("message.meplacementtool.cannot_place"), true);
            return InteractionResult.sidedSuccess(false);
        }

        this.usePower(player, ENERGY_COST, wand);
        level.playSound(null, context.getClickedPos().relative(context.getClickedFace()), SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
        return InteractionResult.sidedSuccess(false);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack wand = player.getItemInHand(hand);

        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            var grid = this.getLinkedGrid(wand, level, player);
            if (grid == null) {
                player.displayClientMessage(Component.translatable("message.meplacementtool.no_linked_grid"), true);
                return InteractionResultHolder.fail(wand);
            }

            int slot = findInventorySlot(player, wand);
            if (slot < 0) {
                LOGGER.warn("Could not find wand in player inventory");
                return InteractionResultHolder.fail(wand);
            }

            NetworkHooks.openScreen(serverPlayer, new SimpleMenuProvider(
                    (id, inventory, p) -> new WandMenu(id, inventory, wand, grid),
                    Component.translatable("container.meplacementtool.wand")
            ), buf -> {
                buf.writeInt(slot);
                buf.writeItem(wand);
            });
        }

        return InteractionResultHolder.sidedSuccess(wand, level.isClientSide);
    }
}
