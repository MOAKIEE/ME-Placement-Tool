package com.moakiee.meplacementtool;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;

import appeng.api.config.Actionable;
import appeng.api.implementations.menuobjects.IMenuItem;
import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.me.helpers.PlayerSource;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.menu.me.crafting.CraftAmountMenu;
import appeng.parts.PartPlacement;

/**
 * ME Placement Tool - Places items directly from AE network
 */
public class ItemMEPlacementTool extends BasePlacementToolItem implements IMenuItem {
    private static final Logger LOGGER = LogUtils.getLogger();

    public ItemMEPlacementTool(Item.Properties props) {
        super(() -> Config.mePlacementToolEnergyCapacity, props);
    }

    @Override
    public ItemMenuHost<?> getMenuHost(Player player, appeng.menu.locator.ItemMenuHostLocator locator,
            @org.jetbrains.annotations.Nullable BlockHitResult hitResult) {
        return new PlacementToolMenuHost(this, player, locator, (p, subMenu) -> {
            p.closeContainer();
        });
    }


    /**
     * Open the crafting menu for an item that can be crafted.
     */
    private void openCraftingMenu(ServerPlayer player, ItemStack wand, AEKey whatToCraft) {
        int wandSlot = findInventorySlot(player, wand);
        if (wandSlot >= 0) {
            CraftAmountMenu.open(player, MenuLocators.forInventorySlot(wandSlot), whatToCraft, 1);
        } else if (player.getMainHandItem() == wand) {
            CraftAmountMenu.open(player, MenuLocators.forHand(player, InteractionHand.MAIN_HAND), whatToCraft, 1);
        } else if (player.getOffhandItem() == wand) {
            CraftAmountMenu.open(player, MenuLocators.forHand(player, InteractionHand.OFF_HAND), whatToCraft, 1);
        }
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
        final double ENERGY_COST = Config.mePlacementToolEnergyCost;

        // Check power
        if (!this.hasPower(player, ENERGY_COST, wand)) {
            player.displayClientMessage(Component.translatable("message.meplacementtool.device_not_powered"), true);
            return InteractionResult.FAIL;
        }

        // Get linked grid
        var grid = this.getLinkedGrid(wand, level, player);
        if (grid == null) {
            return InteractionResult.FAIL;
        }

        // Read config from Data Component
        CompoundTag cfg = wand.get(ModDataComponents.PLACEMENT_CONFIG.get());
        if (cfg == null) {
            cfg = new CompoundTag();
        }

        // Selected slot index
        int selected = cfg.getInt("SelectedSlot");
        if (selected < 0 || selected >= 18) selected = 0;

        // Get target item from config
        ItemStack target = getItemFromConfig(cfg, selected);
        if (target == null || target.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.meplacementtool.no_configured_item"), true);
            return InteractionResult.FAIL;
        }

        // Prepare AE storage/source
        var storage = grid.getStorageService().getInventory();
        var src = new PlayerSource(player);

        BlockPos lastPlacementPos = null;
        boolean lastPlacementWasBlock = false;
        IPart lastPlacedPart = null;

        // Check for wrapped fluid (GenericStack)
        try {
            var unwrapped = GenericStack.unwrapItemStack(target);
            if (unwrapped != null && AEFluidKey.is(unwrapped.what())) {
                return handleFluidPlacement(context, player, wand, storage, src, 
                        (AEFluidKey) unwrapped.what(), ENERGY_COST);
            }
        } catch (Throwable ignored) {}

        // Check for fluid in fluids config
        String fluidId = getFluidFromConfig(cfg, selected);
        if (fluidId != null) {
            return handleFluidIdPlacement(context, player, wand, storage, src, fluidId, ENERGY_COST);
        }

        // Find matching item in AE network
        var aeKey = Config.findMatchingKey(storage, target);
        if (aeKey == null) {
            // Check if craftable
            var craftKey = AEItemKey.of(target);
            var craftingService = grid.getCraftingService();
            if (craftingService != null && craftKey != null && craftingService.isCraftable(craftKey)) {
                openCraftingMenu(serverPlayer, wand, craftKey);
                return InteractionResult.sidedSuccess(false);
            }
            player.displayClientMessage(Component.translatable("message.meplacementtool.network_missing", 
                    target.getHoverName()), true);
            return InteractionResult.FAIL;
        }

        // Simulate extraction
        long avail = storage.extract(aeKey, 1L, Actionable.SIMULATE, src);
        if (avail <= 0) {
            var craftingService = grid.getCraftingService();
            if (craftingService != null && craftingService.isCraftable(aeKey)) {
                openCraftingMenu(serverPlayer, wand, aeKey);
                return InteractionResult.sidedSuccess(false);
            }
            player.displayClientMessage(Component.translatable("message.meplacementtool.network_missing", 
                    target.getHoverName()), true);
            return InteractionResult.FAIL;
        }

        // Check resources for memory card
        if (MemoryCardHelper.hasConfiguredMemoryCard(player)) {
            var resourceCheck = MemoryCardHelper.checkResourcesForMultipleBlocks(player, grid, 1);
            if (!resourceCheck.sufficient) {
                player.displayClientMessage(Component.translatable("message.meplacementtool.missing_resources", 
                        resourceCheck.getMissingItemsMessage()), false);
                return InteractionResult.sidedSuccess(false);
            }
        }

        // Check for Mekanism config card
        boolean hasMekConfigCard = ModCompat.isMekanismLoaded() && 
                MekanismConfigCardHelper.hasConfiguredConfigCard(player);

        // Create stack to place
        ItemStack placeStack = aeKey.toStack(1);
        BlockPos blockPlacePos = context.getClickedPos().relative(context.getClickedFace());
        var prevStateBlock = level.getBlockState(blockPlacePos);
        boolean placed = false;

        try {
            if (placeStack.getItem() instanceof net.minecraft.world.item.BlockItem blockItem) {
                placed = tryPlaceBlock(context, player, placeStack, blockItem);
                if (placed) {
                    lastPlacementPos = blockPlacePos;
                    lastPlacementWasBlock = true;
                }
            } else if (placeStack.getItem() instanceof IPartItem<?>) {
                var result = tryPlacePart(context, player, level, placeStack);
                if (result != null) {
                    placed = true;
                    lastPlacementPos = result.pos();
                    lastPlacementWasBlock = false;
                    lastPlacedPart = result.part();
                }
            } else if (placeStack.getItem() instanceof appeng.api.implementations.items.IFacadeItem) {
                placed = tryPlaceFacade(context, player, level, placeStack);
                if (placed) {
                    lastPlacementPos = context.getClickedPos();
                    lastPlacementWasBlock = false;
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("Exception during placement for player {} at {}", 
                    player.getName().getString(), blockPlacePos, t);
        }

        if (placed) {
            // Extract from AE network
            long extracted = storage.extract(aeKey, 1L, Actionable.MODULATE, src);
            if (extracted <= 0) {
                // Rollback
                BlockPos revertPos = lastPlacementPos != null ? lastPlacementPos : blockPlacePos;
                if (lastPlacementWasBlock) {
                    try {
                        level.setBlockAndUpdate(revertPos, prevStateBlock);
                    } catch (Throwable t) {
                        LOGGER.warn("Failed to revert block at {}", revertPos, t);
                    }
                }
                return InteractionResult.sidedSuccess(false);
            }

            // Consume power
            ItemStack actualWand = player.getItemInHand(context.getHand());
            this.usePower(player, ENERGY_COST, actualWand);
            
            // Play the block's own placement sound
            BlockPos soundPos = lastPlacementPos != null ? lastPlacementPos : blockPlacePos;
            var placedState = level.getBlockState(soundPos);
            var soundType = placedState.getSoundType(level, soundPos, player);
            level.playSound(null, soundPos, soundType.getPlaceSound(), SoundSource.BLOCKS, 
                (soundType.getVolume() + 1.0F) / 2.0F, soundType.getPitch() * 0.8F);

            // Apply memory card / config card
            if (MemoryCardHelper.hasConfiguredMemoryCard(player)) {
                if (lastPlacementWasBlock) {
                    MemoryCardHelper.applyMemoryCardToBlock(player, level, soundPos, true, grid);
                } else if (lastPlacedPart != null) {
                    MemoryCardHelper.applyMemoryCardToPart(player, lastPlacedPart, true, grid);
                }
            } else if (hasMekConfigCard && lastPlacementWasBlock) {
                MekanismConfigCardHelper.applyConfigCardToBlock(player, level, soundPos, true);
            }
        } else {
            player.displayClientMessage(Component.translatable("message.meplacementtool.cannot_place"), true);
        }

        return InteractionResult.sidedSuccess(false);
    }

    private ItemStack getItemFromConfig(CompoundTag cfg, int slot) {
        if (cfg == null) return ItemStack.EMPTY;
        
        CompoundTag itemsTag = cfg.contains("items") ? cfg.getCompound("items") : cfg;
        if (itemsTag.contains("Items")) {
            ListTag list = itemsTag.getList("Items", 10);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag itemTag = list.getCompound(i);
                if (itemTag.getInt("Slot") == slot) {
                    return ItemStack.parseOptional(net.minecraft.core.HolderLookup.Provider.create(
                            java.util.stream.Stream.empty()), itemTag);
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private String getFluidFromConfig(CompoundTag cfg, int slot) {
        if (cfg == null || !cfg.contains("fluids")) return null;
        var ftag = cfg.getCompound("fluids");
        String key = Integer.toString(slot);
        if (ftag.contains(key)) {
            String fluidId = ftag.getString(key);
            return fluidId.isEmpty() ? null : fluidId;
        }
        return null;
    }

    private InteractionResult handleFluidPlacement(UseOnContext context, Player player, ItemStack wand,
            appeng.api.storage.MEStorage storage, PlayerSource src, AEFluidKey aeFluidKey, double energyCost) {
        Level level = context.getLevel();
        BlockPos fluidPlacePos = context.getClickedPos().relative(context.getClickedFace());
        var prevState = level.getBlockState(fluidPlacePos);
        var fluid = aeFluidKey.getFluid();

        // Check network has enough fluid
        long simAvail = storage.extract(aeFluidKey, AEFluidKey.AMOUNT_BLOCK, Actionable.SIMULATE, src);
        if (simAvail < AEFluidKey.AMOUNT_BLOCK) {
            player.displayClientMessage(Component.translatable("message.meplacementtool.network_missing", 
                    aeFluidKey.getDisplayName()), true);
            return InteractionResult.FAIL;
        }

        boolean placedFluid = tryPlaceFluid(level, fluidPlacePos, fluid);

        if (placedFluid) {
            long extracted = storage.extract(aeFluidKey, AEFluidKey.AMOUNT_BLOCK, Actionable.MODULATE, src);
            if (extracted <= 0) {
                try { level.setBlockAndUpdate(fluidPlacePos, prevState); } catch (Throwable ignored) {}
                player.displayClientMessage(Component.translatable("message.meplacementtool.cannot_place"), true);
                return InteractionResult.sidedSuccess(false);
            }
            this.usePower(player, energyCost, wand);
            level.playSound(null, fluidPlacePos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
            return InteractionResult.sidedSuccess(false);
        }

        player.displayClientMessage(Component.translatable("message.meplacementtool.cannot_place"), true);
        return InteractionResult.sidedSuccess(false);
    }

    private InteractionResult handleFluidIdPlacement(UseOnContext context, Player player, ItemStack wand,
            appeng.api.storage.MEStorage storage, PlayerSource src, String fluidId, double energyCost) {
        try {
            var fid = ResourceLocation.tryParse(fluidId);
            if (fid == null) {
                player.displayClientMessage(Component.translatable("message.meplacementtool.unsupported_target"), true);
                return InteractionResult.FAIL;
            }
            
            var fluid = BuiltInRegistries.FLUID.get(fid);
            if (fluid == null || fluid == Fluids.EMPTY) {
                player.displayClientMessage(Component.translatable("message.meplacementtool.unsupported_target"), true);
                return InteractionResult.FAIL;
            }

            var aeFluidKey = AEFluidKey.of(fluid);
            return handleFluidPlacement(context, player, wand, storage, src, aeFluidKey, energyCost);
        } catch (Exception e) {
            LOGGER.warn("Error resolving fluid {}", fluidId, e);
            player.displayClientMessage(Component.translatable("message.meplacementtool.unsupported_target"), true);
            return InteractionResult.FAIL;
        }
    }

    private boolean tryPlaceFluid(Level level, BlockPos pos, net.minecraft.world.level.material.Fluid fluid) {
        var stateAtPos = level.getBlockState(pos);
        boolean isFlowing = fluid instanceof FlowingFluid;
        var legacyBlock = fluid.defaultFluidState().createLegacyBlock();

        if (!isFlowing || stateAtPos == legacyBlock) {
            return false;
        }

        boolean canPlace = stateAtPos.isAir() || stateAtPos.canBeReplaced(fluid) ||
                (stateAtPos.getBlock() instanceof LiquidBlockContainer lbc && 
                 lbc.canPlaceLiquid(null, level, pos, stateAtPos, fluid));

        if (!canPlace) return false;

        if (level.dimensionType().ultraWarm() && fluid.is(FluidTags.WATER)) {
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F, 2.6F);
            return true;
        }

        if (stateAtPos.getBlock() instanceof LiquidBlockContainer lbc && fluid == Fluids.WATER) {
            lbc.placeLiquid(level, pos, stateAtPos, ((FlowingFluid) fluid).getSource(false));
            return true;
        }

        if (stateAtPos.canBeReplaced(fluid) && !stateAtPos.liquid()) {
            level.destroyBlock(pos, true);
        }
        return level.setBlock(pos, legacyBlock, Block.UPDATE_ALL_IMMEDIATE);
    }

    private boolean tryPlaceBlock(UseOnContext context, Player player, ItemStack placeStack, 
            net.minecraft.world.item.BlockItem blockItem) {
        Level level = context.getLevel();
        ItemStack origMain = player.getMainHandItem().copy();
        ItemStack origOff = player.getOffhandItem().copy();
        
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, placeStack);
            BlockPlaceContext placeContext = new BlockPlaceContext(
                    level, player, InteractionHand.MAIN_HAND, placeStack,
                    new BlockHitResult(context.getClickLocation(), context.getClickedFace(),
                            context.getClickedPos(), context.isInside())
            );
            var result = blockItem.place(placeContext);
            return result.consumesAction();
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, origMain);
            player.setItemInHand(InteractionHand.OFF_HAND, origOff);
        }
    }

    private record PartPlacementResult(BlockPos pos, IPart part) {}

    private PartPlacementResult tryPlacePart(UseOnContext context, Player player, Level level, ItemStack placeStack) {
        ItemStack origMain = player.getMainHandItem().copy();
        ItemStack origOff = player.getOffhandItem().copy();
        
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, placeStack);
            var placement = PartPlacement.getPartPlacement(player, level, placeStack, 
                    context.getClickedPos(), context.getClickedFace(), context.getClickLocation());
            
            if (placement != null && level instanceof ServerLevel) {
                @SuppressWarnings("unchecked")
                var partItem = (IPartItem<IPart>) placeStack.getItem();
                var part = PartPlacement.placePart(player, level, partItem, 
                        placeStack.getComponents(), placement.pos(), placement.side());
                if (part != null) {
                    return new PartPlacementResult(placement.pos(), part);
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("Exception during part placement", t);
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, origMain);
            player.setItemInHand(InteractionHand.OFF_HAND, origOff);
        }
        return null;
    }

    private boolean tryPlaceFacade(UseOnContext context, Player player, Level level, ItemStack placeStack) {
        try {
            var facadeItem = (appeng.api.implementations.items.IFacadeItem) placeStack.getItem();
            var facade = facadeItem.createPartFromItemStack(placeStack, context.getClickedFace());
            if (facade == null) return false;

            var host = PartHelper.getPartHost(level, context.getClickedPos());
            if (host == null) return false;

            if (host.getPart(null) != null && host.getFacadeContainer().canAddFacade(facade)) {
                boolean added = host.getFacadeContainer().addFacade(facade);
                if (added) {
                    var blockState = facade.getBlockState();
                    var soundType = blockState.getSoundType();
                    level.playSound(null, context.getClickedPos(), soundType.getPlaceSound(),
                            SoundSource.BLOCKS, (soundType.getVolume() + 1.0F) / 2.0F, soundType.getPitch() * 0.8F);
                    host.markForSave();
                    host.markForUpdate();
                    return true;
                }
            }
        } catch (Throwable t) {
            LOGGER.error("Exception during facade placement", t);
        }
        return false;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        
        // Only open GUI when not targeting a block
        var hr = player.pick(5.0D, 0.0F, false);
        if (hr != null && hr.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
            return new InteractionResultHolder<>(InteractionResult.PASS, stack);
        }

        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            // Open wand configuration menu
            player.openMenu(new net.minecraft.world.MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.translatable("gui.meplacementtool.placement_config");
                }

                @Override
                public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id, 
                        net.minecraft.world.entity.player.Inventory inv, Player p) {
                    return new WandMenu(id, inv, stack);
                }
            }, buf -> {
                CompoundTag cfg = stack.get(ModDataComponents.PLACEMENT_CONFIG.get());
                buf.writeNbt(cfg != null ? cfg : new CompoundTag());
            });
        }

        return new InteractionResultHolder<>(InteractionResult.sidedSuccess(level.isClientSide()), stack);
    }
}
