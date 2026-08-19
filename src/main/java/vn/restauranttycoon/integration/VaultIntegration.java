package vn.restauranttycoon.integration;

import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Server;
import org.bukkit.plugin.RegisteredServiceProvider;

/** Resolves an optional Vault economy provider on the Paper server thread. */
public final class VaultIntegration {
    private final Economy economy;

    private VaultIntegration(Economy economy) {
        this.economy = economy;
    }

    public static Optional<VaultIntegration> connect(Server server, Logger logger) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(logger, "logger");
        if (!server.getPluginManager().isPluginEnabled("Vault")) {
            logger.info("Vault bridge unavailable: Vault is not enabled; RestaurantTycoon ledger remains authoritative");
            return Optional.empty();
        }
        RegisteredServiceProvider<Economy> registration =
                server.getServicesManager().getRegistration(Economy.class);
        if (registration == null || registration.getProvider() == null
                || !registration.getProvider().isEnabled()) {
            logger.warning("Vault bridge unavailable: no enabled Economy provider; RestaurantTycoon ledger remains authoritative");
            return Optional.empty();
        }
        Economy provider = registration.getProvider();
        logger.info("Vault bridge connected to economy provider " + provider.getName()
                + "; no Vault debit or credit is enabled yet");
        return Optional.of(new VaultIntegration(provider));
    }

    public Economy economy() {
        return economy;
    }
}
