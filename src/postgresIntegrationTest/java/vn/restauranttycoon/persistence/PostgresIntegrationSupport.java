package vn.restauranttycoon.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.postgresql.ds.PGSimpleDataSource;

abstract class PostgresIntegrationSupport {
    private String schema;
    protected DataSource dataSource;

    @BeforeEach
    void createIsolatedSchema() throws SQLException {
        schema = "rt_test_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = adminConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
        }
        PGSimpleDataSource postgres = new PGSimpleDataSource();
        postgres.setURL(required("RT_TEST_POSTGRES_URL"));
        postgres.setUser(required("RT_TEST_POSTGRES_USER"));
        postgres.setPassword(required("RT_TEST_POSTGRES_PASSWORD"));
        postgres.setCurrentSchema(schema);
        dataSource = postgres;
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @AfterEach
    void dropIsolatedSchema() throws SQLException {
        if (schema == null) {
            return;
        }
        try (Connection connection = adminConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    protected String schema() {
        return schema;
    }

    private Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(
                required("RT_TEST_POSTGRES_URL"),
                required("RT_TEST_POSTGRES_USER"),
                required("RT_TEST_POSTGRES_PASSWORD"));
    }

    private String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set for PostgreSQL integration tests");
        }
        return value;
    }
}
