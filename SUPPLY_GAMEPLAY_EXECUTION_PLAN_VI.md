# Kế hoạch hoàn thiện gameplay nhập hàng

Ngày lập: 2026-08-15

## 1. Mục đích và phạm vi

Tài liệu này chuyển khoảng trống hiện tại của gameplay nhập hàng thành các gate nhỏ, có dependency và tiêu chí kiểm thử rõ ràng. Kế hoạch ưu tiên domain và persistence trước Citizens/Bukkit runtime; không coi unit test là bằng chứng runtime Paper.

Phạm vi gồm:

- hành trình người chơi mới từ lúc sở hữu nhà hàng đến lúc đặt, nhận và nhập hàng;
- contract order–shipment–package;
- ingredient/SKU/quantity;
- reservation/capture/refund;
- handoff và warehouse stock;
- transaction, idempotency, restart recovery và migration;
- runtime giao hàng chỉ sau khi foundation ổn định.

Không thuộc phạm vi tự động của kế hoạch: deploy, start/restart server, sửa production hoặc Paper smoke test. Mọi thao tác đó cần phê duyệt riêng.

## 2. Trạng thái thực tế

### 2.1 Đã có

- Admin setup lưu bền vững các điểm trung tâm và từng nhà hàng ở schema V7.
- Route waypoint từng nhà hàng được lưu bền vững, thay thế nguyên tử ở schema V8.
- Journey planner immutable tạo thứ tự `DELIVERY_ENTRY -> ROUTE_WAYPOINT* -> DELIVERY_STOP -> UNLOAD_POINT -> DELIVERY_EXIT -> DELIVERY_DESPAWN`, fail-closed khi thiếu điểm, sai owner, trùng loại hoặc xuyên world.
- Convoy simulation có `shipmentId`, state machine và invariant snapshot.
- Package domain có `shipmentId`, `packageId`, owner, operation identity và retry exactly-once trong bộ nhớ.
- Aggregate handoff yêu cầu convoy/package/request cùng nhà hàng và shipment.
- Economy/purchase/dish đã có các mẫu ledger, unique operation ID, transaction JDBC và optimistic/fencing có thể tham khảo; chúng không tự động trở thành contract supply.
- Runtime bắt buộc schema V8 và từ chối database thiếu/newer/dirty.

### 2.2 Chưa có

- Ingredient catalog, SKU/version, unit và quantity semantics.
- Supplier order aggregate, order line và lifecycle.
- Cardinality order–shipment–package–convoy.
- Shipment/package contents.
- Giá snapshot và chính sách thay đổi giá.
- Reservation/capture/refund cho đơn nhập hàng.
- Durable shipment/package/convoy snapshot hoặc operation receipt cho handoff.
- Warehouse stock/lot/transaction và atomic handoff-to-stock.
- Quy tắc nhận đủ, nhận thiếu, nhận một phần, từ chối hoặc package bị mất.
- Player-facing ordering GUI, ETA/status, interaction nhận hàng và feedback nhập kho.
- Citizens navigation, Bukkit handoff, scheduler/recovery coordinator và Paper smoke test.
- Tutorial/onboarding end-to-end nối claim/setup nhà hàng với đặt món nhập đầu tiên.

### 2.3 Lệch tài liệu cần giữ rõ

Roadmap MVP cũ có giả định stock logical vô hạn và defer restocking. Yêu cầu sản phẩm hiện tại đặt nhập hàng thủ công làm gameplay cốt lõi. Không được dùng giả định MVP cũ để tự suy ra schema supply mới.

PostgreSQL integration test hiện có assertion schema V6 trong khi runtime/bundled migrations đã ở V8. Vì test phụ thuộc credential môi trường, full unit build có thể xanh dù nhánh assertion này không chạy. Cần sửa và chạy bằng PostgreSQL thật ở gate migration; compile hoặc skipped test không đủ làm bằng chứng.

## 3. Hành trình người chơi mục tiêu và khoảng trống

Đây là flow cần được xác nhận và sau đó triển khai; các điểm đánh dấu **QUYẾT ĐỊNH** không được tự suy diễn.

1. Người chơi có quyền sử dụng một nhà hàng hợp lệ.
2. Hệ thống kiểm tra admin setup cho nhà hàng và tuyến giao đã complete; nếu thiếu phải fail-closed với hướng dẫn cụ thể.
3. Người chơi tới điểm/GUI đặt hàng, xem nguyên liệu đã mở khóa, đơn vị, số lượng, giá và thời gian giao.
4. Người chơi tạo draft, sửa line và xác nhận tổng tiền.
5. **QUYẾT ĐỊNH:** tiền được reserve hay capture ngay khi xác nhận; khi nào refund.
6. Order được tạo idempotent và chuyển sang chờ fulfillment.
7. **QUYẾT ĐỊNH:** một order tạo một hay nhiều shipment; một shipment có một hay nhiều package/convoy.
8. Người chơi nhận ETA và thay đổi trạng thái, không được cấp item/stock trực tiếp.
9. Runtime spawn supplier Villager và Camel/Llama tại điểm hợp lệ, đi theo route mà không force-load chunk.
10. Convoy dừng tại unload point và chờ đúng người/nhà hàng tương tác.
11. **QUYẾT ĐỊNH:** nhận toàn bộ hay từng package/line; timeout, từ chối và partial failure xử lý thế nào.
12. Handoff durable xảy ra exactly-once; retry sau disconnect/restart không cộng hàng hoặc trừ tiền lần hai.
13. Stock transaction ghi chính xác SKU/quantity vào kho; chỉ sau commit mới báo thành công.
14. Convoy rời nhà hàng/despawn; recovery sau restart tiếp tục hoặc kết thúc theo policy đã chốt.
15. Tutorial chỉ hoàn thành sau đơn đầu tiên được nhập kho thành công, không phải chỉ sau thao tác mở GUI.

## 4. Invariant nền tảng

Các invariant này áp dụng cho mọi thiết kế sau:

- Restaurant owner là authorization scope, không thay thế identity của order/shipment/package/operation.
- Không aggregate nào được ghép chỉ bằng owner nếu có identity cụ thể hơn.
- Mọi mutation có side effect phải có caller-supplied operation ID và semantics retry rõ.
- Cùng operation ID + cùng payload trả lại kết quả đã commit; cùng operation ID + payload khác phải conflict.
- Không cộng stock trước khi package handoff được commit; package không được handoff hai lần.
- Không capture/refund tiền hai lần, kể cả timeout hoặc restart giữa các bước.
- Giá, SKU và unit dùng cho đơn phải là snapshot/versioned data đủ để replay; không phụ thuộc catalog hiện tại một cách mơ hồ.
- Quantity phải dùng kiểu và precision được định nghĩa, không dùng floating point tùy ý.
- Database là nguồn sự thật cho durable lifecycle; Bukkit entity/Citizens NPC chỉ là runtime projection.
- Corrupt, future-version, missing-reference hoặc ambiguous persisted data phải fail-closed và không bị overwrite tự động.
- JDBC chạy async; Bukkit/Citizens/world/entity/inventory access chạy server thread.
- Không force-load chunk và không scan không giới hạn trong tick server.

## 5. Plan theo gate

### Gate 0 — Khóa pure-domain trust boundaries

Dependency: không có quyết định sản phẩm.

Mục tiêu:

- khóa constructor/result snapshot để không biểu diễn state nội bộ bất nhất;
- giữ identity và exactly-once invariant hiện có;
- chưa thêm contents, payment hay persistence.

Test strategy:

- RED test dựng snapshot/result bất hợp lệ;
- focused `supplysetup` tests;
- tự review sibling public records;
- full `clean test build`.

Migration/restart risk: không đổi schema; chỉ fail-fast với object bất hợp lệ.

### Gate 1 — Chốt vocabulary và ingredient catalog

Dependency: quyết định sản phẩm D1–D3.

Mục tiêu:

- định nghĩa `IngredientSku`, display metadata, unlock reference, unit và quantity;
- định nghĩa version/deprecation và hành vi khi catalog thay đổi;
- chưa tạo order/payment.

Invariant:

- SKU là stable machine identity, không dùng display name;
- unit của một SKU không thay đổi ngầm;
- quantity dương, bounded và precision cố định;
- persisted order line có snapshot/version cần thiết để replay.

Test strategy:

- property/boundary tests cho quantity;
- duplicate/unknown/deprecated SKU tests;
- serialization/recovery tests trước migration.

Migration risk: nếu catalog chỉ ở config, reload compatibility và stale order phải được định nghĩa; nếu persisted, cần future-version/corrupt tests.

### Gate 2 — Order aggregate và pricing snapshot

Dependency: Gate 1, quyết định D4–D6.

Mục tiêu:

- draft/submit/cancel lifecycle;
- immutable order lines và total;
- caller operation ID cho submit/cancel;
- không tạo shipment khi order chưa hợp lệ.

Invariant:

- order thuộc đúng restaurant/player context;
- duplicate line policy rõ ràng;
- total được tính bằng integer minor unit với overflow checks;
- submit idempotent và payload conflict fail-closed.

Test strategy:

- state transition table;
- duplicate request/payload conflict;
- price/catalog changes after submit;
- overflow/empty/order-limit boundaries.

### Gate 3 — Payment reservation/capture/refund

Dependency: Gate 2, quyết định D7–D10.

Mục tiêu:

- supply payment ledger riêng hoặc typed reuse có contract rõ;
- reserve/capture/refund state machine;
- order/payment transaction boundary.

Invariant:

- balance không âm theo policy;
- mỗi transition có operation ID và unique durable receipt;
- capture không vượt reserved amount;
- refund không vượt captured amount;
- crash ở bất kỳ điểm nào không tạo double debit/refund.

Test strategy:

- repository transaction tests;
- concurrent same/different operation tests;
- injected failure before/after ledger write;
- restart replay tests trên PostgreSQL thật.

Migration risk: bảng ledger là durable external data; cần upgrade, future-version, dirty/corrupt và rollback policy. Không reuse `purchases` nếu semantics không khớp.

### Gate 4 — Fulfillment: order–shipment–package contents

Dependency: Gate 2, có thể cần Gate 3 tùy thời điểm thanh toán; quyết định D11–D14.

Mục tiêu:

- định nghĩa cardinality và lifecycle;
- map immutable order-line allocation vào shipment/package contents;
- tạo shipment/package idempotent.

Invariant:

- tổng allocated không vượt ordered quantity;
- package content thuộc đúng shipment/order/restaurant;
- không tự sinh thiếu quantity;
- retry fulfillment không tạo package trùng.

Test strategy:

- allocation conservation tests;
- multi-shipment/multi-package theo cardinality đã chốt;
- cancellation race và retry/restart;
- corrupt/missing references fail-closed.

### Gate 5 — Durable shipment/package/convoy recovery

Dependency: Gate 4, quyết định D15–D17.

Mục tiêu:

- migration và repository cho lifecycle snapshot;
- optimistic revision/fencing;
- operation receipt cho handoff;
- recovery policy sau restart.

Invariant:

- state transition atomic và compare-and-set theo revision;
- stale worker không thể commit sau lease/fence mới;
- snapshot chỉ tham chiếu journey/setup version hợp lệ hoặc lưu đủ snapshot;
- không deserialize thành public domain object bất hợp lệ.

Test strategy:

- migration upgrade + schema pin;
- PostgreSQL concurrency/injected-failure/restart tests;
- future-version/dirty/missing-FK/corrupt-row tests;
- V8 -> phiên bản mới và fresh install cùng được kiểm tra.

### Gate 6 — Warehouse stock transaction

Dependency: Gate 1, Gate 4, Gate 5; quyết định D18–D21.

Mục tiêu:

- warehouse inventory/lot/stock movement ledger;
- atomic package handoff + stock credit;
- query phục vụ cooking/UI.

Invariant:

- stock credit đúng SKU/unit/quantity snapshot;
- mỗi package/operation chỉ credit một lần;
- handoff committed không được tồn tại lâu dài mà không có outcome stock rõ ràng;
- partial package/line policy được biểu diễn tường minh.

Test strategy:

- exactly-once credit;
- concurrent handoff;
- failure giữa package transition và stock movement;
- restart reconciliation không cộng lại;
- quantity overflow và unit mismatch.

### Gate 7 — Player ordering/handoff UX

Dependency: Gate 2–6.

Mục tiêu:

- GUI đặt hàng, validation, confirm destructive/costly actions;
- status/ETA/cancel feedback;
- interaction nhận và nhập kho;
- onboarding đơn đầu tiên.

Invariant:

- UI không tự tạo state ngoài durable service;
- main-thread Bukkit access; DB async;
- reconnect/open GUI lại phản ánh database, không cache stale;
- thông báo phân biệt pending/success/conflict/restart-required.

Test strategy:

- presenter/controller tests không cần server khi có thể;
- MockBukkit chỉ cho Bukkit contract phù hợp;
- không tuyên bố Citizens/Paper được verify từ unit tests.

### Gate 8 — Citizens convoy runtime và scheduling

Dependency: Gate 5–7, quyết định runtime D22–D25.

Mục tiêu:

- spawn/navigate/wait/exit/despawn projection;
- bounded scheduler và recovery coordinator;
- chunk-unloaded/offline/dependency unavailable behavior.

Invariant:

- Citizens/Bukkit access server thread;
- không force-load chunk;
- lease/fencing ngăn hai runtime điều khiển cùng shipment;
- dependency unavailable hoặc route unsafe thì fail-closed, không mất hàng/tiền.

Test strategy:

- adapter tests cho state mapping;
- runtime instrumentation/log evidence;
- Paper smoke test chỉ sau phê duyệt deploy/restart riêng.

## 6. Các quyết định cần anh Thành chốt

### Catalog và quantity

- D1: SKU nguồn nào là authoritative: code/config/database hay hybrid?
- D2: Đơn vị hỗ trợ: item nguyên, kg/lít theo fixed scale, hay cả hai?
- D3: Có cho phép SKU bị thay unit; đơn cũ xử lý theo snapshot nào?

### Order và giá

- D4: Ai được đặt: owner, member có permission, hay role nhân viên cụ thể?
- D5: Một order có cho trùng SKU và hệ thống merge line không?
- D6: Giá khóa khi mở GUI, submit hay fulfillment; hết hạn quote thế nào?

### Payment

- D7: Capture ngay lúc submit hay reserve rồi capture ở mốc khác?
- D8: Cancel trước/sau shipment tạo ra được refund bao nhiêu?
- D9: Delivery timeout/failure/refusal có refund toàn phần, một phần hay retry miễn phí?
- D10: Phí giao hàng/tip/discount có nằm trong MVP không?

### Fulfillment

- D11: Một order có thể tách nhiều shipment không?
- D12: Một shipment có thể có nhiều package và/hoặc nhiều convoy không?
- D13: Cho phép fulfill thiếu/partial/backorder không?
- D14: Package contents là allocation theo order line hay chỉ manifest SKU tổng hợp?

### Recovery và runtime

- D15: Sau restart, convoy resume tại checkpoint, respawn từ entry hay chuyển pending manual recovery?
- D16: Setup/route thay đổi khi shipment đang chạy: pin snapshot cũ hay hủy/replan?
- D17: Timeout chờ người chơi nhận hàng là bao lâu và outcome durable nào?

### Warehouse/handoff

- D18: Kho là balance logical, inventory block/chest thực, hay hybrid?
- D19: Handoff toàn package hay từng line/item?
- D20: Kho đầy/unit mismatch xử lý reject, partial hay pending?
- D21: Ai được phép nhận hàng; có bắt buộc đúng người đặt không?

### Citizens/scheduling

- D22: Loại NPC/mount chính xác và ownership lifecycle trong Citizens?
- D23: Chunk unload/player offline dẫn đến pause, despawn hay timeout?
- D24: Số convoy đồng thời toàn server/từng nhà hàng và backpressure?
- D25: SLA/ETA và tick cadence cho scheduler?

## 7. Thứ tự thực thi đề xuất

1. Hoàn thành Gate 0 với các trust-boundary invariant không phụ thuộc gameplay.
2. Anh Thành chốt D1–D14 tối thiểu để khóa catalog, order, payment và fulfillment.
3. Triển khai Gate 1–4 bằng TDD, chưa Citizens.
4. Chốt D15–D21 rồi thiết kế một migration duy nhất đủ coherent cho durable lifecycle + handoff/stock; không tạo bảng nửa vời.
5. Chạy PostgreSQL migration/concurrency/restart verification thật, đồng thời sửa schema assertion stale.
6. Triển khai UX ở Gate 7.
7. Chốt runtime D22–D25, sau đó mới triển khai Citizens/scheduler.
8. Chỉ deploy/restart/Paper smoke khi có phê duyệt rõ ràng và backup theo quy trình.

## 8. Definition of done chung

Mỗi gate chỉ hoàn thành khi:

- có bằng chứng RED cho defect/invariant mới khi thực tế khả thi;
- implementation nhỏ nhất đạt focused tests;
- đã review sibling call paths và public trust boundaries;
- migration gate có fresh-install, upgrade, future-version, dirty/corrupt và restart tests;
- full `./gradlew.bat clean test build --console=plain` GREEN;
- runtime claim có bằng chứng Paper/Citizens riêng, không suy từ unit tests;
- `CURRENT_STATE.md` chỉ được cập nhật khi trạng thái vận hành/thực thi thực sự thay đổi.
