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

/**
 * ME Placement Tool - 占位实现，继承自 AE2 的 WirelessTerminalItem
 */
public class ItemMEPlacementTool extends WirelessTerminalItem implements IMenuItem {
    private static final Logger LOGGER = LogUtils.getLogger();

    public ItemMEPlacementTool(Item.Properties props) {
        super(() -> Config.mePlacementToolEnergyCapacity, props);
    }

    @Override
    public ItemMenuHost getMenuHost(Player player, int inventorySlot, ItemStack itemStack, BlockPos pos) {
        return new PlacementToolMenuHost(player, inventorySlot, itemStack, (p, subMenu) -> {
            // Return to main menu when closing submenu
        });
    }

    /**
     * Open the crafting menu for an item that can be crafted
     */
    private void openCraftingMenu(ServerPlayer player, ItemStack wand, AEKey whatToCraft) {
        // Find the inventory slot containing the wand
        int slot = findInventorySlot(player, wand);
        if (slot < 0) {
            LOGGER.warn("Could not find wand in player inventory");
            return;
        }

        // Open the CraftAmountMenu with the item to craft
        CraftAmountMenu.open(player, MenuLocators.forInventorySlot(slot), whatToCraft, 1);
    }

    /**
     * Find the inventory slot containing the given item stack
     */
    private int findInventorySlot(Player player, ItemStack itemStack) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i) == itemStack) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Menu host for the placement tool that supports autocrafting
     */
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

        // energy cost per placement
        final double ENERGY_COST = Config.mePlacementToolEnergyCost;

        // check power
        if (!this.hasPower(player, ENERGY_COST, wand)) {
            player.displayClientMessage(Component.translatable("message.meplacementtool.device_not_powered"), true);
            return InteractionResult.FAIL;
        }

        // get linked grid
        var grid = this.getLinkedGrid(wand, level, player);
        if (grid == null) {
            // getLinkedGrid already notifies player
            return InteractionResult.FAIL;
        }

        // read config NBT from item
        CompoundTag data = wand.getOrCreateTag();
        CompoundTag cfg = null;
        if (data.contains(WandMenu.TAG_KEY)) {
            cfg = data.getCompound(WandMenu.TAG_KEY);
        }

        // selected slot index (default 0) - read from placement_config tag
        int selected = 0;
        if (cfg != null && cfg.contains("SelectedSlot")) {
            selected = cfg.getInt("SelectedSlot");
            if (selected < 0 || selected >= 9) selected = 0;
        }

        // build handler from NBT
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

        // convert to AEItemKey
        // Prepare AE storage/source for later use
        var storage = grid.getStorageService().getInventory();
        var src = new appeng.me.helpers.PlayerSource(player);

        // Placement tracking (used for rollback/logging)
        BlockPos lastPlacementPos = null;
        boolean lastPlacementWasBlock = false;

        // First: detect if the target is an AE wrapped GenericStack representing a fluid
        try {
            var unwrapped = appeng.api.stacks.GenericStack.unwrapItemStack(target);
            if (unwrapped != null && appeng.api.stacks.AEFluidKey.is(unwrapped.what())) {
                // Direct fluid placement from AE network (same logic as fluidId branch)
                var aeFluidKey = (appeng.api.stacks.AEFluidKey) unwrapped.what();
                var fluid = aeFluidKey.getFluid();

                BlockPos fluidPlacePos = context.getClickedPos().relative(context.getClickedFace());
                var prevState = level.getBlockState(fluidPlacePos);

                // Check AE network has enough fluid
                long simAvail = storage.extract(aeFluidKey, appeng.api.stacks.AEFluidKey.AMOUNT_BLOCK, appeng.api.config.Actionable.SIMULATE, src);
                if (simAvail < appeng.api.stacks.AEFluidKey.AMOUNT_BLOCK) {
                    player.displayClientMessage(Component.translatable("message.meplacementtool.network_missing", aeFluidKey.getDisplayName()), true);
                    return InteractionResult.FAIL;
                }

                boolean placedFluid = false;
                try {
                    if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                        // Check if placement is possible using AE's canPlace logic
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
                            // Direct placement using level.setBlock like AE does
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
                    // Extract FLUID from AE network (not bucket!)
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

        // Check if the selected slot is a fluid (stored in placement_config.fluids)
        String fluidId = null;
        if (cfg != null && cfg.contains("fluids")) {
            var ftag = cfg.getCompound("fluids");
            if (ftag.contains(Integer.toString(selected))) {
                fluidId = ftag.getString(Integer.toString(selected));
                if (fluidId != null && fluidId.isEmpty()) fluidId = null;
            }
        }

        if (fluidId != null) {
            // Try to resolve the fluid and its bucket item
            try {
                var fid = new net.minecraft.resources.ResourceLocation(fluidId);
                var fluid = net.minecraftforge.registries.ForgeRegistries.FLUIDS.getValue(fid);
                if (fluid == null) {
                    player.displayClientMessage(Component.translatable("message.meplacementtool.unsupported_target"), true);
                    return InteractionResult.FAIL;
                }

                var bucketItem = fluid.getBucket();
                if (bucketItem == net.minecraft.world.item.Items.AIR) {
                    // No bucket available -> unsupported for now
                    player.displayClientMessage(Component.translatable("message.meplacementtool.unsupported_target"), true);
                    return InteractionResult.FAIL;
                }

                // Use AE's FluidPlacementStrategy to place fluid directly from AE network
                BlockPos fluidPlacePos = context.getClickedPos().relative(context.getClickedFace());
                var prevState = level.getBlockState(fluidPlacePos);

                // Quick client/server independent pre-check mimicking AE's canPlace
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

                // Prepare AEFluidKey and ensure network has enough
                var aeFluidKey = appeng.api.stacks.AEFluidKey.of(fluid);
                long simAvail = storage.extract(aeFluidKey, appeng.api.stacks.AEFluidKey.AMOUNT_BLOCK, appeng.api.config.Actionable.SIMULATE, src);
                if (simAvail < appeng.api.stacks.AEFluidKey.AMOUNT_BLOCK) {
                    player.displayClientMessage(Component.translatable("message.meplacementtool.network_missing", aeFluidKey.getDisplayName()), true);
                    return InteractionResult.FAIL;
                }

                boolean placedFluid = false;
                try {
                    if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                        // Detailed diagnostic before calling FluidPlacementStrategy
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
                        
                        // AE's canPlace logic
                        boolean aeCanPlace = isFlowingFluid && !stateIsLegacy && !hasTag && (stateIsAir || canBeReplaced || (isLiquidContainer && containerCanPlace));
                        
                        if (!aeCanPlace) {
                            player.displayClientMessage(Component.translatable("message.meplacementtool.cannot_place"), true);
                        } else {
                            // Directly place using level.setBlock like AE does
                            boolean success = false;
                            if (level.dimensionType().ultraWarm() && fluid.is(net.minecraft.tags.FluidTags.WATER)) {
                                // Water evaporates in nether but still "succeeds"
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
            } catch (Exception e) {
                LOGGER.warn("Error resolving fluid {}", fluidId, e);
                player.displayClientMessage(Component.translatable("message.meplacementtool.unsupported_target"), true);
                return InteractionResult.FAIL;
            }
        }

        var aeKey = appeng.api.stacks.AEItemKey.of(target);
        if (aeKey == null) {
            player.displayClientMessage(Component.translatable("message.meplacementtool.unsupported_target"), true);
            return InteractionResult.FAIL;
        }

        // simulate extract to ensure availability, but defer actual extraction until after successful placement
        long avail = storage.extract(aeKey, 1L, appeng.api.config.Actionable.SIMULATE, src);
        if (avail <= 0) {
            // Check if the item can be crafted
            var craftingService = grid.getCraftingService();
            if (craftingService != null && craftingService.isCraftable(aeKey)) {
                // Open crafting menu for the item
                openCraftingMenu(serverPlayer, wand, aeKey);
                return InteractionResult.sidedSuccess(false);
            }
            player.displayClientMessage(Component.translatable("message.meplacementtool.network_missing", target.getHoverName()), true);
            return InteractionResult.FAIL;
        }

        // create stack to place
        ItemStack placeStack = aeKey.toStack(1);

        // attempt placement: blocks use the adjacent position, parts use the clicked block position
        BlockPos blockPlacePos = context.getClickedPos().relative(context.getClickedFace());
        BlockPos partTargetPos = context.getClickedPos();
        boolean placed = false;
        // track placement result
        // capture previous block state for the block placement position (used for possible rollback)
        var prevStateBlock = level.getBlockState(blockPlacePos);
        try {
            if (placeStack.getItem() instanceof net.minecraft.world.item.BlockItem blockItem) {
                // Some mods (including AE) check the player's held stack during placement.
                // Temporarily replace the player's MAIN hand with the extracted stack so placement logic sees it.
                // Also capture original main/off hand to restore afterwards.
                ItemStack origMain = player.getMainHandItem();
                ItemStack origOff = player.getOffhandItem();
                try {
                    player.setItemInHand(InteractionHand.MAIN_HAND, placeStack);
                    BlockPlaceContext placeContext = new BlockPlaceContext(context);
                    var result = blockItem.place(placeContext);
                    boolean consumes = result.consumesAction();
                    if (consumes) {
                        placed = true;
                        lastPlacementPos = blockPlacePos;
                        lastPlacementWasBlock = true;
                    }
                } catch (Throwable t) {
                    LOGGER.warn("Exception during placement attempt for player {} at {}", player.getName().getString(), blockPlacePos, t);
                } finally {
                    // restore original items
                    player.setItemInHand(InteractionHand.MAIN_HAND, origMain);
                    player.setItemInHand(InteractionHand.OFF_HAND, origOff);
                }
            } else if (placeStack.getItem() instanceof appeng.api.parts.IPartItem<?>) {
                // AE part placement (eg. ME Smart Cable) - use AE2's own placement calculation
                ItemStack origMain = player.getMainHandItem();
                ItemStack origOff = player.getOffhandItem();
                try {
                    player.setItemInHand(InteractionHand.MAIN_HAND, placeStack);
                    try {
                        // Use AE2's PartPlacement.getPartPlacement to compute where AE would place this part (same as preview)
                        var placement = appeng.parts.PartPlacement.getPartPlacement(player, level, placeStack, context.getClickedPos(), context.getClickedFace(), context.getClickLocation());
                        if (placement != null) {
                            var serverLevel = (level instanceof net.minecraft.server.level.ServerLevel) ? (net.minecraft.server.level.ServerLevel) level : null;
                            if (serverLevel != null) {
                                // Use AE2's placePart which performs host creation, collision checks and settings import
                                var part = appeng.parts.PartPlacement.placePart(player, serverLevel, (appeng.api.parts.IPartItem) placeStack.getItem(), placeStack.getTag(), placement.pos(), placement.side());
                                if (part != null) {
                                    placed = true;
                                    lastPlacementPos = placement.pos();
                                    lastPlacementWasBlock = false;
                                }
                            }
                        }
                    } catch (Throwable t) {
                        LOGGER.warn("Exception while using PartPlacement for player {} at {}", player.getName().getString(), partTargetPos, t);
                    }
                } catch (Throwable t) {
                    LOGGER.warn("Exception during part placement attempt for player {} at {}", player.getName().getString(), partTargetPos, t);
                } finally {
                    player.setItemInHand(InteractionHand.MAIN_HAND, origMain);
                    player.setItemInHand(InteractionHand.OFF_HAND, origOff);
                }
            }
        } catch (Throwable ignored) {
        }

        if (placed) {
            // After successful placement, perform the actual AE extraction. If extraction fails, roll back placement.
            long extracted = storage.extract(aeKey, 1L, appeng.api.config.Actionable.MODULATE, src);
            if (extracted <= 0) {
                // Attempt rollback
                BlockPos revertPos = lastPlacementPos != null ? lastPlacementPos : blockPlacePos;
                try {
                    if (lastPlacementWasBlock) {
                        level.setBlockAndUpdate(revertPos, prevStateBlock);
                    } else {
                        // For part placements we cannot reliably revert generically; just log the situation
                        LOGGER.warn("Extraction failed after part placement at {} — manual cleanup may be required", revertPos);
                    }
                } catch (Throwable t) {
                    LOGGER.warn("Failed to revert block at {}", revertPos, t);
                }
                // drop the stack as fallback
                var ent = new net.minecraft.world.entity.item.ItemEntity(level, revertPos.getX() + 0.5, revertPos.getY() + 0.5,
                        revertPos.getZ() + 0.5, placeStack);
                level.addFreshEntity(ent);
                level.playSound(null, revertPos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.8F, 1.0F);
            } else {
                // consume power and play placement sound
                this.usePower(player, ENERGY_COST, wand);
                BlockPos soundPos = lastPlacementPos != null ? lastPlacementPos : blockPlacePos;
                level.playSound(null, soundPos, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
                // Ensure clients receive the block update immediately
                try {
                    var finalState = level.getBlockState(soundPos);
                    level.sendBlockUpdated(soundPos, prevStateBlock, finalState, 3);
                } catch (Throwable ignored) {}
            }
        } else {
            // placement did not succeed — notify player
            player.displayClientMessage(Component.translatable("message.meplacementtool.cannot_place"), true);
        }

        return InteractionResult.sidedSuccess(false);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) {
            // Normal right-click: open configuration GUI only when not targeting a block (avoid conflict with placement)
            net.minecraft.world.phys.HitResult hr = player.pick(5.0D, 0.0F, false);
            if (hr != null && hr.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
                return new InteractionResultHolder<>(InteractionResult.PASS, stack);
            }

            if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                CompoundTag data = stack.getOrCreateTag();
                CompoundTag cfg = data.contains(WandMenu.TAG_KEY) ? data.getCompound(WandMenu.TAG_KEY) : null;

                // create handler from existing NBT (server side)
                var handler = new ItemStackHandler(9);
                if (cfg != null) {
                    if (cfg.contains("items")) {
                        handler.deserializeNBT(cfg.getCompound("items"));
                    } else {
                        handler.deserializeNBT(cfg);
                    }
                }

                NetworkHooks.openScreen(serverPlayer,
                    new SimpleMenuProvider((wnd, inv, pl) -> new WandMenu(wnd, inv, handler), Component.translatable("gui.meplacementtool.placement_config")),
                        buf -> buf.writeNbt(cfg));
            }

            return new InteractionResultHolder<>(InteractionResult.sidedSuccess(level.isClientSide()), stack);
        }

        // Shift + right-click in air: cycle next selected slot (client sends update to server)
        if (level.isClientSide()) {
            CompoundTag data = stack.getOrCreateTag();
            CompoundTag cfg = data.contains(WandMenu.TAG_KEY) ? data.getCompound(WandMenu.TAG_KEY).copy() : new CompoundTag();
            int selected = cfg.contains("SelectedSlot") ? cfg.getInt("SelectedSlot") : 0;
            // collect configured indices
            java.util.List<Integer> configured = new java.util.ArrayList<>();
            if (cfg.contains("items")) {
                net.minecraftforge.items.ItemStackHandler h = new net.minecraftforge.items.ItemStackHandler(9);
                h.deserializeNBT(cfg.getCompound("items"));
                for (int i = 0; i < 9; i++) {
                    var s = h.getStackInSlot(i);
                    if (!s.isEmpty()) configured.add(i);
                }
            }
            if (cfg.contains("fluids")) {
                var ftag = cfg.getCompound("fluids");
                for (String k : ftag.getAllKeys()) {
                    try {
                        int idx = Integer.parseInt(k);
                        if (!configured.contains(idx)) configured.add(idx);
                    } catch (NumberFormatException ignored) {}
                }
            }
            if (!configured.isEmpty()) {
                int pos = configured.indexOf(selected);
                if (pos == -1) pos = 0;
                pos = (pos + 1) % configured.size();
                selected = configured.get(pos);
                cfg.putInt("SelectedSlot", selected);
                data.put(WandMenu.TAG_KEY, cfg);
            } else {
                // no configured entries, leave selection unchanged
            }
            com.moakiee.meplacementtool.network.ModNetwork.CHANNEL.sendToServer(
                    new com.moakiee.meplacementtool.network.UpdateWandConfigPacket(cfg));

            // prepare overlay text (unwrap AE wrapped stacks or resolve fluid ids)
            String name = "Empty";
            try {
                if (cfg.contains("items")) {
                    net.minecraftforge.items.ItemStackHandler h = new net.minecraftforge.items.ItemStackHandler(9);
                    h.deserializeNBT(cfg.getCompound("items"));
                    var s = h.getStackInSlot(selected);
                    if (!s.isEmpty()) {
                        try {
                            var gs = appeng.api.stacks.GenericStack.unwrapItemStack(s);
                            if (gs != null) {
                                name = gs.what().getDisplayName().getString();
                            } else {
                                name = s.getHoverName().getString();
                            }
                        } catch (Throwable ignored) {
                            name = s.getHoverName().getString();
                        }
                    } else if (cfg.contains("fluids")) {
                        var f = cfg.getCompound("fluids").getString(Integer.toString(selected));
                        if (f != null && !f.isEmpty()) {
                            try {
                                var rl = new net.minecraft.resources.ResourceLocation(f);
                                var fl = net.minecraftforge.registries.ForgeRegistries.FLUIDS.getValue(rl);
                                if (fl != null) {
                                    name = appeng.api.stacks.AEFluidKey.of(fl).getDisplayName().getString();
                                } else {
                                    name = f;
                                }
                            } catch (Throwable ignored) {
                                name = f;
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
            MEPlacementToolMod.ClientForgeEvents.showSelectedOverlay(name);

        }

        return new InteractionResultHolder<>(InteractionResult.sidedSuccess(level.isClientSide()), stack);
    }
}
