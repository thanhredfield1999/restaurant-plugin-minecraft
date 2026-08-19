package vn.restauranttycoon.supplysetup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class SupplySetupPointPresenterTest {
    @Test
    void presentsUnsetPointWithExplicitStatusAndActions() {
        SupplySetupPointPresenter presenter = new SupplySetupPointPresenter(new FakeMessages());

        SupplySetupPointView view = presenter.present(
                SupplySetupPointType.DELIVERY_STOP,
                Optional.empty());

        assertEquals("Điểm dừng giao hàng", view.title());
        assertEquals(SupplySetupPointStatus.UNSET, view.status());
        assertTrue(view.lore().contains("Trạng thái: Chưa cấu hình"));
        assertTrue(view.lore().contains("Nhấp để mở các thao tác"));
    }

    @Test
    void presentsConfiguredPointWithNamedLocationPlaceholdersAndOverwriteWarning() {
        SupplySetupPoint point = new SupplySetupPoint(
                SupplySetupOwner.restaurant("plot_1"),
                SupplySetupPointType.DELIVERY_STOP,
                new SupplySetupPosition("world", 12.25, 64.0, -8.5, 90.0F, 0.0F));
        SupplySetupPointPresenter presenter = new SupplySetupPointPresenter(new FakeMessages());

        SupplySetupPointView view = presenter.present(point.type(), Optional.of(point));

        assertEquals(SupplySetupPointStatus.CONFIGURED, view.status());
        assertTrue(view.lore().contains("Trạng thái: Đã cấu hình"));
        assertTrue(view.lore().contains("Vị trí: world (12.25, 64.00, -8.50)"));
        assertTrue(view.lore().contains("Đã có điểm: có thể đặt lại, kiểm tra hoặc tháo"));
    }

    private static final class FakeMessages implements SupplySetupMessages {
        private final Map<SupplySetupPointType, String> names = new EnumMap<>(SupplySetupPointType.class);

        private FakeMessages() {
            names.put(SupplySetupPointType.DELIVERY_STOP, "Điểm dừng giao hàng");
        }

        @Override
        public String text(String key, Map<String, String> placeholders) {
            String template = switch (key) {
                case "point.DELIVERY_STOP.name" -> names.get(SupplySetupPointType.DELIVERY_STOP);
                case "point.DELIVERY_STOP.description" -> "Nơi đoàn giao hàng dừng chờ ký nhận";
                case "status.UNSET" -> "Chưa cấu hình";
                case "status.CONFIGURED" -> "Đã cấu hình";
                case "lore.status" -> "Trạng thái: {status}";
                case "lore.location" -> "Vị trí: {world} ({x}, {y}, {z})";
                case "lore.open-actions" -> "Nhấp để mở các thao tác";
                case "lore.configured-hint" -> "Đã có điểm: có thể đặt lại, kiểm tra hoặc tháo";
                default -> throw new IllegalArgumentException("Thiếu key: " + key);
            };
            for (var entry : placeholders.entrySet()) {
                template = template.replace("{" + entry.getKey() + "}", entry.getValue());
            }
            return template;
        }
    }
}
