package vn.restauranttycoon.config;

import org.bukkit.configuration.file.FileConfiguration;

public record DatabaseSettings(
        boolean enabled,
        String host,
        int port,
        String name,
        String username,
        String password,
        int maximumPoolSize,
        long connectionTimeoutMs
) {
    static DatabaseSettings from(FileConfiguration config) {
        boolean enabled = config.getBoolean("database.enabled");
        String host = required(config, "database.host");
        String name = required(config, "database.name");
        String username = required(config, "database.username");
        String password = required(config, "database.password");
        int port = config.getInt("database.port");
        int poolSize = config.getInt("database.maximum-pool-size");
        long timeout = config.getLong("database.connection-timeout-ms");

        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("database.port must be between 1 and 65535");
        }
        if (poolSize < 1 || poolSize > 50) {
            throw new IllegalArgumentException("database.maximum-pool-size must be between 1 and 50");
        }
        if (timeout < 250 || timeout > 60_000) {
            throw new IllegalArgumentException(
                    "database.connection-timeout-ms must be between 250 and 60000");
        }
        if (enabled && password.equals("change-me")) {
            throw new IllegalArgumentException(
                    "database.password must be changed before enabling the database");
        }
        return new DatabaseSettings(
                enabled, host, port, name, username, password, poolSize, timeout);
    }

    public String jdbcUrl() {
        return "jdbc:postgresql://" + host + ":" + port + "/" + name;
    }

    private static String required(FileConfiguration config, String path) {
        String value = config.getString(path, "").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(path + " must not be blank");
        }
        return value;
    }
}
