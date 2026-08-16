# Database startup rejected the bundled V6 schema

Date: 2026-08-14
Status: fixed in source; runtime PostgreSQL/Paper verification pending

## Symptom

After Flyway applies the bundled `V6__dish_entitlements.sql`, database startup checks the current schema against version `5`. The check fails and leaves the plugin database state `DEGRADED`, so database-backed workers and commands cannot start.

## Root cause

`DatabaseManager.REQUIRED_SCHEMA_VERSION` was not advanced when migration V6 was added. Migration coverage verified that Flyway reached V6, but no regression test tied the startup-required version to the latest bundled schema.

## Fix

Advance the required startup schema version from `5` to `6` and add a regression assertion beside the migration test. Exact equality remains fail-closed for unsupported future schemas.

## Regression test

`DatabaseMigrationTest.databaseStartupRequiresLatestBundledSchemaVersion` verifies the startup requirement is V6. `freshMigrationAndRerunReachVersionSix` independently verifies that the bundled migrations reach V6 and rerun idempotently.

Focused verification:

```text
./gradlew.bat test --tests vn.restauranttycoon.persistence.DatabaseMigrationTest --console=plain --no-daemon
BUILD SUCCESSFUL
```

## Runtime verification

Not yet verified against real PostgreSQL or Paper after this source change. Do not treat unit verification as a production startup result.
