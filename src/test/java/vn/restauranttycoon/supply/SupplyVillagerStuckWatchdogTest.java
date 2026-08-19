package vn.restauranttycoon.supply;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SupplyVillagerStuckWatchdogTest {
    @Test
    void resetsCounterOnlyAfterMeaningfulProgress() {
        SupplyVillagerStuckWatchdog watchdog = new SupplyVillagerStuckWatchdog(3, 0.5);
        assertFalse(watchdog.observe(10));
        assertFalse(watchdog.observe(9));
        assertFalse(watchdog.observe(8.9));
        assertFalse(watchdog.observe(8.8));
        assertTrue(watchdog.observe(8.7));
        watchdog.reset();
        assertFalse(watchdog.observe(8));
    }

    @Test
    void rejectsInvalidObservation() {
        SupplyVillagerStuckWatchdog watchdog = new SupplyVillagerStuckWatchdog(2, 0.1);
        assertThrows(IllegalArgumentException.class, () -> watchdog.observe(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> watchdog.observe(-1));
    }
}
