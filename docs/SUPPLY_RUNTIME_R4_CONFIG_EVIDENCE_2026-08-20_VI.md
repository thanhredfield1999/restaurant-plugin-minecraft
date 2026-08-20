# R4 config slice — evidence

## Kết luận

R4 config foundation: PASS. Runtime movement vẫn disabled mặc định.

## Thay đổi

- Thêm `SupplyRuntimeSettings`.
- Thêm `supply-runtime` vào `PluginSettings`.
- Thêm config:
  - `enabled: false`;
  - `max-sessions: 4`;
  - `arrival-radius: 1.5`;
  - `max-speed: 0.25`;
  - `lease-renew-ticks: 20`.
- Bounds fail-closed:
  - sessions `1..100`;
  - arrival radius `>0..16`;
  - speed `>0..2`;
  - renew ticks `1..1200`.

## Tests

- RED: test compile fail vì `SupplyRuntimeSettings` chưa tồn tại.
- GREEN: `SupplyRuntimeSettingsTest` pass.
- `PluginSettingsTest` pass.
- Full `./gradlew.bat clean test build --no-daemon --console=plain`: `BUILD SUCCESSFUL`.
- `git diff --check`: pass.

## Phân loại

- Verified: config parse, defaults, bounds, fail-closed validation.
- Verified: runtime setting `enabled` default false.
- Unknown: runtime tick wiring.
- Unknown: real Villager movement.
- Unknown: lease renew trong movement session.
- Unknown: Paper arrival/CAS/recovery.

## Safety

Không bật movement. Không bật Citizens. Không deploy/restart production.

## Next

Wire settings vào bounded runtime session loop; chỉ hoạt động khi `supply-runtime.enabled=true` trong controlled dev config.

## Status

`R4 config foundation = PASS`.
`R4 runtime movement = CODE-WIRED, NOT PAPER-VERIFIED`.

Ngày: 2026-08-20

## End

Không claim full R4.

## Checklist

- [x] Config model.
- [x] Defaults disabled.
- [x] Bounds validation.
- [x] Focused tests.
- [x] Full build.
- [ ] Runtime tick.
- [ ] Paper movement.
- [ ] Arrival CAS.
- [ ] Recovery.

## Final

Config foundation complete.

## End

## Safety end

Production untouched.

## End

## Next

Runtime session wiring.

## End

## Report complete

.