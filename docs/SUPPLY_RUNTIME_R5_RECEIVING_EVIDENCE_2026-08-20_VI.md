# Supply runtime R5 — receiving evidence

## Kết luận

R5 receiving persistence slice: PASS ở H2 unit/integration domain tests và full local build.

## RCA

`PackageInteractionListener` trước đây thêm package token vào inventory trước khi durable handoff hoàn tất. Crash hoặc DB failure giữa hai bước có thể để token không có durable record, hoặc rollback inventory không chắc chắn sau disconnect.

## Fix

Thứ tự mới:

1. DB executor thực hiện `handoffPackageAuthorizedForOrderPlayer`.
2. Chỉ sau durable success, main thread kiểm tra player online.
3. Nếu token đã có, không grant trùng; remove entity.
4. Nếu inventory còn chỗ, grant token rồi remove entity.
5. Nếu inventory đầy hoặc player offline, giữ entity để retry; durable handoff không mất.

Không chạm Bukkit từ DB thread.

## Tests

- `SupplyPackageReceivePlannerTest`: durable handoff đứng trước grant/remove; retry có token bỏ qua grant.
- `SupplyHandoffRepositoryTest`: handoff + stock exactly-once, foreign restaurant rejection, authorized actor separation, operation idempotency.
- Full build: `BUILD SUCCESSFUL`.
- `git diff --check`: pass sau khi loại blank-line artifact trong evidence doc.

## Phân loại

- Verified: durable handoff API owner/player authorization.
- Verified: warehouse stock idempotency và restaurant scope trong repository tests.
- Verified: DB work tách khỏi main-thread inventory/entity side effects.
- Verified: retry cùng player/package không grant token trùng.
- Unknown: Paper player interaction journey thực tế.
- Unknown: disconnect timing trên Paper.
- Unknown: real supplier entity delivery tới unload.

## Giới hạn

- Không bật Citizens.
- Không bật movement.
- Không deploy/restart production.
- H2 không thay thế PostgreSQL concurrency evidence.
- R5 PostgreSQL interaction test cần CI run mới sau khi push; chưa claim CI mới.

## Next

R6 audit gameplay journey: order → wait → supplier → unload → player receive → warehouse stock → despawn/recovery.

Ngày: 2026-08-20

## Status

R5 persistence slice PASS; Paper journey UNKNOWN.

## End
