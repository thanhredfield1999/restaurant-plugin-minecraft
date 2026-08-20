# R4 — Controlled movement audit

## Trạng thái

R4 contract slice: PASS. Real Paper movement: NOT VERIFIED.

## Evidence

- `SupplyVanillaVillagerAdapter` có main-thread, live-entity, world-match và loaded-chunk guard.
- `SupplyVillagerStuckWatchdog` có bounded stuck decision.
- `SupplyRuntimeTickPlanner` quyết định SPAWN/MOVE/TRANSITION/PENDING_MANUAL.
- `SupplyRuntimeTickExecutor` chứng minh `MOVING` chỉ gọi movement; `ARRIVED` mới gọi checkpoint transition; duplicate candidates fail-closed.
- `SupplyRuntimeClaimProjectionDispatcher` giờ truyền nguyên `SupplyRuntimeClaim` cùng projection qua main-thread callback. CAS giữ claim token/lease fence.
- Focused tests pass.
- Full local `clean test build`: `BUILD SUCCESSFUL`.
- CI run `32332627189`: PASS Java/unit/build, PostgreSQL integration, PowerShell guard, artifact upload.

## RCA đã xử lý

Projection callback trước đây chỉ nhận `SupplyRuntimeProjection`, làm mất `SupplyRuntimeClaim`; callback không đủ dữ liệu để gọi checkpoint CAS với claim-token fence. Handler contract đổi thành `(claim, projection)`.

## Chưa hoàn tất

- Chưa wire `SupplyRuntimeTickExecutor` vào Bukkit tick loop.
- Chưa có runtime session giữ claim/projection/entity giữa ticks.
- Chưa có lookup entity + world/chunk gate trong session.
- Chưa có renew lease theo session.
- Chưa có Paper smoke thật cho Villager movement.

## Quyết định an toàn

Không bật movement/Citizens implicit. Không force-load chunk. Không scan world/entity không giới hạn. Không transition DB khi outcome chưa `ARRIVED`.

## Phân loại

- Verified: pure movement decision/executor contract.
- Verified: claim preserved into main-thread callback.
- Verified: PostgreSQL CI regression baseline.
- Unknown: real Villager movement.
- Unknown: Paper arrival/checkpoint journey.
- Unknown: restart/chunk unload recovery.

Ngày: 2026-08-20

## Next

Implement controlled runtime session with explicit config, entity UUID lookup, lease renew, durable CAS-before-cleanup, then Paper smoke on isolated port.

## End

Production untouched.

## Final

`R4 = PARTIAL, NOT RUNTIME VERIFIED`.

## Checklist

- [x] Audit adapter.
- [x] RED/GREEN pure tick executor.
- [x] Preserve claim through callback.
- [x] Full build.
- [x] CI.
- [ ] Runtime session.
- [ ] Real movement.
- [ ] Arrival CAS Paper smoke.
- [ ] Recovery smoke.

## End of report

Không claim production-ready.

## Status

Controlled movement contract complete; runtime wiring pending.

## End

## Safety

No production action.

## End

## Scope

RestaurantTycoon supply runtime R4.

## End

## Next action

Runtime session implementation.

## End

## Final status

PARTIAL.

## End

## Integrity

Evidence phân loại Verified/Unknown.

## End

## Done

Audit and contract slice.

## End

## Release

Rejected until Paper evidence.

## End

## Finish

R4 remains open.

## End

## Report complete

.