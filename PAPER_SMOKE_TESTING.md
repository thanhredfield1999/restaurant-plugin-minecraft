# Paper 1.20.4 Smoke Testing

The smoke harness boots an isolated local Paper server, loads the shaded plugin,
connects it to the disposable PostgreSQL test database, waits for startup markers,
and sends `stop` for a clean shutdown.

## Pinned Server Artifact

- Minecraft/Paper: `1.20.4`, build `499`, channel `STABLE`
- File: `paper-1.20.4-499.jar`
- Size: `42,781,488` bytes
- SHA-256: `cabed3ae77cf55deba7c7d8722bc9cfd5e991201c211665f9265616d9fe5c77b`
- Source: PaperMC Downloads Service

The Paper JAR is downloaded into the ignored `run/paper-smoke` directory. It is not
bundled into the plugin or redistributed by this project. The harness verifies size
and SHA-256 before execution.

## Requirements

- Java 21 or newer. Paper documentation specifies Java 21 for Paper 1.20.x.
- A disposable PostgreSQL database and test role.
- The three `RT_TEST_POSTGRES_*` variables documented in
  `POSTGRES_INTEGRATION_TESTING.md`.
- Explicit acceptance of the Minecraft EULA by the person running the test.

Read https://www.minecraft.net/eula before setting:

```powershell
$env:RT_ACCEPT_MINECRAFT_EULA = "true"
```

The script will not create `eula.txt` unless this exact opt-in is present.

## Run

From the project root, in the same PowerShell process containing the PostgreSQL
environment variables:

```powershell
.\scripts\paper-smoke.ps1
```

Optional port and startup timeout:

```powershell
.\scripts\paper-smoke.ps1 -Port 25566 -TimeoutSeconds 240
```

The local server uses offline mode, a whitelist, short view distances, and the
ignored `run/paper-smoke` directory. It is a local boot harness, not a production
configuration and must not be exposed to the internet.

The harness-generated config declares `schema-version: 2`, the test database,
worker settings, one `plot_1` with origin and trigger bounds, and a two-ingredient
`supply-catalog`. These sections match what `PluginSettings` and the
ingredient-catalog loader require at enable time, so the plugin reaches its boot
markers instead of disabling on invalid configuration.

The test passes only after the log contains all of these conditions:

- RestaurantTycoon enabled with config schema 2.
- Flyway/database initialization completed.
- The world-operation worker started as `paper-smoke`.
- Paper reached its `Done` startup marker.
- The process accepted `stop` and exited with code 0.

Logs remain under `run/paper-smoke` for diagnosis. Database credentials are written
only into this ignored runtime directory. Delete that directory after testing if the
machine is shared.

## Running-Instance Guard

Before starting Paper, the harness refuses to run when another server would collide:

- It probes the configured `$Port` with a bounded loopback TCP connect (2 s timeout).
- It then queries Windows only for `java.exe`/`javaw.exe` processes whose command
  line contains the exact pinned jar name `paper-1.20.4-499.jar`. The WQL filter is
  evaluated server-side, and the whole query runs inside a
  `Start-Job`/`Wait-Job -Timeout` job that fails closed after 20 s instead of hanging.

A Java Minecraft client, a production Paper server on a differently named jar, or any
other process is never matched. This guard lives in
`scripts/paper-smoke-guard.ps1` and is called by `scripts/paper-smoke.ps1`.

## Guard Regression Tests

`scripts/test-paper-smoke-guard.ps1` exercises the guard helpers without starting
Paper or reading the smoke config: command-line matching, the port probe, the bounded
process query, and the combined collision guard. Run it directly or through Gradle:

```powershell
.\gradlew.bat paperSmokeGuardTest
```

The Gradle task is opt-in, runs only on Windows, and is not part of `check` or
`build`.

## Current Boundary

The default command remains a plugin boot/lifecycle smoke test. An opt-in playerless
projection mode uses console-only fixtures guarded by the JVM system property
`restauranttycoon.testFixtures`. The fixture grants, assigns, and purchases through
the production services; only the normal worker mutates the world.

Run purchase-to-world projection, checkpoint validation, identical replay, test-owned
cleanup, and clean shutdown:

```powershell
.\scripts\paper-smoke.ps1 -Projection
```

Run the crash boundary after purchase commit and before world apply, then recover in a
second Paper process:

```powershell
.\scripts\paper-smoke.ps1 -CrashAfterCommit
.\scripts\paper-smoke.ps1 -RecoverCrash
```

The recovery state file contains only generated fixture UUIDs. The harness never puts
database credentials in it. `-UseExistingConfig` is for local follow-up runs after the
user has already accepted the EULA and generated the ignored smoke config; it neither
reads nor rewrites that config.

## References

- Paper Downloads Service: https://docs.papermc.io/misc/downloads-service/
- Paper startup requirements: https://docs.papermc.io/paper/getting-started/
- Minecraft EULA: https://www.minecraft.net/eula
