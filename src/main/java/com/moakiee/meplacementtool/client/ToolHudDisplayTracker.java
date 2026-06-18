package com.moakiee.meplacementtool.client;

import org.jetbrains.annotations.Nullable;

class ToolHudDisplayTracker {
    private final long displayDurationMs;
    private HeldToolKey lastKey;
    private long displayStartedAtMs;

    ToolHudDisplayTracker(long displayDurationMs) {
        this.displayDurationMs = displayDurationMs;
    }

    boolean shouldDisplay(@Nullable HeldToolKey currentKey, long nowMs) {
        if (currentKey == null) {
            lastKey = null;
            displayStartedAtMs = 0L;
            return false;
        }

        if (!currentKey.equals(lastKey)) {
            lastKey = currentKey;
            displayStartedAtMs = nowMs;
            return true;
        }

        return nowMs - displayStartedAtMs <= displayDurationMs;
    }

    record HeldToolKey(String hand, int inventorySlot, String itemId, String configurationSignature) {
    }
}
