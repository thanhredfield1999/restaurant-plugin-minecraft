# Minecraft Restaurant Tycoon - Product, Technical, and Feasibility Plan

> Research baseline: August 14, 2026. This plan assumes a Minecraft Java Edition
> server running Paper. The desired experience is a Roblox-style tycoon, not a
> detailed restaurant management simulator.
>
> Audit status: revised after independent gameplay, Paper engineering, data
> integrity, operations, and policy reviews. Section 23 contains binding hardening
> requirements that override any less-specific earlier wording.

## 1. Executive Decision

The project is feasible. The recommended product is a hybrid of:

- 70% physical tycoon: claim a plot, buy pads, watch structures appear, increase
  income, hire automation, unlock floors, and prestige.
- 30% active restaurant play: optionally take orders, cook, serve, and clean to
  accelerate income.

The central promise is:

> Press a purchase button, see the restaurant visibly improve, watch customers
> use the new upgrade, earn faster, and unlock the next impressive purchase.

Do not begin with unrestricted restaurant building, complex recipes, realistic
employee scheduling, a global player market, or hundreds of simulated customers.
Those systems increase risk without proving that the core tycoon loop is fun.

### Feasibility summary

| Capability | Feasibility | MVP decision |
|---|---:|---|
| Claimable personal plots | High | Build |
| Physical purchase pads/buttons | High | Build |
| Staged restaurant construction | High | Build |
| Persistent cash and upgrades | High | Build |
| Simple customer pipeline | High | Build |
| Optional manual cooking/serving | High | Build one recipe flow |
| Staff automation | High | Build cook and cashier |
| Custom food/furniture visuals | High | Use a resource pack selectively |
| Free-form building | Medium | Defer until the loop is proven |
| Customer actors | High conditionally | Use optimized vanilla villagers on observed plots |
| 20 concurrent players | Unvalidated target | Must pass representative benchmark |
| 50 concurrent players | Unvalidated stretch | Requires measured workload budgets |
| 100 concurrent players | Unknown | Not an MVP promise; may require sharding |
| Folia at launch | Medium-low | Architect for it, run Paper first |

## 2. Product Pillars

1. Every important purchase changes the physical world.
2. The next goal is visible without opening a large menu.
3. Automation is a reward, not the starting condition.
4. Active play accelerates income but is not mandatory forever.
5. The restaurant becomes visually impressive within one play session.
6. Progress is server-authoritative and resistant to item duplication.
7. Social features showcase restaurants without letting wealth dominate fairness.

## 3. Target Experience

### Audience

- Players who understand Roblox-style button tycoons.
- Minecraft players who enjoy visible building progression and number growth.
- Solo players and groups of two to four friends.
- Sessions from 15 minutes to two hours.

### Session fantasy

The player starts on an empty lot with a small food stall. Customers arrive, the
player completes a very short service flow, and the first cash appears in a
physical register. A nearby purchase pad becomes affordable. Buying it causes a
dining area to rise from the plot, reveals new pads, increases throughput, and
makes the restaurant look materially better. Staff gradually automate repetitive
jobs. Later purchases add a full kitchen, drive-through, upper floor, themed wing,
and landmark sign. The player eventually prestiges into a new restaurant brand.

## 4. Core Gameplay Loop

```text
Claim plot
  -> serve first customer
  -> collect cash
  -> buy a physical upgrade
  -> structure/equipment appears
  -> customer throughput or value increases
  -> hire automation
  -> unlock a new wing/floor/theme
  -> complete the restaurant
  -> prestige and rebuild faster
```

### Customer pipeline

Treat restaurant operation as the equivalent of a classic tycoon production line:

```text
Customer enters
  -> gets a seat/order
  -> kitchen processes the order
  -> meal is served
  -> payment enters the register
  -> customer leaves
```

Customers are not deep agents. Each party only needs a type, order, current state,
patience deadline, path target, and economic result.

### Customer states

```text
SPAWNING -> ENTERING -> ORDERING -> WAITING -> EATING -> PAYING -> LEAVING
                                      |                       |
                                      +-> ABANDONED <---------+
```

Every state must have a timeout and a recovery action. A pathfinding failure must
not punish the player or block the restaurant permanently.

### Active jobs

Manual jobs should take one or two interactions, not become separate simulations:

- Take order: interact with a waiting table.
- Cook: place the correct ingredient token in a station and complete a short
  timing or interaction step.
- Serve: deliver the server-issued dish to the matching table.
- Clean: one interaction resets a table.
- Restock: optional short delivery that provides a temporary revenue bonus.

Active play uses a capped reward budget, not an unrestricted multiplier. Initial
target: an engaged solo player earns 15-25% more total value over a 15-minute
window than an automated but non-idle player at the same progression. No combination
of collaborators, service actions, rushes, or events may exceed a 30% active-value
uplift over that window. These remain playtest hypotheses.

### Complete restaurant operation flow

The restaurant is a visible production pipeline. A dish cannot be served directly
from a menu or created merely by clicking a customer.

```text
Customer arrives
  -> waits at the host/queue
  -> receives an available table
  -> walks to the table and orders
  -> order appears on the kitchen board
  -> player accepts it and reserves ingredients
  -> food passes through the required station sequence
  -> completed dish waits at the serving counter
  -> player carries it to the correct table
  -> customer eats and payment settles
  -> customer leaves
  -> dirty table is cleaned and becomes available
```

#### Arrival and seating

- Spawn a physical villager only when an observed plot has customer capacity.
- The customer follows configured entrance and queue anchors.
- A free table must support the party size and current build stage.
- Before a host is hired, the player assigns a waiting customer through the host
  stand. A host upgrade automates this transition.
- Reserve the table before the villager starts moving so two customers cannot claim
  it. Navigation failure safely releases or recovers the reservation.

#### Ordering

- At the table, the customer selects one recipe from the enabled menu after a short
  readable delay.
- Selection considers customer type, unlocked recipes, station availability, and a
  frozen recipe/price version.
- Before a waiter is hired, the player interacts with the table to take the order.
- The kitchen board shows table, recipe, age, and patience state.
- Each order has one durable `order_id`; GUI icons and items are only views of it.

#### Preparing and cooking

- The player accepts an order from the kitchen board and interacts with its first
  required station.
- MVP inputs are restaurant stock counters or server-issued ingredient tokens, not
  arbitrary vanilla items brought by players.
- Starting a job atomically reserves/consumes inputs and changes the order to
  `PREPARING`.
- Each station has a bounded queue and cannot accept work when full.

Example expanded recipe:

```text
PREP_TABLE: prepare raw burger, 2 seconds
  -> GRILL: cook patty, 8 seconds
  -> ASSEMBLY_COUNTER: assemble burger, 2 seconds
  -> SERVING_COUNTER: completed dish ready
```

Prototype recipe:

```text
Accepted order
  -> GRILL: cook food, 8 seconds
  -> SERVING_COUNTER: completed dish ready
```

Right-clicking the configured grill starts the job only when the order, stock, and
station are valid. Progress can use ItemDisplay/TextDisplay, sound, particles, and
action bar, but visual entities never own job state. Persist an absolute
`finishes_at` timestamp so lag/restart does not corrupt cooking time. MVP has no burn
mechanic; ready food waits safely. Freshness may later reduce a bonus, but server lag
must never destroy a committed dish.

#### Claiming and carrying the dish

- Final-station completion creates one entitlement bound to `order_id`,
  `restaurant_id`, `recipe_id`, and `recipe_version`.
- The player claims it from the serving counter. A held item may contain PDC identity,
  while durable entitlement remains server-authoritative.
- Limit carried dishes in the prototype, ideally one at a time.
- Drop, inventory move, death, disconnect, teleport, and restart cannot clone it.
  Recovery returns an unserved entitlement to the serving counter or reconciliation.

#### Serving the correct table

- Interact with the table's service point while carrying its order-bound dish.
- Validate restaurant, table, order, recipe, order state, and entitlement state.
- A wrong table/dish is rejected with clear feedback and does not consume the item.
- A valid handoff atomically changes the entitlement to `CONSUMED` and the order to
  `SERVED`.
- An ItemDisplay may show food on the table during the eating animation.

#### Eating and payment

- Eating is a short timer/animation and requires no repeated clicks.
- Settlement uses the frozen base price and bounded service/event bonuses.
- The transaction changes the order to `SETTLED`, inserts one ledger row, and updates
  restaurant balance/revision exactly once.
- Early progression may put the settled amount into a logical register balance for
  manual collection; cashier/auto-deposit upgrades automate it.
- Walking to a payment point or exit is presentation only and can never create a
  second payout.

#### Cleaning and table turnover

- After departure, the table becomes `DIRTY` and cannot receive another party.
- Before a cleaner is hired, one interaction at its cleaning point resets it after a
  short duration. Cleaning staff automate it later.
- Cleaning is a capacity bottleneck, not a multi-step chore minigame.

```text
AVAILABLE -> RESERVED -> OCCUPIED -> DIRTY -> CLEANING -> AVAILABLE
```

Use separate queue, order-taking, and food-wait patience values so feedback identifies
the bottleneck. Customers may abandon before service, but server/pathfinding failures
use `TECHNICAL_FAILURE` and do not reduce rating. After a valid serve, payment/path
presentation failure cannot remove the earned result.

#### Automation progression

| Staff/upgrade | Automated responsibility |
|---|---|
| Host | Assign waiting customers to free tables |
| Waiter | Take orders; later deliver completed dishes |
| Prep cook | Run preparation/assembly jobs |
| Grill cook | Run grill/oven jobs |
| Cashier | Collect or auto-deposit settled payments |
| Cleaner | Return dirty tables to available state |

Staff are logical first. A visible staff villager is optional presentation and shares
the physical-entity budget; hiring six staff must not automatically spawn six
permanent pathfinding villagers. On unobserved plots, all staff/customer work is
bounded batch simulation.

#### MVP operation scope

The first playable prototype implements one customer at a time, one table, one simple
recipe, one grill, one serving counter, manual order taking/cooking/serving/payment/
cleaning, and one cashier upgrade. Increase to 4-6 observed customers only after this
loop is fun and recovery-safe. Multi-step recipes, waiter delivery, stock/restocking,
freshness, party sizes, and simultaneous station queues are later increments.

### Post-automation active loop

Automation replaces repetitive execution, not player decisions. After cook and
cashier are hired, the repeated loop becomes:

```text
Inspect the current bottleneck
  -> choose one 30-90 second intervention
  -> resolve a rush or special order
  -> receive a bounded temporary advantage
  -> spend toward the next visible construction goal
```

Every 4-7 minutes of active plot play, offer one optional intervention such as
prioritizing a queue, temporarily overclocking a station, completing a VIP route,
or choosing throughput versus order value. Ignoring it must not stop base income or
cause lasting damage.

## 5. Progression and Pacing

### First 15 minutes

| Time | Expected event |
|---:|---|
| 0-2 min | Claim plot, tutorial customer, first payout |
| 2-5 min | Buy first table and restaurant sign |
| 5-8 min | Buy kitchen station and second customer slot |
| 8-12 min | Hire first cashier or cook |
| 12-15 min | Unlock assisted cash collection and visible building shell |

The player should never wait more than about 60-90 seconds for the next meaningful
purchase during onboarding.

### First hour

- Upgrade from food stall to enclosed restaurant.
- Unlock 4-6 tables and 2-3 customer types.
- Hire a cashier and one cook.
- Move from manual register collection to full auto-deposit.
- Choose one branch: drive-through, dessert bar, or expanded dining.
- Finish with a major exterior upgrade that is visible from other plots.

### First prestige target

- A new solo player who understands the loop reaches first prestige after 150-210
  minutes of active play, normally across 2-4 sessions.
- The first hour completes one visually coherent restaurant tier.
- MVP first completion and first prestige are the same milestone.
- Co-op must not reduce the owner's first-prestige time below 120 minutes.
- Balance against observed P50 and P80 times, segmented by solo/co-op and
  active/automated play.

### Upgrade tree shape

Use a mostly linear trunk with small branches and frequent reconvergence:

```text
Starter Counter
  -> Seating
  -> Kitchen
  -> Cashier
  -> Dining Expansion
       -> Drive-Through
       -> Dessert Bar
       -> Patio
  -> Second Floor
  -> Full Automation
  -> Landmark Upgrade
  -> Prestige
```

Reveal only the next 3-5 purchase pads. Do not cover the plot with dozens of
holograms. Major construction uses floor pads; local equipment and staff use wall
buttons; settings and prestige confirmation can use inventory menus.

## 6. Purchase and Construction UX

### Purchase pads

Each pad displays:

- Upgrade name.
- Price.
- Functional effect.
- Affordable, unaffordable, locked, or purchased state.
- The prerequisite when locked.

Use deliberate interaction or a short hold for expensive purchases. Merely walking
over a pad must not accidentally spend currency.

### Build presentation

Each major purchase should create:

- A 1-4 second build animation.
- An immediate geometry change.
- Sound and restrained particles.
- At least one functional improvement.
- One or more newly revealed purchase opportunities.

The authored structure is saved as a stage or schematic. Plot state stores which
stages are unlocked; it does not store every block as business data.

### Cash collection phases

1. Manual register for the first 10-15 minutes.
2. Cashier transfers part of income automatically.
3. Full auto-deposit unlocks around 25-40 minutes.

Keep the register visually active after auto-deposit is unlocked, but do not force
players to repeatedly walk back to collect routine revenue.

## 7. Economy Design

### Currency sources

- Completed customer orders.
- Active service bonus.
- Rush-hour objective bonus.
- Achievement and milestone rewards.
- Small co-op completion bonus.

### Currency sinks

- Purchase pads and construction stages.
- Equipment and staff hiring.
- Staff upgrades.
- Theme licenses and prestige entry.
- Cosmetic furniture, signs, uniforms, and effects.
- Optional convenience such as plot relocation, not competitive power.

### Revenue model

For a completed order:

```text
base_revenue = base_order_value
             * station_multiplier
             * bounded_prestige_multiplier

revenue = base_revenue
        + capped_service_bonus
        + capped_event_bonus
```

Keep multiplier stacking bounded. Prefer additive or capped bonuses where possible.
Do not let prestige create exponential runaway that makes early stages irrelevant.

### Economy balance sheet

Before authoring 40-60 nodes, define for every progression band:

- Automated and active cash per minute.
- Orders per minute and median order cycle.
- Upgrade price and expected balance before purchase.
- Time to afford and payback time.
- Throughput bottleneck before and after the purchase.
- Solo, two-player, and four-player projections.

Initial hypotheses: onboarding purchases take 20-60 seconds to afford; first-session
minor purchases 60-150 seconds; landmark purchases 3-6 minutes with intermediate
goals; later minor purchases 2-5 minutes; chapter purchases 8-15 minutes with visible
subgoals. Throughput upgrades should usually repay within 10-20 minutes.

### Ledger rules

Money must never be represented only by physical Minecraft items. Every mutation is
an idempotent, server-authoritative ledger operation with:

- Transaction ID.
- Player/restaurant ID.
- Reason.
- Amount.
- Balance before and after, or a revision number.
- Timestamp.
- Related order/purchase ID.

An order may pay exactly once. A purchase uses an atomic conditional update so two
rapid interactions cannot buy or charge twice.

### Offline earnings

Do not include offline earnings in the first MVP. When added:

- Require an automation upgrade.
- Cap accumulation to 4-8 hours.
- Pay 20-35% of normal automated revenue.
- Compute from a saved timestamp, not simulated NPCs.
- Never spawn or tick offline restaurants.

### Restaurant simulation states

1. **Observed:** an owner, collaborator, or visitor is inside the plot. Physical
   customers run within the entity budget.
2. **Online-unobserved:** the owner is online but nobody observes the plot. No
   customer entities exist; bounded batches use the saved automated throughput.
3. **Offline:** the owner is disconnected. MVP revenue is zero.

Online-unobserved batches may produce base automated revenue, but no active bonus,
VIP, satisfaction, seasonal, leaderboard, or worker reward. Visitor/collaborator
presence cannot activate unbounded income for an offline owner.

## 8. Prestige

Prestige becomes available when the authored MVP restaurant is complete, with the
P50 target of 150-210 active minutes defined in Section 5.

Prestige resets:

- Current building stages.
- Local equipment and staff levels.
- Current cash.

Prestige retains:

- Cosmetics and achievements.
- Restaurant themes already mastered.
- Social identity and visit statistics.
- A modest permanent progression bonus.
- Access to new visual brands or branches.

Avoid a pure income-multiplier reset. Each prestige should unlock at least one new
theme, build route, customer category, or active event.

## 9. Multiplayer and Social Design

### Co-op

- The restaurant has one economic owner and up to three collaborators.
- Collaborators never own its balance, unlocks, or prestige.
- Worker actions cannot increase customer spawn rate beyond the restaurant's solo
  progression cap.
- Each order has one fixed co-op reward budget; splitting work divides it and never
  creates extra value.
- Workers receive capped, non-transferable participation rewards aimed at cosmetics,
  not progression cash transferable to another restaurant.
- Manager permissions are individually toggled. Prestige is owner-only.
- Role changes, removals, spending, refunds, and prestige record actor and permission
  revision.
- Four players should improve task coverage but not exceed approximately 1.25x the
  restaurant's solo economic throughput.

### Visits

- Lobby browser and `/restaurant visit <player>`.
- Visitors cannot interact with economic controls.
- Optional likes are cosmetic/social only.
- Do not convert votes directly into large currency payouts.

### Competition

Prefer seasonal categories over lifetime wealth:

- Fastest service chain.
- Most completed rushes.
- Best customer satisfaction.
- Most visited restaurant.
- Curated build showcase after free-build is introduced.

Do not sell competitive leaderboard advantages.

## 10. Technical Architecture

### Recommended stack

- Server: one exact Paper build, pinned after the compatibility spike.
- Candidate baselines must not be mixed: Minecraft/Paper 1.20-1.21.11 uses Java 21;
  Paper 26.1+ uses Java 25. As of the research date, current Paper documentation
  demonstrates 26.2 and `api-version: '26.2'`.
- Release builds use an exact `paper-api` artifact, never a dynamic `+` version.
- Record Paper build, API build, Java, Gradle wrapper, resource-pack format, and all
  plugin versions/hashes in a compatibility matrix.
- Build: Gradle Kotlin DSL, following Paper's supported setup.
- UI text: Adventure Components.
- Persistence: SQLite only for local Phase 0 development. PostgreSQL is required for
  production validation and deployment, even with one Paper process.
- Connection pool: HikariCP for PostgreSQL.
- Migrations: Flyway or a small versioned migration runner.
- Profiling: Paper's bundled spark.
- Protection: custom rectangular plot protection for the tycoon world.
- Resource pack: custom model/item assets, kept optional during the first mechanics
  prototype and required once art production begins.

### Dependency policy

Start with the smallest dependency surface:

- Do not use Citizens for ordinary customers. Use one pinned vanilla `Villager`
  configuration on observed plots; Citizens is not a gameplay dependency.
- No Vault unless the project must share economy with unrelated plugins.
- No WorldGuard for core plot logic. Add a bridge only for concrete admin needs.
- No ItemsAdder/Oraxen until the team chooses one content pipeline and verifies its
  license, update cadence, pack workflow, and exact server compatibility.
- CoreProtect can be integrated for staff audit/rollback, but it is not the source
  of truth for restaurant progression.

### Logical modules

```text
restauranttycoon
  bootstrap       plugin lifecycle and dependency checks
  plot            allocation, ownership, bounds, reset, protection
  build           authored stages, purchase pads, construction playback
  economy         balances, ledger, prices, transactions
  customer        customer state machine, spawn budgets, recovery
  station         cooking jobs, recipes, outputs
  staff           automation and throughput modifiers
  progression     unlock graph, milestones, prestige
  item            tagged items and resource-pack identities
  ui              action bars, displays, inventory menus
  persistence     repositories, migrations, async write pipeline
  scheduler       global, location, entity, and async scheduling adapters
  analytics       gameplay events and operational metrics
  admin           inspection, recovery, grants, rollback-safe tools
```

Use a modular monolith, not microservices. Separate modules by behavior, but ship one
plugin until horizontal server scaling is actually required.

### Runtime model

- The database is authoritative for durable progression and money.
- Memory holds active restaurant aggregates and customer state.
- PDC identifies items, entities, displays, Interaction entities, and tile-state
  stations. Ordinary blocks cannot universally hold PDC; their controls use a
  world-UUID-plus-coordinate registry.
- PDC is not the sole database for balances or unlock history.
- World/entity access runs on the appropriate server thread.
- SQL, files, HTTP, and immutable calculations run asynchronously.
- Results return to the server/location/entity scheduler before changing the world.

### Paper versus Folia

Run Paper first. Still create scheduler interfaces for:

- Global work.
- Location/region work.
- Entity work.
- Async I/O.

Folia is not an MVP compatibility target. Scheduler interfaces use Paper's
Folia-compatible scheduler categories, but the plugin must not declare
`folia-supported: true` until a Folia suite passes. Every mutable runtime object has
one documented owner: global scheduler, one plot region, one entity, a database
transaction, or a thread-safe service. Cross-region workflows use immutable messages
and operation IDs; they do not synchronously mutate two regions.

## 11. Data Model

Minimum tables:

```text
players
  player_uuid PK
  created_at
  last_seen_at
  settings_json

restaurants
  restaurant_id PK
  owner_uuid
  name
  theme_id
  prestige_level
  balance_units BIGINT
  revision
  created_at
  updated_at

restaurant_members
  restaurant_id
  player_uuid
  role
  joined_at
  PRIMARY KEY (restaurant_id, player_uuid)

plot_assignments
  plot_id PK
  restaurant_id UNIQUE
  server_id
  fence_token
  assignment_revision
  assigned_at
  lease_expires_at NULL

unlocks
  restaurant_id
  unlock_id
  purchased_at
  price_paid_units BIGINT
  PRIMARY KEY (restaurant_id, unlock_id)

staff
  restaurant_id
  staff_type
  level
  enabled
  PRIMARY KEY (restaurant_id, staff_type)

economy_ledger
  transaction_id PK
  restaurant_id
  idempotency_key
  operation_type
  amount_units BIGINT
  balance_before_units BIGINT
  balance_after_units BIGINT
  revision_before
  revision_after
  related_id NULL
  actor_uuid NULL
  server_instance_id
  created_at
  UNIQUE (restaurant_id, idempotency_key)
  UNIQUE (restaurant_id, revision_after)

orders
  order_id PK
  restaurant_id
  run_id
  party_id
  table_id
  table_reservation_id
  recipe_id
  recipe_version
  state
  state_revision
  accepted_by_uuid NULL
  assignment_kind NULL
  assignment_revision
  active_step_index
  command_id NULL
  payout_units
  settlement_idempotency_key
  payout_transaction_id UNIQUE NULL
  created_at
  completed_at NULL
  UNIQUE (restaurant_id, settlement_idempotency_key)

purchases
  purchase_id PK
  restaurant_id
  unlock_id
  definition_version
  price_units
  state
  plot_id
  plot_fence_token
  idempotency_key
  UNIQUE (restaurant_id, unlock_id)
  UNIQUE (restaurant_id, idempotency_key)

world_operations
  operation_id PK
  operation_type
  purchase_id UNIQUE NULL
  plot_id
  required_fence_token
  target_stage_revision
  state
  phase
  attempt_count
  claimed_by_instance NULL
  claim_expires_at NULL
  last_error NULL
  created_at
  updated_at

plot_resets
  reset_id PK
  plot_id
  requested_by_uuid
  operation_id UNIQUE
  target_stage_revision
  state
  created_at
  completed_at NULL

prestige_history
  prestige_id PK
  restaurant_id
  old_level
  new_level
  created_at
```

Large volumes of transient customer positions do not belong in SQL. Persist the
business outcome, not every animation step.

## 12. NPC and Performance Budget

Minecraft targets 20 ticks per second; a tick over 50 ms means the server falls
behind. NPC pathfinding and loaded entity density are the main technical risks.

### Initial budgets to validate

| Target | Prototype budget |
|---|---:|
| Active physical customers per plot | 6 normal, 12 overload-test ceiling |
| Active customer entities server-wide | Lower dynamic cap proven by benchmark |
| Customer logic update | Every 10-20 ticks, staggered |
| Path recalculation | State transitions or watchdog, never every tick |
| Visible purchase displays per active plot | Under 20 |
| Forced-loaded inactive plots | 0 |
| Synchronous database/file calls | 0 |

These are test hypotheses. The real limits must come from spark profiles on the
production hardware class.

### NPC rules

- Ordinary physical customers are vanilla `Villager` entities.
- Pin one villager biome/type and adult size for MVP so hitbox, eye height, routes,
  sounds, and furniture alignment remain predictable.
- Remove or suppress all behavior not required by the restaurant: profession and
  workstation acquisition, trading, gossip/social activity, breeding, sleeping,
  panic, wandering, item pickup, restocking, village/POI search, damage, and drops.
- Do not rely on `setAware(false)` for moving customers; it may suppress behavior
  needed for navigation. Control goals through supported Paper APIs and benchmark
  the exact configured villager.
- Disable customer-to-customer collision unless congestion becomes a deliberate,
  tested mechanic. Use an `Interaction` entity only where clicking is required.
- Use fixed anchors for entrance, queue, table, kitchen handoff, register, and exit.
- Validate authored paths before a stage ships.
- Stagger customer updates by ID.
- Apply a global/per-plot path request budget.
- Despawn customers when a plot becomes inactive.
- Do not keep plot chunks loaded for offline income.
- Recover a stuck customer after a timeout and exclude technical failures from
  customer penalties.
- Prefer open arches or plugin-controlled doors on critical routes. Do not depend on
  villagers consistently opening and sharing doors in narrow traffic paths.
- MVP customers may stand at their seat anchor while eating. Sitting through a
  helper/passenger entity is optional and must pass the entity-budget benchmark.

### Customer representation decision

```text
Observed plot:
  4-6 optimized physical villagers
  + additional demand represented logically

Online-unobserved plot:
  0 customer entities
  + bounded batch simulation

Offline plot:
  0 customer entities
  + 0 revenue in MVP
```

Twelve villagers per plot remains an overload-test ceiling, not a production
allocation. Citizens player NPCs are excluded from ordinary customers and may only
be reconsidered later for a strictly bounded VIP/tutorial role after a separate
compatibility and performance test.

### Scaling forecast

Capacity is expressed as simultaneously active plots, ticking entities, path
requests, tracked entities per client, and loaded/entity-ticking chunks, not merely
online players. Plugin logic can be staggered, but vanilla mob AI still ticks. No
20/50/100-player claim is accepted until its representative distribution passes on
the production hardware class.

## 13. Plot and Protection Strategy

Use fixed axis-aligned plots in a dedicated world. A grid makes plot lookup constant
time and avoids scanning all regions on every event.

Protect at least:

- Block place/break.
- Liquids, pistons, explosions, fire, falling blocks, and growth.
- Containers, doors, buttons, stations, furniture, and displays.
- Entity damage and vehicle interactions.
- Boundary-crossing mechanics.
- Plugin/admin actions that bypass ordinary player events.

For MVP, players purchase authored stages and cannot freely alter the restaurant.
This is a product choice as much as a technical choice: it protects navigation and
lets the team prove the tycoon loop first.

Free-build can be a later sandbox layer with validated walkways and station/table
anchors. It should not block MVP release.

### Plot reset protocol

Plot reset is a durable, fenced state machine:

```text
REQUESTED -> QUIESCING -> CLEARING -> RESTORING -> RECONCILING -> COMPLETE
```

Stop purchases and simulation, evacuate players, cancel entity work, remove
plugin-owned entities, clear the full plot volume including air and block entities,
place the canonical base, replay committed stages, reconstruct controls/displays,
validate, then reopen. Every batch verifies the current assignment and fence token.
Temporary chunk tickets are removed on every success or failure path. Restart safely
resumes or repeats an idempotent phase.

## 14. Items, Cooking, and Visuals

### Item identity

Use namespaced PDC keys such as:

```text
restauranttycoon:item_id
restauranttycoon:item_version
restauranttycoon:order_id
restauranttycoon:owner_restaurant_id
```

Never trust display name, lore, model number, or material alone. Revalidate custom
items at every economic boundary.

### Cooking job states

```text
RESERVED -> RUNNING -> READY -> CLAIMED
                    -> CANCELLED
```

Use absolute finish timestamps for longer jobs. Inputs are atomically reserved or
consumed before a job starts, and an output can be claimed once.

### Visual implementation

- `ItemDisplay` for dishes and utensils.
- `BlockDisplay` for animated decorative construction.
- `TextDisplay` for pad labels and status.
- `Interaction` entities for entity-backed hitboxes; ordinary block controls resolve
  through the server-known control registry. Only tile-state blocks have block-local
  PDC.
- Custom `InventoryHolder` for plugin menus; never identify menus by title alone.

Display entities have no default hitbox, movement, sound, or damage behavior, which
makes them suitable for presentation but not as customer simulation entities.

## 15. Anti-Exploit Requirements

### Economy

- Idempotency key for every payout and purchase.
- Atomic balance check and deduction.
- Unique constraint tying one order to one payout.
- No balance writes from asynchronous callbacks without revision checks.
- Audit log for grants, refunds, prestige, and staff actions.
- Use signed 64-bit integer currency units, never floating point.
- Balance update, immutable ledger row, related purchase/order state, and revision
  update commit in one database transaction.
- Durable commands are acknowledged only after SQL commit. Write-behind is limited
  to analytics and other loss-tolerant telemetry.

### Inventory

- Handle click, drag, shift-click, number-key swap, double-click, close, death,
  teleport, disconnect, and plugin disable.
- Server issues dishes for a specific order.
- A dish from another restaurant or expired order cannot be served.
- Reconcile incomplete station jobs after restart.

### World

- Plot ownership checked for every purchase control.
- Visitors cannot activate economic controls.
- Buttons/pads have server-known IDs; location alone is insufficient after reset.
- Plot reset is idempotent and cannot overlap an active purchase/build operation.

### AFK and alts

- Automation may produce bounded base revenue without repeated clicks.
- AFK time earns no rush, VIP, satisfaction, seasonal, co-op, or leaderboard credit.
- Repetition, held controls, macros, and movement loops do not qualify as activity.
- Co-op cannot increase the fixed reward budget of an order.
- Offline earnings, when introduced, are capped and discounted.
- Do not use simplistic movement-only AFK checks as the economic security boundary.

## 16. Monetization Guardrails

If the server is monetized, re-check the current Minecraft Usage Guidelines before
launch and before changing the store. The current guidelines permit some server
monetization but prohibit gameplay entitlements that damage others' experience or
create a competitive advantage.

Safer products:

- Restaurant skins and themes with equivalent function.
- Cosmetic staff uniforms.
- Sign, particle, sound, and title cosmetics.
- Extra cosmetic save slots.
- Server-wide rewards funded by donation goals.

Avoid:

- Permanent paid income multipliers on competitive leaderboards.
- Paid queue priority that harms normal players.
- Paid prestige power.
- Cash-out, transferable real-world value, or play-to-earn systems.
- Paid randomized rewards.

## 17. Delivery Roadmap

Assumption: one experienced Paper developer, one builder/3D-resource artist, and a
part-time designer/QA. Estimates are ranges, not commitments.

### Phase 0: Technical and fun prototype, 2-3 weeks

Build only:

- One plot.
- One claim flow.
- Five purchase pads.
- Three staged construction upgrades.
- One customer route.
- One manual service interaction.
- One cashier automation upgrade.
- Persistent balance and unlocks.
- Basic admin reset command.

Prototype questions:

1. Is the first purchase reached in under 3 minutes without confusion?
2. Does every purchase feel visibly worthwhile?
3. Is watching customers generate money understandable?
4. Is manual service fun enough to repeat for 10 minutes?
5. Does automation feel rewarding rather than removing all play?
6. Can 100-150 representative customer entities run within the tick budget?

Go/no-go gate: do not expand content until both the gameplay loop and representative
NPC load pass.

### Phase 1: Vertical slice, 3-5 weeks

- 30-45 minutes of tuned progression.
- One complete restaurant theme.
- 15-25 purchase nodes.
- Cook and cashier staff.
- 2-3 active jobs.
- 2 customer categories.
- Plot protection and clean reset.
- Resource-pack visual slice.
- Economy ledger, migrations, restart recovery.
- Tutorial and end-of-run summary.

### Phase 2: MVP alpha, 4-6 weeks

- 150-210 minutes P50 active play to first completion and first prestige.
- 40-60 purchase nodes.
- 2 progression branches.
- 2-4 player co-op permissions.
- Visits and basic social browser.
- First prestige loop.
- Admin inspection/recovery tools.
- Analytics and structured logs.
- Load test for 20 and 50-player scenarios.
- Backup and restore rehearsal.

### Phase 3: Closed beta, 3-5 weeks

- Economy and pacing tuning.
- Second visual theme or prestige route.
- Seasonal objectives without pay-to-win rewards.
- Exploit testing and restart/rollback tests.
- Resource-pack failure UX.
- Accessibility and Vietnamese/English text review if bilingual launch is planned.
- Operational runbook and moderation tools.

### Phase 4: Production hardening, 4-7 weeks

- Privacy, moderation, branding, asset/license, and launch-scope review.
- Deployment/canary/rollback automation and production observability.
- Coordinated backup, restore, reconciliation, RPO, and RTO drill.
- Long soak, reconnect storm, degraded-database, and crash-injection tests.
- Store/payment work only if monetization is part of launch.

### Expected MVP duration

- Mechanics-complete internal alpha may fit the original 12-19 week sum.
- Java-only production beta for the stated small team: roughly 16-26 weeks, assuming
  existing hosting and no payment store at first release.
- Solo developer without existing assets: roughly 6-10 months.
- Geyser/Floodgate plus a separate Bedrock pack adds roughly 4-8 weeks and ongoing
  cross-platform QA.
- Free-form building, polished human NPCs, Bedrock parity, or 100-player scale can
  add substantial time and should be separate milestones.

## 18. Test Plan and Acceptance Gates

### Automated tests

- Unlock prerequisite and price rules.
- Balance and ledger invariants.
- Duplicate purchase and duplicate payout rejection.
- Customer state transitions and timeouts.
- Prestige reset/retention rules.
- Offline earning cap when introduced.
- Repository migration and restart recovery.

### Integration tests

- Rapid pad interaction from two collaborators.
- Disconnect during cooking, purchase, construction, and payout.
- Server stop/restart with active customers and jobs.
- Plot release and reassignment without leaked entities/data.
- Resource-pack refusal or download failure.
- Visitor and permission bypass attempts.
- Database outage and delayed write behavior.

### Performance tests

Use spark on the intended hardware class with representative builds and entities:

1. 20 active plots at realistic NPC counts.
2. Burst of simultaneous customer spawns and path requests.
3. Simultaneous construction purchases.
4. Plot unload/reload churn.
5. 50-player mixed workload.
6. Ten-minute and one-hour soak tests.

Initial acceptance targets:

- Define MSPT median, p95, p99 and max; normal-operation p95 must remain below the
  chosen budget and below the 50 ms tick ceiling.
- No routine main-thread SQL/file I/O.
- No unbounded task, entity, display, or chunk growth.
- No duplicated balance across restart and reconnect tests.
- Customer recovery prevents permanent blocked tables.
- No leaked chunk ticket, entity handle, scheduler task, or world-operation lease.
- Repeat the same scenarios after interruption at every purchase/reset phase.

### Product metrics

- Tutorial completion rate.
- Time to first and third purchase.
- Percentage reaching first staff hire.
- Session length and return rate.
- Active service participation after automation.
- Where players stop in the purchase tree.
- Currency earned/spent per progression band.
- Pathfinding recovery and abandoned-order rate.
- Tick time by active plot and NPC count.

Do not tune solely for session length. Look for confusion, waiting, and repetitive
actions that do not create decisions.

## 19. Major Risks and Mitigations

| Risk | Severity | Mitigation |
|---|---:|---|
| NPC pathfinding overload | High | Fixed anchors, budgets, staggered updates, active plots only |
| Gameplay becomes AFK waiting | High | Optional decisions; 15-25% target and 30% hard active-value cap |
| Automation removes all gameplay | High | Rush events, special orders, expansion decisions, social goals |
| Arbitrary builds break routing | High | Authored MVP stages; validate paths before later free-build |
| Economy duplication | High | Ledger, idempotency, atomic transactions, restart tests |
| Too many external plugins | Medium-high | Minimal dependency policy and exact-version compatibility matrix |
| Resource pack reduces joins | Medium | Small optimized pack, clear status UX, vanilla prototype first |
| Exponential progression becomes meaningless | Medium | Bounded multipliers and new content per prestige |
| Content production dominates schedule | Medium | One polished theme first; reusable stage tooling |
| Folia adds concurrency bugs | Medium | Paper launch; scheduler abstraction; dedicated later validation |
| Bedrock/Geyser UX mismatch | Medium | Treat Bedrock as separate validation target, not assumed parity |

## 20. Explicit Non-Goals for MVP

- Fully free-form building.
- Realistic ingredient farming and supply chains.
- Global auction house or player-to-player cash trading.
- Deep employee personalities or schedules.
- Hundreds of recipes.
- Citizens/player-skin customers.
- Multi-server economy synchronization.
- Offline simulated NPCs.
- Mobile/Bedrock feature parity.
- 100 concurrent player guarantee.

## 21. Immediate Next Actions

1. Confirm the target Minecraft version, expected concurrent players, language,
   resource-pack requirement, and whether Bedrock/Geyser support is required.
2. Greybox one complete plot and author five upgrade stages.
3. Implement the Phase 0 prototype without Citizens, Vault, or free-build.
4. Playtest the first 15 minutes with at least five people unfamiliar with the
   design.
5. Run the representative NPC spike before producing a full set of restaurant art.
6. Lock the content pipeline only after comparing vanilla custom models, Oraxen,
   and ItemsAdder against the team's asset workflow and licensing needs.

## 22. Research Sources

### Paper and Minecraft APIs

- Paper project setup: https://docs.papermc.io/paper/dev/project-setup/
- Paper scheduling and thread-safety warning:
  https://docs.papermc.io/paper/dev/scheduler/
- Paper/Folia scheduler model:
  https://docs.papermc.io/paper/dev/folia-support/
- Folia region architecture:
  https://docs.papermc.io/folia/reference/overview/
- Paper Entity Pathfinder API:
  https://docs.papermc.io/paper/dev/entity-pathfinder/
- Paper display entities:
  https://docs.papermc.io/paper/dev/display-entities/
- Paper Persistent Data Container:
  https://docs.papermc.io/paper/dev/pdc/
- Paper custom inventory holders:
  https://docs.papermc.io/paper/dev/custom-inventory-holder/
- Paper recipes: https://docs.papermc.io/paper/dev/recipes/
- Paper database guidance: https://docs.papermc.io/paper/dev/using-databases/
- Paper profiling with spark: https://docs.papermc.io/paper/profiling/

### Optional ecosystem integrations

- Citizens API: https://wiki.citizensnpcs.co/API
- WorldGuard region API:
  https://worldguard.enginehub.org/en/latest/developer/regions/
- CoreProtect API: https://docs.coreprotect.net/api/
- Oraxen API: https://docs.oraxen.com/developers/api
- ItemsAdder Java API: https://itemsadder.devs.beer/developers/java-api
- Vault API: https://github.com/MilkBowl/VaultAPI

### Platform, policy, and cross-play

- Minecraft Usage Guidelines:
  https://www.minecraft.net/en-us/usage-guidelines
- Minecraft EULA: https://www.minecraft.net/en-us/eula
- Geyser current limitations:
  https://geysermc.org/wiki/geyser/current-limitations/

### Evidence note

Roblox experience web pages were not reliably retrievable during this research, so
this plan does not use unverifiable popularity or ranking claims. Roblox-style
conventions are used as product patterns: claimable plots, physical purchase pads,
staged construction, visible production, automation, social visits, and prestige.
The resulting design is original and adapted to Minecraft's controls, entity model,
server performance constraints, and platform rules.

## 23. Audit Addendum: Binding Production Requirements

This section records issues discovered during the second review. These requirements
are release gates, not optional future polish.

### 23.1 Database and world commit contract

A database transaction cannot atomically modify Minecraft blocks or inventories.
Therefore:

1. The database is the authoritative decision.
2. A purchase transaction atomically charges cash, inserts the unlock, snapshots
   price/definition, and creates a `world_operations` record.
3. Only after commit does the server acknowledge durable economic success.
4. The world is a replayable projection of committed stages.
5. A world-operation worker verifies plot assignment and fence token, applies the
   deterministic target stage, validates it, and marks it applied.
6. A crash after payment but before construction produces repair work, not an
   automatic refund race.

Purchase projection states:

```text
REQUESTED -> COMMITTED -> APPLYING -> APPLIED
                    |          |
                    +-> REPAIR_REQUIRED -> APPLYING
REQUESTED -> REJECTED
REPAIR_REQUIRED -> CANCELLED_BY_ADMIN
```

Relative edits such as "add these blocks" are insufficient for recovery. Retrying
must converge on a canonical target stage. Spawned entities carry operation ID,
plot ID, and stage revision for reconciliation.

### 23.2 Order settlement contract

NPC animation and economic settlement are separate:

```text
OPEN -> ACCEPTED -> PREPARING -> READY -> SERVED
SERVED -> SETTLEMENT_PENDING -> SETTLED -> CLOSED

OPEN/ACCEPTED/PREPARING/READY -> CANCELLED
OPEN/ACCEPTED/PREPARING/READY -> TECHNICAL_FAILURE
SERVED/SETTLEMENT_PENDING -> retry settlement with the same idempotency key
```

Only `SETTLED` means currency committed. Leaving or despawning is not a payment
state. Recipe version, base value, multiplier snapshot, final payout, and settlement
idempotency key are frozen before settlement. Order state, ledger row, restaurant
balance, and payout reference update in one SQL transaction.

### 23.3 Entity lifecycle contract

Logical customers and physical mobs are different objects. Chunk unload is not
despawn. On plot deactivation:

- Mark simulation inactive.
- Retire/cancel customer work.
- Explicitly remove plugin-owned physical entities.
- Clear entity handles from memory.

On entity/chunk load, PDC is used only to identify and reconcile remnants. Stale
entities are removed; payouts are never reconstructed from entity PDC. On restart,
durable order/job records decide recovery before any customers are recreated.

The prototype pins one `Mob` type, disables conflicting goals through supported
APIs, and treats `Pathfinder#moveTo` as a request rather than arrival. Failure covers
rejection, no progress, no path, timeout, invalid destination, entity retirement,
and chunk inactivity.

### 23.4 Resource-pack lifecycle

The resource pack is a versioned deployment artifact with:

- Immutable HTTPS URL.
- Content hash and pack UUID/version.
- Minecraft pack format and minimum client/server version.
- Asset provenance manifest and license records.
- CDN bandwidth/cache plan.
- Staging validation before server rollout.

Track client states including accepted, downloaded, successfully loaded, declined,
failed download, invalid URL, failed reload, and discarded. If the pack is required,
gate entry to resource-dependent plots until `SUCCESSFULLY_LOADED`; provide a clear
retry/help path rather than allowing invisible controls. Decide separately whether a
vanilla fallback exists. Geyser does not automatically create Bedrock asset parity;
a Bedrock pack and mapping are separate deliverables.

### 23.5 Schematic/content decision gate

Choose one stage mechanism in Phase 0:

- Bukkit structures for minimal dependency and suitable authored volumes.
- WorldEdit API when `.schem`, masks, transforms, and builder tooling justify the
  dependency.

Whichever is chosen must define air, block entities, inventories, biomes, entities,
transforms, and edit budgets. WorldEdit edit sessions must be closed, and undo
history is not durable reset state. Test reset with containers, fluids, redstone,
displays, Interaction entities, vehicles, passengers, hanging entities, players,
and blocks where the base stage contains air.

### 23.6 Degraded-mode policy

When the database is unavailable:

- Reject new purchases, payouts, prestige, memberships, and item entitlements.
- Never fall back to memory-only economic mutations.
- Continue retrying already committed world operations with bounded leases.
- Apply queue limits and backpressure.
- Expose a clear read-only/degraded message to players and operators.
- Do not hold an SQL transaction open while waiting for a server-thread callback.

### 23.7 Backup and disaster recovery

Before production, define:

- RPO and RTO.
- Backup frequency and retention.
- Encrypted off-site or immutable copies.
- Owners for backup, restore, and reconciliation.
- Periodic restore drills.

Recovery covers the database, worlds, exact plugin/Paper artifacts, configuration,
resource packs, schematics/stages, and version manifests. Database and world backups
from unrelated points in time cannot be restored blindly; replay committed stages
from the authoritative database and run balance/unlock/entity reconciliation.
PostgreSQL production should evaluate WAL/PITR, not rely only on occasional dumps.

### 23.8 Deployment and rollback

- Produce immutable checksummed releases and a dependency/license manifest.
- Test migrations and content versions on production-equivalent staging.
- Snapshot before risky Paper, plugin, schema, or world upgrades.
- Never replace JAR files on a running server and never auto-update production
  without observation.
- Use canary/limited-player rollout with health gates.
- Distinguish binary rollback, configuration rollback, data restore, and forward fix.
- Do not downgrade a world/schema when the migration is not backward compatible.

### 23.9 Observability

Spark profiling is necessary but not sufficient. Monitor and alert on:

- Availability, joins, MSPT percentiles, stalls, CPU, heap, GC, disk, and network.
- Entity/display/Interaction counts, chunks, path requests, and chunk tickets.
- Database pool, query latency, conflicts, transaction failures, and queue depth.
- World-operation/reset backlog and retries.
- Backup freshness and resource-pack delivery status.
- Economy reconciliation failures and anomalous grants/payouts.

Use correlation IDs for purchases, orders, admin mutations, resets, and recovery.
Define log redaction, access, retention, incident severity, and on-call ownership.

### 23.10 Privacy, moderation, and player-generated content

Before any public alpha that stores names, social relationships, analytics, chat, or
build content:

- Publish privacy notice, operator identity, contact, retention, and deletion/export
  workflows appropriate to launch jurisdictions and the likely minor audience.
- Minimize and pseudonymize analytics; separate gameplay telemetry, operations logs,
  moderation evidence, and payment records.
- Publish community rules and provide report, mute/block, evidence, enforcement,
  appeal, and urgent child-safety escalation procedures.
- Filter restaurant names/signs with human review and keep moderator actions audited.
- Define an IP complaint/takedown path and asset/UGC license terms.

Obtain jurisdiction-specific legal review rather than assuming one privacy regime.

### 23.11 Platform and monetization compliance

Before monetization, re-review the then-current EULA and Usage Guidelines and record
the reviewer/date. At minimum:

- Use authenticated, legitimately purchased Minecraft access; do not operate an
  offline/cracked public server.
- Use an original public product name; `Minecraft` must not dominate branding.
- Publish the required unofficial-service disclaimer and a direct operator contact.
- Publish pricing/content before sign-in where required and retain online purchase
  history for real-money buyers.
- Do not hide Minecraft warnings, EULA, or resource-pack dialogs.
- Keep content and advertising suitable for all ages.
- Do not sell competitive advantage, cash-out value, cape-like cosmetics, or paid
  random rewards.
- Require sponsored influencers to disclose their relationship.

### 23.12 Third-party and asset acceptance

For Paper, WorldEdit/WorldGuard, Citizens, CoreProtect, Geyser/Floodgate, Oraxen,
ItemsAdder, and every asset, record exact version/hash, source, license/terms,
commercial and redistribution rights, supported Minecraft/Java versions, network
behavior, data access, update risk, owner, and replacement path. Do not shade,
modify, or redistribute a dependency until its obligations are reviewed.

Every texture, model, font, sound, music track, icon, skin, schematic, and commissioned
build needs provenance and written rights sufficient for server delivery and
commercial use. Do not use real restaurant brands or third-party packs without
clearance.

### 23.13 Revised release gates

The project is technically feasible, but public production is **conditional**, not
unconditionally high-feasibility. Required gates are:

1. First-loop fun test passes with unfamiliar players.
2. Economy balance sheet predicts and observed playtests meet P50/P80 pacing.
3. NPC workload passes realistic MSPT percentile and soak tests.
4. Crash injection proves purchase, settlement, reset, and inventory recovery.
5. Resource-pack delivery/failure UX passes on supported clients.
6. Restore drill meets RPO/RTO and reconciliation finds no duplication.
7. Privacy, moderation, licensing, branding, and store reviews are complete for the
   chosen launch scope.

## 24. Restaurant Runtime Pre-Mortem: Binding Corrections

The service flow introduces additional durable objects and races. This section
overrides any earlier simplified state machine or schema that cannot represent them.

### 24.1 Safe MVP deployment boundary

Phase 0 through the first production validation uses:

- One tycoon Paper process.
- One PostgreSQL primary for production; SQLite only for the local prototype.
- Statically owned world directories loaded by exactly one Paper process.
- No runtime movement of an active restaurant between worlds or Paper instances.
- All collaborators and visitors of a restaurant join its owning Paper process.

Multiple worlds in one Paper JVM provide organization, not independent tick CPU.
Never mount one writable Minecraft world directory in two Paper processes. Velocity
sharding is introduced only with restaurant execution/session fencing and a transfer
protocol; adding `server_id` columns alone is not multi-instance safety.

### 24.2 Restaurant run aggregate

Every open-to-close operating period has a durable `run_id` and monotonically
increasing `operation_epoch`. The following records are required before crash-safe
multi-order operation:

```text
restaurant_runs
  run_id PK
  restaurant_id
  operation_epoch
  admission_state
  simulation_state
  started_at
  quiesced_at NULL

customer_parties
  party_id PK
  restaurant_id
  run_id
  state
  state_revision
  table_id NULL
  logical_deadline NULL
  physical_generation

restaurant_tables
  restaurant_id
  run_id
  table_id
  state
  capacity
  reservation_id NULL
  state_revision
  PRIMARY KEY (restaurant_id, run_id, table_id)

table_reservations
  reservation_id PK
  restaurant_id
  run_id
  table_id
  party_id UNIQUE
  state
  expires_at
  revision
  created_at
  updated_at

stock_balances
  restaurant_id
  ingredient_id
  available_units BIGINT CHECK >= 0
  reserved_units BIGINT CHECK >= 0
  revision
  created_at
  updated_at
  PRIMARY KEY (restaurant_id, ingredient_id)

stock_reservations
  reservation_id PK
  restaurant_id
  run_id
  order_id
  ingredient_id
  units BIGINT
  state
  state_revision
  created_at
  updated_at
  UNIQUE (order_id, ingredient_id)

station_jobs
  job_id PK
  restaurant_id
  run_id
  order_id
  recipe_step_index
  station_id
  state
  started_at NULL
  finishes_at NULL
  definition_version
  operation_epoch
  state_revision
  created_at
  updated_at
  UNIQUE (order_id, recipe_step_index)

dish_entitlements
  entitlement_id PK
  restaurant_id
  run_id
  order_id UNIQUE
  state
  carrier_uuid NULL
  custody_revision
  recipe_id
  recipe_version
  created_at
  updated_at

cleaning_jobs
  cleaning_job_id PK
  restaurant_id
  run_id
  table_id
  state
  state_revision
  finishes_at NULL
  created_at
  updated_at
  UNIQUE (restaurant_id, run_id, table_id)
```

All run-scoped durable child records carry restaurant/run identity, revision, and
timestamps. Restaurant stock deliberately survives open/close runs and is keyed by
restaurant plus ingredient; close/open never creates or refreshes stock.
No recovery path may guess whether stock was consumed, a step completed, or a dish
exists.

Prototype simplification: stock is infinite logical stock and no stock tables are
needed until restocking becomes a feature. Do not display stock or reject orders for
missing stock before its transaction model exists.

### 24.3 Corrected coordinated state machines

Customer party:

```text
WAITING_HOST -> TABLE_RESERVED -> WALKING_TO_TABLE -> SEATED
  -> WAITING_ORDER -> WAITING_FOOD -> EATING -> DEPARTING -> DONE

Any pre-serve state -> ABANDONING -> DEPARTING -> DONE
Any physical state -> TECHNICAL_RECOVERY -> previous logical state | DEPARTING | DONE
```

Table:

```text
AVAILABLE -> RESERVED -> OCCUPIED -> DIRTY -> CLEANING -> AVAILABLE
RESERVED -> RELEASING -> AVAILABLE
OCCUPIED -> RELEASING -> DIRTY | AVAILABLE
```

`EATING` is not a table state. The table stays `OCCUPIED` while its party eats.

Order:

```text
CREATED -> TAKEN -> ACCEPTED -> PREPARING -> READY_AT_COUNTER
  -> IN_TRANSIT -> SERVED -> SETTLEMENT_PENDING -> SETTLED -> CLOSED

Any pre-serve state -> CANCELLING -> CANCELLED
```

`CANCELLING` must resolve station jobs, stock reservation, entitlement custody,
party departure, and table turnover before becoming terminal.

Stock reservation when introduced:

```text
PENDING -> RESERVED -> CONSUMED
PENDING/RESERVED -> RELEASED | CANCELLED
```

Consumed stock is not silently refunded; correction requires an explicit audited
compensation mutation.

Dish entitlement:

```text
PENDING -> READY_AT_COUNTER -> CLAIMED -> CONSUMED
CLAIMED -> RECOVERING -> READY_AT_COUNTER
Any invalid/stale representation -> REVOKED
```

Physical item presence alone never changes entitlement state.

### 24.4 Atomic reservation and single-winner rules

- Table claim is compare-and-set from `AVAILABLE` with expected table revision.
- Arrival and release include the exact `reservation_id`; a late callback cannot
  release a later reservation.
- Only one active reservation may exist per table and party.
- Enforce one active reservation per `(restaurant_id, run_id, table_id)` at the
  database level. If history is retained, use a status-aware partial unique index on
  PostgreSQL or an explicit active-slot claim row.
- Order acceptance is compare-and-set and records `accepted_by_uuid`, assignment
  kind (`MANUAL` or `AUTOMATION`), assignment revision, active step, and command ID.
- Manual host versus automated host and player versus cook use the same single-winner
  command path.
- A station slot and recipe step are claimed atomically before inputs are consumed.
- One order has at most one job per step and one dish entitlement.
- Losing collaborators receive a deterministic "already claimed" or "station full"
  result and perform no inventory/world mutation.

### 24.5 Restaurant admission and owner departure

Admission is independent from observed/unobserved simulation:

```text
CLOSED -> OPEN -> CLOSING -> CLOSED
```

- `CLOSING` stops new customers immediately.
- Existing served orders settle exactly once.
- Pre-serve work either drains or enters the documented cancellation workflow.
- Close/open cycling cannot reset patience, reroll orders, refresh stock, or create
  payouts.
- Prestige, reset, theme change, and plot release require `CLOSED` plus no unresolved
  order, entitlement, settlement, purchase, or world operation.

MVP owner-disconnect command atomically changes `OPEN -> CLOSING`, stops new admission
and active bonuses, and records a drain deadline. Already served orders settle;
automatic running jobs may finish before the deadline. Manual work waiting for the
owner, including a claimed dish in the owner's custody, enters recovery/cancellation.
At the deadline every remaining pre-serve workflow must be terminal or retryable
recovery, then the run becomes `CLOSED`. Collaborators may finish already committed
work only if their permission/session remains valid; they cannot create new owner
income. Reconnect resumes the closing run or starts a new run after reconciliation.

### 24.6 Observation-mode conversion

Use hysteresis so stepping across a plot boundary does not repeatedly materialize
and remove entities. Conversion between observed and online-unobserved preserves the
same `run_id`, orders, deadlines, parties, and payouts.

- Physical villagers are projections of logical parties.
- Batch mode cannot also advance a manual order being actively handled.
- Visitors cannot toggle a more profitable simulation mode.
- Re-observation recreates at most one current-generation entity per active party.
- All delayed callbacks include run ID, operation epoch, reservation/command ID, and
  physical generation; stale contexts update nothing.

### 24.7 Protected dish inventory contract

Prototype policy: a player may carry at most one active dish entitlement; protected
dishes are unstackable, cannot become ordinary ground items, and cannot enter foreign
inventories, crafting grids, equipment, offhand, bundles, merchants, furnaces,
hoppers, or other containers.

The custody scope is the owning restaurant run on its owning Paper process. Leaving
that plot/world/process, losing membership/session authority, or closing the run
requires entitlement recovery. Enforce at most one active `CLAIMED` entitlement per
carrier with a PostgreSQL partial unique index (or equivalent custody-slot row), not
only an application check.

Cover at least:

- Inventory click, drag, close, creative, hotbar-number and offhand swap.
- Player drop/attempted pickup and entity/hopper pickup.
- Item merge.
- Death, retained items, respawn, and post-respawn.
- Quit, join, teleport, completed world change, plugin disable, and restart.
- Plugin/command mutations that may emit no cancellable player event.

Event raised does not mean action completed. Enforce protected movement at an
appropriate high priority, observe final cancellation without mutation at monitor,
then verify actual inventory/entity state on the next valid server/entity tick.
Never return an entitlement to the counter merely because a drop, death, or teleport
event fired.

Inventory-full claim fails without changing `READY_AT_COUNTER` and never drops the
dish into the world. On death or successful scope exit, reconciliation invalidates
the physical view and returns the same entitlement exactly once. Join/post-respawn
reconciliation occurs before normal restaurant interaction.

Do not open/close another inventory directly inside click/drag/close handlers; defer
view changes to the next tick. Do not use deprecated cursor mutation as the custody
commit mechanism.

### 24.8 Villager event and lifecycle policy

In addition to goal removal, protect customer villagers from:

- Damage, death drops/XP, combustion, targeting, item pickup, breeding, profession
  change, trading, zombification/transformation, portals, vehicles, leashes,
  passengers, void removal, and unrelated plugin interaction.

Pin villager profession/type through the registries of the selected Paper version;
do not rely on them being Java enums.

Entity lifecycle callbacks are idempotent:

- `EntitiesUnloadEvent`: detach handles and entity-local tasks; do not treat as
  logical death.
- `EntityRemoveEvent`: monitor removal cause only; never mutate the entity there.
- Entity load/add: reconcile PDC identity, run/physical generation, and stage before
  accepting the entity.
- Mark intentional removal before `Entity#remove()` so its callback cannot trigger a
  second recovery.
- Reapply the exact villager goal/configuration profile whenever an entity is loaded
  or recreated; runtime goals are not durable business state.

Interaction hitboxes process the intended hand only, deduplicate through command ID,
and revalidate plot fence, stage revision, permission, and operation epoch. Attack
events against plugin Interaction entities are separately handled.

### 24.9 Queue, capacity, and obstruction rules

- One atomic admission budget covers entering, queued, reserved, and seated parties.
- When all tables are dirty or the queue is full, do not spawn additional villagers;
  keep demand logical or reject it before materialization.
- Station-full rejection does not consume stock or leave an accepted order impossible.
- Restored committed jobs take precedence over new jobs if configuration capacity
  was reduced.
- Customers and visitors cannot physically determine economic success. Disable or
  neutralize collision/vehicles/obstructions that let visitors block service points
  or villager routes. Persistent obstruction triggers technical recovery without
  rating or payout loss.
- Excessive server stalls extend relevant patience or classify the outcome as
  technical failure; players are not punished for input the server could not process.

### 24.10 Tutorial state machine

The tutorial persists semantic milestones, not GUI clicks:

```text
CLAIM_PLOT -> SEAT_CUSTOMER -> TAKE_ORDER -> START_GRILL
  -> CLAIM_DISH -> SERVE_DISH -> COLLECT_PAYMENT -> CLEAN_TABLE
  -> BUY_FIRST_UPGRADE -> COMPLETE
```

Each step is idempotent and resumable after quit, death, resource-pack failure,
reset, missing customer, path failure, closed GUI, or full inventory. Provide a
visible retry/repair action and an audited reset/skip path that cannot award the
first payout twice.

### 24.11 Multi-instance future contract

Do not implement horizontal restaurant movement until all of the following exist:

- Stable instance ID plus unique process boot ID.
- `restaurant_execution` lease with monotonic execution epoch and database-time
  expiry.
- Every durable mutation and delayed callback validates execution epoch.
- `player_sessions` with session ID/epoch so stale quit/reconnect callbacks cannot
  clear a newer session.
- Explicit transfer attempts with sequence, reservation, destination readiness,
  arrival acknowledgement, failure, and expiry.
- Atomic capacity reservations rather than proxy-reported player maximum.
- Static exclusive world-directory ownership.
- World-operation claim token/epoch on renew, batch, and completion.

Until then, Velocity may front one tycoon Paper instance, but cross-instance visits,
co-op, active restaurant relocation, and failover are out of scope.

### 24.12 New crash/race acceptance matrix

Before expanding beyond the prototype, inject interruption or concurrency at:

1. Table reservation versus expiry/arrival and manual versus automated host.
2. Two collaborators accepting the same order.
3. Station slot claim before/after stock reservation and job creation.
4. Every recipe-step completion.
5. Entitlement creation, claim, inventory-full, death, quit, teleport, and recovery.
6. Serve versus cancellation and two simultaneous serve attempts.
7. Settlement versus close/reset/prestige and register collection versus cashier.
8. Dirty-table creation versus cleaning and all-tables-dirty admission.
9. Observed/unobserved conversion during every order state.
10. Entity unload/load/remove and duplicate physical-generation reconciliation.
11. Owner disconnect with collaborators remaining.
12. Database outage and process stop after each durable transition.

After each test, every nonterminal record must have exactly one valid owner and next
action; no table, stock unit, station slot, entitlement, villager, or payout may be
orphaned or duplicated.
