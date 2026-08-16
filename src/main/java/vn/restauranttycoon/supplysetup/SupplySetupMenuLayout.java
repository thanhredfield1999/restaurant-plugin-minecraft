package vn.restauranttycoon.supplysetup;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class SupplySetupMenuLayout {
    public static final int INVENTORY_SIZE = 27;
    public static final int CONFIRM_DELETE_SLOT = 11;
    public static final int CONFIRM_CANCEL_SLOT = 15;
    public static final int ROUTE_SLOT = 22;

    private static final Map<SupplySetupPointType, Integer> POINT_SLOTS;

    static {
        Map<SupplySetupPointType, Integer> slots = new EnumMap<>(SupplySetupPointType.class);
        slots.put(SupplySetupPointType.ORDER_DESK, 11);
        slots.put(SupplySetupPointType.SUPPLIER_SPAWN, 15);
        slots.put(SupplySetupPointType.DELIVERY_ENTRY, 10);
        slots.put(SupplySetupPointType.DELIVERY_STOP, 11);
        slots.put(SupplySetupPointType.UNLOAD_POINT, 12);
        slots.put(SupplySetupPointType.WAREHOUSE_ENTRY, 14);
        slots.put(SupplySetupPointType.DELIVERY_EXIT, 15);
        slots.put(SupplySetupPointType.DELIVERY_DESPAWN, 16);
        POINT_SLOTS = Collections.unmodifiableMap(slots);
    }

    public int slot(SupplySetupPointType type) {
        return POINT_SLOTS.get(type);
    }

    public Set<Integer> functionalSlots(SupplySetupScope scope) {
        Set<Integer> result = new HashSet<>();
        for (SupplySetupPointType type : SupplySetupPointType.values()) {
            if (type.scope() == scope) {
                result.add(slot(type));
            }
        }
        if (scope == SupplySetupScope.RESTAURANT) {
            result.add(ROUTE_SLOT);
        }
        return Collections.unmodifiableSet(result);
    }

    public Set<Integer> blankSlots(SupplySetupScope scope) {
        Set<Integer> result = new HashSet<>();
        Set<Integer> functional = functionalSlots(scope);
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            if (!functional.contains(slot)) {
                result.add(slot);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    public Set<Integer> confirmFunctionalSlots() {
        return Set.of(CONFIRM_DELETE_SLOT, CONFIRM_CANCEL_SLOT);
    }

    public Set<Integer> confirmBlankSlots() {
        Set<Integer> result = new HashSet<>();
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            if (!confirmFunctionalSlots().contains(slot)) {
                result.add(slot);
            }
        }
        return Collections.unmodifiableSet(result);
    }
}
