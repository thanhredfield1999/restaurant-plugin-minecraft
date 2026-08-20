package vn.restauranttycoon.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class SupplyRuntimeSettingsTest {
    @Test
    void defaultsKeepRuntimeMovementDisabled() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("supply-runtime.enabled", false);
        config.set("supply-runtime.max-sessions", 4);
        config.set("supply-runtime.arrival-radius", 1.5);
        config.set("supply-runtime.max-speed", 0.25);
        config.set("supply-runtime.lease-renew-ticks", 20);

        SupplyRuntimeSettings settings = SupplyRuntimeSettings.from(config);

        assertFalse(settings.enabled());
        assertEquals(4, settings.maxSessions());
        assertEquals(1.5, settings.arrivalRadius());
        assertEquals(0.25, settings.maxSpeed());
        assertEquals(20, settings.leaseRenewTicks());
    }

    @Test
    void invalidMovementBoundsFailClosed() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("supply-runtime.enabled", true);
        config.set("supply-runtime.max-sessions", 0);
        config.set("supply-runtime.arrival-radius", -1.0);
        config.set("supply-runtime.max-speed", 0.0);
        config.set("supply-runtime.lease-renew-ticks", 0);

        assertThrows(IllegalArgumentException.class, () -> SupplyRuntimeSettings.from(config));
    }
}
