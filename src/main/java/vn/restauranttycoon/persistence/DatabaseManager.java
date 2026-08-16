package vn.restauranttycoon.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import vn.restauranttycoon.config.DatabaseSettings;

public final class DatabaseManager implements AutoCloseable {
    private static final String REQUIRED_SCHEMA_VERSION = "12";

    private final DatabaseSettings settings;
    private final Logger logger;
    private final ExecutorService executor;
    private final AtomicReference<DatabaseState> state;
    private final AtomicBoolean closed;
    private final Object lifecycleLock;
    private volatile HikariDataSource dataSource;

    public DatabaseManager(DatabaseSettings settings, Logger logger) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "restauranttycoon-database");
            thread.setDaemon(true);
            return thread;
        });
        this.state = new AtomicReference<>(
                settings.enabled() ? DatabaseState.STARTING : DatabaseState.DISABLED);
        this.closed = new AtomicBoolean(false);
        this.lifecycleLock = new Object();
    }

    public CompletableFuture<Void> start() {
        if (!settings.enabled()) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() -> {
            HikariDataSource candidate = null;
            try {
                HikariConfig hikari = new HikariConfig();
                hikari.setJdbcUrl(settings.jdbcUrl());
                hikari.setDriverClassName("org.postgresql.Driver");
                hikari.setUsername(settings.username());
                hikari.setPassword(settings.password());
                hikari.setMaximumPoolSize(settings.maximumPoolSize());
                hikari.setMinimumIdle(1);
                hikari.setConnectionTimeout(settings.connectionTimeoutMs());
                hikari.setPoolName("RestaurantTycoonPool");
                hikari.setAutoCommit(false);

                candidate = new HikariDataSource(hikari);
                Flyway flyway = Flyway.configure(DatabaseManager.class.getClassLoader())
                        .dataSource(candidate)
                        .locations("classpath:db/migration")
                        .validateMigrationNaming(true)
                        .load();
                flyway.migrate();
                MigrationInfo current = flyway.info().current();
                if (current == null
                        || !REQUIRED_SCHEMA_VERSION.equals(current.getVersion().getVersion())) {
                    throw new IllegalStateException(
                            "Database schema must be at version " + REQUIRED_SCHEMA_VERSION);
                }
                synchronized (lifecycleLock) {
                    if (closed.get()) {
                        candidate.close();
                        return;
                    }
                    dataSource = candidate;
                    state.set(DatabaseState.READY);
                }
                logger.info("Database connected and migrations completed");
            } catch (RuntimeException exception) {
                synchronized (lifecycleLock) {
                    if (!closed.get()) {
                        state.set(DatabaseState.DEGRADED);
                    }
                    dataSource = null;
                }
                if (candidate != null) {
                    candidate.close();
                }
                if (!closed.get()) {
                    logger.log(
                            Level.SEVERE,
                            "Database initialization failed; economy is degraded",
                            exception);
                }
            }
        }, executor);
    }

    public DatabaseState state() {
        return state.get();
    }

    public DataSource requireDataSource() {
        HikariDataSource current = dataSource;
        if (state.get() != DatabaseState.READY || current == null) {
            throw new DatabaseUnavailableException(state.get());
        }
        return current;
    }

    public ExecutorService executor() {
        return executor;
    }

    @Override
    public void close() {
        HikariDataSource current;
        synchronized (lifecycleLock) {
            closed.set(true);
            state.set(DatabaseState.STOPPED);
            current = dataSource;
            dataSource = null;
        }
        if (current != null) {
            current.close();
        }
        executor.shutdownNow();
    }
}
