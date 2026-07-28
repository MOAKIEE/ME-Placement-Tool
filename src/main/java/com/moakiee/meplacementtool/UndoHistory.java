package com.moakiee.meplacementtool;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import appeng.api.networking.IGrid;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;

import java.util.*;

/**
 * Tracks placement history for undo functionality.
 * Matches 1.20.1 implementation - stores list of PlacementSnapshots per player.
 */
public class UndoHistory {
    private final HashMap<UUID, PlayerEntry> history;

    public UndoHistory() {
        history = new HashMap<>();
    }

    private PlayerEntry getEntryFromPlayer(Player player) {
        return history.computeIfAbsent(player.getUUID(), k -> new PlayerEntry());
    }

    public void add(Player player, Level world, List<PlacementSnapshot> placeSnapshots) {
        add(player, world, placeSnapshots, false);
    }

    public void add(Player player, Level world, List<PlacementSnapshot> placeSnapshots, boolean memoryCardApplied) {
        LinkedList<HistoryEntry> list = getEntryFromPlayer(player).entries;
        list.clear();
        list.add(new HistoryEntry(placeSnapshots, world, memoryCardApplied, false));
    }

    /**
     * Add cable placement history.
     * When undoing cable placements, we return the extracted cable back to the network.
     */
    public void addCablePlacement(Player player, Level world, List<CablePlacementSnapshot> cableSnapshots) {
        LinkedList<HistoryEntry> list = getEntryFromPlayer(player).entries;
        list.clear();
        // Convert CablePlacementSnapshot to PlacementSnapshot for storage
        List<PlacementSnapshot> snapshots = new ArrayList<>();
        for (CablePlacementSnapshot cableSnap : cableSnapshots) {
            snapshots.add(cableSnap);
        }
        list.add(new HistoryEntry(snapshots, world, false, true));
    }

    public void removePlayer(Player player) {
        history.remove(player.getUUID());
    }

    public void removePlayer(UUID uuid) {
        history.remove(uuid);
    }

    /** Clears all stored history. Called on server shutdown to release Level references. */
    public void clear() {
        history.clear();
    }

    /**
     * Result of undo operation
     */
    public enum UndoResult {
        SUCCESS,
        NO_HISTORY,
        OUT_OF_RANGE,
        MEMORY_CARD_APPLIED,
        FAILED
    }

    public UndoResult undoWithResult(Player player, Level world, BlockPos pos) {
        PlayerEntry playerEntry = history.get(player.getUUID());
        if (playerEntry == null || playerEntry.entries.isEmpty()) return UndoResult.NO_HISTORY;
        LinkedList<HistoryEntry> historyEntries = playerEntry.entries;
        HistoryEntry entry = historyEntries.getLast();

        if (!entry.world.equals(world) || !entry.withinRange(pos)) return UndoResult.OUT_OF_RANGE;

        if (entry.memoryCardApplied) return UndoResult.MEMORY_CARD_APPLIED;

        if (entry.undo(player)) {
            historyEntries.remove(entry);
            if (historyEntries.isEmpty()) {
                history.remove(player.getUUID());
            }
            return UndoResult.SUCCESS;
        }
        return UndoResult.FAILED;
    }

    public boolean undo(Player player, Level world, BlockPos pos) {
        return undoWithResult(player, world, pos) == UndoResult.SUCCESS;
    }

    private static class PlayerEntry {
        public final LinkedList<HistoryEntry> entries;

        public PlayerEntry() {
            entries = new LinkedList<>();
        }
    }

    private static class HistoryEntry {
        public final List<PlacementSnapshot> placeSnapshots;
        public final Level world;
        public final boolean memoryCardApplied;
        public final boolean isCablePlacement;

        public HistoryEntry(List<PlacementSnapshot> placeSnapshots, Level world, boolean memoryCardApplied, boolean isCablePlacement) {
            this.placeSnapshots = placeSnapshots;
            this.world = world;
            this.memoryCardApplied = memoryCardApplied;
            this.isCablePlacement = isCablePlacement;
        }

        public Set<BlockPos> getBlockPositions() {
            Set<BlockPos> positions = new HashSet<>();
            for (PlacementSnapshot snapshot : placeSnapshots) {
                positions.add(snapshot.pos);
            }
            return positions;
        }

        public boolean withinRange(BlockPos pos) {
            Set<BlockPos> positions = getBlockPositions();

            if (positions.contains(pos)) return true;

            for (BlockPos p : positions) {
                if (pos.closerThan(p, 3)) return true;
            }
            return false;
        }

        public boolean undo(Player player) {
            ItemStack wand = player.getMainHandItem();
            
            // Check if holding the correct tool
            boolean holdingMultiblockTool = wand.getItem() == MEPlacementToolMod.MULTIBLOCK_PLACEMENT_TOOL.get();
            boolean holdingCableTool = wand.getItem() == MEPlacementToolMod.ME_CABLE_PLACEMENT_TOOL.get();
            
            if (isCablePlacement && !holdingCableTool) {
                return false;
            }
            if (!isCablePlacement && !holdingMultiblockTool) {
                return false;
            }

            IGrid grid = null;
            if (wand.getItem() instanceof BasePlacementToolItem placementTool) {
                grid = placementTool.getLinkedGrid(wand, world, player);
            }

            if (grid == null) {
                return false;
            }

            // Check all snapshots can be restored
            for (PlacementSnapshot snapshot : placeSnapshots) {
                if (!snapshot.canRestore(world, player)) return false;
            }
            
            // Perform undo
            for (PlacementSnapshot snapshot : placeSnapshots) {
                if (snapshot.restore(world, player)) {
                    if (!player.isCreative()) {
                        var storage = grid.getStorageService().getInventory();
                        var src = new appeng.me.helpers.PlayerSource(player);
                        
                        AEKey returnKey = snapshot.getReturnKey();
                        if (returnKey != null) {
                            long inserted = storage.insert(returnKey, snapshot.amount,
                                    appeng.api.config.Actionable.MODULATE, src);
                            long leftover = snapshot.amount - inserted;
                            // Network storage full - drop the remainder so items are not lost
                            if (leftover > 0 && returnKey instanceof AEItemKey itemKey) {
                                while (leftover > 0) {
                                    int count = (int) Math.min(leftover, itemKey.getItem().getDefaultMaxStackSize());
                                    ItemStack drop = itemKey.toStack(count);
                                    if (!player.getInventory().add(drop)) {
                                        player.drop(drop, false);
                                    }
                                    leftover -= count;
                                }
                            }
                        }
                    }
                }
            }

            world.playSound(null, player.blockPosition(), SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);

            return true;
        }
    }

    /**
     * Snapshot of a single block placement for potential undo
     */
    public static class PlacementSnapshot {
        public final BlockState blockState;
        public final BlockPos pos;
        public final ItemStack placedItem;
        public final AEKey aeKey;
        public final long amount;

        public PlacementSnapshot(BlockState blockState, BlockPos pos, ItemStack placedItem, AEKey aeKey, long amount) {
            this.blockState = blockState;
            this.pos = pos;
            this.placedItem = placedItem;
            this.aeKey = aeKey;
            this.amount = amount;
        }

        public boolean canRestore(Level world, Player player) {
            if (!world.getBlockState(pos).equals(blockState)) {
                return false;
            }
            if (!canModify(player, world, pos, Direction.UP)) {
                return false;
            }
            // Refuse to undo blocks whose container inventory is no longer empty,
            // otherwise the contents would be silently destroyed.
            var be = world.getBlockEntity(pos);
            if (be instanceof Container container && !container.isEmpty()) {
                return false;
            }
            if (hasStoredCapabilities(world, pos)) {
                return false;
            }
            return true;
        }

        public boolean restore(Level world, Player player) {
            return world.removeBlock(pos, false);
        }
        
        /**
         * Get the key to return to AE network when undoing.
         * Override in subclasses to return different items.
         */
        public AEKey getReturnKey() {
            return aeKey;
        }
    }
    
    /**
     * Snapshot for AE2 side-part placements (planes, buses, etc.).
     * Removes only the part on the recorded side instead of breaking the whole block.
     */
    public static class PartPlacementSnapshot extends PlacementSnapshot {
        public final Direction side;
        public final AEKey returnKey;

        public PartPlacementSnapshot(BlockPos pos, Direction side, AEKey returnKey) {
            super(null, pos, ItemStack.EMPTY, null, 1);
            this.side = side;
            this.returnKey = returnKey;
        }

        @Override
        public boolean canRestore(Level world, Player player) {
            if (!canModify(player, world, pos, side)) {
                return false;
            }
            IPartHost host = PartHelper.getPartHost(world, pos);
            return host != null && partMatchesKey(host.getPart(side), returnKey);
        }

        @Override
        public boolean restore(Level world, Player player) {
            IPartHost host = PartHelper.getPartHost(world, pos);
            if (host == null || !partMatchesKey(host.getPart(side), returnKey)) {
                return false;
            }

            host.removePartFromSide(side);
            host.markForUpdate();
            if (host.isEmpty()) {
                host.cleanup();
            }
            return true;
        }

        @Override
        public AEKey getReturnKey() {
            return returnKey;
        }
    }

    /**
     * Verify the part currently in the world is the same item as the one that was placed.
     * Prevents duping by swapping in a cheaper part before undoing.
     */
    private static boolean partMatchesKey(IPart part, AEKey key) {
        if (part == null) {
            return false;
        }
        if (!(key instanceof AEItemKey itemKey)) {
            // Unknown key type - be conservative and refuse
            return false;
        }
        try {
            List<ItemStack> drops = new ArrayList<>();
            part.addPartDrop(drops, true);
            for (ItemStack drop : drops) {
                AEItemKey dropKey = AEItemKey.of(drop);
                if (itemKey.equals(dropKey)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
            // A part that cannot be serialized safely must not be removed by undo.
        }
        return false;
    }

    /**
     * Snapshot for cable placements that handles AE2 Part removal.
     */
    public static class CablePlacementSnapshot extends PlacementSnapshot {
        public final ItemMECablePlacementTool.CableType cableType;
        public final AEKey returnKey; // The extracted cable key for return
        
        public CablePlacementSnapshot(BlockPos pos, ItemMECablePlacementTool.CableType cableType, AEKey returnKey) {
            super(null, pos, ItemStack.EMPTY, null, 1);
            this.cableType = cableType;
            this.returnKey = returnKey;
        }
        
        @Override
        public boolean canRestore(Level world, Player player) {
            if (!canModify(player, world, pos, Direction.UP)) {
                return false;
            }
            // Check the center part is a cable of the recorded type (any color of this CableType).
            IPartHost host = PartHelper.getPartHost(world, pos);
            if (host == null) return false;
            return cableMatchesType(host.getPart(null), cableType);
        }
        
        @Override
        public boolean restore(Level world, Player player) {
            IPartHost host = PartHelper.getPartHost(world, pos);
            if (host == null) return false;
            
            // Remove the cable part (center part, side = null)
            IPart cablePart = host.getPart(null);
            if (cableMatchesType(cablePart, cableType)) {
                host.removePartFromSide(null);
                host.markForUpdate();
                
                // If host is now empty, cleanup the block entity
                if (host.isEmpty()) {
                    host.cleanup();
                }
                return true;
            }
            return false;
        }
        
        @Override
        public AEKey getReturnKey() {
            return returnKey;
        }

        /**
         * The placed cable may have a different color than the extracted key (recolor-on-place),
         * so match against any color of the recorded cable type.
         */
        private static boolean cableMatchesType(IPart part, ItemMECablePlacementTool.CableType type) {
            if (part == null) return false;
            var partItem = part.getPartItem().asItem();
            for (appeng.api.util.AEColor color : appeng.api.util.AEColor.values()) {
                if (type.getStack(color).getItem() == partItem) {
                    return true;
                }
            }
            return false;
        }
    }

    private static boolean canModify(Player player, Level world, BlockPos pos, Direction side) {
        return world.mayInteract(player, pos)
                && player.mayUseItemAt(pos, side, player.getMainHandItem());
    }

    /**
     * Refuse to remove a block if any commonly exposed storage capability contains data.
     * Capability lookup failures are treated conservatively as non-empty.
     */
    private static boolean hasStoredCapabilities(Level world, BlockPos pos) {
        try {
            for (Direction side : Direction.values()) {
                var itemHandler = world.getCapability(Capabilities.ItemHandler.BLOCK, pos, side);
                if (itemHandler != null) {
                    for (int slot = 0; slot < itemHandler.getSlots(); slot++) {
                        if (!itemHandler.getStackInSlot(slot).isEmpty()) {
                            return true;
                        }
                    }
                }

                var fluidHandler = world.getCapability(Capabilities.FluidHandler.BLOCK, pos, side);
                if (fluidHandler != null) {
                    for (int tank = 0; tank < fluidHandler.getTanks(); tank++) {
                        if (!fluidHandler.getFluidInTank(tank).isEmpty()) {
                            return true;
                        }
                    }
                }

                var energyStorage = world.getCapability(Capabilities.EnergyStorage.BLOCK, pos, side);
                if (energyStorage != null && energyStorage.getEnergyStored() > 0) {
                    return true;
                }
            }
            return false;
        } catch (Throwable ignored) {
            return true;
        }
    }
}
