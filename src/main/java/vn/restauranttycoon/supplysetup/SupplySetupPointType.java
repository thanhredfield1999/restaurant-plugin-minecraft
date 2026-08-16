package vn.restauranttycoon.supplysetup;

public enum SupplySetupPointType {
    ORDER_DESK(SupplySetupScope.CENTRAL_SUPPLIER),
    SUPPLIER_SPAWN(SupplySetupScope.CENTRAL_SUPPLIER),
    DELIVERY_ENTRY(SupplySetupScope.RESTAURANT),
    DELIVERY_STOP(SupplySetupScope.RESTAURANT),
    UNLOAD_POINT(SupplySetupScope.RESTAURANT),
    WAREHOUSE_ENTRY(SupplySetupScope.RESTAURANT),
    DELIVERY_EXIT(SupplySetupScope.RESTAURANT),
    DELIVERY_DESPAWN(SupplySetupScope.RESTAURANT);

    private final SupplySetupScope scope;

    SupplySetupPointType(SupplySetupScope scope) {
        this.scope = scope;
    }

    public SupplySetupScope scope() {
        return scope;
    }
}
