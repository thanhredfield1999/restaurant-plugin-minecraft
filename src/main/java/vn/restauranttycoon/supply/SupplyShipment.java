package vn.restauranttycoon.supply;

import java.util.Objects;
import java.util.UUID;

public final class SupplyShipment {
    private final UUID shipmentId;
    private final SupplyShipmentState state;

    private SupplyShipment(UUID shipmentId, SupplyShipmentState state) {
        this.shipmentId = shipmentId;
        this.state = state;
    }

    public static SupplyShipment create(UUID shipmentId) {
        return new SupplyShipment(Objects.requireNonNull(shipmentId, "shipmentId"),
                SupplyShipmentState.CREATED);
    }

    public SupplyShipment dispatch() {
        require(SupplyShipmentState.CREATED);
        return next(SupplyShipmentState.IN_TRANSIT);
    }

    public SupplyShipment arrive() {
        require(SupplyShipmentState.IN_TRANSIT);
        return next(SupplyShipmentState.ARRIVED);
    }

    public SupplyShipment handoff() {
        if (state == SupplyShipmentState.HANDED_OFF) {
            return this;
        }
        require(SupplyShipmentState.ARRIVED);
        return next(SupplyShipmentState.HANDED_OFF);
    }

    public SupplyShipment cancel() {
        require(SupplyShipmentState.CREATED);
        return next(SupplyShipmentState.CANCELLED);
    }

    public UUID shipmentId() { return shipmentId; }
    public SupplyShipmentState state() { return state; }

    private SupplyShipment next(SupplyShipmentState nextState) {
        return new SupplyShipment(shipmentId, nextState);
    }

    private void require(SupplyShipmentState expected) {
        if (state != expected) {
            throw new IllegalStateException("Shipment is not in state " + expected);
        }
    }
}
