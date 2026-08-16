# Compatibility va Implementation Plan

Tai lieu nay rang buoc stack va thu tu trien khai cho Minecraft Restaurant Tycoon.
Neu mau thuan voi cac de xuat phien ban cu trong plan tong, tai lieu nay duoc uu tien.

## 1. Quyet dinh da chot

| Hang muc | Quyet dinh |
|---|---|
| Backend | Paper 1.20.4, pin exact build sau compatibility spike |
| Client native | Minecraft Java 1.20.4 |
| Client ho tro chinh thuc luc launch | 1.20.3 va 1.20.4 |
| Client may yeu khuyen nghi | 1.20.4 + Fabric + Sodium |
| ViaVersion | Defer; chi them khi mo client khac protocol 765 |
| ViaBackwards | Defer cung ViaVersion |
| ViaRewind | Khong |
| Oraxen | Defer den sau vertical slice |
| ProtocolLib | Khong cai neu chua co tinh nang cu the bat buoc |
| Resource pack | Chua dung trong core vertical slice |
| Multi-version pack | Defer cung Oraxen |
| Pack layering | Tat; dung replacement mode |
| Geyser/Bedrock | Defer thanh milestone rieng |
| Capacity ban dau | 20 concurrent players; 50 la stretch gate |
| Update policy | Staging, backup, full restart, smoke test; khong auto-update production |

Ly do gioi han 1.20.3-1.20.4: hai client dung cung resource-pack format 22,
khong vuot ranh gioi Data Components 1.20.5 va khong vuot ranh gioi Item Model
Definitions 1.21.4. ViaVersion/ViaBackwards la lop van hanh, khong phai cam ket
rang moi client co the ket noi deu duoc support.

Luu y: 1.20.3 va 1.20.4 cung protocol 765. Server/Via khong the phan biet tin
cay exact patch runtime. Enforcement chi co the allow protocol 765; hai patch
duoc test rieng bang client test co quan ly, khong duoc ghi audit nhu du lieu
client exact co tinh bao mat.

Vi hai client launch deu native protocol 765, ViaVersion/ViaBackwards khong dich
gi trong happy path. Core baseline khong cai Via. Chi them Via trong compatibility
milestone rieng khi co nhu cau mo client khac protocol 765.

Oraxen va resource pack cung duoc defer. Vertical slice dung vanilla material,
text va block authored. Dieu nay cho phep kiem chung economy, order, persistence,
restart recovery va performance truoc khi them presentation dependency.

## 2. Cac ranh gioi phien ban nguy hiem

| Ranh gioi | Thay doi | Rui ro voi game |
|---|---|---|
| 1.20.1 -> 1.20.2 | Pack format 15 -> 18 | Pack warning, cache/hash va QA them mot bien the |
| 1.20.2 -> 1.20.3 | Pack format 18 -> 22; layering 1.20.3+ | Sai pack dispatch neu gom client qua rong |
| 1.20.4 -> 1.20.5 | Pack format 22 -> 32; ItemStack NBT -> Data Components | Item conversion, serialization va inventory desync |
| 1.21.1 -> 1.21.2 | Pack format 34 -> 42 | Component/predicate tiep tuc thay doi |
| 1.21.3 -> 1.21.4 | Pack format 42 -> 46; item model system moi | Mat custom texture, sai CMD/item_model, model tim-den |
| 1.21.8 -> 1.21.9 | pack.mcmeta doi min_format/max_format | Pack metadata cu khong con du |

## 3. Loi khong dang co va cach chan tu dau

### 3.1 Mot pack gui cho moi client version

Trieu chung:

- Client bao incompatible pack.
- Item hien material vanilla hoac texture tim-den.
- Pack load duoc nhung model item khong doi.

Nguyen nhan:

- Via chi dich protocol, khong chuyen resource pack.
- Pack format hoac item model syntax khong dung client.

Phong tranh:

- Launch chi support 1.20.3-1.20.4 va pack format 22.
- Neu mo rong client, bat Oraxen `multi_version_packs` va test tung pack range.
- Khong sua `pack.mcmeta` bang tay sau khi Oraxen build.

### 3.2 Nhieu plugin/proxy cung gui pack

Trieu chung:

- Pack reload hai lan.
- Pack Oraxen bi pack lobby thay the.
- Player reconnect bi loop download.

Phong tranh:

- Oraxen la pack dispatcher duy nhat.
- `server.properties` khong khai bao mot resource-pack khac.
- Proxy va plugin lobby khong gui pack.
- `Pack.dispatch.layer: ""` o launch.

### 3.3 CDN cache pack cu trong khi URL/hash khong doi

Trieu chung:

- Mot so player thay model moi, player khac van thay model cu.
- Server bao success nhung asset khong dong nhat.

Phong tranh:

- URL immutable co content hash/version trong ten.
- SHA-1 phai tinh tu exact ZIP da upload.
- Upload truoc, GET lai artifact, so checksum roi moi deploy config.
- Khong overwrite file dang duoc client cache.

### 3.4 ZIP sai root hoac ten asset sai quy uoc

Trieu chung:

- Pack duoc chap nhan nhung khong co asset.
- `pack.mcmeta` not found hoac missing model.

Phong tranh:

- Root ZIP phai chua truc tiep `pack.mcmeta` va `assets/`.
- Moi namespace/path dung chu thuong.
- CI parse moi JSON va kiem tra moi texture/model reference.
- Khong dung path co dau cach, chu hoa hoac ky tu khong hop le.

### 3.5 Trung CustomModelData

Trieu chung:

- Them item moi lam item cu doi texture.
- Hai item dung chung model ngoai y muon.

Phong tranh:

- Oraxen quan ly ID; khong gan CMD thu cong neu khong co registry.
- Luu manifest `oraxen_id -> material -> CMD -> model` trong release artifact.
- Validator fail build khi trung mapping.
- Khong thay mapping cua item da phat hanh neu chua co migration.

### 3.6 Tin Oraxen ID, material, name hoac lore la entitlement kinh te

Trieu chung:

- Rename/clone item de nhan payout.
- Via conversion lam item hop le bi tu choi hoac item gia duoc chap nhan.

Phong tranh:

- Oraxen chi lam presentation.
- Database giu `order_id`, `restaurant_id`, `entitlement_id`, `recipe_version`.
- PDC chi la token lien ket; settlement doi chieu durable state va consume CAS.
- Material/name/lore/CMD/Oraxen ID khong bao gio du de payout.

### 3.7 Display Entity khong ton tai tren client cu

Trieu chung:

- Furniture vo hinh hoac hitbox khong trung model.

Su that:

- Oraxen xac nhan Display Entity furniture chi hien tren client 1.19.4+;
  ViaVersion khong the sua tinh nang client khong co.

Phong tranh:

- Chan client duoi 1.20.3.
- Gameplay critical khong chi dua vao furniture presentation.
- Purchase control co text/action-bar fallback.

### 3.8 Display Entity qua nhieu lam may yeu tut FPS

Trieu chung:

- Server MSPT tot nhung client FPS thap.
- Nhin vao nha hang thi giat, quay di thi het.

Phong tranh:

- Uu tien vanilla/custom block thay vi multi-entity furniture.
- Mot furniture thuong toi da mot Display Entity va mot Interaction Entity.
- Dat `view_range`, display width/height va khong animation moi tick.
- Budget ban dau: toi da 20 furniture display va 4 physical customer/active plot.
- Do FPS client rieng, khong suy ra tu TPS/MSPT.

### 3.9 Oraxen tu sua Paper config khong duoc review

Trieu chung:

- NoteBlock/tripwire/chorus behavior thay doi ngoai du kien.

Phong tranh:

- Dat `Plugin.auto_update_paper_config: false` sau lan bootstrap.
- Commit exact `paper-global.yml` vao infrastructure config.
- Review ro cac setting disable block updates truoc khi dung custom block.
- Plot authored; khong cho free-build trong MVP.

### 3.10 Dung `/reload` hoac plugin manager reload

Trieu chung:

- Duplicate listener/task/entity.
- Via user connection, PacketEvents adapter va Oraxen pack state lech nhau.

Phong tranh:

- Cam `/reload`, PlugMan reload va hot swap production.
- Moi update dung full process restart.
- Health command bao build/version cua Paper, Via, Backwards, Oraxen va plugin.

### 3.11 Auto-update lam version drift

Trieu chung:

- Restart hom sau sinh loi du khong deploy code.
- CommandAPI/PacketEvents duoc Oraxen tai ban khac staging.

Phong tranh:

- Pin exact artifact va checksum tat ca plugin.
- Luu artifact Oraxen auto-downloaded dependency sau bootstrap vao manifest.
- Production khong tu update; staging test truoc.
- Startup fail neu version ngoai compatibility manifest.

### 3.12 Cai Via tren ca proxy va backend khong co chu dich

Trieu chung:

- Kho truy vet protocol path, mapping khac version hoac packet bi dich nhieu lop.

Phong tranh:

- Mot Paper server: cai ViaVersion/ViaBackwards tai backend.
- Neu them Velocity, chon mot topology va test; khong cai khap noi theo cam tinh.
- Record native client protocol trong session/audit.

### 3.13 Mo client range vi "vao duoc"

Trieu chung:

- Login pass nhung inventory, sound, model hoac hitbox sai.

Phong tranh:

- `block-versions` chan `<1.20.3` va `>1.20.4` luc launch.
- Chi mo them range sau full compatibility gate, khong chi login smoke.
- Website/MOTD ghi ro exact supported versions.

### 3.14 Packet limiter tao false positive

Trieu chung:

- Player lag hoac spam GUI bi kick "too many packets".

Phong tranh:

- Khong tat limiter mac dinh.
- Load test inventory/click va do PPS hop le truoc khi tune.
- Neu doi threshold phai co evidence va security review.

### 3.15 Resource pack fail nhung player van vao gameplay

Trieu chung:

- Control/model vo hinh, player click nham hoac khong the choi.

Phong tranh:

- Freeze movement/damage trong luc load.
- Dung admission state server-owned:
  `NEW -> PACK_SENT -> PACK_LOADING -> PACK_READY` hoac
  `PACK_DECLINED/PACK_FAILED/PACK_TIMEOUT -> REJECTED`.
- Moi gameplay entry point check `PACK_READY`: command, GUI, inventory, entity/block
  interaction, teleport vao plot, reward scheduler, join progression va plugin API.
- Decline/fail co retry, help code va fallback/return lobby ro rang.
- Retry co gioi han, timeout [DIEN sau spike], reconnect khong bo qua state.
- Khong cap dish/purchase control truoc pack-ready state.

### 3.16 Update furniture config lam furniture cu hong

Su that:

- Oraxen canh bao furniture da dat khong tu migrate tu ItemFrame sang Display Entity.

Phong tranh:

- Version furniture definition.
- World projection tao lai furniture tu canonical stage khi upgrade.
- Khong sua config deployed tai cho; migration/rebuild co fence token.

### 3.17 NoteBlock custom block xung dot gameplay vanilla

Trieu chung:

- NoteBlock state bi chiem, block update doi model hoac cong trinh player hong.

Phong tranh:

- MVP khong free-build va khong dung NoteBlock gameplay vanilla.
- Custom block chi nam trong plot authored.
- Validator dam bao custom variation unique.
- Reset/reconcile dung canonical stage, khong tin world block hien tai.

### 3.18 Gan version rong hoac `LATEST`

Trieu chung:

- Build/restart tai hai thoi diem cho artifact khac nhau.

Phong tranh:

- Gradle va deployment manifest dung exact version/checksum.
- Khong dung `+`, `SNAPSHOT`, `LATEST` trong release build neu co the pin artifact.

### 3.19 Crash giua inventory mutation va database settlement

Trieu chung:

- Ledger da payout nhung dish van con.
- Dish da bi xoa nhung transaction chua commit.

Phong tranh:

- Dinh nghia settlement state machine durable va mot ordering duy nhat.
- Inventory la projection/entitlement presentation, khong phai source of truth.
- Consume entitlement bang CAS va commit order/ledger trong SQL transaction; sau
  commit moi reconcile/remove physical item idempotently.
- Neu crash sau commit truoc remove, reconnect scan xoa token da consumed.
- Neu crash truoc commit, entitlement van claimable va khong payout.
- Fault injection ngay truoc/sau CAS, SQL commit, inventory mutation, response,
  disconnect va plugin disable.

### 3.20 Baseline Paper cu bi dong bang ban va

Rui ro:

- Pin 1.20.4 tang tinh lap lai nhung khong nhan moi security/network/crash fix.
- Via khong backport Paper fix.

Phong tranh:

- Lap risk exception cho Paper 1.20.4: Java duoc support, advisory, online-mode,
  proxy/firewall, dependency con phat hanh va owner review hang thang.
- Exit gate nang backend neu co lo hong nghiem trong khong the mitigate/backport.
- Security/dependency review bat dau o Milestone 0, khong doi Phase 4.

### 3.21 Dependency tu tai ve ngoai manifest

Rui ro:

- Exact Oraxen co the tu tai CommandAPI/PacketEvents khac voi staging hoac can
  dependency ma policy cam.

Phong tranh:

- Day 1 xuat hard/soft/transitive dependency graph cua exact Oraxen candidate.
- Bootstrap chi trong moi truong cach ly; capture URL/version/checksum/license.
- Production khong duoc tu tai artifact chua nam trong manifest.
- Candidate fail neu bat buoc ProtocolLib/dependency bi cam ma chua co phe duyet.

## 4. Core foundation spike truoc khi code gameplay

Timebox: 3 ngay. Khong cai Oraxen, ViaVersion hoac ViaBackwards trong spike nay.
Neu spike khong pass, khong bat dau progression/economy content.

### Ngay 1: Pin stack va boot

1. Chon exact Paper 1.20.4 build va Java runtime duoc build do ho tro.
2. Khoi tao plugin Paper thuan va boot server sach.
3. Pin Gradle/toolchain/Paper API va ghi checksum artifact server.
4. Them config validation, structured log va health command.
5. Paper native chi allow protocol backend 765; khong gia vo phan biet exact patch.

Gate:

- Startup 0 error/warning conversion chua giai thich.
- Full restart 3 lan cho cung plugin/version/config.
- `/version` va plugin health khop manifest.
- Khong co plugin/dependency runtime ngoai manifest.
- Database unavailable vao degraded mode ma khong block main thread.

### Ngay 2: Persistence va transaction slice

- PostgreSQL schema migration dau tien.
- BIGINT balance, ledger va idempotency contract toi thieu.
- Repository async; synchronous SQL tren main thread = 0.
- DB unavailable health/degraded behavior.

Gate:

- Fresh migration va rerun migration pass.
- Duplicate operation key tao toi da mot ledger mutation.
- Backup/restore fixture va ledger reconciliation pass.
- Restart giua request khong double credit/debit.

### Ngay 3: Vanilla gameplay va inventory slice

Test:

- Click, drag, shift-click, number key, double-click.
- Drop, death, hopper/container, disconnect va reconnect.
- Mot vanilla dish token va mot manual service interaction.
- Client vanilla va Fabric + Sodium tren cau hinh may yeu dai dien.

Gate:

- 0 duplicate/lost entitlement trong fixture.
- Inventory harness doi chieu server slot, cursor, container, entitlement va
  post-reconnect sau moi action; mismatch = 0.
- Orphan plugin-owned entity/task = 0.
- FPS/client frame-time dat budget [DIEN sau benchmark].
- MSPT p95 duoi budget va duoi 50 ms.
- Chay latency/jitter va disconnect injection trong click transaction.

## 5. Thu tu trien khai

### Milestone 0: Core foundation, 3 ngay

Hoan thanh core foundation spike va dong bang manifest.

Deliverables:

- `compatibility-manifest.yml`.
- Exact Paper/plugin/checksum inventory.
- PostgreSQL migration, restore va transaction evidence.
- Vanilla client 1.20.3/1.20.4 test evidence.

### Milestone 1: Project foundation, 3-5 ngay

- Gradle Kotlin DSL, Java toolchain va Paper API exact baseline.
- PostgreSQL, HikariCP, Flyway.
- Config validation, structured logging, health command.
- Vanilla material/content definitions; khong them custom-content abstraction som.
- Testcontainers/integration harness neu moi truong cho phep.
- Backup/restore PostgreSQL fixture, Flyway forward migration va failed-migration recovery.

Gate:

- Plugin boot/shutdown/restart sach.
- DB unavailable vao degraded mode, khong block main thread.
- Plugin boot khong can custom-content plugin.
- Restore fixture va schema migration rehearsal pass truoc khi co du lieu economy.

### Milestone 2: Durable economy va purchase, 1-2 tuan

- BIGINT balance, immutable ledger, idempotency key.
- Purchase commit tao unlock va `world_operations` trong mot SQL transaction.
- Canonical world projection va repair worker.
- Stable entitlement token; presentation dung vanilla item.
- Settlement state machine va inventory reconciliation sau durable consume.

Gate:

- 100,000 duplicate/retry request cho toi da mot mutation.
- Crash sau charge/truc apply duoc repair, khong auto-refund race.
- Visitor/forged/renamed vanilla item khong the purchase/settle.
- Fault injection moi boundary CAS/commit/inventory/response cho ledger va inventory
  sau reconnect deu dung.
- Backup/restore economy fixture va DB/world reconciliation pass.

### Milestone 3: Order va customer core loop, 1-2 tuan

- Restaurant run/epoch, party, reservation, order, station job, dish entitlement.
- Customer logical state tach physical entity.
- Vanilla dish token; durable entitlement moi la source of truth.
- Inventory invariant va consume CAS.

Gate:

- Moi order toi da mot dish entitlement va mot payout.
- Disconnect/restart tai moi state khong clone dish.
- Inventory full khong drop item; entitlement van claimable.

### Milestone 4: Plot projection vanilla, 1 tuan

- Authored build stages.
- Vanilla block/stage manifest co version.
- Fence token cho reset/reassign/rebuild.
- Entity cleanup va reconciliation.

Gate:

- Reapply stage 3 lan cho cung canonical projection result.
- Canonical projection manifest doi chieu stable plot/content/entity IDs; khong
  hash raw chunk, runtime entity ID hoac transient metadata.
- Update content version co migration/rebuild ro rang.
- Reset/reassign de lai 0 orphan entity/task/chunk lease.

### Milestone 5: Gameplay vertical slice, 2-3 tuan

- 5 purchase pad, 3 stage, mot customer route.
- Mot manual service action va cashier automation.
- Tutorial persistent va admin reset.
- Low-end visual budget enforcement.

Gate:

- Upgrade dau <3 phut trong playtest.
- Manual service van hieu sau 10 phut.
- 100-150 representative customer entities dat tick budget.
- Client may yeu dat frame-time budget [DIEN].
- Sustained 20-player/20-active-plot topology voi max authored furniture, 4 customer
  moi plot, chunk leases, inventory traffic va reconnect pass MSPT/GC/network/FPS gate.
- 50-player stretch chay rieng; khong noi suy tu 20-player test.

### Milestone 5.5: Custom content compatibility, tuy chon

Chi bat dau sau khi vertical slice vanilla pass gameplay, restart va performance gate.

- Danh gia/pin exact Oraxen va dependency graph.
- Them custom-content adapter khi da co nhu cau that.
- Resource-pack admission state machine va immutable pack delivery.
- Compatibility matrix, cache/rollback, item/furniture va low-end FPS test.
- ViaVersion/ViaBackwards chi them neu mo client khac protocol 765.

### Milestone 6: MVP va beta features

Tiep tuc Phase 1-3 trong plan tong, nhung moi release candidate phai chay lai:

- Compatibility matrix.
- Pack validation.
- Inventory exploit suite.
- Restart/crash/reconciliation suite.
- Low-end client benchmark.

### Milestone 7: Production hardening

Thuc thi workbook Phase 4: immutable artifact, canary, observability,
backup/restore, RPO/RTO, soak, reconnect storm va failure injection.

## 6. Test matrix bat buoc

| Test | 1.20.3 vanilla | 1.20.4 vanilla | 1.20.4 Sodium |
|---|---:|---:|---:|
| Join/reconnect | Pass | Pass | Pass |
| Pack accept/load/cache refresh | Pass | Pass | Pass |
| Pack decline/download fail | Pass | Pass | Pass |
| 2D/3D item rendering | Pass | Pass | Pass |
| Furniture render/hitbox | Pass | Pass | Pass |
| Inventory click matrix | Pass | Pass | Pass |
| Drop/death/container | Pass | Pass | Pass |
| Purchase/order/payout | Pass | Pass | Pass |
| Restart recovery | Pass | Pass | Pass |
| Client FPS/frame-time | Observe | Observe | Gate |

Neu mo them mot version client, them mot cot va chay toan bo matrix. Khong co
ngoai le "chi test login".

## 7. Cau hinh khoi dau

ViaVersion khi milestone mo rong protocol duoc phe duyet:

```yaml
block-versions:
  - "<1.20.3"
  - ">1.20.4"
send-supported-versions: true
logging:
  log-entity-data-errors: true
  log-text-component-conversion-errors: true
  log-other-conversion-warnings: true
```

Hai rule tren thuc te allow protocol 765 dung chung cho 1.20.3/1.20.4; behavioral
test phai xac nhan config parser cua exact Via release. Neu Via bi loai sau spike,
Paper native da chi chap nhan protocol cua backend va khong dung block config nay.

Oraxen khi Milestone 5.5 duoc phe duyet:

```yaml
Plugin:
  auto_update_paper_config: false
  generation:
    default_assets: false
    default_configs: false

Pack:
  generation:
    generate: true
    compression: BEST_COMPRESSION
    protection: false
    multi_version_packs: false
  dispatch:
    send_on_join: true
    send_pre_join: false
    layer: ""
    disable_movement_on_load: true
    disable_damage_on_load: true
```

Gia tri config cuoi cung phai lay tu exact Oraxen version da pin; khong copy may moc
neu schema version khac.

## 8. Quy tac mo rong client sau launch

Chi mo them version khi:

1. Oraxen tao/dispatch dung pack cho version do.
2. Full test matrix pass.
3. Khong co inventory desync hoac conversion warning chua giai thich.
4. Custom item/furniture/sound/text co fallback hop le.
5. Capacity va client FPS khong regression.
6. Version duoc them vao support matrix, runbook va monitoring.

Thu tu mo rong de xuat:

1. 1.20.2 neu can.
2. 1.20.1 neu sample nguoi choi may yeu cho thay loi ich that.
3. 1.20.5-1.20.6 chi sau Data Components test suite.
4. 1.21-1.21.1 sau do.
5. 1.21.4+ la milestone rieng vi item model system moi.

## 9. Nguon nghien cuu

- Paper documentation: https://docs.papermc.io/paper/
- ViaVersion README/config: https://github.com/ViaVersion/ViaVersion
- ViaBackwards README/config: https://github.com/ViaVersion/ViaBackwards
- Oraxen documentation: https://docs.oraxen.com/
- Oraxen multi-version pack settings:
  https://docs.oraxen.com/plugin-setup/plugin-settings
- Oraxen Display Entity furniture:
  https://docs.oraxen.com/creating-content/furniture/display-entities
- Minecraft pack format history: https://minecraft.wiki/w/Pack_format

## 10. Dieu kien bat dau code

Bat dau gameplay production chi khi Milestone 0 pass. Oraxen/Via khong duoc chen
vao core milestones; chi them sau vertical slice vanilla va phai co compatibility
gate rieng.
