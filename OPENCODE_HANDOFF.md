# OpenCode Handoff - Restaurant Tycoon

## Immediate Action

Paper boot, playerless purchase-to-world projection, identical replay, cleanup, and
crash/restart repair all passed on 2026-08-14. Durable dish entitlement persistence
now covers issue, claim, consume, idempotent retries, and competing-holder rejection.
The normal projection gate confirmed:

```text
PROJECTION_FIXTURE_SEEDED ... balance=375 duplicate=false
PROJECTION_FIXTURE_VERIFIED ... fence=1 stage=1
PROJECTION_FIXTURE_SEEDED ... same IDs ... duplicate=true
PROJECTION_FIXTURE_CLEANED
```

The crash gate halted Paper with exit code 86 after the SQL commit marker and before
world apply. The recovery boot let the normal worker apply the durable operation,
verified the world/checkpoint, cleaned test-owned data, and stopped cleanly. Proceed to
the vanilla PDC item projection and bounded inventory reconciliation. Do not begin
customer NPC mechanics yet. Never display the ignored smoke `config.yml`; it contains
the test password.

## Current Status

- Regular build passes: `./gradlew.bat build --no-daemon`.
- 47 unit tests pass in the latest full build.
- Real PostgreSQL integration suite passed on the user's local PostgreSQL 18:
  `./gradlew.bat postgresIntegrationTest --no-daemon`.
- Prior PostgreSQL integration coverage includes Flyway V1-V5, 250 concurrent purchase
  retries, claim expiry, simulated worker restart, stale callback fencing, and
  canonical projection checkpoint.
- Paper 1.20.4 build 499 boot smoke passed end-to-end and stopped cleanly.
- Shaded JDBC and Flyway service-provider loading work under Paper's plugin
  classloader.
- Playerless projection smoke passed through the real economy, assignment, purchase,
  worker, canonical world validation, checkpoint, replay, and cleanup paths.
- Crash-after-commit/restart repair passed.
- Flyway V6 adds durable dish entitlements and operation receipts. Focused tests and
  the full build pass; V6 has not yet been rerun against real PostgreSQL or Paper.

## Last Failure And Fix

### PostgreSQL Fence-Lock Defect (2026-08-15)

`PostgresDurabilityIntegrationTest.expiredClaimIsReclaimedAfterWorkerRestartAndOldCallbackIsFenced`
failed on real PostgreSQL with `ERROR: FOR UPDATE cannot be applied to the nullable
side of an outer join`. `WorldOperationRepository.lockCurrentFence` (the fence-lock
statement inside `markApplied`) ran `SELECT ... FROM world_operations w LEFT JOIN
purchases p ... JOIN plot_assignments a ... FOR UPDATE`; PostgreSQL refuses to lock
the nullable side (`purchases`) of an outer join. V11 made `purchase_id` nullable for
ONBOARDING operations, so this failure hit every `markApplied` call regardless of
operation source, blocking durable world-operation completion on PostgreSQL.

Fix: the lock query no longer outer-joins `purchases`. It locks only `world_operations`
and `plot_assignments` (both non-nullable) and resolves the expected owner with a
correlated scalar subquery inside `COALESCE((SELECT p.account_id FROM purchases p
WHERE p.purchase_id = w.purchase_id), w.source_account_id)`. The fence/ownership
predicate, the plot-assignment row lock, and the claim-revalidation in the final
`markApplied` UPDATE are unchanged; both PURCHASE and ONBOARDING operations keep the
same fencing semantics, and the outer-join-free query leaves no race that a bare
`FOR UPDATE` removal would have created. The unit suite already drove both operation
sources through `markApplied` on H2; the integration test is the PostgreSQL gate.

### Paper Smoke Regressions

The first Paper smoke run found two issues:

1. `Get-Content -Raw` returned `$null` while `latest.log` was temporarily empty, so
   the script called `.Contains` on null.
2. PostgreSQL was relocated to `vn.restauranttycoon.libs.postgresql` in the shadow
   JAR. Hikari/JDBC service loading then failed with `No suitable driver`.

Fixes already applied:

- `scripts/paper-smoke.ps1` now tolerates empty/temporarily locked logs.
- PostgreSQL is no longer relocated. HikariCP and Flyway remain relocated.
- `mergeServiceFiles()` remains enabled.
- Build gate `verifyShadedJdbc` checks both:
  - `org/postgresql/Driver.class`
  - `META-INF/services/java.sql.Driver` containing `org.postgresql.Driver`
- The full build passed after these changes.

The previous Paper process stopped cleanly. Its log showed Paper 1.20.4 build 499,
plugin config schema 2, server startup, the JDBC error, and clean shutdown.

## Architecture Implemented

### Economy And Purchase

- Currency uses signed `BIGINT` units, never floating point.
- Economy ledger mutations are idempotent by operation UUID.
- Purchase transaction atomically commits balance charge, ledger row, unlock,
  purchase snapshot, and `world_operations` row.
- Same operation UUID plus identical immutable inputs returns the committed result.
- Reusing an operation UUID with changed inputs throws a conflict.
- Purchase verifies current plot owner and fence inside the SQL transaction.

### Plot Assignment

- Plot assignment has monotonically increasing fence token/revision on release and
  reassignment.
- Identical retry for the same `plot + owner + server` is idempotent and does not
  advance the fence.
- `PlotAssignmentService` keeps SQL off the Bukkit main thread.

### World Operations

- Durable states include `REQUESTED`, `APPLYING`, `APPLIED`, and `REPAIR_REQUIRED`.
- Claims use instance ID, unique claim token, and database-time lease expiry.
- Expired claims are reclaimable; old callbacks are fenced.
- Every apply/validation batch verifies plot fence and renews the lease.
- Completion atomically writes `plot_projection_state` and marks the operation
  `APPLIED`.
- Projection stage revision cannot move backward within the same fence.
- `WorldOperationWorker` keeps repository calls on the database executor and delegates
  Bukkit mutation through `WorldProjectionApplier`.

### Canonical Stage Projection

- `stages.yml` schema version 1 stores absolute stage snapshots.
- Every position inside the bounded volume converges to an expected block; undeclared
  positions converge to `minecraft:air`.
- Duplicate/out-of-volume offsets and invalid block data are rejected.
- Volume is capped at 100,000 blocks.
- Bukkit applies blocks with physics disabled in bounded batches, then validates the
  full volume.
- All Bukkit world access and BlockData parsing run on the main thread.

### Durable Dish Entitlements

- Flyway V6 adds `dish_entitlements` and `dish_entitlement_operations`.
- One durable entitlement is issued per `order_id`; an identical issue retry returns
  the existing row and conflicting identity or recipe snapshots are rejected.
- States are `AVAILABLE`, `CLAIMED`, and `CONSUMED` with a monotonic state revision.
- Claim and consume transitions lock the entitlement row and use operation UUID
  receipts. Identical retries return the committed transition without mutating again.
- Reusing an operation UUID with different entitlement, player, or operation type is
  rejected. A competing player cannot claim or consume another holder's entitlement.
- Concurrent identical issue requests converge to one durable entitlement.
- No Bukkit inventory/PDC integration exists yet. The database is authoritative; the
  future item is only a projection.

### Plugin Lifecycle

- `config.yml` schema version 2 defines PostgreSQL, worker settings, and configured
  plot world/origin.
- Stage manifest and loaded worlds are validated during enable.
- Worker starts only after database migration reaches `READY`.
- One in-flight guard prevents overlapping operations per plugin instance.
- Polling stops when DB is not ready and the repeating task is cancelled on disable.

### Development Commands

```text
/restaurant grant <player> <amount>
/restaurant dev assign <player> <plot>
/restaurant dev purchase <player> <plot> <unlock> <definitionVersion> <stageRevision> <price> <operationUuid>
```

- Development commands require `restauranttycoon.admin.dev`.
- Player must be online so UUID lookup is unambiguous.
- Purchase does not auto-grant money.
- Retry must reuse the same operation UUID.

## Test Infrastructure

### Unit Tests

Run:

```powershell
.\gradlew.bat build --no-daemon
```

Unit tests use H2 PostgreSQL mode and cover economy, migration, plot assignment,
purchase concurrency, world claims, worker recovery, stage manifests, config, and
dish entitlement issue/claim/consume invariants. The latest full build ran 47 tests.

### Real PostgreSQL

Documentation: `POSTGRES_INTEGRATION_TESTING.md`.

Run only with the three `RT_TEST_POSTGRES_*` variables set:

```powershell
.\gradlew.bat postgresIntegrationTest --no-daemon
```

The suite creates a random `rt_test_<uuid>` schema and drops only that schema. It
never cleans or drops the database. It has already passed against local PostgreSQL 18.

### Paper Smoke

Documentation: `PAPER_SMOKE_TESTING.md`.

Pinned artifact:

```text
Paper 1.20.4 build 499, STABLE
paper-1.20.4-499.jar
size 42,781,488 bytes
SHA-256 cabed3ae77cf55deba7c7d8722bc9cfd5e991201c211665f9265616d9fe5c77b
```

The harness downloads from PaperMC with an identifying User-Agent, verifies checksum
and size, builds the plugin, boots an isolated server on port 25566, waits for plugin,
database, worker, and Paper startup markers, sends `stop`, and requires exit code 0.
It refuses to create `eula.txt` unless the user explicitly sets
`RT_ACCEPT_MINECRAFT_EULA=true` after reading the EULA.

## Decisions And Constraints

- Target: Paper 1.20.4; Java clients 1.20.3-1.20.4.
- Current host Java is 25.0.1; Paper docs require Java 21 for 1.20.x, so it boots but
  emits old-library warnings under Java 25. Production should pin a tested Java 21.
- PostgreSQL is mandatory for validation/production. H2 is test-only.
- No Docker is installed. PostgreSQL 18 is installed natively.
- No Oraxen, ViaVersion, Citizens, Vault, WorldGuard, ProtocolLib, or resource pack in
  the core vertical slice.
- Paper first; do not declare Folia support.
- Do not use `/reload` or plugin manager hot reload.
- Workspace is not a Git repository. Do not claim commits/history exist.
- Research official documentation and analyze invariants before implementation, per
  the user's explicit instruction.
- `opencode.json` exposes read-only `minecraft-workflow` and `livingnpc` references.
  RestaurantTycoon source, tests, `AGENTS.md`, and `CURRENT_STATE.md` remain
  authoritative. Do not edit either reference during RestaurantTycoon-only work or
  inherit their versions, schemas, dependencies, release gates, or production state.

## Unverified V6 Boundaries

- The current session did not have `RT_TEST_POSTGRES_*` configured. Flyway V6 and
  entitlement concurrency have not yet been rerun against real PostgreSQL.
- No Paper smoke was run after V6. V6 is persistence-only and is not wired into plugin
  startup or player inventory behavior yet.
- H2 PostgreSQL mode and unit tests do not prove Paper inventory events, reconnect,
  restart, inventory-full behavior, or PostgreSQL locking semantics.
- The workspace is not a Git repository and may contain active user changes. Do not
  claim commits or attempt to reset/revert files.

## Completed Projection Gate

Implement a playerless end-to-end projection smoke without weakening production
contracts:

1. Research and design a test-only seeding mechanism. Do not write directly to world
   or bypass the purchase repository.
2. Prefer an opt-in console/dev fixture command or a separate test harness that uses
   the real repositories to grant, assign, and commit a purchase.
3. Use a unique account UUID and operation UUID, and isolate/clean only test-owned
   data. Do not reuse production logical operation IDs.
4. Let the normal world-operation worker apply stage 1.
5. Verify in Paper that world `(0,64,0)` stage volume converges as authored, the
   `plot_projection_state` checkpoint exists, the operation is `APPLIED`, and replay
   is idempotent.
6. Add crash injection after SQL commit/before block apply and verify restart repair.

Purchase-to-world now passes on Paper. Durable entitlement persistence is implemented;
next connect it to vanilla PDC item projection and inventory reconciliation. Do not
begin customer NPC mechanics yet.

## Primary Files

```text
RESTAURANT_TYCOON_PLAN.md
COMPATIBILITY_AND_IMPLEMENTATION_PLAN_VI.md
POSTGRES_INTEGRATION_TESTING.md
PAPER_SMOKE_TESTING.md
scripts/paper-smoke.ps1
build.gradle.kts
src/main/java/vn/restauranttycoon/RestaurantTycoonPlugin.java
src/main/java/vn/restauranttycoon/economy/
src/main/java/vn/restauranttycoon/plot/
src/main/java/vn/restauranttycoon/purchase/
src/main/java/vn/restauranttycoon/worldoperation/
src/main/java/vn/restauranttycoon/build/
src/main/java/vn/restauranttycoon/dish/
src/main/resources/db/migration/
src/main/resources/config.yml
src/main/resources/stages.yml
src/postgresIntegrationTest/
```

## Latest V6 File Manifest

Added:

```text
src/main/resources/db/migration/V6__dish_entitlements.sql
src/main/java/vn/restauranttycoon/dish/DishEntitlement.java
src/main/java/vn/restauranttycoon/dish/DishEntitlementState.java
src/main/java/vn/restauranttycoon/dish/DishEntitlementResult.java
src/main/java/vn/restauranttycoon/dish/DishEntitlementConflictException.java
src/main/java/vn/restauranttycoon/dish/DishEntitlementRepository.java
src/test/java/vn/restauranttycoon/dish/DishEntitlementRepositoryTest.java
```

Updated:

```text
src/test/java/vn/restauranttycoon/persistence/DatabaseMigrationTest.java
src/postgresIntegrationTest/java/vn/restauranttycoon/persistence/PostgresDurabilityIntegrationTest.java
CURRENT_STATE.md
POSTGRES_INTEGRATION_TESTING.md
OPENCODE_HANDOFF.md
```

## Exact Next Action For Hermes

1. Start at `E:\AI.WORK\restauranttycoon` and read `AGENTS.md`, `CURRENT_STATE.md`,
   this handoff, and the current dish source/tests before editing.
2. If disposable PostgreSQL credentials are available, run
   `.\gradlew.bat postgresIntegrationTest --no-daemon` first to validate Flyway V6.
   Never point it at production or display credentials.
3. Design the smallest vanilla 1.20.4 PDC token projection using `ItemMeta` PDC, not
   display name or lore as identity. Keep all inventory access on the main thread and
   all repository calls off it.
4. Implement bounded reconciliation for reconnect and inventory changes. The database
   remains authoritative; duplicate, forged, wrong-holder, and consumed tokens must be
   removed or rejected idempotently, while inventory-full must leave the entitlement
   claimable rather than dropping an item.
5. Add focused unit tests, run the full build, then obtain controlled PostgreSQL and
   isolated Paper evidence before claiming inventory/restart behavior. Do not begin
   customer NPC mechanics.

## Source References Used

- Paper scheduling: https://docs.papermc.io/paper/dev/scheduler/
- Paper database guidance: https://docs.papermc.io/paper/dev/using-databases/
- Paper plugin configuration: https://docs.papermc.io/paper/dev/plugin-configurations/
- Paper commands/registration: https://docs.papermc.io/paper/dev/command-api/basics/registration/
- Paper PDC guidance: https://docs.papermc.io/paper/dev/pdc/
- Paper plugin.yml: https://docs.papermc.io/paper/dev/plugin-yml/
- Paper startup: https://docs.papermc.io/paper/getting-started/
- Paper Downloads Service: https://docs.papermc.io/misc/downloads-service/
- Minecraft EULA: https://www.minecraft.net/eula
- Testcontainers PostgreSQL docs: https://java.testcontainers.org/modules/databases/postgres/
