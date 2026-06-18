package com.moakiee.meplacementtool.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ToolHudDisplayTrackerTest {

    @Test
    void keepsHudVisibleOnlyForConfiguredDuration() {
        ToolHudDisplayTracker tracker = new ToolHudDisplayTracker(2_000L);
        ToolHudDisplayTracker.HeldToolKey key = new ToolHudDisplayTracker.HeldToolKey("main", 0, "me_tool", "slot=0");

        assertTrue(tracker.shouldDisplay(key, 1_000L));
        assertTrue(tracker.shouldDisplay(key, 2_999L));
        assertFalse(tracker.shouldDisplay(key, 3_001L));
    }

    @Test
    void switchingBetweenSameItemTypeInDifferentMainHandSlotsShowsHudAgain() {
        ToolHudDisplayTracker tracker = new ToolHudDisplayTracker(2_000L);
        ToolHudDisplayTracker.HeldToolKey firstSlot =
                new ToolHudDisplayTracker.HeldToolKey("main", 0, "me_tool", "slot=0");
        ToolHudDisplayTracker.HeldToolKey secondSlot =
                new ToolHudDisplayTracker.HeldToolKey("main", 1, "me_tool", "slot=0");

        assertTrue(tracker.shouldDisplay(firstSlot, 1_000L));
        assertFalse(tracker.shouldDisplay(firstSlot, 3_500L));
        assertTrue(tracker.shouldDisplay(secondSlot, 3_600L));
    }

    @Test
    void changingRenderedConfigurationShowsHudAgain() {
        ToolHudDisplayTracker tracker = new ToolHudDisplayTracker(2_000L);
        ToolHudDisplayTracker.HeldToolKey initialConfig =
                new ToolHudDisplayTracker.HeldToolKey("main", 0, "multi_tool", "slot=0;count=1;direction=north");
        ToolHudDisplayTracker.HeldToolKey changedConfig =
                new ToolHudDisplayTracker.HeldToolKey("main", 0, "multi_tool", "slot=0;count=9;direction=north");

        assertTrue(tracker.shouldDisplay(initialConfig, 1_000L));
        assertFalse(tracker.shouldDisplay(initialConfig, 3_500L));
        assertTrue(tracker.shouldDisplay(changedConfig, 3_600L));
    }

    @Test
    void clearingHeldToolResetsNextDisplayWindow() {
        ToolHudDisplayTracker tracker = new ToolHudDisplayTracker(2_000L);
        ToolHudDisplayTracker.HeldToolKey key = new ToolHudDisplayTracker.HeldToolKey("main", 0, "me_tool", "slot=0");

        assertTrue(tracker.shouldDisplay(key, 1_000L));
        tracker.shouldDisplay(null, 1_500L);
        assertTrue(tracker.shouldDisplay(key, 4_000L));
    }
}
