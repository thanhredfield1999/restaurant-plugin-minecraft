package vn.restauranttycoon.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.restauranttycoon.supply.SupplyPackageState;
import vn.restauranttycoon.supply.SupplyPlayerDeliveryStatus;
import vn.restauranttycoon.supply.SupplyReceivingProgress;
import vn.restauranttycoon.supply.SupplyShipmentState;

final class OperationsDashboardEntryTest {
    @Test
    void entriesSortByShipmentIdAndExposeNextAction() {
        OperationsDashboardEntry waiting = entry(
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                SupplyPlayerDeliveryStatus.WAITING_FOR_RECEIVING);
        OperationsDashboardEntry complete = entry(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                SupplyPlayerDeliveryStatus.COMPLETED);

        assertEquals(List.of(complete, waiting), OperationsDashboardModel.stable(List.of(waiting, complete)));
        assertEquals("Nhận package hoặc thuê receiving worker", OperationsDashboardModelView.nextAction(waiting));
        assertEquals("Xem tồn kho", OperationsDashboardModelView.nextAction(complete));
        assertEquals("Đang chờ nhận hàng", OperationsDashboardLabels.status(waiting.playerStatus()));
    }

    private static OperationsDashboardEntry entry(UUID shipmentId, SupplyPlayerDeliveryStatus status) {
        return new OperationsDashboardEntry(
                shipmentId,
                UUID.randomUUID(),
                "plot_1",
                SupplyShipmentState.ARRIVED,
                SupplyPackageState.IN_TRANSIT,
                "UNLOAD_POINT",
                SupplyReceivingProgress.NONE,
                status,
                1,
                false);
    }
}
