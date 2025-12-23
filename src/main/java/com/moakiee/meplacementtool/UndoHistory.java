package com.moakiee.meplacementtool;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import java.util.*;

public class UndoHistory
{
    private final HashMap<UUID, PlayerEntry> history;
    private static final int MAX_HISTORY_SIZE = 1;

    public UndoHistory() {
        history = new HashMap<>();
    }

    private PlayerEntry getEntryFromPlayer(Player player) {
        return history.computeIfAbsent(player.getUUID(), k -> new PlayerEntry());
    }

    public void add(Player player, Level world, List<PlacementSnapshot> placeSnapshots) {
        LinkedList<HistoryEntry> list = getEntryFromPlayer(player).entries;
        list.clear();
        list.add(new HistoryEntry(placeSnapshots, world));
    }

    public void removePlayer(Player player) {
        history.remove(player.getUUID());
    }

    public boolean undo(Player player, Level world, BlockPos pos) {
        PlayerEntry playerEntry = getEntryFromPlayer(player);
        LinkedList<HistoryEntry> historyEntries = playerEntry.entries;
        if(historyEntries.isEmpty()) return false;
        HistoryEntry entry = historyEntries.getLast();

        if(!entry.world.equals(world) || !entry.withinRange(pos)) return false;

        if(entry.undo(player)) {
            historyEntries.remove(entry);
            return true;
        }
        return false;
    }

    private static class PlayerEntry
    {
        public final LinkedList<HistoryEntry> entries;

        public PlayerEntry() {
            entries = new LinkedList<>();
        }
    }

    private static class HistoryEntry
    {
        public final List<PlacementSnapshot> placeSnapshots;
        public final Level world;

        public HistoryEntry(List<PlacementSnapshot> placeSnapshots, Level world) {
            this.placeSnapshots = placeSnapshots;
            this.world = world;
        }

        public Set<BlockPos> getBlockPositions() {
            Set<BlockPos> positions = new HashSet<>();
            for(PlacementSnapshot snapshot : placeSnapshots) {
                positions.add(snapshot.pos);
            }
            return positions;
        }

        public boolean withinRange(BlockPos pos) {
            Set<BlockPos> positions = getBlockPositions();

            if(positions.contains(pos)) return true;

            for(BlockPos p : positions) {
                if(pos.closerThan(p, 3)) return true;
            }
            return false;
        }

        public boolean undo(Player player) {
            ItemStack wand = player.getMainHandItem();
            if(wand.isEmpty() || wand.getItem() != MEPlacementToolMod.MULTIBLOCK_PLACEMENT_TOOL.get()) {
                return false;
            }

            appeng.api.networking.IGrid grid = null;
            if(wand.getItem() instanceof appeng.items.tools.powered.WirelessTerminalItem wirelessTerminal) {
                grid = wirelessTerminal.getLinkedGrid(wand, world, player);
            }

            if(grid == null) {
                return false;
            }

            for(PlacementSnapshot snapshot : placeSnapshots) {
                if(!snapshot.canRestore(world, player)) return false;
            }
            for(PlacementSnapshot snapshot : placeSnapshots) {
                if(snapshot.restore(world, player)) {
                    if(!player.isCreative()) {
                        var storage = grid.getStorageService().getInventory();
                        var src = new appeng.me.helpers.PlayerSource(player);
                        if(snapshot.aeKey != null) {
                            storage.insert(snapshot.aeKey, snapshot.amount, appeng.api.config.Actionable.MODULATE, src);
                        }
                    }
                }
            }

            world.playSound(null, player.blockPosition(), SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);

            return true;
        }
    }

    public static class PlacementSnapshot
    {
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
    }
}
