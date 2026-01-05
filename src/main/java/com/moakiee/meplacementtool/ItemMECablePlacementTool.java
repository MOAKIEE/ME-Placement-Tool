package com.moakiee.meplacementtool;

import appeng.api.config.Actionable;
import appeng.api.implementations.menuobjects.IMenuItem;
import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.api.networking.IGrid;
import appeng.api.parts.IPartItem;
import appeng.parts.PartPlacement;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.api.util.AEColor;
import appeng.core.definitions.AEParts;
import appeng.core.definitions.ColoredItemDefinition;
import appeng.me.helpers.PlayerSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ItemMECablePlacementTool extends BasePlacementToolItem implements IMenuItem {

    public enum PlacementMode {
        LINE,
        PLANE_FILL,
        PLANE_BRANCHING
    }

    public enum CableType {
        GLASS(AEParts.GLASS_CABLE),
        COVERED(AEParts.COVERED_CABLE),
        SMART(AEParts.SMART_CABLE),
        DENSE_COVERED(AEParts.COVERED_DENSE_CABLE),
        DENSE_SMART(AEParts.SMART_DENSE_CABLE);

        private final ColoredItemDefinition<?> definition;

        CableType(ColoredItemDefinition<?> definition) {
            this.definition = definition;
        }

        public ItemStack getStack(AEColor color) {
            return definition.stack(color);
        }
    }

    public ItemMECablePlacementTool(Item.Properties props) {
        super(() -> Config.cablePlacementToolEnergyCapacity, props);
    }

    @Override
    public ItemMenuHost getMenuHost(Player player, int inventorySlot, ItemStack itemStack, BlockPos pos) {
        return new PlacementToolMenuHost(player, inventorySlot, itemStack, (p, subMenu) -> p.closeContainer());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        
        if (!player.isShiftKeyDown()) {
            // Open GUI on right-click (non-sneaking)
            if (!level.isClientSide && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                openCableToolMenu(serverPlayer, hand);
            }
            return InteractionResultHolder.success(stack);
        }

        return InteractionResultHolder.pass(stack);
    }

    /**
     * Opens the Cable Tool GUI menu for the player.
     */
    private void openCableToolMenu(net.minecraft.server.level.ServerPlayer player, InteractionHand hand) {
        int slot = hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : 40;
        ItemStack stack = player.getItemInHand(hand);
        net.minecraftforge.network.NetworkHooks.openScreen(player, new net.minecraft.world.MenuProvider() {
            @Override
            public net.minecraft.network.chat.Component getDisplayName() {
                return Component.translatable("gui.meplacementtool.cable_tool");
            }

            @Override
            public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int containerId, net.minecraft.world.entity.player.Inventory playerInventory, Player p) {
                return new CableToolMenu(containerId, playerInventory, stack, slot);
            }
        }, buf -> {
            buf.writeInt(slot);
        });
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        // Left-click clears points (handled in event handler)
        // Right-click sets points
        // Get the position next to the clicked face (like vanilla block placement)
        BlockPos clickedPos = context.getClickedPos();
        Direction face = context.getClickedFace();
        BlockPos targetPos = clickedPos.relative(face);
        
        // If the target position is not air, use clicked position instead
        if (!level.getBlockState(targetPos).isAir()) {
            targetPos = clickedPos;
        }
        
        PlacementMode mode = getMode(stack);
        BlockPos p1 = getPoint1(stack);
        BlockPos p2 = getPoint2(stack);

        if (mode == PlacementMode.PLANE_BRANCHING) {
            // Branching mode uses 3 points
            if (p1 == null) {
                setPoint1(stack, targetPos);
                player.displayClientMessage(Component.translatable("message.meplacementtool.branch_point1_set", targetPos.toShortString()), true);
            } else if (p2 == null) {
                setPoint2(stack, targetPos);
                player.displayClientMessage(Component.translatable("message.meplacementtool.branch_point2_set", targetPos.toShortString()), true);
            } else {
                setPoint3(stack, targetPos);
                player.displayClientMessage(Component.translatable("message.meplacementtool.branch_point3_set", targetPos.toShortString()), true);
                executeBranchPlacement((ServerPlayer) player, stack, level, p1, p2, targetPos);
                setPoint1(stack, null);
                setPoint2(stack, null);
                setPoint3(stack, null);
            }
        } else {
            // LINE and PLANE_FILL use 2 points
            if (p1 == null) {
                setPoint1(stack, targetPos);
                player.displayClientMessage(Component.translatable("message.meplacementtool.point1_set", targetPos.toShortString()), true);
            } else {
                setPoint2(stack, targetPos);
                player.displayClientMessage(Component.translatable("message.meplacementtool.point2_set", targetPos.toShortString()), true);
                executePlacement((ServerPlayer) player, stack, level, p1, targetPos);
                setPoint1(stack, null);
                setPoint2(stack, null);
            }
        }

        return InteractionResult.SUCCESS;
    }

    /**
     * Find an available cable key from ME storage.
     * Priority: same color > any other color
     */
    private AEItemKey findAvailableCableKey(MEStorage storage, PlayerSource src, CableType cableType, AEColor preferredColor) {
        // First try: preferred (same) color
        ItemStack preferredStack = cableType.getStack(preferredColor);
        AEItemKey preferredKey = AEItemKey.of(preferredStack);
        if (storage.extract(preferredKey, 1, Actionable.SIMULATE, src) >= 1) {
            return preferredKey;
        }
        
        // Second try: any other color
        for (AEColor c : AEColor.values()) {
            if (c == preferredColor) continue;
            ItemStack stack = cableType.getStack(c);
            AEItemKey key = AEItemKey.of(stack);
            if (storage.extract(key, 1, Actionable.SIMULATE, src) >= 1) {
                return key;
            }
        }
        
        return null; // No cable available
    }

    /**
     * Get the AEColor from a cable AEItemKey.
     */
    private AEColor getColorFromCableKey(AEItemKey key, CableType cableType) {
        for (AEColor c : AEColor.values()) {
            ItemStack stack = cableType.getStack(c);
            if (AEItemKey.of(stack).equals(key)) {
                return c;
            }
        }
        return AEColor.TRANSPARENT;
    }

    private void executePlacement(ServerPlayer player, ItemStack tool, Level level, BlockPos p1, BlockPos p2) {
        PlacementMode mode = getMode(tool);
        CableType cableType = getCableType(tool);
        boolean hasUpgrade = hasUpgrade(tool);

        // Determine effective color and whether to consume dye
        ColorLogicResult colorLogic = determineColorLogic(player, tool);
        AEColor color = colorLogic.color;

        List<BlockPos> positions = calculatePositions(p1, p2, mode);
        if (positions.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.meplacementtool.no_positions"), true);
            return;
        }

        // Check Power
        double energyCost = Config.cablePlacementToolEnergyCost * positions.size();
        if (!this.hasPower(player, energyCost, tool)) {
            player.displayClientMessage(Component.translatable("message.meplacementtool.device_not_powered"), true);
            return;
        }

        // Check Network
        IGrid grid = this.getLinkedGrid(tool, level, player);
        if (grid == null) return;
        MEStorage storage = grid.getStorageService().getInventory();
        PlayerSource src = new PlayerSource(player);

        // The actual cable to place
        ItemStack placeCableStack = cableType.getStack(color);

        // Place Cables and track for undo
        // Dye consumption is now dynamic based on extracted cable color
        int placedCount = 0;
        int dyeConsumed = 0;
        List<UndoHistory.CablePlacementSnapshot> placedSnapshots = new java.util.ArrayList<>();

        for (BlockPos pos : positions) {
            if (!level.getBlockState(pos).isAir()) continue;

            // Find available cable (priority: same color > any color)
            AEItemKey keyToExtract = findAvailableCableKey(storage, src, cableType, color);
            if (keyToExtract == null) {
                player.displayClientMessage(Component.translatable("message.meplacementtool.missing_cable", placeCableStack.getHoverName()), true);
                break;
            }

            // Check if we need dye for this cable (only if extracted color != target color)
            AEColor extractedColor = getColorFromCableKey(keyToExtract, cableType);
            boolean needsDyeForThis = colorLogic.needsDye && (extractedColor != color);

            // If dye is needed, try to consume it
            if (needsDyeForThis && color != AEColor.TRANSPARENT) {
                // Check dye availability (1 dye per 8 cables, we check per cable here)
                // For simplicity, consume 1 dye per 8 cables that need dyeing
                if ((dyeConsumed == 0 || placedCount % 8 == 0) && dyeConsumed < (placedCount / 8) + 1) {
                    if (!consumeDye(player, storage, src, color, 1)) {
                        player.displayClientMessage(Component.translatable("message.meplacementtool.missing_dye", 1, DyeItem.byColor(color.dye).getDescription()), true);
                        break;
                    }
                    dyeConsumed++;
                }
            }

            // Place
            if (placeCable(player, (ServerLevel) level, pos, placeCableStack)) {
                storage.extract(keyToExtract, 1, Actionable.MODULATE, src);
                placedCount++;
                // Record for undo - return the same type of cable that was extracted
                placedSnapshots.add(new UndoHistory.CablePlacementSnapshot(pos, cableType, keyToExtract));
            }
        }

        if (placedCount > 0) {
            this.usePower(player, Config.cablePlacementToolEnergyCost * placedCount, tool);
            player.displayClientMessage(Component.translatable("message.meplacementtool.placed_count", placedCount), true);
            
            // Add to undo history
            MEPlacementToolMod.instance.undoHistory.addCablePlacement(player, level, placedSnapshots);
        }
    }

    private boolean placeCable(ServerPlayer player, ServerLevel level, BlockPos pos, ItemStack cableStack) {
        try {
            IPartItem<?> partItem = (IPartItem<?>) cableStack.getItem();
            if (PartPlacement.placePart(player, level, partItem, null, pos, Direction.UP) != null) {
                return true;
            }
        } catch (Exception e) {
            // Ignore
        }
        return false;
    }

    /**
     * Execute branch placement using 3 points.
     */
    private void executeBranchPlacement(ServerPlayer player, ItemStack tool, Level level, BlockPos p1, BlockPos p2, BlockPos p3) {
        CableType cableType = getCableType(tool);
        boolean hasUpgrade = hasUpgrade(tool);
        
        // Determine effective color and whether to consume dye
        ColorLogicResult colorLogic = determineColorLogic(player, tool);
        AEColor color = colorLogic.color;

        List<BlockPos> positions = calculateBranchPositions(p1, p2, p3);
        if (positions.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.meplacementtool.no_positions"), true);
            return;
        }

        // Check Power
        double energyCost = Config.mePlacementToolEnergyCost * positions.size();
        if (!this.hasPower(player, energyCost, tool)) {
            player.displayClientMessage(Component.translatable("message.meplacementtool.device_not_powered"), true);
            return;
        }

        // Check Network
        IGrid grid = this.getLinkedGrid(tool, level, player);
        if (grid == null) return;
        MEStorage storage = grid.getStorageService().getInventory();
        PlayerSource src = new PlayerSource(player);

        // The actual cable to place
        ItemStack placeCableStack = cableType.getStack(color);

        // Place Cables and track for undo
        // Dye consumption is now dynamic based on extracted cable color
        int placedCount = 0;
        int dyeConsumed = 0;
        List<UndoHistory.CablePlacementSnapshot> placedSnapshots = new java.util.ArrayList<>();
        
        for (BlockPos pos : positions) {
            if (!level.getBlockState(pos).isAir()) continue;

            // Find available cable (priority: same color > any color)
            AEItemKey keyToExtract = findAvailableCableKey(storage, src, cableType, color);
            if (keyToExtract == null) {
                player.displayClientMessage(Component.translatable("message.meplacementtool.missing_cable", placeCableStack.getHoverName()), true);
                break;
            }

            // Check if we need dye for this cable (only if extracted color != target color)
            AEColor extractedColor = getColorFromCableKey(keyToExtract, cableType);
            boolean needsDyeForThis = colorLogic.needsDye && (extractedColor != color);

            // If dye is needed, try to consume it
            if (needsDyeForThis && color != AEColor.TRANSPARENT) {
                // Check dye availability (1 dye per 8 cables that need dyeing)
                if ((dyeConsumed == 0 || placedCount % 8 == 0) && dyeConsumed < (placedCount / 8) + 1) {
                    if (!consumeDye(player, storage, src, color, 1)) {
                        player.displayClientMessage(Component.translatable("message.meplacementtool.missing_dye", 1, DyeItem.byColor(color.dye).getDescription()), true);
                        break;
                    }
                    dyeConsumed++;
                }
            }

            if (placeCable(player, (ServerLevel) level, pos, placeCableStack)) {
                storage.extract(keyToExtract, 1, Actionable.MODULATE, src);
                placedCount++;
                // Record for undo - return the same type of cable that was extracted
                placedSnapshots.add(new UndoHistory.CablePlacementSnapshot(pos, cableType, keyToExtract));
            }
        }

        if (placedCount > 0) {
            this.usePower(player, Config.mePlacementToolEnergyCost * placedCount, tool);
            player.displayClientMessage(Component.translatable("message.meplacementtool.placed_count", placedCount), true);
            
            // Add to undo history
            MEPlacementToolMod.instance.undoHistory.addCablePlacement(player, level, placedSnapshots);
        }
    }

    /**
     * Calculate positions for cable placement based on the selected mode.
     * LINE mode: only axis-aligned or smart-snap lines (no diagonal stepping).
     * PLANE_FILL: fill a rectangular area.
     * PLANE_BRANCHING: calculated separately with 3 points.
     */
    public static List<BlockPos> calculatePositions(BlockPos p1, BlockPos p2, PlacementMode mode) {
        List<BlockPos> list = new ArrayList<>();
        int x1 = p1.getX(), y1 = p1.getY(), z1 = p1.getZ();
        int x2 = p2.getX(), y2 = p2.getY(), z2 = p2.getZ();

        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int dz = Math.abs(z2 - z1);

        if (mode == PlacementMode.LINE) {
            // Determine main axis and check if we should snap
            int maxDelta = Math.max(dx, Math.max(dy, dz));
            
            // Snap threshold: if main axis >= 10 and deviations <= 3, snap to main axis
            final int SNAP_MIN_LENGTH = 10;
            final int SNAP_MAX_DEVIATION = 3;
            
            if (maxDelta == dx && dx >= SNAP_MIN_LENGTH && dy <= SNAP_MAX_DEVIATION && dz <= SNAP_MAX_DEVIATION) {
                // Snap to X axis
                int min = Math.min(x1, x2);
                int max = Math.max(x1, x2);
                for (int x = min; x <= max; x++) list.add(new BlockPos(x, y1, z1));
            } else if (maxDelta == dy && dy >= SNAP_MIN_LENGTH && dx <= SNAP_MAX_DEVIATION && dz <= SNAP_MAX_DEVIATION) {
                // Snap to Y axis
                int min = Math.min(y1, y2);
                int max = Math.max(y1, y2);
                for (int y = min; y <= max; y++) list.add(new BlockPos(x1, y, z1));
            } else if (maxDelta == dz && dz >= SNAP_MIN_LENGTH && dx <= SNAP_MAX_DEVIATION && dy <= SNAP_MAX_DEVIATION) {
                // Snap to Z axis
                int min = Math.min(z1, z2);
                int max = Math.max(z1, z2);
                for (int z = min; z <= max; z++) list.add(new BlockPos(x1, y1, z));
            } 
            // Standard axis-aligned lines (exact alignment only) - NO diagonal lines
            else if (dx > 0 && dy == 0 && dz == 0) {
                int min = Math.min(x1, x2);
                int max = Math.max(x1, x2);
                for (int x = min; x <= max; x++) list.add(new BlockPos(x, y1, z1));
            } else if (dx == 0 && dy > 0 && dz == 0) {
                int min = Math.min(y1, y2);
                int max = Math.max(y1, y2);
                for (int y = min; y <= max; y++) list.add(new BlockPos(x1, y, z1));
            } else if (dx == 0 && dy == 0 && dz > 0) {
                int min = Math.min(z1, z2);
                int max = Math.max(z1, z2);
                for (int z = min; z <= max; z++) list.add(new BlockPos(x1, y1, z));
            }
            // If not meeting any criteria, return empty list (no diagonal lines allowed)
        } else if (mode == PlacementMode.PLANE_FILL) {
            // Fill area - works for any 3D box
            int minX = Math.min(x1, x2), maxX = Math.max(x1, x2);
            int minY = Math.min(y1, y2), maxY = Math.max(y1, y2);
            int minZ = Math.min(z1, z2), maxZ = Math.max(z1, z2);
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        list.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        // PLANE_BRANCHING is handled by calculateBranchPositions with 3 points
        return list;
    }

    /**
     * Calculate branching positions using 3 points.
     * Point 1 (A): Start point
     * Point 2 (B): Determines main trunk direction and branch interval (interval = distance - 1)
     * Point 3 (C): Forms a plane with Point 1, determines branch length
     */
    public static List<BlockPos> calculateBranchPositions(BlockPos p1, BlockPos p2, BlockPos p3) {
        List<BlockPos> list = new ArrayList<>();
        
        int x1 = p1.getX(), y1 = p1.getY(), z1 = p1.getZ();
        int x2 = p2.getX(), y2 = p2.getY(), z2 = p2.getZ();
        int x3 = p3.getX(), y3 = p3.getY(), z3 = p3.getZ();
        
        // P1 to P2 determines trunk direction and branch interval
        int dx12 = x2 - x1;
        int dy12 = y2 - y1;
        int dz12 = z2 - z1;
        
        // Branch interval is the distance from P1 to P2
        int interval = Math.max(1, Math.abs(dx12) + Math.abs(dz12)); // Manhattan distance in XZ plane
        if (interval <= 0) interval = 1;
        
        // Determine main trunk direction (X or Z) based on P1-P2
        boolean trunkAlongX = Math.abs(dx12) >= Math.abs(dz12);
        int trunkDir = trunkAlongX ? Integer.signum(dx12) : Integer.signum(dz12);
        if (trunkDir == 0) trunkDir = 1;
        
        // P1 to P3 determines the extent of the plane
        int dx13 = x3 - x1;
        int dz13 = z3 - z1;
        
        // Calculate trunk length (in the direction of P1-P3, along the trunk axis)
        int trunkLength, branchLength;
        int branchDir;
        
        if (trunkAlongX) {
            // Trunk along X, branches along Z
            trunkLength = Math.abs(dx13);
            branchLength = Math.abs(dz13);
            branchDir = dz13 == 0 ? 1 : Integer.signum(dz13);
        } else {
            // Trunk along Z, branches along X  
            trunkLength = Math.abs(dz13);
            branchLength = Math.abs(dx13);
            branchDir = dx13 == 0 ? 1 : Integer.signum(dx13);
        }
        
        // Generate trunk and branches
        for (int t = 0; t <= trunkLength; t++) {
            int trunkX = trunkAlongX ? x1 + t * trunkDir : x1;
            int trunkZ = trunkAlongX ? z1 : z1 + t * trunkDir;
            
            // Add trunk position
            list.add(new BlockPos(trunkX, y1, trunkZ));
            
            // Add branches at intervals (starting from first position, every 'interval' blocks)
            if (t % interval == 0) {
                for (int b = 1; b <= branchLength; b++) {
                    int branchX = trunkAlongX ? trunkX : trunkX + b * branchDir;
                    int branchZ = trunkAlongX ? trunkZ + b * branchDir : trunkZ;
                    list.add(new BlockPos(branchX, y1, branchZ));
                }
            }
        }
        
        return list;
    }

    public static void setPoint1(ItemStack stack, @Nullable BlockPos pos) {
        CompoundTag tag = stack.getOrCreateTag();
        if (pos == null) {
            tag.remove("Point1");
        } else {
            tag.put("Point1", NbtUtils.writeBlockPos(pos));
        }
    }

    @Nullable
    public static BlockPos getPoint1(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("Point1")) {
            return NbtUtils.readBlockPos(tag.getCompound("Point1"));
        }
        return null;
    }

    public static void setPoint2(ItemStack stack, @Nullable BlockPos pos) {
        CompoundTag tag = stack.getOrCreateTag();
        if (pos == null) {
            tag.remove("Point2");
        } else {
            tag.put("Point2", NbtUtils.writeBlockPos(pos));
        }
    }

    @Nullable
    public static BlockPos getPoint2(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("Point2")) {
            return NbtUtils.readBlockPos(tag.getCompound("Point2"));
        }
        return null;
    }

    public static void setPoint3(ItemStack stack, @Nullable BlockPos pos) {
        CompoundTag tag = stack.getOrCreateTag();
        if (pos == null) {
            tag.remove("Point3");
        } else {
            tag.put("Point3", NbtUtils.writeBlockPos(pos));
        }
    }

    @Nullable
    public static BlockPos getPoint3(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("Point3")) {
            return NbtUtils.readBlockPos(tag.getCompound("Point3"));
        }
        return null;
    }


    public static PlacementMode getMode(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("Mode")) {
            return PlacementMode.values()[tag.getInt("Mode")];
        }
        return PlacementMode.LINE;
    }

    public static void setMode(ItemStack stack, PlacementMode mode) {
        stack.getOrCreateTag().putInt("Mode", mode.ordinal());
    }

    public static CableType getCableType(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("CableType")) {
            return CableType.values()[tag.getInt("CableType")];
        }
        return CableType.GLASS;
    }

    public static void setCableType(ItemStack stack, CableType type) {
        stack.getOrCreateTag().putInt("CableType", type.ordinal());
    }

    public static AEColor getColor(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("Color")) {
            return AEColor.values()[tag.getInt("Color")];
        }
        return AEColor.TRANSPARENT;
    }

    public static void setColor(ItemStack stack, AEColor color) {
        stack.getOrCreateTag().putInt("Color", color.ordinal());
    }

    public static boolean hasUpgrade(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean("HasUpgrade");
    }

    public static void setUpgrade(ItemStack stack, boolean has) {
        stack.getOrCreateTag().putBoolean("HasUpgrade", has);
    }

    private static class ColorLogicResult {
        AEColor color;
        boolean needsDye;

        ColorLogicResult(AEColor color, boolean needsDye) {
            this.color = color;
            this.needsDye = needsDye;
        }
    }

    @Nullable
    private AEColor getDyeColorFromStack(ItemStack stack) {
        if (!stack.isEmpty() && stack.getItem() instanceof DyeItem dyeItem) {
            return AEColor.fromDye(dyeItem.getDyeColor());
        }
        return null;
    }

    private ColorLogicResult determineColorLogic(Player player, ItemStack tool) {
        ItemStack offhandStack = player.getItemInHand(InteractionHand.OFF_HAND);
        AEColor offhandDyeColor = getDyeColorFromStack(offhandStack);
        boolean hasUpgrade = hasUpgrade(tool);
        AEColor selectedColor = getColor(tool);

        if (offhandDyeColor != null) {
            // Case 1 & 2: Offhand has dye.
            if (hasUpgrade) {
                // Case 1: Has Upgrade -> Use Offhand Color, No Cost.
                return new ColorLogicResult(offhandDyeColor, false);
            } else {
                // Case 2: No Upgrade -> Use Offhand Color, Consume Dye.
                return new ColorLogicResult(offhandDyeColor, true);
            }
        } else {
            // Case 3 & 4: Offhand has NO dye.
            if (hasUpgrade) {
                // Case 3: Has Upgrade -> Use Selected Color, No Cost.
                return new ColorLogicResult(selectedColor, false);
            } else {
                // Case 4: No Upgrade -> Use Transparent (Fluix), No Dye Cost.
                return new ColorLogicResult(AEColor.TRANSPARENT, false);
            }
        }
    }

    private boolean consumeDye(Player player, MEStorage storage, PlayerSource src, AEColor color, int amount) {
        if (amount <= 0 || color == AEColor.TRANSPARENT) return true;

        DyeItem dyeItem = (DyeItem) DyeItem.byColor(color.dye);
        AEItemKey dyeKey = AEItemKey.of(dyeItem);

        // 1. Try AE Network
        long extractedFromAE = storage.extract(dyeKey, amount, Actionable.SIMULATE, src);
        if (extractedFromAE >= amount) {
            storage.extract(dyeKey, amount, Actionable.MODULATE, src);
            return true;
        }

        // 2. Try Player Inventory (excluding offhand for now to handle it last)
        int remaining = amount;
        
        // This is a simplified check. For strict ordering (AE -> Inv -> Offhand),
        // we should try to satisfy demand from AE first, then Inv, then Offhand.
        // However, AE extract is all-or-nothing usually for simplicity in tools.
        // Let's implement partial extraction priority.

        // Phase 1: AE
        long takenFromAE = storage.extract(dyeKey, remaining, Actionable.MODULATE, src);
        remaining -= takenFromAE;
        if (remaining <= 0) return true;

        // Phase 2: Player Main Inventory
        // We scan main inventory (0-35)
        for (int i = 0; i < player.getInventory().items.size(); i++) {
            ItemStack slotStack = player.getInventory().items.get(i);
            if (getDyeColorFromStack(slotStack) == color) {
                int take = Math.min(remaining, slotStack.getCount());
                slotStack.shrink(take);
                remaining -= take;
                if (remaining <= 0) break;
            }
        }
        if (remaining <= 0) return true;

        // Phase 3: Offhand (Right Hand / Shield Slot)
        ItemStack offhand = player.getItemInHand(InteractionHand.OFF_HAND);
        if (getDyeColorFromStack(offhand) == color) {
             int take = Math.min(remaining, offhand.getCount());
             offhand.shrink(take);
             remaining -= take;
        }

        return remaining <= 0;
    }
}
