package vn.restauranttycoon.supplysetup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

final class SupplyDeliveryJourneySnapshotSerializerTest {
    @Test
    void serializesCanonicalBoundedPayload() {
        SupplySetupOwner owner = SupplySetupOwner.restaurant("plot_1");
        SupplyDeliveryJourneyPlan plan = new SupplyDeliveryJourneyPlan(owner, List.of(
                step(SupplyDeliveryJourneyStage.DELIVERY_ENTRY, "world", 1.25),
                step(SupplyDeliveryJourneyStage.UNLOAD_POINT, "world", 2.5)));

        String payload = SupplyDeliveryJourneySnapshotSerializer.serialize(plan);

        assertEquals(
                "{\"version\":1,\"owner\":\"plot_1\",\"steps\":["
                        + "{\"stage\":\"DELIVERY_ENTRY\",\"world\":\"world\",\"x\":1.250000,\"y\":64.000000,\"z\":2.000000,\"yaw\":90.000000,\"pitch\":0.000000},"
                        + "{\"stage\":\"UNLOAD_POINT\",\"world\":\"world\",\"x\":2.500000,\"y\":64.000000,\"z\":2.000000,\"yaw\":90.000000,\"pitch\":0.000000}]}",
                payload);
    }

    @Test
    void serializedJourneyCanBeDecodedForRuntimeTransition() {
        SupplyDeliveryJourneyPlan plan = new SupplyDeliveryJourneyPlan(
                SupplySetupOwner.restaurant("plot_1"), List.of(
                        step(SupplyDeliveryJourneyStage.DELIVERY_ENTRY, "world", 1.25),
                        step(SupplyDeliveryJourneyStage.UNLOAD_POINT, "world", 2.5)));

        SupplyDeliveryJourneySnapshot decoded = SupplyDeliveryJourneySnapshotCodec.decode(
                SupplyDeliveryJourneySnapshotSerializer.serialize(plan));

        assertEquals(2, decoded.steps().size());
        assertEquals(SupplyDeliveryJourneyStage.DELIVERY_ENTRY, decoded.steps().get(0).stage());
        assertEquals(1.25, decoded.steps().get(0).position().x(), 0.000001);
        assertEquals(SupplyDeliveryJourneyStage.UNLOAD_POINT, decoded.steps().get(1).stage());
    }

    @Test
    void escapesWorldNameWithoutChangingCanonicalShape() {
        SupplySetupOwner owner = SupplySetupOwner.restaurant("plot_1");
        SupplyDeliveryJourneyPlan plan = new SupplyDeliveryJourneyPlan(owner, List.of(
                step(SupplyDeliveryJourneyStage.DELIVERY_ENTRY, "world\"quoted", 1)));

        assertTrue(SupplyDeliveryJourneySnapshotSerializer.serialize(plan).contains("world\\\"quoted"));
    }

    @Test
    void rejectsPayloadLargerThanDatabaseContract() {
        SupplySetupOwner owner = SupplySetupOwner.restaurant("plot_1");
        SupplyDeliveryJourneyPlan plan = new SupplyDeliveryJourneyPlan(owner, List.of(
                step(SupplyDeliveryJourneyStage.DELIVERY_ENTRY, "x".repeat(16_300), 1)));

        assertThrows(IllegalArgumentException.class,
                () -> SupplyDeliveryJourneySnapshotSerializer.serialize(plan));
    }

    private static SupplyDeliveryJourneyStep step(
            SupplyDeliveryJourneyStage stage, String world, double x) {
        return new SupplyDeliveryJourneyStep(
                stage, new SupplySetupPosition(world, x, 64, 2, 90, 0));
    }
}
