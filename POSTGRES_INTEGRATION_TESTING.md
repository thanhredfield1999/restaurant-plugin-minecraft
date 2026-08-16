# PostgreSQL Integration Testing

The PostgreSQL suite is opt-in. It validates SQL behavior that H2 PostgreSQL mode
cannot prove, including `FOR UPDATE SKIP LOCKED`, concurrent idempotent purchase
retries, claim expiry, and worker restart recovery.

## Safety

Use a disposable database and a dedicated test role. Do not point these tests at a
production or staging database. The role must be able to create and drop schemas.

Each test creates a schema named `rt_test_<random UUID>`, runs Flyway migrations in
that schema, and drops only that generated schema during cleanup. The suite never
runs Flyway clean and never drops the database.

## Environment

Set these environment variables:

```text
RT_TEST_POSTGRES_URL=jdbc:postgresql://localhost:5432/restaurant_tycoon_test
RT_TEST_POSTGRES_USER=restaurant_tycoon_test
RT_TEST_POSTGRES_PASSWORD=<test-only password>
```

On PowerShell for the current process:

```powershell
$env:RT_TEST_POSTGRES_URL = "jdbc:postgresql://localhost:5432/restaurant_tycoon_test"
$env:RT_TEST_POSTGRES_USER = "restaurant_tycoon_test"
$env:RT_TEST_POSTGRES_PASSWORD = "<test-only password>"
.\gradlew.bat postgresIntegrationTest --no-daemon
```

If any variable is missing, the Gradle task is skipped. Regular `build` does not run
the PostgreSQL suite:

```powershell
.\gradlew.bat build --no-daemon
```

## Suite Coverage

- Fresh Flyway V1-V12 migration and migration rerun pinned to the latest bundled
  schema version.
- 250 concurrent retries of one logical purchase produce one charge, unlock,
  purchase, and world operation.
- An expired world-operation claim can be reclaimed after a simulated worker restart.
- A callback from the pre-restart claim cannot complete the operation.
- The restarted worker writes one canonical projection checkpoint and leaves no
  claimable duplicate operation.

The suite source expects Flyway V12, matching the runtime startup pin
`DatabaseManager.REQUIRED_SCHEMA_VERSION`. The latest recorded real-PostgreSQL run
predates V7 and verified V1-V5 only. Re-run the suite with disposable test credentials
before claiming V12 PostgreSQL compatibility.

Paper server boot and block projection smoke tests remain a separate gate. They
require a pinned Paper 1.20.4 server artifact, Java 21 or newer, and a reachable test
PostgreSQL instance.

## References

- Paper database guidance: https://docs.papermc.io/paper/dev/using-databases/
- Paper server requirements: https://docs.papermc.io/paper/getting-started/
- Testcontainers PostgreSQL module: https://java.testcontainers.org/modules/databases/postgres/
- Testcontainers JUnit 5 integration: https://java.testcontainers.org/test_framework_integration/junit_5/
