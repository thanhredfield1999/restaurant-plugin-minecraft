package vn.restauranttycoon.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PlotTriggerSettingsTest {
    private final PlotSettings plot = new PlotSettings(
            "plot_1",
            "world",
            0,
            64,
            0,
            new PlotTriggerSettings(-2, 64, -2, 2, 67, 2));

    @Test
    void containsEveryPointInsideInclusiveBlockBounds() {
        assertTrue(plot.trigger().contains("world", plot, -2.0, 64.0, -2.0));
        assertTrue(plot.trigger().contains("world", plot, 2.999, 67.999, 2.999));
    }

    @Test
    void rejectsOtherWorldAndPointsOutsideBlockBounds() {
        assertFalse(plot.trigger().contains("world_nether", plot, 0.0, 65.0, 0.0));
        assertFalse(plot.trigger().contains("world", plot, -2.001, 65.0, 0.0));
        assertFalse(plot.trigger().contains("world", plot, 3.0, 65.0, 0.0));
        assertFalse(plot.trigger().contains("world", plot, 0.0, 68.0, 0.0));
    }
}
