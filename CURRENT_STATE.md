# RestaurantTycoon Current State

Last reviewed: 2026-08-15

## Baseline

- Source version: `0.1.0-SNAPSHOT`.
- Target: Paper `1.20.4`; isolated smoke harness pins build `499`.
- Java source and bytecode target: `17`.
- PostgreSQL is required for integration and production behavior; H2 is test-only.
- Flyway migrations currently cover schema versions V1 through V12.
- Database startup now requires schema V12, matching the latest bundled migration.
- V11 adds free onboarding world operations; V12 adds supply shipment claim fields and handoff recipient audit fields.
- Onboarding listener now allocates a configured plot, guides player by compass, and requests initial stage when player enters configured trigger. Paper runtime remains unverified.
- Supply runtime still lacks durable claim implementation in coordinator, Citizens delivery handler, player package interaction, and warehouse interaction flow.
- V7/V8 own durable supply setup points and restaurant-scoped route waypoints.
- V9 owns durable supply orders, order lines, payments, shipments, packages, and
  warehouse stock tables; V10 owns package line snapshots.
- V8 owns restaurant-scoped delivery-route waypoints with a composite
  `(setup_scope, owner_id, sequence_no)` primary key. Replacing a route deletes
  and reinserts one restaurant's ordered waypoint set in a single transaction.

## Verification

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
single-flight coordinator polls bounded durable shipment/package work on the database
executor and logs pending work without mutating state or spawning entities. Paper/Citizens
convoy execution and smoke testing remain unverified and require explicit approval, a
non-production test server, and real configured plot IDs/worlds. Do not claim runtime
verification from unit tests or the local build alone.
Plugin disable closes the supply runtime coordinator before the database is closed. The
coordinator rejects later polls and checks its closed state before querying or dispatching
each subsequent batch item; it intentionally does not interrupt a handler or JDBC operation
that already started. Durable claim/lease/fence ownership remains required before enabling
a mutating Citizens runtime.

The domain currently supports the setup points required to begin the GUI slice:
central order desk and supplier spawn, plus restaurant delivery entry/stop,
unload point, warehouse entry, delivery exit, and despawn. Route waypoint
ordering, validation, persistence, and admin GUI editing are implemented and
unit-verified. The pure delivery-journey planner, immutable convoy state machine,
and package/handoff authorization are also unit-verified. Convoy/package matching is
now fail-closed by both restaurant and shipment identity, and public convoy snapshots
reject invalid progress/state combinations. Shipment and package identity are now durable database records created atomically with
captured supplier orders. Citizens navigation, runtime convoy execution, Bukkit ordering
and handoff interaction, and live ingredient-stock gameplay are still not implemented.

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
