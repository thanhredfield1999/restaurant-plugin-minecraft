package vn.restauranttycoon.menu;

import java.util.Objects;
import java.util.UUID;
import vn.restauranttycoon.supply.SupplyPackageState;
import vn.restauranttycoon.supply.SupplyPlayerDeliveryStatus;
import vn.restauranttycoon.supply.SupplyReceivingProgress;
import vn.restauranttycoon.supply.SupplyShipmentState;

public record OperationsDashboardEntry(
        UUID shipmentId,
        UUID packageId,
        String restaurantId,
        SupplyShipmentState shipmentState,
        SupplyPackageState packageState,
        String checkpointStage,
        SupplyReceivingProgress receivingProgress,
        SupplyPlayerDeliveryStatus playerStatus,
        int attemptCount,
        boolean retryableFailure
) {
    public OperationsDashboardEntry {
        Objects.requireNonNull(shipmentId, "shipmentId");
        Objects.requireNonNull(packageId, "packageId");
        Objects.requireNonNull(restaurantId, "restaurantId");
        Objects.requireNonNull(shipmentState, "shipmentState");
        Objects.requireNonNull(packageState, "packageState");
        Objects.requireNonNull(checkpointStage, "checkpointStage");
        Objects.requireNonNull(receivingProgress, "receivingProgress");
        Objects.requireNonNull(playerStatus, "playerStatus");
        if (attemptCount < 0) throw new IllegalArgumentException("attemptCount must not be negative");
    }
}

final class OperationsDashboardModel {
    private OperationsDashboardModel() {
    }

    static java.util.List<OperationsDashboardEntry> stable(java.util.List<OperationsDashboardEntry> entries) {
        Objects.requireNonNull(entries, "entries");
        return entries.stream()
                .sorted(java.util.Comparator.comparing(OperationsDashboardEntry::shipmentId))
                .toList();
    }
}

final class OperationsDashboardLabels {
    private OperationsDashboardLabels() {
    }

    static String status(SupplyPlayerDeliveryStatus status) {
        return switch (Objects.requireNonNull(status, "status")) {
            case ORDER_SUBMITTED -> "Đã đặt hàng";
            case PAYMENT_CAPTURED -> "Đã thanh toán";
            case PREPARING -> "Đang chuẩn bị";
            case IN_TRANSIT -> "Đang giao hàng";
            case AT_DELIVERY_STOP -> "Đã tới điểm dừng";
            case WAITING_FOR_RECEIVING -> "Đang chờ nhận hàng";
            case RECEIVED_MANUALLY -> "Player đã nhận hàng";
            case RECEIVED_BY_WORKER -> "Worker đã nhận hàng";
            case STOCKING -> "Đang nhập kho";
            case STOCKED, COMPLETED -> "Đã nhập kho / hoàn tất";
            case WAITING_RETRY -> "Đang chờ thử lại";
            case PENDING_MANUAL -> "Cần xử lý thủ công";
            case FAILED_SAFE -> "Thất bại an toàn";
        };
    }
}

final class OperationsDashboardModelView {
    private OperationsDashboardModelView() {
    }

    static String nextAction(OperationsDashboardEntry entry) {
        return switch (entry.playerStatus()) {
            case WAITING_FOR_RECEIVING -> "Nhận package hoặc thuê receiving worker";
            case RECEIVED_MANUALLY, RECEIVED_BY_WORKER, STOCKING -> "Chờ nhập kho hoàn tất";
            case PENDING_MANUAL, FAILED_SAFE -> "Mở chi tiết và xử lý theo hướng dẫn";
            case COMPLETED, STOCKED -> "Xem tồn kho";
            default -> "Theo dõi chuyến giao";
        };
    }
}
