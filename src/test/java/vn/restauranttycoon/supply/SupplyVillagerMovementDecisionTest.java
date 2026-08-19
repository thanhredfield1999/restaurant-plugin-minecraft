package vn.restauranttycoon.supply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SupplyVillagerMovementDecisionTest {
    @Test
    void capsVectorAndReportsArrivalWithinRadius() {
        SupplyVillagerMovementDecision moving = SupplyVillagerMovementDecision.toward(
                0, 0, 0, 3, 4, 0, 0.5, 2);
        assertFalse(moving.arrived());
        assertEquals(1.2, moving.velocityX(), 0.000001);
        assertEquals(1.6, moving.velocityY(), 0.000001);

        SupplyVillagerMovementDecision arrived = SupplyVillagerMovementDecision.toward(
                0, 0, 0, 0.2, 0, 0, 0.5, 2);
        assertTrue(arrived.arrived());
        assertEquals(0, arrived.velocityX());
    }

    @Test
    void rejectsInvalidMovementLimits() {
        assertThrows(IllegalArgumentException.class, () ->
                SupplyVillagerMovementDecision.toward(0, 0, 0, 1, 0, 0, -1, 1));
        assertThrows(IllegalArgumentException.class, () ->
                SupplyVillagerMovementDecision.toward(0, 0, 0, 1, 0, 0, 1, 0));
    }
}
