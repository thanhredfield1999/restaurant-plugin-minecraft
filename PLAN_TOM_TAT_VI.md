# Restaurant Tycoon Minecraft - Tom tat ke hoach

## Ket luan

Du an kha thi co dieu kien neu lam theo huong tycoon cua Roblox, khong lam restaurant
simulator qua sau ngay tu dau. Sau lan audit thu hai, co mot so release blocker phai
giai quyet trong kien truc chu khong the de den beta moi xu ly.

Cong thuc phu hop nhat:

- 70% tycoon vat ly: nhan plot, mua nut/pad, cong trinh moc len, tang thu nhap,
  thue nhan vien, mo tang moi va prestige.
- 30% phuc vu chu dong: nguoi choi co the nhan order, nau va giao mon de kiem
  nhanh hon, nhung ve sau co the tu dong hoa.

Cam giac cot loi can dat duoc:

> Bam mua mot nang cap, thay nha hang thay doi ro rang, thay khach su dung nang cap
> do, tien tang nhanh hon va thay ngay muc tieu tiep theo.

## MVP nen co

1. Plot rieng co the claim.
2. Purchase pad va button nam truc tiep trong world.
3. Cong trinh dung san theo tung stage, co animation ngan khi mua.
4. Khach di theo pipeline don gian: vao, order, doi, an, tra tien, roi di.
5. Mot flow nau va giao mon thu cong.
6. Hai loai automation: cashier va cook.
7. Cash, unlock va transaction duoc luu ben vung.
8. Mot restaurant theme hoan chinh.
9. Prestige dau tien sau khi hoan thanh nha hang.
10. Admin tool de inspect, reset va phuc hoi plot.

## Cach nha hang van hanh

Mon an khong duoc tao truc tiep khi click khach. No phai di qua flow bep:

```text
Khach vao
-> xep hang
-> duoc gan ban
-> di toi ban va goi mon
-> order vao kitchen board
-> player nhan order
-> nau tai dung station
-> mon ra serving counter
-> player cam mon toi dung ban
-> khach an va thanh toan
-> khach roi di
-> ban dirty
-> don ban
-> ban available lai
```

Vi du burger day du:

```text
PREP_TABLE 2 giay
-> GRILL 8 giay
-> ASSEMBLY_COUNTER 2 giay
-> SERVING_COUNTER
-> giao dung table
```

Prototype chi can:

```text
Nhan order
-> GRILL 8 giay
-> lay mon tai SERVING_COUNTER
-> giao dung ban
-> thu tien
-> don ban
```

Quy tac:

- Moi order co `order_id`, table, recipe va restaurant ro rang.
- Trong prototype stock la vo han logical; click lo chi check order dung va station
  con cho. Chi check stock sau khi stock transaction model duoc implement.
- Job nau luu `finishes_at` timestamp de lag/restart khong lam sai thoi gian.
- MVP khong co chay mon; mon ready cho an toan tai serving counter.
- Dish do server cap va link voi order; item chi la view, khong phai source of truth.
- Giao sai ban thi bao loi va khong mat mon.
- Giao dung thi dish `CONSUMED`, order `SERVED`.
- Chi `SETTLED` moi cong tien exactly once.
- Khach di ra quay/exit chi la animation, khong quyet dinh payout.

Table lifecycle:

```text
AVAILABLE -> RESERVED -> OCCUPIED -> DIRTY -> CLEANING -> AVAILABLE
```

Automation mo tung cong viec:

| Staff | Tu dong hoa |
|---|---|
| Host | Gan khach vao ban trong |
| Waiter | Nhan order, sau nay co the giao mon |
| Prep cook | So che/lap rap |
| Grill cook | Nau tai grill/oven |
| Cashier | Thu tien/auto-deposit |
| Cleaner | Don ban dirty |

Staff la logic truoc, villager hien thi sau. Khong spawn mot villager pathfinding cho
moi staff neu khong can. Plot khong duoc quan sat thi customer/staff pipeline chay
batch va khong co entity.

## Chua nen lam trong MVP

- Xay nha hang tu do bang moi block.
- Hang tram cong thuc nau an.
- Citizens/player NPC co skin rieng.
- Cho nguoi choi giao dich tien voi nhau.
- Mo phong nguyen lieu va nhan vien qua chi tiet.
- Offline NPC van tiep tuc hoat dong.
- Ho tro Folia hoac Bedrock day du ngay tu launch.
- Cam ket 100 nguoi online cung luc khi chua load test.

## Tien trinh 60 phut dau

| Thoi gian | Moc mong doi |
|---:|---|
| 0-2 phut | Claim plot, phuc vu khach tutorial, nhan tien dau tien |
| 2-5 phut | Mua ban va bien hieu dau tien |
| 5-8 phut | Mua bep va mo them slot khach |
| 8-12 phut | Thue cashier hoac cook dau tien |
| 12-15 phut | Mo auto-collect mot phan va dung vo nha hang |
| 15-30 phut | Tang so ban, nang bep, tu dong hoa cong viec lap lai |
| 30-45 phut | Auto-deposit day du, chon nhanh drive-through/dessert/patio |
| 45-60 phut | Mua exterior upgrade lon, nhin thay ro tu plot ben canh |

Trong tutorial, khong nen de nguoi choi cho qua 60-90 giay ma khong co mot muc tieu
mua moi co y nghia.

## Kien truc de xuat

- Minecraft Java Edition, Paper, Java dung theo version Paper duoc pin.
- Gradle Kotlin DSL va Adventure Components.
- SQLite chi cho local Phase 0; PostgreSQL bat buoc tu production validation tro di,
  ke ca chi co mot Paper process.
- Plugin modular monolith, chua can microservice.
- Custom plot protection trong mot world rieng.
- PDC de danh dau item/entity va station dua tren TileState; purchase pad/button la
  block thuong phai lookup qua world UUID va toa do.
- Database la source of truth cho cash, unlock va prestige.
- SQL/file/HTTP chay async; thao tac world/entity quay ve server scheduler.
- Paper truoc; chi danh gia Folia sau MVP.

Khong them Citizens, Vault, WorldGuard, ItemsAdder hay Oraxen vao prototype neu chua
co nhu cau cu the. Moi dependency lam tang rui ro khac version va van hanh.

## Gioi han NPC can thu nghiem

- Khach vat ly chinh thuc dung vanilla `Villager`, khong dung Citizens.
- 4-6 villager da bo AI thua cho moi plot dang duoc quan sat.
- 12 khach vat ly chi la overload-test ceiling, khong phai quota duoc cap cho moi
  plot trong production.
- Plot online nhung khong co ai quan sat dung 0 villager va tinh income theo batch.
- Plot offline dung 0 villager va 0 income trong MVP.
- Customer logic cap nhat moi 10-20 tick va duoc chia le.
- Chi tim duong khi doi state hoac watchdog phat hien bi ket.
- Plot khong active thi despawn khach, khong force-load chunk.
- Moi state cua khach phai co timeout va co che recovery.
- Loi pathfinding khong duoc tru diem hay khoa ban cua nguoi choi.

Villager MVP dung cung mot biome/type va adult size. Plugin bo profession/workstation,
trading, gossip, breeding, sleeping, panic, wander, pickup, restock, village/POI
search, damage va drop. Khong dung `setAware(false)` neu no lam hong navigation; quan
ly goal bang Paper API. Uu tien cua mo hoac cua do plugin dieu khien, va cho khach
dung tai seat marker trong MVP thay vi tao them helper entity de ngoi.

Danh gia quy mo:

- Khong danh gia capacity chi bang so player online.
- Can do active plots, tong mob AI, display/Interaction entities, path requests,
  tracked entities/client va entity-ticking chunks.
- 20/50/100 players deu la claim co dieu kien, chi duoc cong bo sau benchmark dung
  workload va hardware production.

Con so tren la ngan sach de benchmark, khong phai bao dam. Can dung spark tren dung
loai hardware se deploy.

## Chong exploit bat buoc

- Moi payout va purchase co idempotency key.
- Tru tien va them unlock trong cung transaction co dieu kien.
- Mot order chi co toi da mot payout transaction.
- Khong tin item name, lore, material hay custom model de xac dinh item kinh te.
- Dish phai mang order ID va restaurant ID do server cap.
- Xu ly click, drag, shift-click, number key, double-click, disconnect va restart.
- Visitor khong duoc su dung purchase control.
- Plot reset khong duoc chay dong thoi voi purchase/build.
- Co audit log cho grant, refund, prestige va thao tac admin.

## Roadmap

### Phase 0 - Prototype, 2-3 tuan

- Mot plot, nam purchase pad, ba build stage.
- Mot route khach, mot thao tac phuc vu, mot cashier automation.
- Luu cash/unlock va reset admin.
- Test 100-150 customer entities dai dien.

Chi di tiep neu gameplay loop vui va benchmark NPC dat yeu cau.

### Phase 1 - Vertical slice, 3-5 tuan

- 30-45 phut progression da tune.
- 15-25 purchase node.
- Mot theme hoan chinh, cook/cashier, 2-3 active job.
- Resource-pack visual slice, ledger, migration va restart recovery.

### Phase 2 - MVP alpha, 4-6 tuan

- P50 150-210 phut active play de hoan thanh va prestige lan dau.
- 40-60 purchase node va hai nhanh progression.
- Co-op 2-4 nguoi, visit, prestige, analytics va admin tools.
- Load test kich ban 20 va 50 players.

### Phase 3 - Closed beta, 3-5 tuan

- Tune economy/pacing, theme thu hai hoac prestige route.
- Exploit, restart, rollback va resource-pack failure test.
- Runbook van hanh va moderation.

### Phase 4 - Production hardening, 4-7 tuan

- Privacy/moderation/branding/license review.
- Deployment, canary, rollback va observability.
- Backup/restore/reconciliation drill voi RPO/RTO.
- Long soak, reconnect storm, database degraded va crash-injection test.

Tong uoc tinh sau audit:

- 12-19 tuan chi hop ly cho mechanics-complete internal alpha.
- Java-only production beta: 16-26 tuan voi team nho co kinh nghiem.
- Solo developer chua co asset: 6-10 thang.
- Geyser/Floodgate va Bedrock pack: cong them khoang 4-8 tuan va QA lien tuc.

## Cac van de nghiem trong phat hien khi audit

1. **Database va world khong the commit atomically.** Can commit purchase/charge vao
   database truoc, sau do coi block/entity trong world la projection co the replay.
   Neu crash sau khi tru tien, world operation phai duoc repair idempotently.
2. **Plot reset chua du an toan.** Can state machine co fence token:
   `REQUESTED -> QUIESCING -> CLEARING -> RESTORING -> RECONCILING -> COMPLETE`.
3. **NPC unload khong phai despawn.** Khi plot inactive phai remove entity ro rang,
   cancel task va reconcile entity con sot luc chunk load lai.
4. **PDC khong gan duoc len moi block.** Block thuong phai lookup bang world UUID va
   toa do; chi TileState moi co block-local PDC.
5. **Folia scheduler abstraction chua du.** Moi mutable object phai co owner va khong
   duoc khai `folia-supported` truoc khi race/integration suite pass.
6. **Pacing bi mau thuan.** Plan cu co ca moc 2-3 gio, 2-5 gio va 10 gio cho prestige.
   Moc moi la P50 150-210 phut, thuong trong 2-4 session.
7. **Automation/AFK chua co contract.** Can tach `observed`, `online-unobserved` va
   `offline`; chi observed moi co active/VIP/leaderboard reward.
8. **Co-op co the nhan ban reward.** Moi order chi co mot reward budget; chia viec
   chi chia budget, khong tao them currency.
9. **Resource pack can lifecycle day du.** Phai theo doi accepted/downloaded/loaded,
   declined, failed download/reload, invalid URL va discarded; invisible control la
   release blocker neu pack bat buoc.
10. **Backup world va DB khac thoi diem co the duplicate.** Can RPO/RTO, restore drill,
    content version va reconciliation; PostgreSQL production nen danh gia WAL/PITR.
11. **Monetization plan cu thieu policy.** Can branding/disclaimer, operator contact,
    price disclosure, purchase history, privacy, all-ages, no paid random/no
    competitive advantage va review lai guideline luc launch.
12. **Social/UGC can moderation som.** Restaurant name, signs, chat, visit va co-op
    can report, mute/block, audit, appeal, child-safety escalation va IP takedown.

## Contract kinh te moi

- Dung `BIGINT` currency units, khong dung floating point.
- Balance, ledger row, revision va purchase/order state commit trong cung mot SQL
  transaction.
- Idempotency key la cua logical operation, khong tao key moi khi retry.
- Durable command chi bao thanh cong sau SQL commit.
- Write-behind chi danh cho analytics/telemetry co the mat.
- Order `SETTLED` moi co nghia la da tra tien; NPC `PAYING/LEAVING` chi la animation.
- Khi database down, server vao read-only/degraded mode, khong mutate economy trong
  memory.

## Contract world operation moi

```text
REQUESTED -> COMMITTED -> APPLYING -> APPLIED
                    -> REPAIR_REQUIRED -> APPLYING
REQUESTED -> REJECTED
```

- Purchase transaction tao `world_operations` record.
- Moi batch paste/reset check plot assignment va fence token.
- Retry phai hoi tu ve canonical target stage, khong paste tuong doi vo dieu kien.
- Entity tao ra mang operation ID, plot ID va stage revision.
- Khong dung WorldEdit undo history lam durable reset state.

## Gate production bo sung

1. Crash injection o moi phase purchase, payout, reset va inventory entitlement.
2. MSPT median/p95/p99/max, GC, entity/chunk/task growth va soak test dai.
3. Resource-pack retry/failure test tren moi client duoc support.
4. Restore drill dat RPO/RTO va khong duplicate cash/unlock/world stage.
5. Dependency va asset provenance/license manifest hoan tat.
6. Privacy, moderation, branding va monetization review hoan tat cho thi truong launch.

## Tieu chi go/no-go prototype

1. Nguoi moi mua duoc upgrade dau tien trong duoi 3 phut.
2. Nguoi choi hieu quan he giua khach, service va cash ma khong can giai thich dai.
3. Moi purchase tao thay doi hinh anh de nhan ra.
4. Manual service van vui sau 10 phut lap lai.
5. Automation tao cam giac tien bo nhung khong xoa het gameplay.
6. Representative NPC load van gan 20 TPS, khong co tang task/entity/chunk vo han.
7. Restart va spam click khong tao duplicate cash/unlock.

## Nam quyet dinh can chot truoc khi code production

1. Minecraft version muc tieu.
2. Muc tieu concurrent players: 20, 50 hay cao hon.
3. Resource pack bat buoc hay tuy chon.
4. Co can Geyser/Bedrock ngay luc launch hay khong.
5. Server chi tieng Viet hay Viet-Anh.

Ban nghien cuu, kien truc, data model, risk matrix, test plan va nguon tham khao day
du nam trong `RESTAURANT_TYCOON_PLAN.md`.

## Ket qua pre-mortem lan 3

Flow choi da ro, nhung schema cu chua du de recover khi crash giua luc nau. Ban full
plan da bo sung Section 24 voi cac sua doi bat buoc:

- Moi lan mo nha hang co `run_id` va `operation_epoch`.
- Luu rieng party, table reservation, station job, dish entitlement va cleaning job.
- Stock prototype la vo han logical; chua hien stock UI den khi co transaction model.
- Table khong co state EATING; table giu OCCUPIED, con party/order giu EATING.
- Huy order phai qua `CANCELLING`, khong nhay thang CANCELLED.
- Gan ban, nhan order, claim station va giao mon deu la compare-and-set single winner.
- Them state nha hang `CLOSED -> OPEN -> CLOSING -> CLOSED`.
- Owner logout: dung nhan khach moi, drain/settle cong viec da commit, roi quiesce.
- Mot player prototype chi cam toi da mot dish entitlement.
- Inventory full thi claim that bai va mon van o counter, khong drop xuong dat.
- Event drop/death/teleport bi goi chua co nghia action da xay ra; phai kiem tra final
  cancellation va reconcile state vat ly o tick sau.
- Villager unload khong phai chet; entity remove event chi monitor, khong mutate.
- Reapply villager goal/config khi entity load/recreate.
- Queue full, all tables dirty hoac het entity budget thi khong spawn villager moi.
- Tutorial co state machine persistent va retry/repair, khong chi la chat huong dan.
- Multi-instance/Velocity sharding bi defer den khi co execution epoch, player session,
  transfer protocol va exclusive world ownership.

Rui ro lon nhat con lai truoc prototype:

1. Hai player nhan cung mot order/ban/station trong cung luc.
2. Crash sau khi tru stock nhung truoc khi tao job/mon.
3. Dish bi nhan doi qua inventory, death, drop, hopper hoac plugin khac.
4. Table bi ket RESERVED/OCCUPIED/DIRTY sau path failure/logout/reset.
5. Physical villager va batch simulation cung advance mot order.
6. Callback cu cua run/plot truoc sua state moi sau reset/reassign.

Tat ca phai co crash/race test truoc khi tang tu mot customer len 4-6 customer/plot.
