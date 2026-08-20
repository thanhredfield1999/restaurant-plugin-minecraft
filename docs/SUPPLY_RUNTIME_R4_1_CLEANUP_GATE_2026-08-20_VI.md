# R4.1 cleanup gate — evidence

Ngày: 2026-08-20

## Kết luận

R4.1 code gate: PASS. Paper movement smoke: PENDING USER TEST.

## RCA

Runtime target resolver không có terminal handling rõ ràng. Delivery route tới `DELIVERY_DESPAWN` cần arrival trước, sau đó mới chạy `finalizeDelivery`, remove entity main thread, rồi `confirmEntityCleanup` durable.

## Fix

- Thêm terminal target handling.
- Thêm single-flight finalization theo shipment.
- `finalizeDelivery` chạy DB async.
- Entity chỉ remove sau finalize durable success.
- `confirmEntityCleanup` chạy sau remove entity.
- Finalization lỗi giữ session/entity, không xóa sớm.
- Cleanup entity mismatch chuyển `PENDING_MANUAL`.
- Thêm log markers cho spawn, moving, arrived, CAS, cleanup.
- Smoke script thêm `-Movement`; config generated bật runtime chỉ khi flag này có.

## Verification

- Focused movement target test: PASS.
- Full `./gradlew.bat clean test build --no-daemon --console=plain`: `BUILD SUCCESSFUL`.
- `git diff --check`: PASS.
- GitHub Actions run `32348506569`: PASS.
- PowerShell smoke guard CI: PASS.

## Chưa verified

- Real Villager movement trên Paper.
- Loaded-world/chunk recovery trên Paper.
- Arrival/CAS/cleanup runtime end-to-end trên Paper.
- Player receiving journey.

## Safety

`Movement` chưa chạy. Production untouched. Citizens chưa bật.

## Commit

`a6c2ee3 fix: gate terminal cleanup before entity despawn`

## Next

User chạy controlled dev smoke:

```powershell
$env:RT_ACCEPT_MINECRAFT_EULA='true'
.\gradlew.bat build --no-daemon --console=plain
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\paper-smoke.ps1 -RuntimeFixture -Movement -Port 25572 -TimeoutSeconds 300
```

Không dùng `-UseExistingConfig` với `-Movement`. Cần `RT_TEST_POSTGRES_URL`, `RT_TEST_POSTGRES_USER`, `RT_TEST_POSTGRES_PASSWORD`. Không gửi credential vào chat.

## Status

R4.1 code PASS; R4 Paper smoke chưa PASS.

## End

Không release.
