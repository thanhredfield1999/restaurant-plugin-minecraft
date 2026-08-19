package vn.restauranttycoon.supply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;
import vn.restauranttycoon.supplysetup.SupplyDeliveryJourneyStage;
import vn.restauranttycoon.supplysetup.SupplyDeliveryJourneyStep;
import vn.restauranttycoon.supplysetup.SupplyDeliveryJourneySnapshot;
import vn.restauranttycoon.supplysetup.SupplySetupPosition;

class SupplyRuntimeCheckpointResolverTest {
    @Test
    void resolvesExactCheckpointPosition() {
        SupplySetupPosition expected = new SupplySetupPosition("world", 4, 65, 6, 90, 0);
        SupplyDeliveryJourneySnapshot snapshot = new SupplyDeliveryJourneySnapshot(1, "plot", List.of(
                new SupplyDeliveryJourneyStep(SupplyDeliveryJourneyStage.DELIVERY_ENTRY, expected)));

        assertEquals(expected, SupplyRuntimeCheckpointResolver.resolve(snapshot, "DELIVERY_ENTRY"));
    }

    @Test
    void rejectsUnknownCheckpoint() {
        SupplyDeliveryJourneySnapshot snapshot = new SupplyDeliveryJourneySnapshot(1, "plot", List.of(
                new SupplyDeliveryJourneyStep(SupplyDeliveryJourneyStage.DELIVERY_ENTRY,
                        new SupplySetupPosition("world", 4, 65, 6, 90, 0))));

        assertThrows(IllegalArgumentException.class,
                () -> SupplyRuntimeCheckpointResolver.resolve(snapshot, "UNLOAD_POINT"));
    }
}
