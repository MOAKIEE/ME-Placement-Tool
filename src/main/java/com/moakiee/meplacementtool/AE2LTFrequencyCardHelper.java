package com.moakiee.meplacementtool;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import com.moakiee.ae2lt.grid.wirelesslink.WirelessLinkRegistry;

/**
 * Soft-dependency helper for AE2LT Overloaded Frequency Card auto-connect.
 * Queues newly placed ME-capable blocks/parts so AE2LT can link them when the
 * player carries a bound auto-connect frequency card.
 */
public final class AE2LTFrequencyCardHelper {
    private AE2LTFrequencyCardHelper() {
    }

    /**
     * Queue AE2LT auto-connect for a placed block ({@code side == null}) or part
     * ({@code side != null}). Safe no-op when AE2LT is absent or on the client.
     */
    public static void queueAutoConnect(Player player, Level level, BlockPos pos, @Nullable Direction side) {
        if (level.isClientSide || !ModCompat.isAe2ltLoaded()) {
            return;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        try {
            queueAutoConnectInternal(serverPlayer, level, pos, side);
        } catch (Throwable t) {
            // AE2LT classes unavailable or API mismatch — ignore
        }
    }

    private static void queueAutoConnectInternal(ServerPlayer player, Level level, BlockPos pos,
            @Nullable Direction side) {
        var registry = WirelessLinkRegistry.get();
        if (registry != null) {
            registry.queueAutoConnect(player, level.dimension(), pos, side, 2);
        }
    }
}
