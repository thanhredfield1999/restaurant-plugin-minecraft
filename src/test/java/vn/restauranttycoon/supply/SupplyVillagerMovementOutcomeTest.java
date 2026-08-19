package vn.restauranttycoon.supply;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SupplyVillagerMovementOutcomeTest {
    @Test
    void exposesExplicitTerminalAndNonTerminalOutcomes() {
        assertEquals(3, SupplyVillagerMovementOutcome.values().length);
        assertEquals(SupplyVillagerMovementOutcome.STUCK,
                SupplyVillagerMovementOutcome.valueOf("STUCK"));
    }
}
