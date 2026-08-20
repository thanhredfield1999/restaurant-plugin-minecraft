# Roadmap supply runtime — 2026-08-19

## Kết luận

Khung runtime đã boot Paper và chạy claim polling, projection dispatch đã wire. Chưa đủ điều kiện bật Villager movement hoặc Citizens. Roadmap giữ runtime fail-closed, mỗi gate có evidence riêng.

## Trạng thái hiện tại

- Verified: claim/lease/fence bằng H2 và source review.
- Verified: projection load từ claim, DB executor → main thread dispatch.
- Verified: Paper `1.21.11-132` boot với PostgreSQL schema `17`, shutdown sạch.
- Verified: R0 fixture Paper smoke port `25569`: seed → claim → projection callback exact shipment → cleanup.
- Verified: R1 PostgreSQL concurrency/restart gate chạy trên GitHub Actions PostgreSQL 17 service, run `32284723370`.
- Verified: concurrent supply workers chỉ claim một shipment; stale lease bị reclaim; old claim bị fence; replacement dispatch thành công.
- Verified: Flyway V17 rerun, market concurrency, purchase idempotency, unit/build và PowerShell smoke guard cùng pass trong run `32284723370`.
- Verified: R2 entity projection-only Paper smoke port `25570`: callback exact shipment tạo `entityAction=SPAWN`, movement disabled, cleanup `deleted=true`, shutdown sạch.
- Verified: runtime config foundation fail-closed, movement disabled by default, bounded session/radius/speed/lease-renew settings; full local build passed.
- Unknown: real Villager spawn/reuse, loaded-world recovery, runtime tick wiring, checkpoint CAS sau entity action; local PostgreSQL replay vì máy local không có `RT_TEST_POSTGRES_*`.
- Verified: R3 checkpoint CAS vertical slice: retry cùng operation ID sau `confirmEntityCleanup` trả `IDEMPOTENT_REPLAY`; normal revision/stage/index CAS và claim fence giữ nguyên.
- Unknown: PostgreSQL replay sau R3 fix vì local integration task `SKIPPED`; Paper checkpoint CAS/entity cleanup end-to-end chưa smoke.
- Blocked: real entity lifecycle, movement, full checkpoint CAS + cleanup end-to-end, receiving authorization journey.

## Current implementation mapping

Tên gate lịch sử bên dưới giữ nguyên để không mất trace. Mapping code hiện tại:

- R0: observability fixture — PASS.
- R1: PostgreSQL durability — PASS qua CI.
- R2: entity projection-only — PASS, real entity chưa enable.
- R3: checkpoint CAS vertical slice — PASS, Paper end-to-end UNKNOWN.
- R4: movement foundation/config/session contract — PARTIAL; runtime tick/movement chưa verified.
- R5: receiving persistence slice — PASS; Paper interaction UNKNOWN.
- R6: full gameplay journey — NOT VERIFIED.
- R7: release gate — REJECTED.

## Gates

### Gate R0 — Observability fixture

Mục tiêu: tạo test-only shipment/runtime fixture bằng operation ID ổn định; smoke log được `claim → projection → main-thread handoff`. Không spawn entity.

Implementation:

- `SupplyRuntimeFixture.java`;
- `SupplyRuntimeFixtureRepository.java`;
- `SupplyRuntimeFixtureRepositoryTest.java`;
- `RestaurantTycoonPlugin`: `restaurant dev runtime-fixture seed|cleanup`;
- `scripts/paper-smoke.ps1 -RuntimeFixture`.

Status:

- focused fixture test: pass;
- full `./gradlew.bat clean test build --no-daemon --console=plain`: `BUILD SUCCESSFUL`;
- Paper callback smoke: pending system approval;
- movement/Citizens: disabled.

Acceptance giữ nguyên: fixture chỉ console/dev flag, cleanup idempotent, log shipment/checkpoint, không bật movement.


### Gate R1 — PostgreSQL durability

Mục tiêu: chạy concurrent claim, stale lease, restart, transition CAS, cleanup receipt trên PostgreSQL thật.

Acceptance:

- old claim không ghi đè revision mới;
- retry operation ID idempotent;
- corrupt/missing snapshot → `PENDING_MANUAL`;
- schema future/dirty bị từ chối;
- không force-load chunk.

### Gate R2 — Entity projection only

Mục tiêu: spawn/recover đúng một Villager supplier tại checkpoint trên main thread.

Acceptance:

- registry event lifecycle không scan world;
- 0 candidate → spawn;
- 1 candidate → reuse;
- >1 candidate → manual recovery;
- chunk/world mismatch → fail-closed;
- restart/chunk unload không nhân đôi entity.

### Gate R3 — Movement watchdog

Mục tiêu: di chuyển test-controlled từ checkpoint này sang checkpoint kế.

Acceptance:

- loaded world/chunk only;
- arrival radius rõ;
- stuck → manual recovery;
- velocity/navigation behavior được Paper reproduction chứng minh;
- không transition DB khi chưa arrived.

### Gate R4 — Checkpoint CAS

Mục tiêu: movement arrival → command planner → PostgreSQL CAS → next checkpoint.

Acceptance:

- expected revision/stage/index + claim fence bắt buộc;
- stale callback không transition;
- duplicate operation ID idempotent;
- terminal stage chuyển cleanup command, không tự xóa entity trước durable commit.

### Gate R5 — Receiving authorization/handoff

Mục tiêu: đúng owner/member nhận tại unload point, stock commit exactly-once.

Acceptance:

- owner/member permission source-of-truth;
- package/restaurant/shipment match;
- disconnect/retry không cộng stock hai lần;
- timeout → `PENDING_MANUAL`, không refund tự động.

### Gate R6 — Controlled gameplay journey

Mục tiêu: order → wait → supplier → unload → player receive → warehouse stock → despawn.

Chỉ chạy dev Paper, có backup/cleanup. Không deploy production tự động.

## Thứ tự thực hiện

R0 → R1 → R2 → R3 → R4 → R5 → R6.

Không nhảy gate. Unit/build pass không thay thế Paper journey hoặc PostgreSQL evidence.

## Quyết định an toàn

- Runtime movement giữ disabled tới khi R1–R4 pass.
- Citizens chưa bật; vanilla Villager adapter chỉ là code path chưa được enable.
- Production deploy/restart cần user approval riêng.
- Mọi kết quả, RCA và evidence ghi trong repo.
