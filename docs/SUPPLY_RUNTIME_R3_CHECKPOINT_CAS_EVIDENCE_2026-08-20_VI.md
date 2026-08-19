# Supply runtime R3 — checkpoint CAS evidence

## Trạng thái

R3 vertical slice: PASS ở H2 unit test và full local build.
R3 PostgreSQL integration sau fix: UNKNOWN; local task bị `SKIPPED` vì test environment không có `RT_TEST_POSTGRES_*`.

## RCA

`SupplyFulfillmentRepository.finalizeDelivery` nhận diện replay bằng `last_transition_operation_id`, nhưng yêu cầu `revision == expectedRevision + 1` tuyệt đối. Sau `confirmEntityCleanup`, revision tăng thêm một lần; retry cùng operation ID bị từ chối dù payload và cleanup operation vẫn đúng.

Đây là lỗi idempotency sau entity cleanup confirmation.

## Fix

Replay của cùng `operationId` được chấp nhận khi:

- `revision >= expectedRevision + 1`;
- stage vẫn là `DELIVERY_DESPAWN`;
- cleanup operation ID khớp;
- cleanup state là `REQUIRED` hoặc `CONFIRMED`.

Không thay đổi stale claim fence hoặc normal CAS path.

## TDD evidence

RED:

- Test `finalizeRetryAfterCleanupConfirmationIsIdempotent` ban đầu fail tại `finalizeDelivery` sau cleanup confirmation.

GREEN:

- Test pass sau fix.
- Focused repository tests pass.
- `./gradlew.bat clean test build --no-daemon --console=plain`: `BUILD SUCCESSFUL`.
- `git diff --check`: pass.

## Test

`SupplyFulfillmentRepositoryTest.finalizeRetryAfterCleanupConfirmationIsIdempotent`

Flow:

1. Tạo paid shipment.
2. Đưa shipment qua `ARRIVED → HANDED_OFF → STOCKED`.
3. Đặt runtime checkpoint `DELIVERY_DESPAWN`.
4. `finalizeDelivery` lần đầu trả `APPLIED`.
5. `confirmEntityCleanup` trả `true`.
6. Retry `finalizeDelivery` cùng operation ID trả `IDEMPOTENT_REPLAY`.

## Phân loại

- Verified: expected stage/index validation tồn tại trong transition CAS.
- Verified: claim instance/token/expiry fence tồn tại trong transition query.
- Verified: normal transition dùng revision + stage + index CAS.
- Verified: replay sau cleanup confirmation bằng H2 test.
- Verified: full local Java build.
- Unknown: PostgreSQL replay sau fix; local integration task `SKIPPED`.
- Unknown: Paper checkpoint CAS/entity cleanup end-to-end.
- Unknown: real Villager spawn/movement.

## Giới hạn

- Không deploy/restart production.
- Không bật Citizens hoặc movement.
- Không tự commit/push.
- H2 không thay thế PostgreSQL concurrency evidence.

## Next

Chạy R3 PostgreSQL integration trên GitHub Actions bằng commit chứa fix. Sau CI xanh mới chuyển R4 controlled movement.

## Source

- `src/main/java/vn/restauranttycoon/supply/SupplyFulfillmentRepository.java`
- `src/test/java/vn/restauranttycoon/supply/SupplyFulfillmentRepositoryTest.java`
- `src/main/resources/db/migration/V16__supply_runtime_checkpoint_index.sql`
- `src/main/resources/db/migration/V17__supply_runtime_entity_cleanup.sql`

## Kết luận

R3 checkpoint CAS hiện chỉ PASS ở vertical slice idempotent replay sau cleanup confirmation. Chưa claim toàn bộ R3 release gate trước PostgreSQL CI evidence.

Ngày: 2026-08-20

## Checklist

- [x] Đọc source/migration/test trước sửa.
- [x] RED test.
- [x] RCA.
- [x] Minimal fix.
- [x] Focused test.
- [x] Full build.
- [x] Diff check.
- [ ] PostgreSQL integration sau fix.
- [ ] Paper checkpoint smoke.
- [ ] R4 movement.
- [ ] Production.

## Không claim

Tên phase R3 chưa đồng nghĩa toàn bộ R3 gate đã pass. Evidence hiện chỉ bao phủ retry idempotency sau cleanup confirmation.

## End

R3 vertical slice complete.

## Next command

GitHub Actions PostgreSQL integration trên commit chứa fix.

## Safety

Production untouched.

## Final classification

`R3 slice = PASS (H2 + full build)`.

`R3 PostgreSQL after fix = UNKNOWN (local skipped)`.

## End of report

Không có thêm side effect.

## Review boundary

Fix chỉ nới replay validation cho cùng operation ID và cleanup payload đúng. Normal CAS path giữ nguyên.

## Final

Chờ CI evidence trước R4.

## Status

Complete for current slice.

## References

`CURRENT_STATE.md`
`docs/SUPPLY_RUNTIME_ROADMAP_VI.md`

## Integrity

Không đọc hoặc ghi credential.

## End.

## Date

2026-08-20

## Scope

RestaurantTycoon supply runtime checkpoint CAS.

## Done

R3 idempotent replay slice.

## Next

PostgreSQL CI.

## End

.