# Contract setup nhập hàng

## Mục tiêu

Tài liệu này là nguồn sự thật cho phần setup không phụ thuộc các quyết định sản phẩm D1–D25. Nó phân biệt rõ dữ liệu đã lưu, dữ liệu đủ cấu trúc và hành vi đã được xác minh trong runtime.

## Hành trình cần hỗ trợ

1. Admin cấu hình khu nhà cung cấp trung tâm, nơi người chơi đặt hàng và nơi đoàn giao hàng bắt đầu.
2. Admin cấu hình từng nhà hàng, từ cổng nhận đoàn giao hàng đến điểm dừng, điểm dỡ hàng, lối vào kho, lối rời đi và điểm despawn.
3. Admin có thể thêm các waypoint trung gian khi địa hình cần dẫn đường; tuyến thẳng không bắt buộc waypoint.
4. Admin phải xem được trạng thái, vị trí đã lưu, dịch chuyển để kiểm tra, đặt lại và xóa có xác nhận.
5. Hệ thống chỉ được báo đủ cấu trúc khi toàn bộ dữ liệu bắt buộc hiện diện và nhất quán.
6. Người chơi đặt hàng tại trung tâm, chờ đoàn giao, tương tác nhận package ở điểm dỡ hàng rồi tự đưa hàng qua lối vào kho.

Bước 6 vẫn là product/runtime contract cần các gate sau. Setup không được tự nhận là đã vận hành được chỉ vì đủ tọa độ.

## Ma trận authoritative

| Scope | Thành phần | Bắt buộc để đủ cấu trúc | Vai trò |
|---|---|---:|---|
| Central supplier | `ORDER_DESK` | Có | Điểm tương tác đặt hàng thủ công |
| Central supplier | `SUPPLIER_SPAWN` | Có | Điểm bắt đầu đoàn giao hàng |
| Restaurant | `DELIVERY_ENTRY` | Có | Điểm đoàn giao đi vào khu nhà hàng |
| Restaurant | `DELIVERY_STOP` | Có | Điểm đoàn dừng trước khi dỡ hàng |
| Restaurant | `UNLOAD_POINT` | Có | Điểm chờ người chơi nhận package |
| Restaurant | `WAREHOUSE_ENTRY` | Có | Điểm người chơi đưa hàng vào kho; không phải stage điều hướng NPC |
| Restaurant | `DELIVERY_EXIT` | Có | Điểm đoàn rời khu nhà hàng sau handoff |
| Restaurant | `DELIVERY_DESPAWN` | Có | Điểm kết thúc và thu hồi entity runtime |
| Restaurant | Route waypoint | Không | Các khúc trung gian theo thứ tự; `ROUTE_WAYPOINT*` cho phép tuyến rỗng |

## Các mức trạng thái

### 1. Trạng thái từng thành phần

- `UNSET`: chưa có dữ liệu.
- `CONFIGURED`: đã lưu dữ liệu hợp lệ cục bộ.

`CONFIGURED` không có nghĩa là toàn bộ setup hoàn chỉnh.

### 2. Đủ cấu trúc (`STRUCTURALLY_COMPLETE`)

Central supplier chỉ đủ cấu trúc khi có đúng hai điểm bắt buộc cùng owner và cùng world.

Một nhà hàng chỉ đủ cấu trúc khi:

- có đủ sáu điểm bắt buộc trong bảng;
- mọi điểm và waypoint thuộc đúng restaurant owner;
- không trùng loại điểm;
- toàn bộ điểm và waypoint nằm trong cùng world;
- sequence waypoint liên tục từ 1; route rỗng vẫn hợp lệ.

Bộ đánh giá phải trả danh sách vấn đề cụ thể, không chỉ boolean, để GUI có thể chỉ ra bước admin cần sửa.

### 3. Đã kiểm chứng runtime (`RUNTIME_VERIFIED`)

Chỉ được dùng sau khi có bằng chứng Paper/Citizens thực tế cho spawn, navigation, handoff, exit/despawn, chunk/lifecycle và restart/recovery tương ứng. Unit test hoặc trạng thái `STRUCTURALLY_COMPLETE` không đủ để tuyên bố mức này.

## Validation và UX

- Fail closed khi scope/owner/world/sequence không nhất quán.
- Hiển thị trạng thái tổng và các thành phần còn thiếu ngay trong GUI, theo nguyên tắc visibility of system status và recognition rather than recall.
- Giữ thao tác xem vị trí, teleport, đặt/đặt lại và xóa có xác nhận.
- Không buộc admin sửa YAML hoặc đoán tọa độ.
- Không ghi đè hoặc xóa dữ liệu bằng thao tác mơ hồ.
- Validation địa hình, vùng plot, block an toàn và khả năng Citizens điều hướng là lớp runtime/server-thread riêng; không giả vờ chứng minh bằng evaluator thuần Java.

## Những gì contract này chưa chốt

D1–D25 vẫn quyết định catalog, order, payment, fulfillment, recovery, kho/handoff và Citizens scheduling. Contract này không chọn đáp án cho các quyết định đó và không mở release gate tương ứng.

## Nguồn và căn cứ

- `CURRENT_STATE.md`: journey hiện hành và ranh giới giữa `WAREHOUSE_ENTRY` với NPC navigation.
- `SUPPLY_GAMEPLAY_EXECUTION_PLAN_VI.md`: dependency gate, D1–D25 và `ROUTE_WAYPOINT*`.
- Source/test hiện tại trong `vn.restauranttycoon.supplysetup`: owner, point type, route và journey invariant.
- Nielsen Norman Group, *10 Usability Heuristics for User Interface Design*: visibility of system status, error prevention, recognition rather than recall, error recovery và help/documentation: https://www.nngroup.com/articles/ten-usability-heuristics/
- Nielsen Norman Group, *User Control and Freedom*: back/cancel/undo và khả năng sửa sai: https://www.nngroup.com/articles/user-control-and-freedom/
