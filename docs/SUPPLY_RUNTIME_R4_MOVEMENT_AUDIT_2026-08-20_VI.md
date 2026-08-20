# R4 — Controlled movement audit

## Trạng thái

R4 contract slice: PASS. Real Paper movement: NOT VERIFIED.

## Evidence

- `SupplyVanillaVillagerAdapter` có main-thread, live-entity, world-match và loaded-chunk guard.
- `SupplyVillagerStuckWatchdog` có bounded stuck decision.
- `SupplyRuntimeTickPlanner` quyết định SPAWN/MOVE/TRANSITION/PENDING_MANUAL.
- `SupplyRuntimeTickExecutor` chứng minh `MOVING` chỉ gọi movement; `ARRIVED` mới gọi checkpoint transition; duplicate candidates fail-closed.
- `SupplyRuntimeClaimProjectionDispatcher` truyền nguyên `SupplyRuntimeClaim` cùng projection qua main-thread callback.
- `SupplyRuntimeSession` fail-closed khi claim/projection identity mismatch.
- `SupplyRuntimeSessionRegistry` bounded, one session/shipment, không scan world/entity.
- Focused tests pass.
- Full local `clean test build`: `BUILD SUCCESSFUL`.
- CI run `32332627189`: PASS Java/unit/build, PostgreSQL integration, PowerShell guard, artifact upload.

## RCA đã xử lý

Projection callback trước đây chỉ nhận `SupplyRuntimeProjection`, làm mất `SupplyRuntimeClaim`; callback không đủ dữ liệu để gọi checkpoint CAS với claim-token fence. Handler contract đổi thành `(claim, projection)`.

## Chưa hoàn tất

- Chưa wire `SupplyRuntimeTickExecutor` vào Bukkit tick loop.
- Chưa có entity lookup bounded trong runtime session.
- Chưa có renew lease theo session.
- Chưa có Paper smoke thật cho Villager movement.
- Chưa có arrival → CAS → cleanup Paper journey.

## Quyết định an toàn

Không bật movement/Citizens implicit. Không force-load chunk. Không scan world/entity không giới hạn. Không transition DB khi outcome chưa `ARRIVED`.

## Phân loại

- Verified: pure movement decision/executor contract.
- Verified: claim preserved into main-thread callback.
- Verified: bounded session identity registry.
- Verified: PostgreSQL CI regression baseline.
- Unknown: real Villager movement.
- Unknown: Paper arrival/checkpoint journey.
- Unknown: restart/chunk unload recovery.

Ngày: 2026-08-20

## Next

Wire controlled runtime session với explicit config, entity UUID lookup, lease renew, durable CAS-before-cleanup, rồi Paper smoke isolated port.

## Final

`R4 = PARTIAL, NOT RUNTIME VERIFIED`.

Production untouched.

## Checklist

- [x] Audit adapter.
- [x] RED/GREEN pure tick executor.
- [x] Preserve claim through callback.
- [x] Bounded session registry.
- [x] Full build.
- [x] CI.
- [ ] Runtime session tick wiring.
- [ ] Real movement.
- [ ] Arrival CAS Paper smoke.
- [ ] Recovery smoke.

## End

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

Runtime session tick implementation.

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