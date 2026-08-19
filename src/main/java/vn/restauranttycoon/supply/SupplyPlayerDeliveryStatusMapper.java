package vn.restauranttycoon.supply;

import java.util.Objects;
import java.util.Set;

public final class SupplyPlayerDeliveryStatusMapper {
    private static final Set<String> KNOWN_CHECKPOINTS = Set.of(
            "DELIVERY_ENTRY", "ROUTE_WAYPOINT", "DELIVERY_STOP", "UNLOAD_POINT",
            "DELIVERY_EXIT", "DELIVERY_DESPAWN", "PENDING_MANUAL");
    private SupplyPlayerDeliveryStatusMapper() {
    }

    public static SupplyPlayerDeliveryStatus map(
            SupplyShipmentState shipmentState,
            SupplyPackageState packageState,
            String checkpointStage,
            SupplyReceivingProgress receivingProgress,
            boolean retryableFailure
    ) {
        Objects.requireNonNull(shipmentState, "shipmentState");
        Objects.requireNonNull(packageState, "packageState");
        Objects.requireNonNull(checkpointStage, "checkpointStage");
        Objects.requireNonNull(receivingProgress, "receivingProgress");
        if (!KNOWN_CHECKPOINTS.contains(checkpointStage)) {
            return SupplyPlayerDeliveryStatus.FAILED_SAFE;
        }
        if (retryableFailure) return SupplyPlayerDeliveryStatus.WAITING_RETRY;
        if ("PENDING_MANUAL".equals(checkpointStage)) return SupplyPlayerDeliveryStatus.PENDING_MANUAL;
        if (shipmentState == SupplyShipmentState.CANCELLED) return SupplyPlayerDeliveryStatus.FAILED_SAFE;
        if (packageState == SupplyPackageState.STOCKED) {
            return "DELIVERY_DESPAWN".equals(checkpointStage)
                    ? SupplyPlayerDeliveryStatus.COMPLETED
                    : SupplyPlayerDeliveryStatus.STOCKED;
        }
        if (receivingProgress == SupplyReceivingProgress.WORKER_STOCKING
                && packageState == SupplyPackageState.HANDED_OFF) {
            return SupplyPlayerDeliveryStatus.STOCKING;
        }
        if (packageState == SupplyPackageState.HANDED_OFF) {
            return receivingProgress == SupplyReceivingProgress.WORKER_HANDOFF_COMMITTED
                    ? SupplyPlayerDeliveryStatus.RECEIVED_BY_WORKER
                    : SupplyPlayerDeliveryStatus.RECEIVED_MANUALLY;
        }
        if ("DELIVERY_STOP".equals(checkpointStage)
                && shipmentState != SupplyShipmentState.ARRIVED) {
            return SupplyPlayerDeliveryStatus.AT_DELIVERY_STOP;
        }
        if (shipmentState == SupplyShipmentState.ARRIVED) {
            return SupplyPlayerDeliveryStatus.WAITING_FOR_RECEIVING;
        }
        if (shipmentState == SupplyShipmentState.CREATED) {
            return SupplyPlayerDeliveryStatus.PREPARING;
        }
        return SupplyPlayerDeliveryStatus.IN_TRANSIT;
    }
}
