package vn.restauranttycoon;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import vn.restauranttycoon.build.BukkitWorldProjectionApplier;
import vn.restauranttycoon.build.AuthoredStage;
import vn.restauranttycoon.build.BlockOffset;
import vn.restauranttycoon.build.ConfiguredPlotRegistry;
import vn.restauranttycoon.build.StageManifest;
import vn.restauranttycoon.build.StageManifestLoader;
import vn.restauranttycoon.config.PluginSettings;
import vn.restauranttycoon.drink.DrinkDispenserListener;
import vn.restauranttycoon.economy.CurrencyAmount;
import vn.restauranttycoon.economy.EconomyService;
import vn.restauranttycoon.economy.OperationKey;
import vn.restauranttycoon.persistence.DatabaseManager;
import vn.restauranttycoon.persistence.DatabaseState;
import vn.restauranttycoon.onboarding.OnboardingListener;
import vn.restauranttycoon.plot.PlotAssignmentService;
import vn.restauranttycoon.purchase.PurchaseRequest;
import vn.restauranttycoon.purchase.PurchaseService;
import vn.restauranttycoon.worldoperation.WorldOperationRepository;
import vn.restauranttycoon.worldoperation.WorldOperationWorker;
import vn.restauranttycoon.supplysetup.SupplySetupMenuController;
import vn.restauranttycoon.supply.SupplyFulfillmentRepository;
import vn.restauranttycoon.supply.SupplyRuntimeCoordinator;
import vn.restauranttycoon.supply.SupplyRuntimeWork;
import vn.restauranttycoon.supply.BukkitIngredientCatalogLoader;
import vn.restauranttycoon.supply.IngredientCatalog;
import vn.restauranttycoon.supply.SupplyOrderMenuController;
import vn.restauranttycoon.supplysetup.SupplyRouteMenuController;

public final class RestaurantTycoonPlugin extends JavaPlugin {
    private static final String FIXTURE_PROPERTY = "restauranttycoon.testFixtures";
    private static final String FIXTURE_CRASH_PROPERTY = "restauranttycoon.testCrashAfterCommit";
    private static final String FIXTURE_PLOT = "plot_1";
    private static final String FIXTURE_UNLOCK = "paper_smoke_stage_1";
    private static final long FIXTURE_GRANT = 500;
    private static final long FIXTURE_PRICE = 125;

    private PluginSettings settings;
    private DatabaseManager database;
    private EconomyService economy;
    private PlotAssignmentService plotAssignments;
    private PurchaseService purchases;
    private StageManifest stageManifest;
    private OnboardingListener onboardingListener;
    private DrinkDispenserListener drinkDispenserListener;
    private SupplySetupMenuController supplySetupMenuController;
    private SupplyRouteMenuController supplyRouteMenuController;
    private SupplyRuntimeCoordinator supplyRuntimeCoordinator;
    private SupplyOrderMenuController supplyOrderMenuController;
    private BukkitTask supplyRuntimePollTask;
    private BukkitTask worldOperationPollTask;
    private final AtomicBoolean worldOperationInFlight = new AtomicBoolean(false);
    private final AtomicBoolean fixtureCrashArmed = new AtomicBoolean(false);

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ConfiguredPlotRegistry plotRegistry;
        IngredientCatalog ingredientCatalog;
        try {
            settings = PluginSettings.from(getConfig());
            stageManifest = loadStageManifest();
            plotRegistry = new ConfiguredPlotRegistry(getServer(), settings.plots());
            ingredientCatalog = new BukkitIngredientCatalogLoader().load(getConfig());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            getLogger().severe("Invalid configuration: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        Objects.requireNonNull(getCommand("restaurant")).setExecutor(this);
        drinkDispenserListener = new DrinkDispenserListener(settings.drinkDispensers(), this);
        getServer().getPluginManager().registerEvents(drinkDispenserListener, this);
        database = new DatabaseManager(settings.database(), getLogger());
        economy = new EconomyService(database);
        plotAssignments = new PlotAssignmentService(database);
        purchases = new PurchaseService(database);
        supplyOrderMenuController = new SupplyOrderMenuController(
                this, database, ingredientCatalog);
        getServer().getPluginManager().registerEvents(supplyOrderMenuController, this);
        supplyRouteMenuController = new SupplyRouteMenuController(
                this,
                database,
                settings.plots().stream()
                        .map(vn.restauranttycoon.config.PlotSettings::plotId)
                        .collect(java.util.stream.Collectors.toSet()));
        getServer().getPluginManager().registerEvents(supplyRouteMenuController, this);
        supplySetupMenuController = new SupplySetupMenuController(
                this,
                database,
                settings.plots().stream()
                        .map(vn.restauranttycoon.config.PlotSettings::plotId)
                        .collect(java.util.stream.Collectors.toSet()),
                supplyRouteMenuController::openFromCommand);
        getServer().getPluginManager().registerEvents(supplySetupMenuController, this);
        database.start().whenComplete((ignored, error) -> runSync(() -> {
            if (error != null) {
                getLogger().severe("Database startup task failed: " + rootMessage(error));
                return;
            }
            if (database.state() == DatabaseState.READY) {
                startWorldOperationWorker(stageManifest, plotRegistry);
                startSupplyRuntimeCoordinator();
                startOnboarding();
            }
        }));
        getLogger().info(() -> "RestaurantTycoon enabled with config schema "
                + settings.schemaVersion() + "; custom content disabled");
    }

    @Override
    public void onDisable() {
        if (onboardingListener != null) {
            onboardingListener.close();
            onboardingListener = null;
        }
        if (drinkDispenserListener != null) {
            drinkDispenserListener.clear();
            drinkDispenserListener = null;
        }
        supplySetupMenuController = null;
        supplyRouteMenuController = null;
        if (supplyOrderMenuController != null) {
            supplyOrderMenuController.close();
            supplyOrderMenuController = null;
        }
        if (worldOperationPollTask != null) {
            worldOperationPollTask.cancel();
            worldOperationPollTask = null;
        }
        if (supplyRuntimePollTask != null) {
            supplyRuntimePollTask.cancel();
            supplyRuntimePollTask = null;
        }
        if (supplyRuntimeCoordinator != null) {
            supplyRuntimeCoordinator.close();
        }
        supplyRuntimeCoordinator = null;
        if (database != null) {
            database.close();
        }
    }

    private StageManifest loadStageManifest() {
        InputStream stream = getResource("stages.yml");
        if (stream == null) {
            throw new IllegalStateException("Missing bundled stages.yml");
        }
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return new StageManifestLoader().load(reader);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Could not close stages.yml", exception);
        }
    }

    private void startWorldOperationWorker(
            StageManifest stageManifest,
            ConfiguredPlotRegistry plotRegistry
    ) {
        WorldOperationRepository repository = new WorldOperationRepository(database.requireDataSource());
        BukkitWorldProjectionApplier applier = new BukkitWorldProjectionApplier(
                this,
                stageManifest,
                plotRegistry,
                claim -> CompletableFuture.runAsync(() -> {
                    try {
                        repository.assertCurrentFence(claim);
                        repository.renew(
                                claim,
                                Duration.ofSeconds(settings.worldOperations().leaseSeconds()));
                    } catch (java.sql.SQLException exception) {
                        throw new java.util.concurrent.CompletionException(exception);
                    }
                }, database.executor()),
                settings.worldOperations().blocksPerTick());
        WorldOperationWorker worker = new WorldOperationWorker(
                repository,
                applier,
                database.executor(),
                settings.worldOperations().instanceId(),
                Duration.ofSeconds(settings.worldOperations().leaseSeconds()));
        long pollTicks = settings.worldOperations().pollTicks();
        worldOperationPollTask = getServer().getScheduler().runTaskTimer(
                this,
                () -> pollWorldOperations(worker),
                pollTicks,
                pollTicks);
        getLogger().info(() -> "World-operation worker started as "
                + settings.worldOperations().instanceId());
    }

    private void pollWorldOperations(WorldOperationWorker worker) {
        if (database.state() != DatabaseState.READY
                || fixtureCrashArmed.get()
                || !worldOperationInFlight.compareAndSet(false, true)) {
            return;
        }
        worker.runOnce().whenComplete((result, error) -> {
            worldOperationInFlight.set(false);
            if (error != null && isEnabled()) {
                getLogger().warning("World-operation worker failed: " + rootMessage(error));
            }
        });
    }

    private void startSupplyRuntimeCoordinator() {
        SupplyFulfillmentRepository repository = new SupplyFulfillmentRepository(database.requireDataSource());
        supplyRuntimeCoordinator = new SupplyRuntimeCoordinator(
                repository,
                database.executor(),
                work -> getLogger().info(() -> "Durable supply work pending: " + work.shipmentId()
                        + " state=" + work.shipmentState()));
        supplyRuntimePollTask = getServer().getScheduler().runTaskTimer(
                this,
                () -> supplyRuntimeCoordinator.poll(),
                20L,
                20L);
        getLogger().info("Supply runtime coordinator started; Citizens adapter remains disabled until runtime contract is configured");
    }

    private void startOnboarding() {
        onboardingListener = new OnboardingListener(
                this,
                database,
                plotAssignments,
                settings.worldOperations().instanceId(),
                settings.plots());
        getServer().getPluginManager().registerEvents(onboardingListener, this);
        getServer().getOnlinePlayers().forEach(onboardingListener::begin);
        getLogger().info("Onboarding plot guidance started");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("health")) {
            return showHealth(sender);
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("balance")) {
            return showBalance(sender);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("grant")) {
            return grant(sender, args[1], args[2]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("setup")) {
            return openSupplySetup(sender, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("setup-route")) {
            return openSupplyRoute(sender, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("order")) {
            return openSupplyOrder(sender, args[1]);
        }
        if (args.length == 4
                && args[0].equalsIgnoreCase("dev")
                && args[1].equalsIgnoreCase("assign")) {
            return assignPlot(sender, args[2], args[3]);
        }
        if (args.length == 9
                && args[0].equalsIgnoreCase("dev")
                && args[1].equalsIgnoreCase("purchase")) {
            return purchase(sender, args);
        }
        if (args.length == 6
                && args[0].equalsIgnoreCase("dev")
                && args[1].equalsIgnoreCase("fixture")
                && args[2].equalsIgnoreCase("seed")) {
            return seedProjectionFixture(sender, args);
        }
        if (args.length == 4
                && args[0].equalsIgnoreCase("dev")
                && args[1].equalsIgnoreCase("fixture")
                && args[2].equalsIgnoreCase("verify")) {
            return verifyProjectionFixture(sender, args[3]);
        }
        if (args.length == 4
                && args[0].equalsIgnoreCase("dev")
                && args[1].equalsIgnoreCase("fixture")
                && args[2].equalsIgnoreCase("cleanup")) {
            return cleanupProjectionFixture(sender, args[3]);
        }
        return false;
    }

    private boolean openSupplySetup(CommandSender sender, String target) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can open the setup GUI.");
            return true;
        }
        return Objects.requireNonNull(supplySetupMenuController).openFromCommand(player, target);
    }

    private boolean openSupplyRoute(CommandSender sender, String plotId) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can open the route setup GUI.");
            return true;
        }
        return Objects.requireNonNull(supplyRouteMenuController).openFromCommand(player, plotId);
    }

    private boolean openSupplyOrder(CommandSender sender, String plotId) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Chỉ người chơi mới có thể mở GUI đặt hàng.");
            return true;
        }
        if (settings.plots().stream().noneMatch(plot -> plot.plotId().equals(plotId))) {
            sender.sendMessage("Không tìm thấy nhà hàng cấu hình: " + plotId);
            return true;
        }
        return Objects.requireNonNull(supplyOrderMenuController).openFromCommand(player, plotId);
    }

    private boolean seedProjectionFixture(CommandSender sender, String[] args) {
        if (!fixtureAllowed(sender)) {
            return true;
        }
        UUID accountId;
        UUID grantOperationId;
        UUID purchaseOperationId;
        try {
            accountId = UUID.fromString(args[3]);
            grantOperationId = UUID.fromString(args[4]);
            purchaseOperationId = UUID.fromString(args[5]);
        } catch (IllegalArgumentException exception) {
            sender.sendMessage("Fixture IDs must be UUIDs.");
            return true;
        }
        if (grantOperationId.equals(purchaseOperationId)) {
            sender.sendMessage("Grant and purchase operation UUIDs must be distinct.");
            return true;
        }
        if (stageManifest.find(FIXTURE_PLOT, 1).isEmpty()) {
            sender.sendMessage("Fixture stage is not authored.");
            return true;
        }
        boolean crashAfterCommit = Boolean.getBoolean(FIXTURE_CRASH_PROPERTY);
        if (crashAfterCommit && !fixtureCrashArmed.compareAndSet(false, true)) {
            sender.sendMessage("A fixture crash is already armed.");
            return true;
        }

        economy.grant(
                accountId,
                new CurrencyAmount(FIXTURE_GRANT),
                new OperationKey(grantOperationId))
                .thenCompose(ignored -> plotAssignments.assign(
                        FIXTURE_PLOT,
                        accountId,
                        settings.worldOperations().instanceId()))
                .thenCompose(assignment -> purchases.commit(new PurchaseRequest(
                        accountId,
                        new OperationKey(purchaseOperationId),
                        FIXTURE_UNLOCK,
                        1,
                        new CurrencyAmount(FIXTURE_PRICE),
                        FIXTURE_PLOT,
                        assignment.fenceToken(),
                        1)).thenApply(result -> new FixtureSeedResult(
                                assignment.fenceToken(), result)))
                .whenComplete((seed, error) -> runSync(() -> {
                    if (error != null) {
                        getLogger().warning("PROJECTION_FIXTURE_SEED_FAILED account=" + accountId
                                + " error=" + rootMessage(error));
                        return;
                    }
                    getLogger().info("PROJECTION_FIXTURE_SEEDED account=" + accountId
                            + " purchase=" + seed.purchase().purchaseId()
                            + " worldOperation=" + seed.purchase().worldOperationId()
                            + " fence=" + seed.fenceToken()
                            + " balance=" + seed.purchase().balance().units()
                            + " duplicate=" + seed.purchase().duplicate());
                    if (crashAfterCommit && !seed.purchase().duplicate()) {
                        getLogger().warning("PROJECTION_FIXTURE_CRASH_ARMED worldOperation="
                                + seed.purchase().worldOperationId());
                        getServer().getScheduler().runTaskLater(
                                this,
                                () -> Runtime.getRuntime().halt(86),
                                1L);
                    }
                }));
        return true;
    }

    private boolean verifyProjectionFixture(CommandSender sender, String operationText) {
        if (!fixtureAllowed(sender)) {
            return true;
        }
        UUID worldOperationId;
        try {
            worldOperationId = UUID.fromString(operationText);
        } catch (IllegalArgumentException exception) {
            sender.sendMessage("World-operation ID must be a UUID.");
            return true;
        }
        CompletableFuture.supplyAsync(() -> loadFixtureState(worldOperationId), database.executor())
                .whenComplete((fixture, error) -> runSync(() -> {
                    if (error != null) {
                        getLogger().warning("PROJECTION_FIXTURE_VERIFY_FAILED worldOperation="
                                + worldOperationId + " error=" + rootMessage(error));
                        return;
                    }
                    try {
                        verifyFixtureWorld(fixture);
                        getLogger().info("PROJECTION_FIXTURE_VERIFIED worldOperation="
                                + worldOperationId + " account=" + fixture.accountId()
                                + " fence=" + fixture.fenceToken()
                                + " stage=" + fixture.stageRevision());
                    } catch (RuntimeException exception) {
                        getLogger().warning("PROJECTION_FIXTURE_VERIFY_FAILED worldOperation="
                                + worldOperationId + " error=" + rootMessage(exception));
                    }
                }));
        return true;
    }

    private boolean fixtureAllowed(CommandSender sender) {
        if (!(sender instanceof ConsoleCommandSender)) {
            sender.sendMessage("Projection fixtures are console-only.");
            return false;
        }
        if (!Boolean.getBoolean(FIXTURE_PROPERTY)) {
            sender.sendMessage("Projection fixtures are disabled.");
            return false;
        }
        if (database.state() != DatabaseState.READY) {
            sender.sendMessage("Database is not ready.");
            return false;
        }
        return true;
    }

    private boolean cleanupProjectionFixture(CommandSender sender, String accountText) {
        if (!fixtureAllowed(sender)) {
            return true;
        }
        UUID accountId;
        try {
            accountId = UUID.fromString(accountText);
        } catch (IllegalArgumentException exception) {
            sender.sendMessage("Fixture account ID must be a UUID.");
            return true;
        }
        CompletableFuture.runAsync(() -> cleanupFixtureData(accountId), database.executor())
                .whenComplete((ignored, error) -> runSync(() -> {
                    if (error != null) {
                        getLogger().warning("PROJECTION_FIXTURE_CLEANUP_FAILED account="
                                + accountId + " error=" + rootMessage(error));
                        return;
                    }
                    getLogger().info("PROJECTION_FIXTURE_CLEANED account=" + accountId);
                }));
        return true;
    }

    private void cleanupFixtureData(UUID accountId) {
        try (Connection connection = database.requireDataSource().getConnection()) {
            connection.setAutoCommit(false);
            try {
                assertOnlyFixtureData(connection, accountId);
                executeFixtureDelete(connection, """
                        DELETE FROM plot_projection_state
                        WHERE last_world_operation_id IN (
                            SELECT w.world_operation_id
                            FROM world_operations w
                            JOIN purchases p ON p.purchase_id = w.purchase_id
                            WHERE p.account_id = ? AND p.unlock_id = ?)
                        """, accountId, true);
                executeFixtureDelete(connection, """
                        DELETE FROM world_operations
                        WHERE purchase_id IN (
                            SELECT purchase_id FROM purchases
                            WHERE account_id = ? AND unlock_id = ?)
                        """, accountId, true);
                executeFixtureDelete(connection,
                        "DELETE FROM purchases WHERE account_id = ? AND unlock_id = ?",
                        accountId,
                        true);
                executeFixtureDelete(connection,
                        "DELETE FROM unlocks WHERE account_id = ? AND unlock_id = ?",
                        accountId,
                        true);
                executeFixtureDelete(connection,
                        "DELETE FROM economy_ledger WHERE account_id = ?",
                        accountId,
                        false);
                executeFixtureDelete(connection,
                        "DELETE FROM plot_assignments WHERE plot_id = ? AND account_id = ?",
                        accountId,
                        false);
                executeFixtureDelete(connection,
                        "DELETE FROM economy_accounts WHERE account_id = ?",
                        accountId,
                        false);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new CompletionException(exception);
        }
    }

    private void assertOnlyFixtureData(Connection connection, UUID accountId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*)
                FROM purchases
                WHERE account_id = ?
                  AND (unlock_id <> ? OR plot_id <> ? OR target_stage_revision <> 1)
                """)) {
            statement.setObject(1, accountId);
            statement.setString(2, FIXTURE_UNLOCK);
            statement.setString(3, FIXTURE_PLOT);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                if (result.getLong(1) != 0) {
                    throw new IllegalStateException("Account owns non-fixture purchases");
                }
            }
        }
    }

    private void executeFixtureDelete(
            Connection connection,
            String sql,
            UUID accountId,
            boolean bindUnlock
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (sql.contains("plot_id = ? AND account_id = ?")) {
                statement.setString(1, FIXTURE_PLOT);
                statement.setObject(2, accountId);
            } else {
                statement.setObject(1, accountId);
                if (bindUnlock) {
                    statement.setString(2, FIXTURE_UNLOCK);
                }
            }
            statement.executeUpdate();
        }
    }

    private FixtureState loadFixtureState(UUID worldOperationId) {
        try (Connection connection = database.requireDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT p.account_id, w.state, w.phase, w.required_fence_token,
                            w.target_stage_revision, a.balance,
                            s.fence_token, s.stage_revision, s.last_world_operation_id
                     FROM world_operations w
                     JOIN purchases p ON p.purchase_id = w.purchase_id
                     JOIN economy_accounts a ON a.account_id = p.account_id
                     LEFT JOIN plot_projection_state s ON s.plot_id = w.plot_id
                     WHERE w.world_operation_id = ?
                       AND w.plot_id = ? AND p.unlock_id = ?
                     """)) {
            statement.setObject(1, worldOperationId);
            statement.setString(2, FIXTURE_PLOT);
            statement.setString(3, FIXTURE_UNLOCK);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalStateException("Fixture world operation was not found");
                }
                if (!"APPLIED".equals(result.getString(2))
                        || !"COMPLETE".equals(result.getString(3))) {
                    throw new IllegalStateException("World operation is not applied yet");
                }
                UUID checkpointOperation = result.getObject(9, UUID.class);
                if (result.getLong(6) != FIXTURE_GRANT - FIXTURE_PRICE
                        || result.getLong(7) != result.getLong(4)
                        || result.getLong(8) != result.getLong(5)
                        || !worldOperationId.equals(checkpointOperation)) {
                    throw new IllegalStateException("Fixture database checkpoint does not match");
                }
                return new FixtureState(
                        result.getObject(1, UUID.class),
                        result.getLong(4),
                        result.getLong(5),
                        worldOperationId);
            }
        } catch (SQLException exception) {
            throw new CompletionException(exception);
        }
    }

    private void verifyFixtureWorld(FixtureState fixture) {
        AuthoredStage stage = stageManifest.find(FIXTURE_PLOT, fixture.stageRevision())
                .orElseThrow(() -> new IllegalStateException("Fixture stage disappeared"));
        var plot = settings.plots().stream()
                .filter(candidate -> candidate.plotId().equals(FIXTURE_PLOT))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Fixture plot is not configured"));
        World world = Objects.requireNonNull(
                getServer().getWorld(plot.worldName()), "Fixture world is not loaded");
        Map<BlockOffset, String> authored = stage.blockDataByOffset();
        for (int x = 0; x < stage.volume().sizeX(); x++) {
            for (int y = 0; y < stage.volume().sizeY(); y++) {
                for (int z = 0; z < stage.volume().sizeZ(); z++) {
                    BlockOffset offset = new BlockOffset(x, y, z);
                    String expected = authored.getOrDefault(offset, "minecraft:air");
                    String actual = world.getBlockAt(
                                    plot.originX() + x,
                                    plot.originY() + y,
                                    plot.originZ() + z)
                            .getBlockData()
                            .getAsString();
                    if (!expected.equals(actual)) {
                        throw new IllegalStateException(
                                "Block mismatch at " + offset + ": expected "
                                        + expected + " but was " + actual);
                    }
                }
            }
        }
    }

    private boolean showHealth(CommandSender sender) {
        if (!sender.hasPermission("restauranttycoon.admin.health")) {
            sender.sendMessage("You do not have permission to view plugin health.");
            return true;
        }

        List<String> health = List.of(
                "RestaurantTycoon " + getPluginMeta().getVersion(),
                "State: READY",
                "Database: " + database.state(),
                "Custom content: DISABLED",
                "Active plot limit: " + settings.maxActivePlots(),
                "Customer limit per plot: " + settings.maxPhysicalCustomersPerPlot());
        health.forEach(sender::sendMessage);
        return true;
    }

    private boolean showBalance(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players have a restaurant balance.");
            return true;
        }
        economy.balance(player.getUniqueId()).whenComplete((balance, error) ->
                runSync(() -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (error != null) {
                        player.sendMessage("Economy is temporarily unavailable.");
                        getLogger().warning("Balance query failed for " + player.getUniqueId()
                                + ": " + rootMessage(error));
                        return;
                    }
                    player.sendMessage("Restaurant balance: " + balance.units());
                }));
        return true;
    }

    private boolean grant(CommandSender sender, String playerName, String amountText) {
        if (!sender.hasPermission("restauranttycoon.admin.grant")) {
            sender.sendMessage("You do not have permission to grant currency.");
            return true;
        }
        Player target = getServer().getPlayerExact(playerName);
        if (target == null) {
            sender.sendMessage("Target player must be online.");
            return true;
        }
        long amount;
        try {
            amount = Long.parseLong(amountText);
            if (amount <= 0) {
                throw new NumberFormatException("not positive");
            }
        } catch (NumberFormatException exception) {
            sender.sendMessage("Amount must be a positive whole number.");
            return true;
        }

        UUID operationId = UUID.randomUUID();
        economy.grant(target.getUniqueId(), new CurrencyAmount(amount), new OperationKey(operationId))
                .whenComplete((result, error) -> runSync(() -> {
                    if (error != null) {
                        sender.sendMessage("Grant failed; economy is unavailable. Operation: " + operationId);
                        getLogger().warning("Admin grant failed for operation " + operationId
                                + ": " + rootMessage(error));
                        return;
                    }
                    sender.sendMessage("Granted " + amount + " to " + target.getName()
                            + ". Balance: " + result.balance().units()
                            + ". Operation: " + operationId);
                }));
        return true;
    }

    private boolean assignPlot(CommandSender sender, String playerName, String plotId) {
        if (!sender.hasPermission("restauranttycoon.admin.dev")) {
            sender.sendMessage("You do not have permission to use development commands.");
            return true;
        }
        Player target = getServer().getPlayerExact(playerName);
        if (target == null) {
            sender.sendMessage("Target player must be online.");
            return true;
        }
        if (settings.plots().stream().noneMatch(plot -> plot.plotId().equals(plotId))) {
            sender.sendMessage("Unknown configured plot: " + plotId);
            return true;
        }
        plotAssignments.assign(
                plotId,
                target.getUniqueId(),
                settings.worldOperations().instanceId())
                .whenComplete((assignment, error) -> runSync(() -> {
                    if (error != null) {
                        sender.sendMessage("Plot assignment failed: " + rootMessage(error));
                        return;
                    }
                    sender.sendMessage("Assigned " + assignment.plotId() + " to "
                            + target.getName() + " with fence " + assignment.fenceToken() + ".");
                }));
        return true;
    }

    private boolean purchase(CommandSender sender, String[] args) {
        if (!sender.hasPermission("restauranttycoon.admin.dev")) {
            sender.sendMessage("You do not have permission to use development commands.");
            return true;
        }
        Player target = getServer().getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage("Target player must be online.");
            return true;
        }
        String plotId = args[3];
        String unlockId = args[4];
        long definitionVersion;
        long stageRevision;
        long price;
        UUID operationId;
        try {
            definitionVersion = positiveLong(args[5], "Definition version");
            stageRevision = positiveLong(args[6], "Stage revision");
            price = positiveLong(args[7], "Price");
            operationId = UUID.fromString(args[8]);
        } catch (IllegalArgumentException exception) {
            sender.sendMessage(exception.getMessage());
            return true;
        }
        if (stageManifest.find(plotId, stageRevision).isEmpty()) {
            sender.sendMessage("No authored stage for " + plotId + " revision " + stageRevision + ".");
            return true;
        }
        UUID accountId = target.getUniqueId();
        plotAssignments.find(plotId).thenCompose(assignment -> {
            var current = assignment.orElseThrow(() -> new IllegalStateException(
                    "Plot is not assigned: " + plotId));
            if (!current.accountId().equals(java.util.Optional.of(accountId))) {
                throw new IllegalStateException("Plot is not assigned to " + target.getName());
            }
            return purchases.commit(new PurchaseRequest(
                    accountId,
                    new OperationKey(operationId),
                    unlockId,
                    definitionVersion,
                    new CurrencyAmount(price),
                    plotId,
                    current.fenceToken(),
                    stageRevision));
        }).whenComplete((result, error) -> runSync(() -> {
            if (error != null) {
                sender.sendMessage("Purchase failed: " + rootMessage(error)
                        + ". Operation: " + operationId);
                return;
            }
            sender.sendMessage((result.duplicate() ? "Purchase retry confirmed" : "Purchase committed")
                    + ". Purchase: " + result.purchaseId()
                    + ", world operation: " + result.worldOperationId()
                    + ", balance: " + result.balance().units()
                    + ", operation: " + operationId + ".");
        }));
        return true;
    }

    private long positiveLong(String value, String name) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed < 1) {
                throw new NumberFormatException("not positive");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be a positive whole number.");
        }
    }

    private void runSync(Runnable action) {
        if (isEnabled()) {
            getServer().getScheduler().runTask(this, action);
        }
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null
                ? current.getClass().getSimpleName()
                : current.getMessage();
    }

    private record FixtureSeedResult(long fenceToken, vn.restauranttycoon.purchase.PurchaseResult purchase) {
    }

    private record FixtureState(
            UUID accountId,
            long fenceToken,
            long stageRevision,
            UUID worldOperationId
    ) {
    }
}
