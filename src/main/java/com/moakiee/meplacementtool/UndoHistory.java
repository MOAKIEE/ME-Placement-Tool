package com.moakiee.meplacementtool;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
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
        PlayerEntry playerEntry = getEntryFromPlayer(player);
        LinkedList<HistoryEntry> historyEntries = playerEntry.entries;
        if (historyEntries.isEmpty()) return UndoResult.NO_HISTORY;
        HistoryEntry entry = historyEntries.getLast();

        if (!entry.world.equals(world) || !entry.withinRange(pos)) return UndoResult.OUT_OF_RANGE;

        if (entry.memoryCardApplied) return UndoResult.MEMORY_CARD_APPLIED;

        if (entry.undo(player)) {
            historyEntries.remove(entry);
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
                        
                        // Get the key to return - explicit check for cable snapshots
                        AEKey returnKey;
                        if (snapshot instanceof CablePlacementSnapshot cableSnapshot) {
                            returnKey = cableSnapshot.returnKey;
                        } else {
                            returnKey = snapshot.aeKey;
                        }
                        
                        if (returnKey != null) {
                            storage.insert(returnKey, snapshot.amount, appeng.api.config.Actionable.MODULATE, src);
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
            return world.getBlockState(pos).equals(blockState);
        }

        public boolean restore(Level world, Player player) {
            world.removeBlock(pos, false);
            return true;
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
            // Check if there's a cable part at this position
            IPartHost host = PartHelper.getPartHost(world, pos);
            if (host == null) return false;
            
            // Check if there's a cable (center part)
            IPart cablePart = host.getPart(null);
            return cablePart != null;
        }
        
        @Override
        public boolean restore(Level world, Player player) {
            IPartHost host = PartHelper.getPartHost(world, pos);
            if (host == null) return false;
            
            // Remove the cable part (center part, side = null)
            IPart cablePart = host.getPart(null);
            if (cablePart != null) {
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
    }
}
