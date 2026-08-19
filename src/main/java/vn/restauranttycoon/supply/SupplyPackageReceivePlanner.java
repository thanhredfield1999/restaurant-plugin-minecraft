package vn.restauranttycoon.supply;

import java.util.List;

public final class SupplyPackageReceivePlanner {
    private SupplyPackageReceivePlanner() {
    }

    public static List<SupplyPackageReceiveAction> plan(boolean tokenAlreadyPresent) {
        return tokenAlreadyPresent
                ? List.of(SupplyPackageReceiveAction.DURABLE_HANDOFF,
                        SupplyPackageReceiveAction.REMOVE_ENTITY)
                : List.of(SupplyPackageReceiveAction.DURABLE_HANDOFF,
                        SupplyPackageReceiveAction.GRANT_TOKEN,
                        SupplyPackageReceiveAction.REMOVE_ENTITY);
    }
}
