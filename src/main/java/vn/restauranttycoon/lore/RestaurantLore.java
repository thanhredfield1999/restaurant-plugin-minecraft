package vn.restauranttycoon.lore;

import java.util.List;
import java.util.Objects;

public final class RestaurantLore {
    private RestaurantLore() {
    }

    public static List<String> dashboardGuide() {
        return List.of(
                "Hiệp hội Phố Bếp ghi nhận quán của bạn.",
                "1. Chọn quán để lập đơn tại Chợ đầu mối.",
                "2. Chuẩn bị điểm giao trước khi gửi đơn.",
                "3. Sau khi giao runtime sẵn sàng, tự nhận kiện rồi đưa vào kho.");
    }

    public static List<String> plotGuide(String plotId, int x, int y, int z) {
        Objects.requireNonNull(plotId, "plotId");
        return List.of(
                "Hiệp hội Phố Bếp đã cấp lô đất " + plotId + ".",
                "Đi theo la bàn tới " + x + ", " + y + ", " + z + ".",
                "Bước vào lô đất để đội xây dựng dựng bộ khung quán đầu tiên.");
    }

    public static List<String> restaurantCard(String plotId) {
        Objects.requireNonNull(plotId, "plotId");
        return List.of(
                "Mở sổ vận hành cho " + plotId + ".",
                "Lập đơn nguyên liệu tại Chợ đầu mối.",
                "Hệ thống kiểm tra quyền sở hữu và điểm giao trước khi tạo đơn.");
    }
}
