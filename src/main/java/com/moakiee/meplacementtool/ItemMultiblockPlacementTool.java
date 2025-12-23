package com.moakiee.meplacementtool;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.function.DoubleSupplier;
import java.util.function.BiConsumer;
import java.util.ArrayList;
import java.util.List;

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
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.items.ItemStackHandler;

public class ItemMultiblockPlacementTool extends WirelessTerminalItem implements IMenuItem {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int[] PLACEMENT_COUNTS = {1, 8, 64, 256, 1024};
    private static final String TAG_PLACEMENT_COUNT = "placement_count";

    public ItemMultiblockPlacementTool(Item.Properties props) {
        super(() -> Config.multiblockPlacementToolEnergyCapacity, props);
    }

    public static int getPlacementCount(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(TAG_PLACEMENT_COUNT)) {
            int count = tag.getInt(TAG_PLACEMENT_COUNT);
            for (int i = 0; i < PLACEMENT_COUNTS.length; i++) {
                if (PLACEMENT_COUNTS[i] == count) {
                    return count;
                }
            }
        }
        return PLACEMENT_COUNTS[0];
    }

    public static int getNextPlacementCount(ItemStack stack, boolean forward) {
        int current = getPlacementCount(stack);
        for (int i = 0; i < PLACEMENT_COUNTS.length; i++) {
            if (PLACEMENT_COUNTS[i] == current) {
                int nextIndex = forward ? (i + 1) % PLACEMENT_COUNTS.length : (i - 1 + PLACEMENT_COUNTS.length) % PLACEMENT_COUNTS.length;
                return PLACEMENT_COUNTS[nextIndex];
            }
        }
        return PLACEMENT_COUNTS[0];
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

        int placementCount = getPlacementCount(wand);
        final double ENERGY_COST = Config.multiblockPlacementToolBaseEnergyCost * placementCount;

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

        long totalNeeded = (long) placementCount * target.getCount();

        long simAvail = storage.extract(itemKey, totalNeeded, appeng.api.config.Actionable.SIMULATE, src);
        if (simAvail < totalNeeded) {
            player.displayClientMessage(Component.translatable("message.meplacementtool.network_missing", itemKey.getDisplayName()), true);
            return InteractionResult.FAIL;
        }

        var blockItem = target.getItem();
        if (!(blockItem instanceof BlockItem)) {
            player.displayClientMessage(Component.translatable("message.meplacementtool.unsupported_target"), true);
            return InteractionResult.FAIL;
        }

        var block = ((BlockItem) blockItem).getBlock();
        BlockPos clickedPos = context.getClickedPos();
        var clickedFace = context.getClickedFace();
        var clickedState = level.getBlockState(clickedPos);

        java.util.LinkedList<BlockPos> candidates = new java.util.LinkedList<>();
        java.util.HashSet<BlockPos> allCandidates = new java.util.HashSet<>();
        java.util.ArrayList<BlockPos> placePositions = new java.util.ArrayList<>();

        BlockPos startingPoint = clickedPos.relative(clickedFace);
        candidates.add(startingPoint);

        while (!candidates.isEmpty() && placePositions.size() < placementCount) {
            BlockPos currentCandidate = candidates.removeFirst();
            if (!allCandidates.add(currentCandidate)) {
                continue;
            }

            BlockPos supportingPoint = currentCandidate.relative(clickedFace.getOpposite());
            var supportingState = level.getBlockState(supportingPoint);

            if (supportingState.getBlock() == clickedState.getBlock()) {
                var currentState = level.getBlockState(currentCandidate);
                boolean canPlace = level.isEmptyBlock(currentCandidate);
                if (!canPlace) {
                    try {
                        BlockPlaceContext checkContext = new BlockPlaceContext(new net.minecraft.world.item.context.UseOnContext(
                            player, context.getHand(), new net.minecraft.world.phys.BlockHitResult(
                                context.getClickLocation(), context.getClickedFace(), currentCandidate, context.isInside()
                            )
                        ));
                        canPlace = currentState.canBeReplaced(checkContext);
                    } catch (Throwable t) {}
                }
                if (canPlace) {
                    placePositions.add(currentCandidate);
                }

                switch (clickedFace) {
                    case DOWN:
                    case UP:
                        candidates.add(currentCandidate.north());
                        candidates.add(currentCandidate.south());
                        candidates.add(currentCandidate.east());
                        candidates.add(currentCandidate.west());
                        candidates.add(currentCandidate.north().east());
                        candidates.add(currentCandidate.north().west());
                        candidates.add(currentCandidate.south().east());
                        candidates.add(currentCandidate.south().west());
                        break;
                    case NORTH:
                    case SOUTH:
                        candidates.add(currentCandidate.east());
                        candidates.add(currentCandidate.west());
                        candidates.add(currentCandidate.above());
                        candidates.add(currentCandidate.below());
                        candidates.add(currentCandidate.above().east());
                        candidates.add(currentCandidate.above().west());
                        candidates.add(currentCandidate.below().east());
                        candidates.add(currentCandidate.below().west());
                        break;
                    case EAST:
                    case WEST:
                        candidates.add(currentCandidate.north());
                        candidates.add(currentCandidate.south());
                        candidates.add(currentCandidate.above());
                        candidates.add(currentCandidate.below());
                        candidates.add(currentCandidate.above().north());
                        candidates.add(currentCandidate.above().south());
                        candidates.add(currentCandidate.below().north());
                        candidates.add(currentCandidate.below().south());
                        break;
                }
            }
        }

        if (placePositions.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.meplacementtool.cannot_place"), true);
            return InteractionResult.sidedSuccess(false);
        }

        int placedCount = 0;
        List<UndoHistory.PlacementSnapshot> placedSnapshots = new ArrayList<>();

        for (BlockPos placePos : placePositions) {
            var placeStack = itemKey.toStack(1);
            ItemStack origMain = player.getMainHandItem();
            ItemStack origOff = player.getOffhandItem();
            try {
                player.setItemInHand(InteractionHand.MAIN_HAND, placeStack);
                BlockPlaceContext placeContext = new BlockPlaceContext(new net.minecraft.world.item.context.UseOnContext(
                    player, context.getHand(), new net.minecraft.world.phys.BlockHitResult(
                        context.getClickLocation(), context.getClickedFace(), placePos, context.isInside()
                    )
                ));
                var result = ((BlockItem) blockItem).place(placeContext);
                boolean consumes = result.consumesAction();
                if (consumes) {
                    placedCount++;
                    placedSnapshots.add(new UndoHistory.PlacementSnapshot(level.getBlockState(placePos), placePos, placeStack, itemKey, 1));
                }
            } catch (Throwable t) {
                LOGGER.warn("Exception during placement attempt for player {} at {}", player.getName().getString(), placePos, t);
            } finally {
                player.setItemInHand(InteractionHand.MAIN_HAND, origMain);
                player.setItemInHand(InteractionHand.OFF_HAND, origOff);
            }
        }

        if (placedCount == 0) {
            player.displayClientMessage(Component.translatable("message.meplacementtool.cannot_place"), true);
            return InteractionResult.sidedSuccess(false);
        }

        long extracted = storage.extract(itemKey, (long) placedCount * target.getCount(), appeng.api.config.Actionable.MODULATE, src);
        if (extracted <= 0) {
            player.displayClientMessage(Component.translatable("message.meplacementtool.cannot_place"), true);
            return InteractionResult.sidedSuccess(false);
        }

        MEPlacementToolMod.instance.undoHistory.add(player, level, placedSnapshots);

        this.usePower(player, ENERGY_COST * placedCount / placementCount, wand);
        level.playSound(null, clickedPos.relative(clickedFace), SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
        return InteractionResult.sidedSuccess(false);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack wand = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) {
            net.minecraft.world.phys.HitResult hr = player.pick(5.0D, 0.0F, false);
            if (hr != null && hr.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
                return new InteractionResultHolder<>(InteractionResult.PASS, wand);
            }

            if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                CompoundTag data = wand.getOrCreateTag();
                CompoundTag cfg = data.contains(WandMenu.TAG_KEY) ? data.getCompound(WandMenu.TAG_KEY) : null;

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

            return new InteractionResultHolder<>(InteractionResult.sidedSuccess(level.isClientSide()), wand);
        }

        if (level.isClientSide()) {
            CompoundTag data = wand.getOrCreateTag();
            CompoundTag cfg = data.contains(WandMenu.TAG_KEY) ? data.getCompound(WandMenu.TAG_KEY).copy() : new CompoundTag();
            int selected = cfg.contains("SelectedSlot") ? cfg.getInt("SelectedSlot") : 0;
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
            }
            com.moakiee.meplacementtool.network.ModNetwork.CHANNEL.sendToServer(
                    new com.moakiee.meplacementtool.network.UpdateWandConfigPacket(cfg));

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
                    }
                } else if (cfg.contains("fluids")) {
                    var ftag = cfg.getCompound("fluids");
                    String fluidId = ftag.getString(Integer.toString(selected));
                    if (fluidId != null && !fluidId.isEmpty()) {
                        try {
                            var fid = new net.minecraft.resources.ResourceLocation(fluidId);
                            var fluid = net.minecraftforge.registries.ForgeRegistries.FLUIDS.getValue(fid);
                            if (fluid != null) {
                                name = fluid.defaultFluidState().createLegacyBlock().getBlock().getName().getString();
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            } catch (Throwable ignored) {}

            player.displayClientMessage(Component.literal("Selected: " + name), true);
        }

        return new InteractionResultHolder<>(InteractionResult.sidedSuccess(level.isClientSide()), wand);
    }
}
