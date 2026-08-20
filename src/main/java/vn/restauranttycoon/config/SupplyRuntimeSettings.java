package vn.restauranttycoon.config;

import org.bukkit.configuration.file.FileConfiguration;

public record SupplyRuntimeSettings(
        boolean enabled,
        int maxSessions,
        double arrivalRadius,
        double maxSpeed,
        int leaseRenewTicks) {
    public SupplyRuntimeSettings {
        if (maxSessions < 1 || maxSessions > 100) {
            throw new IllegalArgumentException("supply-runtime.max-sessions must be between 1 and 100");
        }
        if (!Double.isFinite(arrivalRadius) || arrivalRadius <= 0.0 || arrivalRadius > 16.0) {
            throw new IllegalArgumentException("supply-runtime.arrival-radius must be > 0 and <= 16");
        }
        if (!Double.isFinite(maxSpeed) || maxSpeed <= 0.0 || maxSpeed > 2.0) {
            throw new IllegalArgumentException("supply-runtime.max-speed must be > 0 and <= 2");
        }
        if (leaseRenewTicks < 1 || leaseRenewTicks > 1200) {
            throw new IllegalArgumentException("supply-runtime.lease-renew-ticks must be between 1 and 1200");
        }
    }

    public static SupplyRuntimeSettings from(FileConfiguration config) {
        return new SupplyRuntimeSettings(
                config.getBoolean("supply-runtime.enabled", false),
                config.getInt("supply-runtime.max-sessions", 4),
                config.getDouble("supply-runtime.arrival-radius", 1.5),
                config.getDouble("supply-runtime.max-speed", 0.25),
                config.getInt("supply-runtime.lease-renew-ticks", 20));
    }
}
