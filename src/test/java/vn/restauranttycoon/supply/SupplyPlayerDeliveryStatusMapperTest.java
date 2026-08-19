package vn.restauranttycoon.supply;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class SupplyPlayerDeliveryStatusMapperTest {
    @Test
    void completedRequiresStockedPackage() {
        assertEquals(
                SupplyPlayerDeliveryStatus.RECEIVED_MANUALLY,
                SupplyPlayerDeliveryStatusMapper.map(
                        SupplyShipmentState.HANDED_OFF,
                        SupplyPackageState.HANDED_OFF,
                        "DELIVERY_EXIT",
                        SupplyReceivingProgress.NONE,
                        false));
        assertEquals(
                SupplyPlayerDeliveryStatus.COMPLETED,
                SupplyPlayerDeliveryStatusMapper.map(
                        SupplyShipmentState.HANDED_OFF,
                        SupplyPackageState.STOCKED,
                        "DELIVERY_DESPAWN",
                        SupplyReceivingProgress.NONE,
                        false));
    }

    @Test
    void arrivedPackageWaitsForPlayerOrWorker() {
        assertEquals(
                SupplyPlayerDeliveryStatus.WAITING_FOR_RECEIVING,
                SupplyPlayerDeliveryStatusMapper.map(
                        SupplyShipmentState.ARRIVED,
                        SupplyPackageState.IN_TRANSIT,
                        "UNLOAD_POINT",
                        SupplyReceivingProgress.NONE,
                        false));
        assertEquals(
                SupplyPlayerDeliveryStatus.WAITING_FOR_RECEIVING,
                SupplyPlayerDeliveryStatusMapper.map(
                        SupplyShipmentState.ARRIVED,
                        SupplyPackageState.IN_TRANSIT,
                        "DELIVERY_STOP",
                        SupplyReceivingProgress.NONE,
                        false));
    }

    @Test
    void workerProgressIsVisibleBeforeStockCommit() {
        assertEquals(
                SupplyPlayerDeliveryStatus.RECEIVED_BY_WORKER,
                SupplyPlayerDeliveryStatusMapper.map(
                        SupplyShipmentState.HANDED_OFF,
                        SupplyPackageState.HANDED_OFF,
                        "DELIVERY_EXIT",
                        SupplyReceivingProgress.WORKER_HANDOFF_COMMITTED,
                        false));
        assertEquals(
                SupplyPlayerDeliveryStatus.STOCKING,
                SupplyPlayerDeliveryStatusMapper.map(
                        SupplyShipmentState.HANDED_OFF,
                        SupplyPackageState.HANDED_OFF,
                        "DELIVERY_EXIT",
                        SupplyReceivingProgress.WORKER_STOCKING,
                        false));
    }

    @Test
    void unknownCheckpointFailsSafe() {
        assertEquals(
                SupplyPlayerDeliveryStatus.FAILED_SAFE,
                SupplyPlayerDeliveryStatusMapper.map(
                        SupplyShipmentState.IN_TRANSIT,
                        SupplyPackageState.IN_TRANSIT,
                        "CORRUPT_STAGE",
                        SupplyReceivingProgress.NONE,
                        false));
    }

    @Test
    void manualRecoveryAndRetryOverrideNormalProgress() {
        assertEquals(
                SupplyPlayerDeliveryStatus.PENDING_MANUAL,
                SupplyPlayerDeliveryStatusMapper.map(
                        SupplyShipmentState.IN_TRANSIT,
                        SupplyPackageState.IN_TRANSIT,
                        "PENDING_MANUAL",
                        SupplyReceivingProgress.NONE,
                        false));
        assertEquals(
                SupplyPlayerDeliveryStatus.WAITING_RETRY,
                SupplyPlayerDeliveryStatusMapper.map(
                        SupplyShipmentState.IN_TRANSIT,
                        SupplyPackageState.IN_TRANSIT,
                        "DELIVERY_ENTRY",
                        SupplyReceivingProgress.NONE,
                        true));
    }
}
