# R4 — Controlled movement audit

## Kết luận

R4 chưa mở runtime movement.

## Evidence

- `SupplyVanillaVillagerAdapter` có pure Bukkit adapter cho spawn/move/remove.
- Adapter kiểm tra main thread.
- Adapter kiểm tra world mismatch.
- Adapter kiểm tra spawn chunk loaded.
- `SupplyVillagerStuckWatchdog` có giới hạn tick và minimum progress.
- `SupplyRuntimeTickPlanner` phân biệt `SPAWN`, `MOVE`, `TRANSITION_CHECKPOINT`, `MARK_PENDING_MANUAL`.
- `SupplyRuntimeMovementActionResolver` chỉ transition khi `ARRIVED`, stuck chuyển manual.
- Không tìm thấy call path từ projection callback hiện tại tới `moveToward` hoặc `SupplyRuntimeTickPlanner`.
- Projection callback hiện tại chỉ log `entityAction` và giữ `movement=disabled`.

## RCA / gap

Movement code tồn tại nhưng chưa được wire vào runtime coordinator. Đây là boundary an toàn hiện tại, không phải runtime failure: bật movement ngay sẽ tạo side effect Bukkit chưa có Paper journey evidence và có thể bypass durable checkpoint orchestration.

## Phân loại

- Verified: movement chưa bật.
- Verified: adapter có main-thread/world/chunk guards.
- Verified: watchdog pure behavior đã có unit test.
- Verified: projection smoke không spawn entity thật.
- Unknown: real Villager navigation/velocity trong Paper.
- Unknown: chunk unload/reload lifecycle khi entity thật.
- Unknown: arrival → PostgreSQL transition trên Paper.

## Không tự thực hiện

Không bật movement/Citizens trong R4 audit vì project rule yêu cầu approval riêng trước runtime movement.
Không force-load chunk.
Không deploy/restart production.

## Cần approval

Cần user approval rõ cho controlled dev Paper movement test, gồm:

- spawn vanilla supplier trong dev smoke;
- move bằng velocity trong loaded chunk/world;
- ghi checkpoint CAS khi `ARRIVED`;
- stuck → `PENDING_MANUAL`;
- cleanup sau terminal stage;
- port riêng, cleanup bắt buộc, không production.

## Next

Sau approval: RED test cho movement runtime bridge, GREEN wire path main-thread-only, focused test, Paper controlled smoke, rồi PostgreSQL CI.

Ngày: 2026-08-20

## Status

R4 audit complete — blocked by explicit movement approval.

## Safety

Production untouched.

## End

R4 chưa PASS.

## References

- `SupplyVanillaVillagerAdapter.java`
- `SupplyVillagerStuckWatchdog.java`
- `SupplyRuntimeTickPlanner.java`
- `SupplyRuntimeMovementActionResolver.java`
- `SupplyRuntimeClaimProjectionDispatcher.java`
- `CURRENT_STATE.md`
- `docs/SUPPLY_RUNTIME_ROADMAP_VI.md`

## Final classification

`R4 = BLOCKED (approval required for real movement side effects)`.

## End.

## Checklist

- [x] Source audit.
- [x] Call-path audit.
- [x] Main-thread guard audit.
- [x] Chunk/world guard audit.
- [x] Evidence report.
- [ ] Movement approval.
- [ ] RED runtime bridge test.
- [ ] Paper controlled movement.
- [ ] PostgreSQL movement transition.

## No production action

No production runtime changed.

## End of report

R4 audit done.

## Decision

Do not enable movement implicitly.

## End
