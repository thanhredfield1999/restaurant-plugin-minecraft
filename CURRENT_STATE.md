# RestaurantTycoon Current State

Last reviewed: 2026-08-15

## Baseline

- Source version: `0.1.0-SNAPSHOT`.
- Target: Paper `1.21.11`; isolated smoke harness pins build `132`.
- Java source and bytecode target: `21`.
- PostgreSQL is required for integration and production behavior; H2 is test-only.
- Flyway migrations currently cover schema versions V1 through V17.
- Database startup now requires schema V17, matching the latest bundled migration. V14 adds durable market cycles, per-SKU prices, idempotent demand events, and order-line price snapshots; V15 adds market invariants and composite price references; V16 adds runtime checkpoint indexes; V17 adds entity cleanup state.
- `MarketRepository` now provides local PostgreSQL market foundation: open-cycle creation/lookup, SKU price lookup, row-locked purchase demand event recording, atomic aggregate update, and operation-payload conflict detection. `SupplyOrderRepository.captureMarket` now snapshots DB market prices with demand, payment, order lines, and shipment in one transaction. Supplier Villager runtime listener đã có: chỉ Villager có PDC byte `supplier_villager=1` mới mở market; entity interaction bị cancel. Admin dev command `/restaurant dev mark-supplier <entityUuid>` đánh dấu loaded Villager. Listener async resolve plot duy nhất đang sở hữu từ `plot_assignments`; không có plot thì fail closed, rồi mở market với plot scope. Player vẫn có thể dùng command chọn plot rõ ràng. `/restaurant market` mở inventory market; `/restaurant market <plotId>` thêm confirmation flow. Player chọn quantity bằng click phải/trái hoặc Shift ±10, xem subtotal/total, quay lại hoặc xác nhận. Xác nhận gọi `captureMarketAuthorized`, kiểm tra plot ownership/setup rồi atomic snapshot giá + demand + payment + order + shipment; chưa có GUI runtime proof. Cycle rollover now closes expired cycles; H2 concurrency regression and PostgreSQL integration tests cover multi-worker convergence, claim recovery/fencing, Flyway rerun, and idempotent purchase retry. PostgreSQL integration passed using local test database credentials from the attached positional file; secret values are not stored here.
- V11 adds free onboarding world operations; V12 adds supply shipment claim fields and handoff recipient audit fields.
- Onboarding listener now allocates a configured plot, guides player by compass, and requests initial stage when player enters configured trigger. Paper runtime remains unverified.
- Supply runtime claim/renew/handoff/stock persistence exists. Added `handoffAuthorized` and `handoffPackageAuthorized` ownership gates. `PackageInteractionListener` handles only entities with PDC `supply_package_id`; it cancels interaction, performs async DB handoff, then gives player a CHEST package token with item PDC `supply_package_id` and removes entity on main thread. Admin dev command `/restaurant dev mark-package <entityUuid> <packageUuid>` marks loaded package entity. `WarehouseInteractionListener` handles only tile blocks with PDC `supply_warehouse=1` and item package PDC, then calls idempotent `stock`. Admin dev command `/restaurant dev mark-warehouse <world> <x> <y> <z>` marks a loaded tile block. Citizens delivery handler remains disabled; Paper runtime proof pending.
- V7/V8 own durable supply setup points and restaurant-scoped route waypoints.
- V9 owns durable supply orders, order lines, payments, shipments, packages, and
  warehouse stock tables; V10 owns package line snapshots.
- V8 owns restaurant-scoped delivery-route waypoints with a composite
  `(setup_scope, owner_id, sequence_no)` primary key. Replacing a route deletes
  and reinserts one restaurant's ordered waypoint set in a single transaction.

## Current runtime roadmap status

- R0/R1/R2 projection gates: verified.
- R3 checkpoint CAS vertical slice: verified; Paper end-to-end unknown.
- R4 config foundation: verified; `supply-runtime.enabled=false` by default with bounded session/radius/speed/lease settings. Runtime tick/movement remains unverified.
- R5 receiving persistence: verified; Paper interaction unknown.
- R6 full journey and R7 release: not ready.
- Production untouched; Citizens/movement disabled.

## Verification

Latest controlled Paper smoke on 2026-08-20:

- `scripts/paper-smoke.ps1 -RuntimeFixture -UseExistingConfig -Port 25569 -TimeoutSeconds 180` passed on Paper `1.21.11-132`, Java 21, PostgreSQL 18.6, schema V17. Evidence reached fixture seed, durable claim/dispatch, exact-shipment projection callback, fixture cleanup, and clean shutdown. Movement/Citizens remained disabled. Smoke port `25569` was released after shutdown.
- GitHub Actions CI run `32285177007` on default branch `main` passed Java build/tests, PostgreSQL 17 integration tests, Windows smoke-guard regression, and plugin artifact upload.
- DecentHolograms was absent; plugin logged `DecentHolograms unavailable; station holograms disabled` and gameplay/plugin startup continued. This verifies fail-soft behavior only, not hologram rendering.
- `paperSmokeGuardTest` passed all 11 assertions. Local Paper instance on port `23556` was not mistaken for smoke process on port `25569`.
- Supply setup GUI now has scope dashboard: configured/required progress, ready/incomplete status, validate button, close button, and explicit point action submenu (set/reset, teleport, remove, cancel/back). Remove requires confirmation; drag and ambiguous Shift+right-click flow are blocked. New shaded JAR and messages copied to local Paper; controlled restart completed; local Paper port `23556` is listening. GUI click journey still needs manual player interaction verification.
- Menu layout central points use slots `10` and `16`; restaurant point slots remain separate by scope. Point presenter now shows status, location, and action-menu hint.

Latest local verification on 2026-08-15:

- `scripts/paper-smoke.ps1` no longer hangs: the running-Paper check previously used an
  unbounded full-table `Get-CimInstance Win32_Process` enumeration that could block for
  minutes before the server started (no Java process, no port, no logs). The check now
  lives in `scripts/paper-smoke-guard.ps1` as `Assert-NoRunningPaperSmoke`: it first
  probes the smoke TCP port with a bounded 2-second loopback connect, then queries only
  `java.exe`/`javaw.exe` rows through a server-side WQL filter inside a
  `Start-Job`/`Wait-Job -Timeout` job (default 20 seconds, fail-closed). The two-Paper
  safeguard is preserved, the match still requires the exact pinned
  `paper-1.20.4-499.jar` in the command line so a Java Minecraft client or production
  Paper jar is never matched, and the JAR download also gained `-TimeoutSec 120`.
  Regression coverage: `scripts/test-paper-smoke-guard.ps1` (11 assertions, run via
  `.\gradlew.bat paperSmokeGuardTest` on Windows) exercises the command-line predicate,
  the port probe, the bounded process query, and the combined guard. The full local
  build passed.

- PostgreSQL defect fixed in `WorldOperationRepository.lockCurrentFence`: the
  `markApplied` fence-lock query combined `LEFT JOIN purchases` with `FOR UPDATE`,
  which PostgreSQL rejects (`FOR UPDATE cannot be applied to the nullable side of
  an outer join`). After V11 made `purchase_id` nullable for ONBOARDING operations,
  the durable world-operation completion path failed on PostgreSQL even for PURCHASE
  operations, so the projection pipeline could never commit. The lock query now joins
  only `world_operations` and `plot_assignments` and resolves the expected owner via a
  correlated `purchases` subquery inside `COALESCE`, keeping the same fence/ownership
  predicate for both PURCHASE and ONBOARDING operations while locking only non-nullable
  rows. The fence row lock and claim-revalidation in the final `markApplied` UPDATE are
  unchanged, so fencing semantics are preserved. H2 unit coverage already exercised both
  operation sources; the PostgreSQL integration test
  `expiredClaimIsReclaimedAfterWorkerRestartAndOldCallbackIsFenced` remains the
  PostgreSQL gate and now passes against real PostgreSQL.
- `.\gradlew.bat build --no-daemon --console=plain`: passed.
- 179 tests across 44 suites passed with no failures, errors, or skips, counted
  directly from `build/test-results/test/TEST-*.xml` after the full build.
- `PostgresDurabilityIntegrationTest` now pins the migrated schema to V12 to match
  `DatabaseManager.REQUIRED_SCHEMA_VERSION`, and the bundled `config.yml` regression
  test verifies both `PluginSettings` and the ingredient-catalog loader parse it.
- `scripts/paper-smoke.ps1` now writes a minimal valid smoke config that also declares
  the `supply-catalog` and `plots.plot_1.trigger` sections required by the current
  enable-time parsers, so the plugin reaches its boot markers instead of disabling.
- Supply runtime coordinator tests verify durable work discovery, single-flight polling,
  rejection after lifecycle close, suppression of queued work closed before execution,
  stopping dispatch before the next item when close occurs during a batch, and recovery of
  the single-flight guard when executor submission is rejected. These tests do not prove
  interruption of a JDBC call or handler that was already running.
- Supply ordering GUI lifecycle now assigns one generation ticket to each player open and
  carries it through preflight, the open order holder, and submit completion. A newer open,
  player logout, or controller/plugin close invalidates stale preflight and submit callbacks.
  Scheduler submission is serialized with lifecycle close and queued callbacks recheck the
  ticket before touching Bukkit state. Five focused lifecycle tests and an independent
  re-review found no remaining blocker in this boundary. This does not verify Paper inventory
  behavior, provide a user-visible retry workflow, or prove setup readiness remains unchanged
  between preflight and the capture transaction.
- `/restaurant` with no arguments now opens the player dashboard GUI. It shows the async-loaded
  restaurant balance, configured restaurant cards, and a guide; clicking a restaurant delegates
  to the existing ownership/setup-gated order GUI. The pure layout test fixes stable plot ordering,
  dashboard capacity, and non-functional white-glass slots. Isolated Paper 1.21.11 startup loaded
  the new JAR successfully; player click-through remains controlled-Paper manual verification.
- Dashboard and onboarding now use the bounded `RestaurantLore` copy: Hiệp hội Phố Bếp, plot,
  stage, Chợ đầu mối, manual receiving, and warehouse are described without claiming that Citizens
  delivery runtime is enabled. `RestaurantLoreTest` covers the required and prohibited claims.
- D22 locks visible operational characters to Villager-only. Steve/player-skin NPCs are not a
  Restaurant Tycoon gameplay standard. This is an art/runtime contract only; it does not authorize
  Villager spawning or enable the blocked Citizens delivery runtime.
- DecentHolograms `2.9.9` is an optional station-progress renderer. Drink stations create
  non-persistent API holograms, refresh at most once per second, and remove only holograms created
  by this plugin on disable. Missing DecentHolograms fails soft; it does not affect dispenser state.
- Vault API is a compile-only, soft dependency. RestaurantTycoon does not debit or credit Vault yet;
  internal PostgreSQL ledger remains authoritative until an audited bridge exists.
- Station hologram projection uses optional DecentHolograms `2.9.9` via `DHAPI`; `plugin.yml`
  soft-depends on it. The first slice projects configured drink stations once per second with
  server-authoritative fill progress bounded to `0..100`, does not persist provider holograms,
  and removes only RestaurantTycoon-owned holograms during plugin disable. Missing provider logs
  and leaves gameplay enabled. Full build passed; no DecentHolograms JAR is installed in the local
  Paper run directory yet, so controlled-Paper provider verification remains pending.
- Supply order repository tests verify that economy debit, submitted order/line snapshots,
  captured payment, shipment, package, and package-line snapshots commit atomically and
  retry idempotently. A forced payment-table failure verifies rollback of both the debit
  ledger entry and order data.
- The first player-ordering UX slice is implemented behind
  `/restaurant order <plotId>`. It loads a versioned ingredient catalog from config,
  presents bounded quantity controls and an explicit payment confirmation, checks
  current plot ownership plus central/restaurant setup readiness asynchronously, and
  submits the existing atomic order/payment/fulfillment transaction. The repository
  rechecks plot ownership under the same database transaction as the debit, so a stale
  GUI cannot charge a player after ownership changes. Focused model, preflight, and
  authorization tests passed. The production capture boundary also revalidates every
  SKU/display/unit/price/quantity against the authoritative catalog and compares the
  complete persisted order/line snapshot on an idempotent retry; a same-total payload
  change now conflicts instead of being accepted as a duplicate. Bukkit inventory
  interaction itself remains Paper-smoke unverified.
- The focused supply-setup and migration tests passed. The full build produced
  `build/libs/restaurant-tycoon-0.1.0-SNAPSHOT.jar`; verify the current file size from the build output before any release claim.
- The setup slice now includes validated point domain records, V7 persistence,
  localized message templates, and an admin inventory GUI opened through
  `/restaurant setup <central|plotId>`. Left click stores the current location,
  right click teleports, and Shift+right click requires delete confirmation.
- Empty/non-functional GUI slots are filled with
  `WHITE_STAINED_GLASS_PANE` in both the setup menu and delete-confirmation menu;
  functional slots remain reserved for point icons and confirmation buttons.
- The restaurant setup GUI reserves slot 22 for the localized delivery-route
  editor. It supports up to seven ordered waypoints: add at the player's current
  position, overwrite by left click, teleport by right click, and delete through
  Shift+right-click confirmation. Deleting a middle waypoint resequences the
  remaining route contiguously; deleting the final waypoint persists an empty
  route for the same restaurant. All non-functional slots in the route and
  route-delete-confirmation menus use `WHITE_STAINED_GLASS_PANE`.
- Route validation rejects central-supplier ownership, mixed restaurant ownership,
  duplicate or non-contiguous sequences, numbering that does not start at 1, and
  routes longer than the seven-waypoint GUI/DB contract. The route GUI now exposes
  waypoint slots as an ordered list rather than relying on Set iteration order,
  preventing sequence-to-slot mismatches.
  The GUI rejects plot IDs absent from loaded configuration. JDBC operations run
  on the database executor; inventory and teleport operations return to the server
  thread.
- `docs/SUPPLY_SETUP_CONTRACT_VI.md` now records the authoritative setup matrix,
  the distinction between `CONFIGURED`, `STRUCTURALLY_COMPLETE`, and
  `RUNTIME_VERIFIED`, and the admin/player journey. A pure
  `SupplySetupReadinessEvaluator` reports stable, actionable missing/duplicate,
  foreign-owner, and multi-world issues. It includes `WAREHOUSE_ENTRY` while
  keeping an empty waypoint route valid. Constructor-level owner/scope validation
  remains the authoritative guard; the evaluator does not duplicate impossible
  cross-scope fixtures. This is domain evidence only, not Paper/Citizens runtime
  verification.
- A pure delivery-journey planner now produces the deterministic one-way sequence
  `DELIVERY_ENTRY`, ordered route waypoints, `DELIVERY_STOP`, `UNLOAD_POINT`,
  `DELIVERY_EXIT`, and `DELIVERY_DESPAWN`. It fails closed for route or setup-point
  ownership mismatches, duplicate setup-point types, missing required points, and
  journeys crossing worlds. `WAREHOUSE_ENTRY` is intentionally excluded because it
  remains the player's manual stock-entry point rather than an NPC navigation stage.
- A pure immutable convoy simulation now advances that verified plan one step at a
  time. It stops at `UNLOAD_POINT` in `WAITING_FOR_HANDOFF`, rejects handoff before
  that state, resumes at `DELIVERY_EXIT` only after confirmation, and reaches the
  terminal `COMPLETED` state after `DELIVERY_DESPAWN`. Every convoy snapshot now
  carries its caller-provided shipment identity through every transition. Empty plans,
  out-of-range progress, completion/progress disagreement, waiting outside the unload
  point, and transitions from invalid states fail closed. This is domain behavior only:
  it does not spawn, navigate, unload, persist, or remove Citizens entities.
- A pure immutable delivery-package handoff slice now models only package identity,
  restaurant ownership, `IN_TRANSIT`/`HANDED_OFF`, operation identity, and revision.
  Matching retries are idempotent; a different operation cannot overwrite a committed
  handoff. A coordinator atomically returns package/convoy snapshots, requires the
  convoy to be waiting at unload, recovers a partial aggregate retry, and rejects
  restaurant, shipment, or pre-unload mismatches. This closes the ambiguity where two
  shipments for the same restaurant could previously be paired. A public package
  handoff result also rejects any snapshot whose package is still `IN_TRANSIT`, so a
  result cannot claim handoff before the package transition commits. It deliberately
  contains no ingredient SKU, quantity, order, payment, warehouse stock, database
  migration, or Bukkit interaction.
- Public delivery journey plans now reject central-supplier ownership, and aggregate
  handoff results reject packages that are still `IN_TRANSIT`. These trust-boundary
  invariants prevent callers from constructing success snapshots that the planner or
  handoff transition could never legitimately produce.
- The verified source includes bounded claimed-entitlement and bulk-ID queries,
  fail-closed Paper 1.20.4 `ItemMeta` PDC token encoding, and the pure inventory
  reconciliation planner. The planner separates the bounded projection page from
  the bulk-ID validation snapshot, so a valid token outside the first projection
  page is not treated as absent from the authoritative database state. Plans also
  retain the complete storage classification snapshot and must not be applied after a
  relevant slot changes classification or token identity; a stacked PDC token is
  classified invalid because one entitlement projects to one physical item. A pure
  lifecycle coordinator now issues plugin-epoch/player-generation tickets and rejects
  stale asynchronous completions after reconnect, logout, or epoch advance. Its
  monotonic generation sequence prevents an old ticket from becoming current again
  after logout/reconnect (the ABA case).

Additional recorded evidence from `OPENCODE_HANDOFF.md`:

- The earlier pre-V6 full build passed with 42 unit tests; the 80-test result above
  supersedes it for the current source.
- `.\gradlew.bat postgresIntegrationTest --no-daemon`: passed against local
  PostgreSQL 18.
- Isolated Paper 1.20.4 build 499 startup and clean-shutdown smoke: passed.

These historical results do not verify later uncommitted changes. Re-run applicable
commands before making a new completion claim.

The wall-lever water-dispenser slice is now implemented in the current source. A
configured lever starts filling when pushed down, remains down
after the three-second default fill duration, and grants one vanilla water potion only
when the same player raises it with an empty main hand. Starting a fill does not require
an empty hand. Early pickup, occupied-hand pickup, other-player, standard Bukkit block
replacement, disable, and restart-without-memory paths fail closed. Physics-triggered
replacement validation is deferred to the next main-thread tick so it observes the
post-physics block state without discarding fills on ordinary physics checks. Because
Paper may report only the physics root, validation checks the root and its six directly
adjacent blocks through bounded configured-station lookups.
The state machine and configuration parser have focused unit coverage. Runtime Paper
interaction is not yet verified, and the bundled configuration deliberately has no
active station coordinates (`stations: {}`) because the authored stages contain no
authoritative drink-lever location. Fill in a real lever world/XYZ before smoke testing;
do not infer or invent one. Drink-dispenser settings are loaded only during plugin
enable, so changes to station coordinates or fill duration require a safe restart.

## Current Gate

The end-to-end supply gap and dependency order are recorded in
`SUPPLY_GAMEPLAY_EXECUTION_PLAN_VI.md`. The MVP domain/persistence contract is now
implemented: ingredient SKU/unit/quantity snapshots, submitted orders, captured
payments, one shipment/package per order, durable handoff, and exactly-once warehouse
stock reconciliation. Migrations V9/V10 and repository tests cover the durable path.
Player order capture now uses one database transaction for the economy ledger debit,
order/payment persistence, and initial shipment/package creation, removing the crash
window where a player could be charged without durable fulfillment work. The Bukkit
ordering GUI and ownership/setup authorization are implemented and unit-verified but
not yet runtime-verified on Paper.
Supply runtime recovery wiring is now connected after `DatabaseState.READY`: a
single-flight claim worker polls durable shipment work on the database executor, claims
with lease/fence, transitions only `CREATED` to `IN_TRANSIT`, releases claims for states
not yet handled by runtime, and closes before database shutdown. Paper/Citizens convoy
execution and smoke testing remain unverified and require explicit approval, a
non-production test server, and real configured plot IDs/worlds. Do not claim runtime
verification from unit tests or the local build alone.
Plugin disable closes the supply runtime claim worker before the database is closed. The
worker rejects later runs; it intentionally does not interrupt a JDBC operation that already
started. Durable claim/lease/fence ownership now exists in `SupplyFulfillmentRepository`
and is unit-verified for takeover, stale-worker rejection, and the claim-worker transition.
No Citizens runtime is enabled. Runtime policy decisions D15, D16,
D17, D21 and V13 are recorded in `docs/SUPPLY_RUNTIME_DECISIONS_VI.md`.

V13 now adds `supply_shipment_runtime`: revision, logical checkpoint, immutable snapshot
payload/version, `UNLOAD_POINT` deadline, recovery outcome, and transition-operation receipt.
Upgrade rows without an old runtime snapshot fail closed as `PENDING_MANUAL`; new journeys must
write a valid pinned snapshot before they become recoverable. New fulfillment rows now create
an explicit `PENDING_MANUAL` runtime row in the same transaction, preventing an ambiguous
missing-row state. Route pinning is intentionally fail-closed until setup-point/route data is
loaded transactionally. A deterministic bounded serializer now produces versioned immutable
journey payloads with escaped world names and six-decimal coordinates. Repository pinning accepts
only matching restaurant + `CREATED` shipment + `PENDING_MANUAL` runtime row; it increments
revision and resets recovery state atomically. The claim worker still refuses unpinned rows.
H2 migration, serializer, pinning, and runtime recovery tests pass. Supply order fulfillment now also creates the explicit `PENDING_MANUAL` runtime row in its atomic payment/order/shipment transaction. Setup loading + journey planning can pin a snapshot atomically for a matching `restaurantId` and setup owner. Supply order capture now persists the runtime row atomically, but does not auto-pin route yet: current authorization equates `restaurantId` with ordering account in some paths while setup uses `plotId`; auto-joining them without a durable mapping would risk cross-restaurant route pinning. PostgreSQL, concurrency, and Paper/Villager runtime verification remain outstanding. Added `SupplyVanillaVillagerAdapter` for server-thread-only supplier Villager projection with PDC role/shipment markers. Added bounded V1 journey snapshot decoder for persisted checkpoint/step payloads; decoder is pure Java and still not wired to world mutation. Added bounded `SupplyRuntimeProjection` DB read model: shipment/package/restaurant IDs, runtime revision, checkpoint, and decoded snapshot; invalid version/recovery/payload returns empty. Added pure recovery decision: one matching PDC candidate reuses, none requests spawn, duplicate candidates fail-closed; no world-wide entity scan. Added pure checkpoint resolver from persisted stage to snapshot position and adapter method `spawnSupplierAtCheckpoint(...)`; it validates loaded world and server thread. Added pure checkpoint transition state machine. It allows normal progression and repeated `ROUTE_WAYPOINT` steps, rejects skips/backward moves and all implicit `PENDING_MANUAL` transitions. Added V16 `checkpoint_index` durable column and bounded index. Non-waypoint rows require zero; waypoint rows use zero-based repeated-waypoint index. Projection and adapter now carry index; resolver selects exact waypoint. V16 migration test passes. Added atomic `transitionCheckpoint(...)` with claim instance/token/lease validation, revision + stage + index compare-and-set, operation receipt, and idempotent replay payload validation. It rejects invalid waypoint index progression. Added `finalizeDelivery(...)`: locks runtime/shipment/package, requires `HANDED_OFF + STOCKED + DELIVERY_DESPAWN`, applies revision/operation receipt atomically, and supports validated replay. Claim is not reused after handoff because claim lifecycle ends at shipment `HANDED_OFF`. Added V17 durable entity cleanup state (`NONE`/`REQUIRED`/`CONFIRMED`) and cleanup operation receipt. `finalizeDelivery(...)` marks cleanup `REQUIRED`; main-thread adapter removes matching PDC entity; repository can confirm cleanup idempotently. DB/entity actions remain two-phase by design. Added bounded in-memory `SupplyVillagerRegistry` keyed by shipment ID, capped at four entity candidates; duplicate/overflow remains fail-closed. Added `SupplyVillagerRegistryListener` and registered it in `RestaurantTycoonPlugin`: supplier Villagers are indexed on creature spawn/entity load and removed on entity unload/death/remove. Listener only reads PDC and never queries DB. Registry remains bounded at four candidates and overflow fail-closed. Added pure bounded `SupplyVillagerMovementDecision` with arrival radius and max-speed vector cap; adapter `moveToward(...)` applies velocity only on main thread and rejects cross-world target. This is vector projection, not Paper pathfinding or obstacle navigation. Added pure `SupplyVillagerStuckWatchdog`: bounded no-progress ticks, meaningful-distance threshold, invalid-distance rejection, and reset. Added explicit `SupplyVillagerMovementOutcome` (`MOVING`, `ARRIVED`, `STUCK`) and adapter overload combining bounded velocity movement with stuck watchdog. It does not mutate DB; caller must transition checkpoint or mark `PENDING_MANUAL`. Added pure `SupplyRuntimeMovementActionResolver`: `MOVING` continues, `ARRIVED` transitions or finalizes at `DELIVERY_DESPAWN`, `STUCK` marks manual recovery. Resolver has no side effects. Added `SupplyRuntimeMainThreadBridge`: DB coordinator can enqueue immutable runtime work onto server-thread executor; bridge performs no DB/entity mutation itself. Added `SupplyRuntimeProjectionLoader`: reloads projection by shipment + restaurant scope, then verifies package identity from queued work before main-thread entity access. Missing/mismatched projection fails closed. Full runtime orchestration remains incomplete. Paper API research review ghi rõ generic `Executor` chưa chứng minh main-thread Bukkit execution. Added `SupplyBukkitMainThreadExecutor`, wrapping `Server#getScheduler().runTask(...)`; bridge vẫn chưa được wire vào runtime handler thật. Adapter now rejects stale/dead Villagers before movement/removal and rejects unloaded spawn chunks; no chunk force-load. Review found and fixed two bugs: finalize retry could overwrite an existing cleanup receipt with a different operation; new supplier PDC was written after `CreatureSpawnEvent`, so spawn-time registry missed it. Review also tightened checkpoint transition: `ROUTE_WAYPOINT` can enter `DELIVERY_STOP` only at final immutable waypoint; transition now decodes and validates snapshot under row lock.

The domain currently supports the setup points required to begin the GUI slice:
central order desk and supplier spawn, plus restaurant delivery entry/stop,
unload point, warehouse entry, delivery exit, and despawn. Route waypoint
ordering, validation, persistence, and admin GUI editing are implemented and
unit-verified. The pure delivery-journey planner, immutable convoy state machine,
and package/handoff authorization are also unit-verified. Convoy/package matching is
now fail-closed by both restaurant and shipment identity, and public convoy snapshots
reject invalid progress/state combinations. Shipment and package identity are now durable database records created atomically with
Citizens navigation, runtime convoy execution, Bukkit ordering
  and handoff interaction, and live ingredient-stock gameplay are still not implemented.

A trust-boundary correction now separates `restaurantId` from `receivingActorId` in authorized package handoff. Player package interaction resolves restaurant ownership from `supply_orders.player_id` before calling the explicit-scope API; worker callers can use the same API with a worker actor without pretending worker UUID is restaurant scope. Focused H2 handoff tests pass. This is domain/unit evidence only; membership permission adapter, durable receiving-worker jobs, player operations GUI, Citizens spawn/navigation, and Paper runtime verification remain pending.

`SupplyPlayerDeliveryStatus` and pure mapper now expose player-facing lifecycle states including preparing, in transit, arrival/waiting, manual or worker receipt, stocking, completed, retry, manual recovery, and safe failure. `DELIVERY_STOP` no longer overrides `ARRIVED`; an arrived package remains `WAITING_FOR_RECEIVING` until durable handoff. `OperationsDashboardEntry` adds immutable player-facing entry data, stable shipment ordering, localized status labels, and next-action text. Focused tests pass. These are pure/read-model contracts only; the inventory GUI still needs bounded DB projection wiring, checkpoint/worker progress read model, and Paper verification. It does not claim runtime state until those sources are connected.

Independent review found handoff/token ordering, split authorization transactions, duplicate interaction risk, and idempotency payload mismatch. Current mitigation pre-checks/adds token before async handoff, uses deterministic operation ID plus in-flight debounce and duplicate-token check, removes token on failed DB mutation, removes entity after successful handoff even if player disconnects, makes player owner authorization plus handoff one DB transaction with row locks, and accepts idempotent replay only when operation ID and receiving actor both match. Unknown checkpoint values now map fail-safe to `FAILED_SAFE`; `DELIVERY_STOP` does not override durable `ARRIVED`. This is not atomic with a real server crash or client inventory manipulation: a crash can leave durable HANDED_OFF state, entity, and/or token requiring recovery. Warehouse stock success now removes one matching package token on the main thread, preventing token reuse after durable stock commit. Stock locks package before checking the existing operation and rejects mismatched replay payloads; repeated matching operation IDs remain idempotent. Player stock authorization now resolves restaurant scope and stock mutation under one transaction/row lock. `STOCKED` is distinct from `COMPLETED` until delivery despawn checkpoint. Durable receipt/token recovery, location binding, and Paper verification remain required.

Connect durable dish entitlements to a vanilla PDC item projection and implement
bounded inventory reconciliation. The database remains authoritative: reconnect,
inventory-full, duplicate token, consumed-token, and restart paths must not clone or
lose an entitlement. Do not begin customer NPC mechanics yet.

The repository/PDC/planner core and lifecycle ticket guard are implemented and
unit-verified. The repository now
loads the bounded holder projection and all observed storage-token IDs through one
read-only transaction and one SQL statement, then returns an immutable reconciliation
snapshot. The single statement-level database snapshot prevents H2 or PostgreSQL from
mixing entitlement revisions across projection and token validation. Runtime inventory
snapshot/apply and lifecycle activation remain gated because the current source has no
authoritative recipe-to-Bukkit-`Material` contract. Do not invent that mapping or enable
item grants until the content contract is defined and the server-thread boundary has
integration coverage.

The remaining runtime adapter must reclassify the complete storage inventory on the
server thread immediately before apply and abandon/retry the whole plan on any semantic
mismatch. Applying individual stale slot decisions is not safe while the player can
move items. Replacing one unrelated vanilla item with another may remain semantically
equal because the planner never mutates a slot classified as vanilla.

Runtime activation must consume the new transactional repository snapshot rather than
performing independent repository reads. The lifecycle ticket guard provides plugin
epoch and per-player session generation invalidation for quit, reconnect, and disable,
but runtime single-flight/coalescing and entitlement-transition invalidation are not yet
wired. They remain required before activation.
Only `PlayerInventory#getStorageContents()` is in reconciliation scope; armor,
offhand, cursor, containers, and world items must not be scanned or mutated by this
runtime. Because storage has fewer slots than the bulk-ID limit of 100, an observed
storage snapshot cannot legitimately exceed that validation bound.

The prior playerless purchase-to-world projection gate, including replay and
crash/restart repair, passed on isolated Paper on 2026-08-14 as recorded in
`OPENCODE_HANDOFF.md`.

## Operational Safety

- The workspace is a Git repository on `main` with no commits; all current project files
  are untracked. Preserve them and do not claim a committed baseline.
- Do not expose local PostgreSQL credentials.
- Do not use Paper `/reload` or a plugin hot-loader.
- Do not deploy or restart production without explicit approval.

## Documentation Rule

`OPENCODE_HANDOFF.md` contains detailed architecture and historical evidence. This
file is the concise current-state entry point when the two disagree.
