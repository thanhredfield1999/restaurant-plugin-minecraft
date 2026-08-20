# WORKING_STATE — RestaurantTycoon

Ngày cập nhật: 2026-08-20
Repository: `E:\AI.WORK\restauranttycoon`
Branch: `ci/github-actions-postgres-integration`

## Scope

Tiếp tục R4–R7 theo roadmap. R4 controlled Paper smoke là blocker trước R5/R6/R7. Không deploy/restart production; Citizens chưa bật.

## Baseline

- Paper target: `1.21.11-132`
- Java: `21`
- PostgreSQL là source of truth; H2 chỉ unit test.
- Flyway schema: `17`.
- `supply-runtime.enabled=false` mặc định; movement-enabled chỉ dùng dev Paper smoke.
- Runtime không force-load chunk hoặc scan world/entity không bounded.

## Current evidence

- R0/R1/R2: verified.
- R3 checkpoint CAS vertical slice: verified; Paper end-to-end chưa verified.
- R4: code-wired; controlled Paper movement chưa verified.
- R5 Paper receiving, R6 full journey, R7 release: chưa sẵn sàng.
- Smoke gần nhất boot/fixture/claim/spawn được, nhưng stale fixture gây session capacity conflict và Villager `setAI(false)+setVelocity` bị `STUCK`; Paper shutdown sạch. Đã thêm `cleanupAllFixtures` + regression test và smoke hiện đợi marker cleanup-all trước seed; lần smoke mới chưa chạy.
- Villager `STUCK` đã được reproduce trên Paper khi chỉ dùng `setVelocity`; adapter hiện dùng Paper `Pathfinder.moveTo` với fallback velocity. Cần controlled smoke mới để xác nhận movement thật; không fake PASS.
- Full local build gần nhất: `./gradlew.bat clean test build --no-daemon --console=plain` — PASS.

## Latest code

Commit gần nhất: `55e602c fix: isolate runtime smoke fixtures and enable villager motion`.

Thay đổi gần nhất gồm cleanup stale fixtures trước smoke, bật AI cho Vanilla Villager, giữ session sau CAS và reload projection kế tiếp, smoke chờ terminal cleanup.

## Working tree safety

Có thay đổi người dùng/chưa phân loại trong docs, smoke guard và test; không reset/xóa. Không đọc/in/commit `filepasspostgresdacapnhat.txt`.

## Next steps

1. Kiểm tra CI của `55e602c`.
2. Nếu CI pass, chạy lại controlled Paper smoke trên port rảnh.
3. Nếu Villager vẫn `STUCK`, nghiên cứu/sửa Paper movement path; không fake PASS.
4. Chỉ ghi R4 Paper PASS khi đủ entity spawn → moving → arrived → checkpoint CAS → entity cleaned.
5. Sau R4 mới tiếp tục R5 receiving, R6 journey, R7 release gate.

## Safety

Không claim Paper/Citizens/restart/performance verified từ unit test hoặc CI בלבד. Ghi RCA/evidence/decision quan trọng trong repo.

Evidence hiện tại: `R4 CODE-WIRED; CONTROLLED PAPER MOVEMENT NOT VERIFIED; R5/R6/R7 NOT READY.`
