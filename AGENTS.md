# RestaurantTycoon Project Rules

## Session Startup

Before changing code:

1. Read `CURRENT_STATE.md`.
2. Read only the relevant sections of `OPENCODE_HANDOFF.md` and testing documents.
3. Inspect current source, tests, build configuration, and workspace changes.
4. Preserve unrelated user changes.

Before cross-project integration, re-read each participating reference's
`CURRENT_STATE.md` and relevant current source and tests. Do not rely on earlier
conversation context or an older handoff. Keep references read-only unless the user
explicitly requests a cross-repository change.

## Baseline

- Target Paper: `1.20.4`, with the isolated harness pinned to build `499`.
- Java bytecode target: `17`; use a Paper-compatible Java runtime for smoke tests.
- Persistence: PostgreSQL with Flyway migrations; H2 PostgreSQL mode is unit-test only.
- Full local verification: `.\gradlew.bat build --no-daemon`.
- Opt-in database verification: `.\gradlew.bat postgresIntegrationTest --no-daemon`
  with the documented `RT_TEST_POSTGRES_*` environment variables.
- Paper smoke: `.\scripts\paper-smoke.ps1` after explicit EULA acceptance and test
  database setup.

## Safety

- Never use Paper `/reload`, PlugMan, or another plugin hot-loader.
- Do not deploy, restart, or modify production without explicit user approval.
- Never display or copy `run/paper-smoke/plugins/RestaurantTycoon/config.yml`; it can
  contain a local PostgreSQL test password.
- Never point automated tests at production. Integration tests may create and drop
  only their randomly named test schema.
- Keep SQL, file, and network work off the Bukkit main thread. Keep Bukkit world,
  entity, player, inventory, and BlockData access on the main thread.
- Preserve idempotency, fence-token, lease, and restart-recovery invariants.
- Keep world projection work bounded and do not claim Paper behavior from unit tests.

## Source Of Truth

1. Current source and tests for implemented behavior.
2. `CURRENT_STATE.md` for current verification and next gate.
3. Current testing and architecture documents.
4. `OPENCODE_HANDOFF.md` as detailed evidence that may contain older state.

External references provide patterns only. Do not inherit LivingNPC versions,
Citizens assumptions, YAML schemas, release gates, or production state.
